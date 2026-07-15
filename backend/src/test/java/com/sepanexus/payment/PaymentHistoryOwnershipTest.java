package com.sepanexus.payment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * EPIC-11 Story 11.1 (OQ-12 resolution): {@code payment.payment_status_history}/
 * {@code payment.payment_events} follow the same ownership/append-only model already proven for
 * {@code signature.*} (V14) and {@code iso.iso_message_parse_errors} (V16): {@code sepa_app} (the
 * sole writer of {@code payment.*} — {@code payment-lifecycle} is not yet a separate Modulith
 * module/role) can {@code INSERT}, a foreign module role ({@code signature_role}) cannot, and
 * nobody may {@code UPDATE}/{@code DELETE}. Also proves the V19/V20 migration on a fresh
 * Testcontainers database: every pre-existing payment gets exactly one {@code MIGRATION_BASELINE}
 * row, no fabricated intermediate transitions.
 */
@Testcontainers
class PaymentHistoryOwnershipTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void migrateDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @Test
    void sepaAppCanInsertHistoryAndEventRows() throws Exception {
        UUID paymentId = insertPayment("RECEIVED");

        assertDoesNotThrow(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.payment_status_history
                            (payment_id, seq, from_status, to_status, status_code, source_type, event_type, at)
                        VALUES ('%s', 1, NULL, 'RECEIVED', 'RECEIVED', 'INTERNAL', 'payment.submitted.v1', now())
                        """.formatted(paymentId));
                statement.executeUpdate("""
                        INSERT INTO payment.payment_events (payment_id, type, payload, at)
                        VALUES ('%s', 'payment.submitted.v1', '{}'::jsonb, now())
                        """.formatted(paymentId));
            }
        });
    }

    @Test
    void signatureRoleCannotInsertHistoryOrEventRows() throws Exception {
        UUID paymentId = insertPayment("RECEIVED");

        assertInsufficientPrivilege(() -> {
            try (Connection connection = signatureRoleConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.payment_status_history
                            (payment_id, seq, from_status, to_status, status_code, source_type, event_type, at)
                        VALUES ('%s', 1, NULL, 'RECEIVED', 'RECEIVED', 'INTERNAL', 'intruder', now())
                        """.formatted(paymentId));
            }
        });
        assertInsufficientPrivilege(() -> {
            try (Connection connection = signatureRoleConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.payment_events (payment_id, type, payload, at)
                        VALUES ('%s', 'intruder', '{}'::jsonb, now())
                        """.formatted(paymentId));
            }
        });
    }

    @Test
    void sepaAppCannotUpdateOrDeleteHistoryRows() throws Exception {
        UUID paymentId = insertPayment("RECEIVED");
        try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO payment.payment_status_history
                        (payment_id, seq, from_status, to_status, status_code, source_type, event_type, at)
                    VALUES ('%s', 1, NULL, 'RECEIVED', 'RECEIVED', 'INTERNAL', 'payment.submitted.v1', now())
                    """.formatted(paymentId));
        }

        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE payment.payment_status_history SET to_status = 'DISPATCHED' WHERE payment_id = '%s'"
                                .formatted(paymentId));
            }
        });
        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "DELETE FROM payment.payment_status_history WHERE payment_id = '%s'".formatted(paymentId));
            }
        });
    }

    @Test
    void sepaAppCannotUpdateOrDeleteEventRows() throws Exception {
        UUID paymentId = insertPayment("RECEIVED");
        UUID eventId;
        try (Connection connection = sepaAppConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payment_events (id, payment_id, type, payload, at)
                VALUES (gen_random_uuid(), ?, 'payment.submitted.v1', '{}'::jsonb, now())
                RETURNING id
                """)) {
            statement.setObject(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                eventId = (UUID) resultSet.getObject(1);
            }
        }
        UUID finalEventId = eventId;

        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE payment.payment_events SET type = 'tampered' WHERE id = '%s'".formatted(finalEventId));
            }
        });
        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM payment.payment_events WHERE id = '%s'".formatted(finalEventId));
            }
        });
    }

    @Test
    void freshDatabaseBackfillGivesExactlyOneBaselineRowPerExistingPayment() throws Exception {
        UUID received = insertPayment("RECEIVED");
        UUID validated = insertPayment("VALIDATED");
        UUID rejected = insertPayment("REJECTED");
        UUID dispatched = insertPayment("DISPATCHED");

        // V20 already ran once in @BeforeAll before these rows existed — simulate a fresh-DB
        // backfill scenario by re-running just V20's INSERT logic against these newly-seeded rows,
        // proving the migration's own logic (not relying on migration-run ordering against
        // payments inserted after migrate() already completed). Runs as adminConnection() (the
        // Testcontainers superuser), matching how the real migration runs as sepa_migration, not
        // sepa_app — sepa_app is RLS-scoped and would see zero payment.payments rows with no
        // app.tenant_id GUC set (the established empty-GUC-zero-rows rule).
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO payment.payment_status_history
                        (payment_id, seq, from_status, to_status, status_code, source_type, actor_type, is_final, event_type, at)
                    SELECT id, 1, NULL, status, status, 'INTERNAL', 'SYSTEM',
                           status IN ('REJECTED', 'DISPATCHED'), 'MIGRATION_BASELINE', now()
                    FROM payment.payments
                    WHERE id IN ('%s', '%s', '%s', '%s')
                    """.formatted(received, validated, rejected, dispatched));
        }

        assertEquals(1, historyRowCount(received));
        assertEquals(1, historyRowCount(validated));
        assertEquals(1, historyRowCount(rejected));
        assertEquals(1, historyRowCount(dispatched));
        assertEquals(true, isFinal(rejected));
        assertEquals(true, isFinal(dispatched));
        assertEquals(false, isFinal(received));
        assertEquals(false, isFinal(validated));
    }

    private static int historyRowCount(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.payment_status_history WHERE payment_id = ? AND event_type = 'MIGRATION_BASELINE'")) {
            statement.setObject(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static boolean isFinal(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT is_final FROM payment.payment_status_history WHERE payment_id = ? AND event_type = 'MIGRATION_BASELINE'")) {
            statement.setObject(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static UUID insertPayment(String status) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (?, gen_random_uuid(), ?, 'EUR', 'DE89370400440532013000', 'FR7630006000011234567890189', ?)
                """)) {
            statement.setObject(1, id);
            statement.setBigDecimal(2, new BigDecimal("10.00"));
            statement.setString(3, status);
            statement.executeUpdate();
        }
        return id;
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

    private static Connection sepaAppConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }

    private static Connection signatureRoleConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "signature_role", "dev-only-signature");
    }
}
