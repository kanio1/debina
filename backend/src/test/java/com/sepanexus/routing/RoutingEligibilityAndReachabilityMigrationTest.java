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

/** Fresh PostgreSQL 18 proof for the source-defined EPIC-52 catalog and routing runtime boundary. */
@Testcontainers
class RoutingEligibilityAndReachabilityMigrationTest {
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

    @Test void referenceDataOwnsEligibilityCatalogsAndRoutingCanOnlyReadThem() throws Exception {
        UUID participant = UUID.randomUUID(); UUID profile = UUID.randomUUID();
        try (var c = referenceData(); var s = c.createStatement()) {
            s.executeUpdate("INSERT INTO reference_data.participant_capabilities VALUES ('%s','%s','DIRECT')".formatted(participant, profile));
            s.executeUpdate("INSERT INTO reference_data.participant_eligibility_rules VALUES ('%s','AMOUNT_RANGE','synthetic')".formatted(profile));
        }
        try (var c = routing(); var s = c.createStatement(); var r = s.executeQuery("SELECT access_mode FROM reference_data.participant_capabilities")) {
            assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo("DIRECT");
        }
        assertDenied(() -> { try (var c = routing(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO reference_data.participant_capabilities VALUES (gen_random_uuid(),gen_random_uuid(),'DIRECT')"); } });
    }

    @Test void routingOwnsReachabilityAndDispatcherHasOnlyOutboxPublicationSlice() throws Exception {
        UUID participant = UUID.randomUUID(); UUID profile = UUID.randomUUID(); UUID event = UUID.randomUUID();
        try (var c = routing(); var s = c.createStatement()) {
            assertEquals(1, s.executeUpdate("INSERT INTO routing.participant_reachability VALUES ('%s','%s','REACHABLE','DIRECT',now())".formatted(participant, profile)));
            s.executeUpdate("INSERT INTO routing.outbox_events (id,aggregate_id,topic,type,payload) VALUES ('%s',gen_random_uuid(),'payment.routed','routing.test.v1','{}')".formatted(event));
        }
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher"); var s = c.createStatement()) {
            assertEquals(1, s.executeUpdate("UPDATE routing.outbox_events SET published_at=now() WHERE id='%s'".formatted(event)));
        }
        assertDenied(() -> { try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher"); var s = c.createStatement()) { s.executeUpdate("INSERT INTO routing.participant_reachability VALUES (gen_random_uuid(),gen_random_uuid(),'REACHABLE','DIRECT',now())"); } });
        assertDenied(() -> { try (var c = foreign(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO routing.participant_reachability VALUES (gen_random_uuid(),gen_random_uuid(),'REACHABLE','DIRECT',now())"); } });
    }

    private interface SqlWork { void run() throws Exception; }
    private static void assertDenied(SqlWork work) { assertEquals("42501", assertThrows(SQLException.class, work::run).getSQLState()); }
    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection referenceData() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role", "dev-only-reference-data"); }
    private static Connection routing() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing"); }
    private static Connection foreign() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "foreign_role", "dev-only-foreign"); }
}
