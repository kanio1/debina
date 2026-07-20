package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;
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

/** PostgreSQL 18 fresh-migration and writer-isolation proof for EPIC-51 Story 51.1. */
@Testcontainers
class RouteCandidateCatalogMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
            statement.execute("CREATE ROLE foreign_module_role LOGIN PASSWORD 'dev-only-foreign-module'");
        }
        migrate(null);
    }

    @Test
    void referenceDataRoleIsTheOnlyCatalogWriterAndRoutingRoleCanRead() throws Exception {
        UUID profileId = UUID.randomUUID();
        try (Connection connection = referenceData(); Statement statement = connection.createStatement()) {
            assertEquals(1, statement.executeUpdate("""
                    INSERT INTO reference_data.profile_route_priorities
                        (scheme, service_level, currency, profile_id, priority)
                    VALUES ('SEPA', 'URGP', 'EUR', '%s', 10)
                    """.formatted(profileId)));
        }
        try (Connection connection = routing(); Statement statement = connection.createStatement();
                var result = statement.executeQuery("SELECT profile_id, priority FROM reference_data.profile_route_priorities WHERE profile_id = '%s'".formatted(profileId))) {
            assertThat(result.next()).isTrue();
            assertThat(result.getObject("profile_id", UUID.class)).isEqualTo(profileId);
            assertThat(result.getInt("priority")).isEqualTo(10);
        }
        assertPrivilegeDenied(() -> foreign().createStatement().executeUpdate("""
                INSERT INTO reference_data.profile_route_priorities
                    (scheme, service_level, currency, profile_id, priority)
                VALUES ('SEPA', 'URGP', 'EUR', gen_random_uuid(), 20)
                """));
        assertPrivilegeDenied(() -> routing().createStatement().executeUpdate("""
                INSERT INTO reference_data.profile_route_priorities
                    (scheme, service_level, currency, profile_id, priority)
                VALUES ('SEPA', 'URGP', 'EUR', gen_random_uuid(), 20)
                """));
    }

    @Test
    void sourcePrimaryKeyRejectsDuplicateCandidateCatalogEntry() throws Exception {
        UUID profileId = UUID.randomUUID();
        try (Connection connection = referenceData(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO reference_data.profile_route_priorities
                        (scheme, service_level, currency, profile_id, priority)
                    VALUES ('SEPA', 'URGP', 'EUR', '%s', 10)
                    """.formatted(profileId));
            SQLException conflict = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO reference_data.profile_route_priorities
                        (scheme, service_level, currency, profile_id, priority)
                    VALUES ('SEPA', 'URGP', 'EUR', '%s', 20)
                    """.formatted(profileId)));
            assertEquals("23505", conflict.getSQLState());
        }
    }

    private interface SqlOperation { void execute() throws Exception; }

    private static void assertPrivilegeDenied(SqlOperation operation) {
        SQLException exception = assertThrows(SQLException.class, operation::execute);
        assertEquals("42501", exception.getSQLState());
    }

    private static void migrate(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection referenceData() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role", "dev-only-reference-data"); }
    private static Connection routing() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing"); }
    private static Connection foreign() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "foreign_module_role", "dev-only-foreign-module"); }
}
