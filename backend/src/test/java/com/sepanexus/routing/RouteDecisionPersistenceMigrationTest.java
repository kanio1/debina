package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 18 proof for immutable, tenant-scoped routing decision evidence. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class RouteDecisionPersistenceMigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @BeforeAll static void migrate() throws Exception {
        try (var c = admin(); var s = c.createStatement()) {
            s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
            s.execute("CREATE ROLE foreign_role LOGIN PASSWORD 'dev-only-foreign'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    @Test void decisionRowsAreTenantIsolatedAndImmutable() throws Exception {
        UUID tenantA = UUID.randomUUID(); UUID tenantB = UUID.randomUUID(); UUID decision = UUID.randomUUID();
        try (var c = routing(tenantA); var s = c.createStatement()) {
            assertEquals(1, s.executeUpdate(decisionInsert(decision, tenantA)));
            c.commit();
        }
        try (var c = routing(tenantA); var s = c.createStatement()) {
            assertDenied(() -> s.executeUpdate("UPDATE routing.route_decisions SET outcome='ROUTE_FAILED' WHERE id='%s'".formatted(decision)));
            c.rollback();
        }
        try (var c = routing(tenantA); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM routing.route_decisions")) {
            assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isEqualTo(1); c.commit();
        }
        try (var c = routing(tenantB); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM routing.route_decisions")) {
            assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isZero();
            assertDenied(() -> s.executeUpdate(decisionInsert(UUID.randomUUID(), tenantA))); c.rollback();
        }
        try (var c = routingWithoutTenant(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM routing.route_decisions")) {
            assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isZero();
        }
    }

    @Test void candidateResultsAndExplanationAreVisibleOnlyThroughTheirTenantDecision() throws Exception {
        UUID tenantA = UUID.randomUUID(); UUID tenantB = UUID.randomUUID(); UUID decision = UUID.randomUUID();
        try (var c = routing(tenantA); var s = c.createStatement()) {
            s.executeUpdate(decisionInsert(decision, tenantA));
            s.executeUpdate("INSERT INTO routing.route_candidate_results VALUES ('%s',gen_random_uuid(),1,true,NULL)".formatted(decision));
            s.executeUpdate("INSERT INTO routing.route_decision_explanations VALUES ('%s','{\"pipeline\":\"captured\"}','ref-v1',NULL,now())".formatted(decision));
            c.commit();
        }
        try (var c = routing(tenantB); var s = c.createStatement()) {
            try (var results = s.executeQuery("SELECT count(*) FROM routing.route_candidate_results")) {
                assertThat(results.next()).isTrue(); assertThat(results.getInt(1)).isZero();
            }
            try (var explanations = s.executeQuery("SELECT count(*) FROM routing.route_decision_explanations")) {
                assertThat(explanations.next()).isTrue(); assertThat(explanations.getInt(1)).isZero();
            }
            assertDenied(() -> s.executeUpdate("INSERT INTO routing.route_candidate_results VALUES ('%s',gen_random_uuid(),1,true,NULL)".formatted(decision))); c.rollback();
        }
    }

    private static String decisionInsert(UUID id, UUID tenant) {
        return "INSERT INTO routing.route_decisions (id,payment_id,tenant_id,outcome,decided_at) VALUES ('%s',gen_random_uuid(),'%s','ROUTE_SELECTED',now())".formatted(id, tenant);
    }
    private interface SqlWork { void run() throws Exception; }
    private static void assertDenied(SqlWork work) { assertEquals("42501", assertThrows(SQLException.class, work::run).getSQLState()); }
    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection routing(UUID tenant) throws SQLException {
        Connection c = routingWithoutTenant(); c.setAutoCommit(false);
        try (var s = c.createStatement()) { s.execute("SET LOCAL app.tenant_id = '%s'".formatted(tenant)); }
        return c;
    }
    private static Connection routingWithoutTenant() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing"); }
}
