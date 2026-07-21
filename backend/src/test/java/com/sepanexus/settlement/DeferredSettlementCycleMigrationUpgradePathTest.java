package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Representative V50-to-V52 PostgreSQL 18 upgrade; Wave 3/4 routing evidence must survive. */
@Testcontainers
class DeferredSettlementCycleMigrationUpgradePathTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test void upgradesV50RoutingHistoryToV52WithoutRewritingIt() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").target("50").load().migrate();
        UUID decision = UUID.randomUUID(); UUID payment = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO routing.route_decisions (id, payment_id, tenant_id, outcome, fallback_applied, decided_at) VALUES ('"
                    + decision + "', '" + payment + "', '" + tenant + "', 'ROUTE_SELECTED', false, now())");
            try (ResultSet before = statement.executeQuery("SELECT to_regclass('settlement.settlement_cycles') IS NULL")) {
                before.next(); assertThat(before.getBoolean(1)).isTrue();
            }
        }

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();

        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            try (ResultSet current = statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")) {
                current.next(); assertThat(current.getString(1)).isEqualTo("52");
            }
            try (ResultSet route = statement.executeQuery("SELECT count(*) FROM routing.route_decisions WHERE id = '" + decision + "'")) {
                route.next(); assertThat(route.getInt(1)).isEqualTo(1);
            }
            try (ResultSet cycle = statement.executeQuery("SELECT to_regclass('settlement.settlement_cycles') IS NOT NULL")) {
                cycle.next(); assertThat(cycle.getBoolean(1)).isTrue();
            }
        }
    }

    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}
