package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Representative V48→V50 PostgreSQL 18 upgrade proof for fallback-rule integrity. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class FallbackRuleCatalogMigrationUpgradePathTest {

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test
    void upgradeRetainsExistingV48DecisionAndAddsBoundFallbackIdentity() throws Exception {
        try (Connection connection = admin(); var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        migrate("48");
        UUID tenant = UUID.randomUUID();
        UUID existingDecision = UUID.randomUUID();
        try (Connection connection = routing(tenant); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO routing.route_decisions (id, payment_id, tenant_id, outcome, decided_at)
                    VALUES ('%s', gen_random_uuid(), '%s', 'ROUTE_SELECTED', now())
                    """.formatted(existingDecision, tenant));
            connection.commit();
        }

        migrate(null);

        try (Connection connection = routing(tenant); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT fallback_applied, fallback_rule_id FROM routing.route_decisions
                        WHERE id = '%s'
                        """.formatted(existingDecision))) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean("fallback_applied")).isFalse();
            assertThat(result.getObject("fallback_rule_id")).isNull();
        }
    }

    private static void migrate(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection routing(UUID tenant) throws Exception {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing");
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute("SET LOCAL app.tenant_id = '%s'".formatted(tenant));
        }
        return connection;
    }
}
