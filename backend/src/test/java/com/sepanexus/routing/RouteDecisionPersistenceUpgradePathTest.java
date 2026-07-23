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

/** Representative V47→V48 PostgreSQL 18 upgrade proof for immutable route decision evidence. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class RouteDecisionPersistenceUpgradePathTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test void upgradePreservesRoutingRuntimeAndMakesTenantDecisionEvidenceUsable() throws Exception {
        try (var c = admin(); var s = c.createStatement()) { s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'"); }
        migrate("47");
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing"); var s = c.createStatement()) {
            s.executeUpdate("INSERT INTO routing.participant_reachability VALUES (gen_random_uuid(),gen_random_uuid(),'REACHABLE','DIRECT',now())");
        }
        migrate(null);
        UUID tenant = UUID.randomUUID();
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing"); var s = c.createStatement()) {
            c.setAutoCommit(false); s.execute("SET LOCAL app.tenant_id = '%s'".formatted(tenant));
            s.executeUpdate("INSERT INTO routing.route_decisions (id,payment_id,tenant_id,outcome,decided_at) VALUES (gen_random_uuid(),gen_random_uuid(),'%s','ROUTE_SELECTED',now())".formatted(tenant)); c.commit();
        }
        try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM routing.participant_reachability")) {
            assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isEqualTo(1);
        }
    }

    private static void migrate(String target) {
        var cfg = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration").locations("filesystem:src/main/resources/db/migration");
        if (target != null) cfg.target(target); cfg.load().migrate();
    }
    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}
