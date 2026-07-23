package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 * EPIC-28 Story 28.1: {@code iso.iso_message_parse_errors} follows the same ownership/append-only
 * model already proven for {@code signature.*} (V14, {@code NonSignatureRoleCannotWriteSignatureTest}):
 * {@code sepa_app} (the sole writer of {@code iso.*} today — iso-adapter is not yet a separate
 * Modulith module/role) can {@code INSERT}, a foreign module role ({@code signature_role}) cannot,
 * and nobody may {@code UPDATE}/{@code DELETE} — the table is append-only evidence.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class IsoMessageParseErrorOwnershipTest {

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
    void sepaAppCanInsertParseError() throws Exception {
        UUID rawMessageId = archiveRawMessage();

        assertDoesNotThrow(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO iso.iso_message_parse_errors
                            (raw_message_id, message_type_guess, error_code, error_message)
                        VALUES ('%s', 'pain.001', 'MALFORMED_XML', 'DOCTYPE is disallowed')
                        """.formatted(rawMessageId));
            }
        });
    }

    @Test
    void signatureRoleCannotInsertParseError() throws Exception {
        UUID rawMessageId = archiveRawMessage();

        assertInsufficientPrivilege(() -> {
            try (Connection connection = signatureRoleConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO iso.iso_message_parse_errors
                            (raw_message_id, message_type_guess, error_code, error_message)
                        VALUES ('%s', 'pain.001', 'MALFORMED_XML', 'intruder row')
                        """.formatted(rawMessageId));
            }
        });
    }

    @Test
    void sepaAppCannotUpdateParseError() throws Exception {
        UUID rawMessageId = archiveRawMessage();
        UUID errorId = UUID.randomUUID();
        try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO iso.iso_message_parse_errors
                        (id, raw_message_id, message_type_guess, error_code, error_message)
                    VALUES ('%s', '%s', 'pain.001', 'MALFORMED_XML', 'original')
                    """.formatted(errorId, rawMessageId));
        }

        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE iso.iso_message_parse_errors SET error_message = 'tampered' WHERE id = '%s'"
                                .formatted(errorId));
            }
        });
    }

    @Test
    void sepaAppCannotDeleteParseError() throws Exception {
        UUID rawMessageId = archiveRawMessage();
        UUID errorId = UUID.randomUUID();
        try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO iso.iso_message_parse_errors
                        (id, raw_message_id, message_type_guess, error_code, error_message)
                    VALUES ('%s', '%s', 'pain.001', 'MALFORMED_XML', 'original')
                    """.formatted(errorId, rawMessageId));
        }

        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM iso.iso_message_parse_errors WHERE id = '%s'".formatted(errorId));
            }
        });
    }

    private static UUID archiveRawMessage() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO ingress.raw_inbound_messages (id, channel, tenant_id, message_type, payload, payload_sha256)
                    VALUES ('%s', 'bank-xml', gen_random_uuid(), 'pain.001', '\\x00', '\\x00')
                    """.formatted(id));
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
