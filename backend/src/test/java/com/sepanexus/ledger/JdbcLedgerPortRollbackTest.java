package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.shared.ClockPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Failure-injection proof that each LedgerPort command leaves one atomic money effect or none. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class JdbcLedgerPortRollbackTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    private static JdbcLedgerPort port;

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        ClockPort clock = () -> Instant.parse("2026-07-20T10:15:30Z");
        port = new JdbcLedgerPort(
                new LedgerConnectionFactory(POSTGRES.getJdbcUrl(), "ledger_role", "dev-only-ledger"), clock);
    }

    @Test
    void postFailureAfterEntryCreationRollsBackEntryLinesAccountsAndReservationTransition() throws Exception {
        UUID debtor = account("EUR", 1_000);
        UUID creditor = account("EUR", 100);
        UUID payment = UUID.randomUUID();
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) port.reserve(UUID.randomUUID(), payment, debtor, 400);
        installPostLineFailureTrigger();

        assertThatThrownBy(() -> port.post(reserved.reservationId(), creditor, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not consume reservation");

        assertThat(balance(debtor)).containsExactly(600L, 400L);
        assertThat(balance(creditor)).containsExactly(100L, 0L);
        assertThat(string("SELECT state FROM ledger.reservations WHERE id = '" + reserved.reservationId() + "'"))
                .isEqualTo("ACTIVE");
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + payment + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ledger.journal_lines WHERE entry_id = '" + reserved.reserveEntryId() + "'"))
                .isEqualTo(2);
    }

    @Test
    void creditorCurrencyFailureRollsBackWithoutTerminalEffect() throws Exception {
        UUID debtor = account("EUR", 1_000);
        UUID usdCreditor = account("USD", 100);
        UUID payment = UUID.randomUUID();
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) port.reserve(UUID.randomUUID(), payment, debtor, 400);

        assertThatThrownBy(() -> port.post(reserved.reservationId(), usdCreditor, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Creditor account currency");

        assertThat(balance(debtor)).containsExactly(600L, 400L);
        assertThat(balance(usdCreditor)).containsExactly(100L, 0L);
        assertThat(string("SELECT state FROM ledger.reservations WHERE id = '" + reserved.reservationId() + "'"))
                .isEqualTo("ACTIVE");
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + payment + "'"))
                .isEqualTo(1);
    }

    private static void installPostLineFailureTrigger() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION ledger.fail_injected_post_line() RETURNS trigger AS $$
                    BEGIN
                        IF EXISTS (SELECT 1 FROM ledger.journal_entries WHERE id = NEW.entry_id AND entry_type = 'POST') THEN
                            RAISE EXCEPTION 'injected post line failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE TRIGGER ledger_injected_post_line_failure
                    BEFORE INSERT ON ledger.journal_lines
                    FOR EACH ROW EXECUTE FUNCTION ledger.fail_injected_post_line()
                    """);
        }
    }

    private static UUID account(String currency, long available) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                VALUES (?, gen_random_uuid(), gen_random_uuid(), ?, ?)
                """)) {
            statement.setObject(1, id); statement.setString(2, currency); statement.setLong(3, available);
            statement.executeUpdate();
        }
        return id;
    }

    private static long[] balance(UUID account) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT available_minor, reserved_minor FROM ledger.liquidity_accounts WHERE id = ?
                """)) {
            statement.setObject(1, account);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new long[] {result.getLong(1), result.getLong(2)};
            }
        }
    }

    private static int count(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String string(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
