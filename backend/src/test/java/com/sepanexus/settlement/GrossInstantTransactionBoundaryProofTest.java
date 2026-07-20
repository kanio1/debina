package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.ledger.JdbcLedgerPort;
import com.sepanexus.ledger.LedgerConnectionFactory;
import com.sepanexus.ledger.LedgerPort;
import com.sepanexus.modules.paymentlifecycle.service.JdbcPaymentFinalityProjection;
import com.sepanexus.shared.ClockPort;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL 18 proof of the current transaction boundary, deliberately not an implementation of
 * EPIC-33/36. It records the real transaction IDs of the four existing operations. A strategy
 * must not call this four-commit sequence "one transaction".
 */
@SpringBootTest(classes = SepaNexusApplication.class)
class GrossInstantTransactionBoundaryProofTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    private static final Instant NOW = Instant.parse("2026-07-20T13:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000003306");
    private static boolean initialized;

    @Autowired
    private JdbcPaymentFinalityProjection paymentProjection;

    private JdbcLedgerPort ledger;
    private SettlementFinalityService finality;
    private UUID paymentId;
    private UUID debtor;
    private UUID creditor;
    private UUID attempt;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
        registry.add("ledger.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("ledger.datasource.username", () -> "ledger_role");
        registry.add("ledger.datasource.password", () -> "dev-only-ledger");
        registry.add("settlement.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("settlement.datasource.username", () -> "settlement_role");
        registry.add("settlement.datasource.password", () -> "dev-only-settlement");
    }

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        ledger = new JdbcLedgerPort(
                new LedgerConnectionFactory(POSTGRES.getJdbcUrl(), "ledger_role", "dev-only-ledger"), () -> NOW);
        finality = new SettlementFinalityService(
                new SettlementConnectionFactory(POSTGRES.getJdbcUrl(), "settlement_role", "dev-only-settlement"),
                paymentProjection);
        paymentId = UUID.randomUUID();
        attempt = UUID.randomUUID();
        debtor = account(1_000);
        creditor = account(100);
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, debtor_iban, creditor_iban)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, paymentId);
            statement.setObject(2, TENANT);
            statement.setBigDecimal(3, new BigDecimal("4.00"));
            statement.setString(4, "DE89370400440532013000");
            statement.setString(5, "FR7630006000011234567890189");
            statement.executeUpdate();
        }
    }

    @Test
    void currentDedicatedRoleImplementationsCommitReservePostFinalityAndProjectionInFourTransactions() throws Exception {
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) ledger.reserve(attempt, paymentId, debtor, 400);
        LedgerPort.TerminalResult post = ledger.post(reserved.reservationId(), creditor, UUID.randomUUID());

        finality.recordLedgerPost(TENANT, attempt, paymentId, post, new byte[] {3, 3, 0, 6});

        Set<Long> transactionIds = new LinkedHashSet<>();
        transactionIds.add(auditTxid("ledger", "RESERVE"));
        transactionIds.add(auditTxid("ledger", "POST"));
        transactionIds.add(auditTxid("settlement", "FINALITY"));
        transactionIds.add(auditTxid("payment", "PROJECTION"));
        assertThat(transactionIds).hasSize(4);
        assertThat(booleanValue("SELECT finality_record_id IS NOT NULL FROM payment.payments WHERE id = '" + paymentId + "'"))
                .isTrue();
    }

    @Test
    void projectionFailureAfterCommittedPostAndFinalityLeavesAnExplicitRecoveryWindow() throws Exception {
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) ledger.reserve(attempt, paymentId, debtor, 400);
        LedgerPort.TerminalResult post = ledger.post(reserved.reservationId(), creditor, UUID.randomUUID());
        installProjectionFailure();

        assertThatThrownBy(() -> finality.recordLedgerPost(TENANT, attempt, paymentId, post, new byte[] {7}))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + paymentId
                + "' AND entry_type = 'POST'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(booleanValue("SELECT finality_record_id IS NULL FROM payment.payments WHERE id = '" + paymentId + "'"))
                .isTrue();
        assertThat(count("SELECT count(*) FROM payment.gross_instant_tx_audit WHERE stage = 'PROJECTION'"))
                .isZero();
    }

    private static synchronized void initializeDatabase() {
        if (initialized) return;
        POSTGRES.start();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception);
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        installAuditTriggers();
        initialized = true;
    }

    private static void installAuditTriggers() {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ledger.gross_instant_tx_audit (stage text PRIMARY KEY, txid bigint NOT NULL)");
            statement.execute("CREATE TABLE settlement.gross_instant_tx_audit (stage text PRIMARY KEY, txid bigint NOT NULL)");
            statement.execute("CREATE TABLE payment.gross_instant_tx_audit (stage text PRIMARY KEY, txid bigint NOT NULL)");
            statement.execute("GRANT SELECT, INSERT ON ledger.gross_instant_tx_audit TO ledger_role");
            statement.execute("GRANT SELECT, INSERT ON settlement.gross_instant_tx_audit TO settlement_role");
            statement.execute("GRANT SELECT, INSERT ON payment.gross_instant_tx_audit TO sepa_app");
            statement.execute("""
                    CREATE FUNCTION ledger.capture_gross_instant_tx() RETURNS trigger AS $$
                    BEGIN
                        INSERT INTO ledger.gross_instant_tx_audit VALUES (NEW.entry_type, txid_current());
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE FUNCTION settlement.capture_gross_instant_tx() RETURNS trigger AS $$
                    BEGIN
                        INSERT INTO settlement.gross_instant_tx_audit VALUES ('FINALITY', txid_current());
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE FUNCTION payment.capture_gross_instant_tx() RETURNS trigger AS $$
                    BEGIN
                        INSERT INTO payment.gross_instant_tx_audit VALUES ('PROJECTION', txid_current());
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE TRIGGER ledger_capture_gross_instant_tx AFTER INSERT ON ledger.journal_entries
                    FOR EACH ROW WHEN (NEW.entry_type IN ('RESERVE', 'POST'))
                    EXECUTE FUNCTION ledger.capture_gross_instant_tx()
                    """);
            statement.execute("""
                    CREATE TRIGGER settlement_capture_gross_instant_tx AFTER INSERT ON settlement.settlement_finality_records
                    FOR EACH ROW EXECUTE FUNCTION settlement.capture_gross_instant_tx()
                    """);
            statement.execute("""
                    CREATE TRIGGER payment_capture_gross_instant_tx AFTER UPDATE OF finality_record_id ON payment.payments
                    FOR EACH ROW WHEN (NEW.finality_record_id IS NOT NULL)
                    EXECUTE FUNCTION payment.capture_gross_instant_tx()
                    """);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot install transaction-boundary audit triggers", exception);
        }
    }

    private void clearData() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payments, settlement.settlement_finality_records, settlement.settlement_profile_snapshots, ledger.reservations, ledger.journal_lines, ledger.journal_entries, ledger.liquidity_accounts CASCADE");
            statement.execute("TRUNCATE ledger.gross_instant_tx_audit, settlement.gross_instant_tx_audit, payment.gross_instant_tx_audit");
            statement.execute("DROP TRIGGER IF EXISTS gross_instant_projection_failure ON payment.payments");
            statement.execute("DROP FUNCTION IF EXISTS payment.fail_gross_instant_projection()");
        }
    }

    private void installProjectionFailure() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION payment.fail_gross_instant_projection() RETURNS trigger AS $$
                    BEGIN RAISE EXCEPTION 'projection fault'; END;
                    $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE TRIGGER gross_instant_projection_failure BEFORE UPDATE OF finality_record_id ON payment.payments
                    FOR EACH ROW EXECUTE FUNCTION payment.fail_gross_instant_projection()
                    """);
        }
    }

    private UUID account(long available) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                VALUES (?, ?, gen_random_uuid(), 'EUR', ?)
                """)) {
            statement.setObject(1, id); statement.setObject(2, TENANT); statement.setLong(3, available);
            statement.executeUpdate();
        }
        return id;
    }

    private static long auditTxid(String schema, String stage) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(
                "SELECT txid FROM " + schema + ".gross_instant_tx_audit WHERE stage = ?")) {
            statement.setString(1, stage);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private static int count(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static String value(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static boolean booleanValue(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
