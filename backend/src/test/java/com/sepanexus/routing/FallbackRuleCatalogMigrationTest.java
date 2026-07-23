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

/** PostgreSQL 18 fresh-migration proof for explicit fallback configuration and writer isolation. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class FallbackRuleCatalogMigrationTest {

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
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    @Test
    void referenceDataOwnsExplicitFallbackRulesAndRoutingCanOnlyReadThem() throws Exception {
        UUID sourceProfile = UUID.randomUUID();
        UUID fallbackProfile = UUID.randomUUID();
        UUID ruleId;
        try (Connection connection = referenceData(); Statement statement = connection.createStatement();
                var result = statement.executeQuery("""
                        INSERT INTO reference_data.profile_fallback_rules
                            (profile_id, fallback_profile_id, priority, condition)
                        VALUES ('%s', '%s', 10, NULL)
                        RETURNING id
                        """.formatted(sourceProfile, fallbackProfile))) {
            assertThat(result.next()).isTrue();
            ruleId = result.getObject("id", UUID.class);
        }
        try (Connection connection = routing(); Statement statement = connection.createStatement();
                var result = statement.executeQuery("SELECT id, priority FROM reference_data.profile_fallback_rules WHERE id = '%s'".formatted(ruleId))) {
            assertThat(result.next()).isTrue();
            assertThat(result.getObject("id", UUID.class)).isEqualTo(ruleId);
            assertThat(result.getInt("priority")).isEqualTo(10);
        }
        assertDenied(() -> routing().createStatement().executeUpdate("""
                INSERT INTO reference_data.profile_fallback_rules
                    (profile_id, fallback_profile_id, priority, condition)
                VALUES (gen_random_uuid(), gen_random_uuid(), 20, NULL)
                """));
        assertDenied(() -> foreign().createStatement().executeUpdate("""
                INSERT INTO reference_data.profile_fallback_rules
                    (profile_id, fallback_profile_id, priority, condition)
                VALUES (gen_random_uuid(), gen_random_uuid(), 20, NULL)
                """));
    }

    @Test
    void sourceOrderedRuleIdentityRemainsUniqueAndPublicHasNoPrivileges() throws Exception {
        UUID sourceProfile = UUID.randomUUID();
        try (Connection connection = referenceData(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO reference_data.profile_fallback_rules
                        (profile_id, fallback_profile_id, priority, condition)
                    VALUES ('%s', gen_random_uuid(), 10, NULL)
                    """.formatted(sourceProfile));
            SQLException conflict = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO reference_data.profile_fallback_rules
                        (profile_id, fallback_profile_id, priority, condition)
                    VALUES ('%s', gen_random_uuid(), 10, NULL)
                    """.formatted(sourceProfile)));
            assertEquals("23505", conflict.getSQLState());
        }
        try (Connection connection = admin(); Statement statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT has_table_privilege('public', 'reference_data.profile_fallback_rules', 'SELECT,INSERT,UPDATE,DELETE')
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean(1)).isFalse();
        }
    }

    private interface SqlOperation { void execute() throws Exception; }

    private static void assertDenied(SqlOperation operation) {
        SQLException exception = assertThrows(SQLException.class, operation::execute);
        assertEquals("42501", exception.getSQLState());
    }

    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection referenceData() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role", "dev-only-reference-data"); }
    private static Connection routing() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing"); }
    private static Connection foreign() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "foreign_module_role", "dev-only-foreign-module"); }
}
