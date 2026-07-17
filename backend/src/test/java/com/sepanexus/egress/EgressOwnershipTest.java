package com.sepanexus.egress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-43 Story 43.1: fresh-database migration evidence (this test's own {@code @BeforeAll}
 * applies all 24 migrations to an empty PostgreSQL 18 Testcontainer) plus the grant matrix —
 * {@code egress_role} is the sole writer of {@code egress.outbound_messages}, a foreign role is
 * rejected, and {@code outbox_dispatcher_role} (ADR-N5) can only claim/mark-published its narrow
 * slice of {@code egress.outbox_events} — never {@code egress.outbound_messages} or any other
 * domain table.
 */
@Testcontainers
class EgressOwnershipTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
            statement.execute("CREATE ROLE other_module_role LOGIN PASSWORD 'dev-only-other-module'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @Test
    void egressRoleCanInsertIntoOutboundMessages() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "egress_role", "dev-only-egress");
                Statement statement = connection.createStatement()) {
            statement.execute("SET app.role = 'system_relay'");
            int inserted = statement.executeUpdate(insertOutboundMessageSql());
            assertEquals(1, inserted);
        }
    }

    @Test
    void foreignRoleCannotInsertIntoOutboundMessages() {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), "other_module_role", "dev-only-other-module");
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertOutboundMessageSql());
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    @Test
    void outboxDispatcherRoleCannotWriteOutboundMessages() {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher");
                    Statement statement = connection.createStatement()) {
                statement.execute("SET app.role = 'system_relay'");
                statement.executeUpdate(insertOutboundMessageSql());
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    @Test
    void outboxDispatcherRoleCanClaimAndMarkPublishedOnlyOnOutboxEvents() throws Exception {
        UUID eventId;
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "egress_role", "dev-only-egress");
                Statement statement = connection.createStatement()) {
            eventId = UUID.randomUUID();
            statement.executeUpdate("""
                    INSERT INTO egress.outbox_events (id, aggregate_id, topic, type, payload)
                    VALUES ('%s', gen_random_uuid(), 'egress.delivery.requested', 'egress.delivery.requested.v1', '{}'::jsonb)
                    """.formatted(eventId));
        }

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher");
                Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate(
                    "UPDATE egress.outbox_events SET published_at = now() WHERE id = '%s'".formatted(eventId));
            assertEquals(1, updated);
        }
    }

    @Test
    void outboxDispatcherRoleCannotInsertIntoOutboxEvents() {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher");
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO egress.outbox_events (aggregate_id, topic, type, payload)
                        VALUES (gen_random_uuid(), 'egress.delivery.requested', 'egress.delivery.requested.v1', '{}'::jsonb)
                        """);
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    @Test
    void outboxDispatcherRoleCannotWriteAnyOtherModuleDomainTable() {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher");
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 10.00, 'EUR', 'DE1', 'FR1', 'RECEIVED')
                        """);
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    private static String insertOutboundMessageSql() {
        return """
                INSERT INTO egress.outbound_messages
                    (tenant_id, recipient_id, channel, artifact_type, correlation_msg_id, payload, payload_sha256)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'WEBHOOK', 'PACS002', 'corr-1', 'x'::bytea, sha256('x'::bytea))
                """;
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
