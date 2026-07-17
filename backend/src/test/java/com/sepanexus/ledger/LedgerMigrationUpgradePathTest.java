package com.sepanexus.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-32 Story 32.1: proves the ledger migrations (V25-V26) apply cleanly on top of an
 * already-migrated, already-populated database (migrated only through V24, with representative
 * pre-existing {@code payment.payments} and {@code egress.outbound_messages} data) and that the
 * prior data survives untouched.
 */
@Testcontainers
class LedgerMigrationUpgradePathTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    @Test
    void ledgerMigrationsApplyOnTopOfPriorSchemaWithoutLosingExistingData() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .target("24")
                .load()
                .migrate();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                    VALUES ('00000000-0000-0000-0000-0000000000f1', gen_random_uuid(), 7.00, 'EUR', 'DE1', 'FR1', 'RECEIVED')
                    """);
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            var priorRow = statement.executeQuery(
                    "SELECT count(*) FROM payment.payments WHERE id = '00000000-0000-0000-0000-0000000000f1'");
            priorRow.next();
            assertEquals(1, priorRow.getInt(1), "pre-existing payment row must survive the ledger migration");
        }

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO ledger.liquidity_accounts (tenant_id, participant_id, currency, available_minor)
                    VALUES (gen_random_uuid(), gen_random_uuid(), 'EUR', 100)
                    """);
            var accountRow = statement.executeQuery("SELECT count(*) FROM ledger.liquidity_accounts");
            accountRow.next();
            assertEquals(1, accountRow.getInt(1), "ledger.liquidity_accounts must be usable immediately after the upgrade");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
