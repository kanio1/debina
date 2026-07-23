package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Representative V45→V47 PostgreSQL 18 upgrade proof for the routing ownership slice. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class RoutingEligibilityAndReachabilityUpgradePathTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test void upgradePreservesPriorRouteCandidateCatalogAndMakesNewRuntimeSurfaceUsable() throws Exception {
        try (var c = admin(); var s = c.createStatement()) { s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'"); }
        migrate("45");
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role", "dev-only-reference-data"); var s = c.createStatement()) {
            s.executeUpdate("INSERT INTO reference_data.profile_route_priorities VALUES ('SEPA','URGP','EUR',gen_random_uuid(),10)");
        }
        migrate(null);
        try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM reference_data.profile_route_priorities")) {
            assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isEqualTo(1);
        }
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing"); var s = c.createStatement()) {
            assertThat(s.executeUpdate("INSERT INTO routing.participant_reachability VALUES (gen_random_uuid(),gen_random_uuid(),'DEGRADED',NULL,now())")).isEqualTo(1);
        }
    }
    private static void migrate(String target) {
        var cfg = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration").locations("filesystem:src/main/resources/db/migration");
        if (target != null) cfg.target(target); cfg.load().migrate();
    }
    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}
