package com.sepanexus.referencedata;

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
 * EPIC-12 Story 12.1: {@code reference_data} is the sole writer of its own catalogs
 * (business_calendars/scheme_profiles/settlement_cutoff_calendar); every other module
 * (represented here by the real {@code sepa_app} role from {@code payment}) may only
 * read them, never write, and {@code reference_data_role} may not write into
 * {@code payment}'s tables either.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class ReferenceDataOwnershipTest {

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
    void referenceDataRoleCanWriteItsOwnCatalogs() {
        assertDoesNotThrow(() -> {
            try (Connection connection = referenceDataConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO reference_data.scheme_profiles (profile_code, family, valid_from)
                        VALUES ('TIPS_LIKE', 'GROSS_INSTANT', CURRENT_DATE)
                        """);
                statement.executeUpdate("""
                        INSERT INTO reference_data.business_calendars (calendar_code, business_date, is_business_day, session_no)
                        VALUES ('TARGET2', CURRENT_DATE, true, 1)
                        """);
                statement.executeUpdate("""
                        INSERT INTO reference_data.settlement_cutoff_calendar (profile_id, business_date, session_no, cutoff_at)
                        VALUES (gen_random_uuid(), CURRENT_DATE, 1, now())
                        """);
            }
        });
    }

    @Test
    void paymentModuleRoleCanReadButNotWriteReferenceDataCatalogs() throws Exception {
        try (Connection connection = paymentAppConnection(); Statement statement = connection.createStatement()) {
            assertDoesNotThrow(() -> statement.executeQuery("SELECT count(*) FROM reference_data.scheme_profiles"));
        }

        assertInsufficientPrivilege(() -> {
            try (Connection connection = paymentAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO reference_data.scheme_profiles (profile_code, family, valid_from)
                        VALUES ('INTRUDER', 'GROSS_INSTANT', CURRENT_DATE)
                        """);
            }
        });
    }

    @Test
    void referenceDataRoleCannotWritePaymentSchema() throws Exception {
        assertInsufficientPrivilege(() -> {
            try (Connection connection = referenceDataConnection(); Statement statement = connection.createStatement()) {
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

    private static Connection referenceDataConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role", "dev-only-reference-data");
    }

    private static Connection paymentAppConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }
}
