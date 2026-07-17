package com.sepanexus.egress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
 * EPIC-18 Story 18.2: proves V28's {@code outbox_dispatcher_role} grant extension applies cleanly
 * on top of an already-migrated database (migrated only through V27, with representative
 * pre-existing rows in {@code payment.outbox_events} and {@code iso.outbox_events}) — not just on
 * a pristine empty schema — and that prior rows survive and become immediately claimable.
 */
@Testcontainers
class OutboxDispatcherGrantsMigrationUpgradePathTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    @Test
    void dispatcherGrantsApplyOnTopOfPriorSchemaAndPriorRowsBecomeImmediatelyClaimable() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .target("27")
                .load()
                .migrate();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO payment.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                    VALUES ('00000000-0000-0000-0000-0000000000f1', gen_random_uuid(), 'sweep.pre-upgrade.v1', '{}'::jsonb, gen_random_uuid())
                    """);
            statement.execute("""
                    INSERT INTO iso.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                    VALUES ('00000000-0000-0000-0000-0000000000f2', gen_random_uuid(), 'sweep.pre-upgrade.v1', '{}'::jsonb, gen_random_uuid())
                    """);
        }

        // Before V28, outbox_dispatcher_role has zero grants on payment/iso schemas — confirms the
        // upgrade actually changes something, not a vacuous "it was already claimable" check.
        assertThrows(SQLException.class, () -> {
            try (Connection connection = dispatcherConnection(); Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT id FROM payment.outbox_events LIMIT 1");
            }
        });

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            var priorRow = statement.executeQuery(
                    "SELECT count(*) FROM payment.outbox_events WHERE id = '00000000-0000-0000-0000-0000000000f1'");
            priorRow.next();
            assertEquals(1, priorRow.getInt(1), "pre-existing payment.outbox_events row must survive V28");
        }

        try (Connection connection = dispatcherConnection(); Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate(
                    "UPDATE payment.outbox_events SET published_at = now() WHERE id = '00000000-0000-0000-0000-0000000000f1'");
            assertEquals(1, updated, "pre-existing payment.outbox_events row must be immediately claimable after V28");

            updated = statement.executeUpdate(
                    "UPDATE iso.outbox_events SET published_at = now() WHERE id = '00000000-0000-0000-0000-0000000000f2'");
            assertEquals(1, updated, "pre-existing iso.outbox_events row must be immediately claimable after V28");
        }
    }

    private static Connection dispatcherConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
