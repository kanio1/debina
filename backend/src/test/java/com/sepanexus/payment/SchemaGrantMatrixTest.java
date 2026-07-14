package com.sepanexus.payment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EPIC-09 Story 9.1: generic proof of the one-writer-per-schema mechanism, exercised
 * against every table currently owned by the {@code payment} schema (not just outbox,
 * which {@link OutboxOwnershipTest} already covered narrowly). {@code other_module_role}
 * stands in for a hypothetical second module's writer role — per the frozen
 * "[DEFER] all schemas upfront" decision, no second real schema is created just to prove
 * this; a synthetic role against the one real schema that exists today is sufficient to
 * prove the grant pattern generalizes.
 */
@Testcontainers
class SchemaGrantMatrixTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void migrateDatabase() throws Exception {
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
    void ownerRoleCanWriteEveryTableItOwns() {
        assertDoesNotThrow(() -> {
            try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.tenant_id', '11111111-1111-1111-1111-111111111111', false)");
                statement.executeUpdate("""
                        INSERT INTO payment.payments (tenant_id, end_to_end_id, amount, debtor_iban, creditor_iban)
                        VALUES ('11111111-1111-1111-1111-111111111111', 'schema-grant-matrix-owner', 1.00,
                                'DE89370400440532013000', 'FR7630006000011234567890189')
                        """);
                statement.executeUpdate("""
                        INSERT INTO payment.outbox_events (aggregate_id, event_type, payload, correlation_id)
                        VALUES (gen_random_uuid(), 'payment.created', '{}'::jsonb, gen_random_uuid())
                        """);
                statement.executeUpdate("""
                        INSERT INTO payment.inbox_events (source_event_id)
                        VALUES (gen_random_uuid())
                        """);
            }
        });
    }

    @Test
    void anotherModuleRoleCannotWritePaymentsTable() throws Exception {
        assertInsufficientPrivilege(() -> {
            try (Connection connection = otherModuleConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.payments (tenant_id, end_to_end_id, amount, debtor_iban, creditor_iban)
                        VALUES (gen_random_uuid(), 'schema-grant-matrix-intruder', 1.00,
                                'DE89370400440532013000', 'FR7630006000011234567890189')
                        """);
            }
        });
    }

    @Test
    void anotherModuleRoleCannotWritePaymentOutbox() throws Exception {
        assertInsufficientPrivilege(() -> {
            try (Connection connection = otherModuleConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.outbox_events (aggregate_id, event_type, payload, correlation_id)
                        VALUES (gen_random_uuid(), 'payment.created', '{}'::jsonb, gen_random_uuid())
                        """);
            }
        });
    }

    @Test
    void anotherModuleRoleCannotWritePaymentInbox() throws Exception {
        assertInsufficientPrivilege(() -> {
            try (Connection connection = otherModuleConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.inbox_events (source_event_id)
                        VALUES (gen_random_uuid())
                        """);
            }
        });
    }

    @Test
    void anotherModuleRoleHasNoUsageOnPaymentSchemaAtAll() throws Exception {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = otherModuleConnection(); Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT count(*) FROM payment.payments");
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertInsufficientPrivilege(ThrowingRunnable runnable) throws Exception {
        SQLException exception = assertThrows(SQLException.class, runnable::run);
        assertEquals("42501", exception.getSQLState());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }

    private static Connection otherModuleConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "other_module_role", "dev-only-other-module");
    }
}
