package com.sepanexus.signature;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/**
 * EPIC-31 Story 31.1 (OWN-10 S4): {@code signature_role} is the sole writer of
 * {@code signature.*} — every other module role (represented here by the real {@code sepa_app}
 * role) may not write there, and {@code signature_role} may not write into {@code payment}'s
 * tables either (symmetric non-domain-mutation check, blueprint §2 "must not").
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class NonSignatureRoleCannotWriteSignatureTest {

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
    void signatureRoleCanWriteItsOwnTables() {
        assertDoesNotThrow(() -> {
            try (Connection connection = signatureConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO signature.signature_keys (purpose, algo, public_material, valid_from)
                        VALUES ('VERIFY', 'ED25519-SYNTH', 'pub-material', now())
                        """);
            }
        });
    }

    @Test
    void sepaAppRoleCannotWriteSignatureSchema() throws Exception {
        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO signature.signature_keys (purpose, algo, public_material, valid_from)
                        VALUES ('VERIFY', 'ED25519-SYNTH', 'intruder-material', now())
                        """);
            }
        });
    }

    @Test
    void sepaAppRoleCannotReadSignatureSchema() throws Exception {
        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT count(*) FROM signature.signature_keys");
            }
        });
    }

    @Test
    void signatureRoleCanStillUpdateSignatureKeysForRotation() {
        assertDoesNotThrow(() -> {
            try (Connection connection = signatureConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO signature.signature_keys (id, purpose, algo, public_material, valid_from)
                        VALUES ('00000000-0000-0000-0000-0000000000f1', 'VERIFY', 'Ed25519', 'pub-material', now())
                        """);
                statement.executeUpdate("""
                        UPDATE signature.signature_keys SET valid_to = now(), status = 'EXPIRED'
                        WHERE id = '00000000-0000-0000-0000-0000000000f1'
                        """);
            }
        });
    }

    @Test
    void signatureRoleCannotUpdateVerificationEvents() throws Exception {
        try (Connection connection = signatureConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO signature.signature_verification_events (id, verdict, channel, verified_at)
                    VALUES ('00000000-0000-0000-0000-0000000000f2', 'VERIFIED', 'bank-xml', now())
                    """);
        }
        assertInsufficientPrivilege(() -> {
            try (Connection connection = signatureConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        UPDATE signature.signature_verification_events SET verdict = 'FAILED'
                        WHERE id = '00000000-0000-0000-0000-0000000000f2'
                        """);
            }
        });
    }

    @Test
    void signatureRoleCannotDeleteVerificationEvents() throws Exception {
        try (Connection connection = signatureConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO signature.signature_verification_events (id, verdict, channel, verified_at)
                    VALUES ('00000000-0000-0000-0000-0000000000f3', 'VERIFIED', 'bank-xml', now())
                    """);
        }
        assertInsufficientPrivilege(() -> {
            try (Connection connection = signatureConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        DELETE FROM signature.signature_verification_events
                        WHERE id = '00000000-0000-0000-0000-0000000000f3'
                        """);
            }
        });
    }

    @Test
    void signatureRoleCannotWritePaymentSchema() throws Exception {
        assertInsufficientPrivilege(() -> {
            try (Connection connection = signatureConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.payments (tenant_id, amount, debtor_iban, creditor_iban)
                        VALUES (gen_random_uuid(), 1.00,
                                'DE89370400440532013000', 'FR7630006000011234567890189')
                        """);
            }
        });
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

    private static Connection signatureConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "signature_role", "dev-only-signature");
    }

    private static Connection sepaAppConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }
}
