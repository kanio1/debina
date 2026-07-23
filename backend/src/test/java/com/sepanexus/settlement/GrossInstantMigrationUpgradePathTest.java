package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 18 proof that the ADR-N11 migrations upgrade an existing V34 database in place. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class GrossInstantMigrationUpgradePathTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test
    void v34DatabaseUpgradesToTheGrossInstantCommandSurfaces_withoutLosingExistingData() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        migrate("34");
        UUID tenant = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        UUID debtor = account(tenant, 1_000);
        UUID creditor = account(tenant, 100);
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (?, ?, ?, 'EUR', 'DE89370400440532013000', 'FR7630006000011234567890189', 'VALIDATED')
                """)) {
            statement.setObject(1, payment); statement.setObject(2, tenant); statement.setBigDecimal(3, new BigDecimal("4.00"));
            statement.executeUpdate();
        }

        migrate(null);

        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT status, amount, finality_at, finality_record_id FROM payment.payments WHERE id = ?
                """)) {
            statement.setObject(1, payment);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("VALIDATED");
                assertThat(result.getBigDecimal(2)).hasToString("4.00");
                assertThat(result.getObject(3)).isNull();
                assertThat(result.getObject(4)).isNull();
            }
        }
        assertThat(functionCount()).isEqualTo(5);
        assertThat(booleanValue("SELECT has_table_privilege('gross_instant_executor_role', 'ledger.journal_entries', 'INSERT')"))
                .isFalse();

        UUID attempt = UUID.randomUUID();
        UUID command = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T15:00:00Z");
        try (Connection connection = executor()) {
            connection.setAutoCommit(false);
            try (PreparedStatement tenantGuc = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                tenantGuc.setString(1, tenant.toString()); tenantGuc.execute();
            }
            UUID reserve; UUID post;
            try (PreparedStatement ledger = connection.prepareStatement("""
                    SELECT reservation_id, terminal_entry_id FROM ledger.gross_instant_reserve_post(?, ?, ?, ?, ?, 400,
                        'EUR', ?, ?)
                    """)) {
                ledger.setObject(1, tenant); ledger.setObject(2, attempt); ledger.setObject(3, payment);
                ledger.setObject(4, debtor); ledger.setObject(5, creditor); ledger.setObject(6, command);
                ledger.setObject(7, java.sql.Timestamp.from(occurredAt));
                try (ResultSet result = ledger.executeQuery()) { assertThat(result.next()).isTrue(); reserve = result.getObject(1, UUID.class); post = result.getObject(2, UUID.class); }
            }
            UUID finality;
            try (PreparedStatement settlement = connection.prepareStatement("""
                    SELECT finality_record_id FROM settlement.record_gross_instant_post(?, ?, ?, ?, ?, ?, 400, 'EUR', ?, ?)
                    """)) {
                settlement.setObject(1, tenant); settlement.setObject(2, attempt); settlement.setObject(3, payment);
                settlement.setObject(4, command); settlement.setObject(5, reserve); settlement.setObject(6, post);
                settlement.setObject(7, java.sql.Timestamp.from(occurredAt)); settlement.setBytes(8, new byte[] {4, 3});
                try (ResultSet result = settlement.executeQuery()) { assertThat(result.next()).isTrue(); finality = result.getObject(1, UUID.class); }
            }
            try (PreparedStatement projection = connection.prepareStatement("""
                    SELECT projection_outcome FROM payment.project_gross_instant_finality(?, ?, ?, ?, ?, ?)
                    """)) {
                projection.setObject(1, tenant); projection.setObject(2, payment); projection.setObject(3, command);
                projection.setObject(4, finality); projection.setObject(5, java.sql.Timestamp.from(occurredAt));
                projection.setObject(6, java.sql.Timestamp.from(occurredAt));
                try (ResultSet result = projection.executeQuery()) { assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("PROJECTED"); }
            }
            connection.commit();
        }
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + payment + "'"))
                .isEqualTo(1);
    }

    private UUID account(UUID tenant, long available) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                VALUES (?, ?, gen_random_uuid(), 'EUR', ?)
                """)) {
            statement.setObject(1, id); statement.setObject(2, tenant); statement.setLong(3, available); statement.executeUpdate();
        }
        return id;
    }

    private void migrate(String target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) config.target(target);
        config.load().migrate();
    }
    private static long functionCount() throws Exception { return count("""
            SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE (n.nspname, p.proname) IN (('ledger','gross_instant_reserve_post'),
              ('settlement','record_gross_instant_post'), ('settlement','record_gross_instant_insufficient_liquidity'),
              ('payment','project_gross_instant_finality'), ('payment','record_gross_instant_insufficient_liquidity'))
            """); }
    private static boolean booleanValue(String sql) throws Exception { return Boolean.parseBoolean(value(sql)); }
    private static long count(String sql) throws Exception { return Long.parseLong(value(sql)); }
    private static String value(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue(); return result.getString(1);
        }
    }
    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection executor() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "gross_instant_executor_role", "dev-only-gross-instant-executor"); }
}
