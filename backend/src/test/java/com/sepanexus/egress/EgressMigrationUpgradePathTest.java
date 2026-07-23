package com.sepanexus.egress;

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
 * EPIC-43 Story 43.1: proves the egress migrations (V22-V24) apply cleanly on top of an
 * already-migrated, already-populated database (migrated only through V21, with a representative
 * pre-existing {@code payment.payments} row) — not just on a pristine empty schema — and that the
 * prior data survives untouched.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class EgressMigrationUpgradePathTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    @Test
    void egressMigrationsApplyOnTopOfPriorSchemaWithoutLosingExistingData() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .target("21")
                .load()
                .migrate();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            // test_admin (superuser) bypasses RLS — this insert only seeds representative prior
            // data for the upgrade-path check, it is not itself testing tenant isolation.
            statement.execute("""
                    INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                    VALUES ('00000000-0000-0000-0000-0000000000e1', gen_random_uuid(), 42.00, 'EUR', 'DE1', 'FR1', 'RECEIVED')
                    """);
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            var priorRow = statement.executeQuery(
                    "SELECT count(*) FROM payment.payments WHERE id = '00000000-0000-0000-0000-0000000000e1'");
            priorRow.next();
            assertEquals(1, priorRow.getInt(1), "pre-existing payment row must survive the egress migration");
        }

        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "egress_role", "dev-only-egress");
                Statement statement = connection.createStatement()) {
            statement.execute("SET app.role = 'system_relay'");
            int inserted = statement.executeUpdate("""
                    INSERT INTO egress.outbound_messages
                        (tenant_id, recipient_id, channel, artifact_type, correlation_msg_id, payload, payload_sha256)
                    VALUES (gen_random_uuid(), gen_random_uuid(), 'WEBHOOK', 'PACS002', 'corr-upgrade', 'x'::bytea, sha256('x'::bytea))
                    """);
            assertEquals(1, inserted, "egress.outbound_messages must be usable immediately after the upgrade");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
