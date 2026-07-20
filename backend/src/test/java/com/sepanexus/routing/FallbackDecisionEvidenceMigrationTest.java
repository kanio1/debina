package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL 18 proof for ADR-N12's immutable, explicit fallback decision boundary. */
@Testcontainers
class FallbackDecisionEvidenceMigrationTest {

    private static final Instant DECIDED_AT = Instant.parse("2026-07-20T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    @Test
    void selectedExplicitFallbackWritesConsistentImmutableTenantEvidence() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        FallbackRule rule = configuredRule(null);
        FallbackSelection selection = new FallbackPolicy().select(rule.profileId(), List.of(rule)).orElseThrow();
        FallbackDecisionCommand command = command(tenant, selection, List.of(
                new RouteCandidateEvidence(rule.profileId(), (short) 1, false, "PRIMARY_UNAVAILABLE"),
                new RouteCandidateEvidence(rule.fallbackProfileId(), (short) 2, true, null)));

        assertThat(recorder().record(command)).isEqualTo(command.decisionId());

        try (Connection connection = routing(tenant); Statement statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT outcome, fallback_applied, fallback_rule_id, selected_profile_id
                        FROM routing.route_decisions WHERE id = '%s'
                        """.formatted(command.decisionId()))) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("outcome")).isEqualTo("FALLBACK_SELECTED");
            assertThat(result.getBoolean("fallback_applied")).isTrue();
            assertThat(result.getObject("fallback_rule_id", UUID.class)).isEqualTo(rule.id());
            assertThat(result.getObject("selected_profile_id", UUID.class)).isEqualTo(rule.fallbackProfileId());
        }
        try (Connection connection = routing(tenant); Statement statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT explanation::text FROM routing.route_decision_explanations
                        WHERE decision_id = '%s'
                        """.formatted(command.decisionId()))) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).contains(rule.id().toString(), rule.profileId().toString(),
                    rule.fallbackProfileId().toString(), "UNCONDITIONAL_ONLY");
        }
        try (Connection connection = routing(otherTenant); Statement statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM routing.route_decisions")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
        assertCountsZeroExceptRouting(command.decisionId());
    }

    @Test
    void failedCandidateEvidenceRollsBackTheDecisionAndCannotCreatePartialState() throws Exception {
        UUID tenant = UUID.randomUUID();
        FallbackRule rule = configuredRule(null);
        FallbackSelection selection = new FallbackPolicy().select(rule.profileId(), List.of(rule)).orElseThrow();
        RouteCandidateEvidence duplicate = new RouteCandidateEvidence(rule.fallbackProfileId(), (short) 1, true, null);
        FallbackDecisionCommand command = command(tenant, selection, List.of(duplicate, duplicate));

        assertThatThrownBy(() -> recorder().record(command)).isInstanceOf(DataIntegrityViolationException.class);
        assertTenantEvidenceCount(tenant, "routing.route_decisions", command.decisionId(), 0);
        assertTenantEvidenceCount(tenant, "routing.route_candidate_results", command.decisionId(), 0);
        assertTenantEvidenceCount(tenant, "routing.route_decision_explanations", command.decisionId(), 0);
    }

    @Test
    void retryWithTheSameTechnicalDecisionIdentityCannotCreateASecondEffect() throws Exception {
        UUID tenant = UUID.randomUUID();
        FallbackRule rule = configuredRule(null);
        FallbackSelection selection = new FallbackPolicy().select(rule.profileId(), List.of(rule)).orElseThrow();
        FallbackDecisionCommand command = command(tenant, selection,
                List.of(new RouteCandidateEvidence(rule.fallbackProfileId(), (short) 1, true, null)));

        recorder().record(command);
        assertThatThrownBy(() -> recorder().record(command)).isInstanceOf(DataIntegrityViolationException.class);
        assertTenantEvidenceCount(tenant, "routing.route_decisions", command.decisionId(), 1);
        assertTenantEvidenceCount(tenant, "routing.route_candidate_results", command.decisionId(), 1);
        assertTenantEvidenceCount(tenant, "routing.route_decision_explanations", command.decisionId(), 1);
    }

    @Test
    void configuredNonNullConditionFailsClosedBeforeAnyRoutingEvidenceIsWritten() throws Exception {
        UUID tenant = UUID.randomUUID();
        FallbackRule rule = configuredRule("source-does-not-define-this-language");
        FallbackSelection forgedSelection = new FallbackSelection(
                rule.id(), rule.profileId(), rule.fallbackProfileId(), rule.priority());
        FallbackDecisionCommand command = command(tenant, forgedSelection,
                List.of(new RouteCandidateEvidence(rule.fallbackProfileId(), (short) 1, true, null)));

        assertThatThrownBy(() -> recorder().record(command)).isInstanceOf(UnsupportedFallbackConditionException.class);
        assertTenantEvidenceCount(tenant, "routing.route_decisions", command.decisionId(), 0);
        assertTenantEvidenceCount(tenant, "routing.route_candidate_results", command.decisionId(), 0);
        assertTenantEvidenceCount(tenant, "routing.route_decision_explanations", command.decisionId(), 0);
    }

    @Test
    void fallbackFlagAndRuleForeignKeyCannotBeBypassed() throws Exception {
        UUID tenant = UUID.randomUUID();
        try (Connection connection = routing(tenant); Statement statement = connection.createStatement()) {
            SQLException exception = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO routing.route_decisions
                        (id, payment_id, tenant_id, outcome, fallback_applied, fallback_rule_id, decided_at)
                    VALUES (gen_random_uuid(), gen_random_uuid(), '%s', 'FALLBACK_SELECTED', true, NULL, now())
                    """.formatted(tenant)));
            assertEquals("23514", exception.getSQLState());
        }
        try (Connection connection = routing(tenant); Statement statement = connection.createStatement()) {
            SQLException exception = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO routing.route_decisions
                        (id, payment_id, tenant_id, outcome, fallback_applied, fallback_rule_id, decided_at)
                    VALUES (gen_random_uuid(), gen_random_uuid(), '%s', 'FALLBACK_SELECTED', true, gen_random_uuid(), now())
                    """.formatted(tenant)));
            assertEquals("23503", exception.getSQLState());
        }
    }

    private static FallbackRule configuredRule(String condition) throws SQLException {
        UUID id;
        UUID source = UUID.randomUUID();
        UUID fallback = UUID.randomUUID();
        try (Connection connection = referenceData(); Statement statement = connection.createStatement();
                var result = statement.executeQuery("""
                        INSERT INTO reference_data.profile_fallback_rules
                            (profile_id, fallback_profile_id, priority, condition)
                        VALUES ('%s', '%s', 10, %s)
                        RETURNING id
                        """.formatted(source, fallback, condition == null ? "NULL" : "'" + condition + "'"))) {
            assertThat(result.next()).isTrue();
            id = result.getObject(1, UUID.class);
        }
        return new FallbackRule(id, source, fallback, (short) 10, condition);
    }

    private static FallbackDecisionCommand command(UUID tenant, FallbackSelection selection, List<RouteCandidateEvidence> candidates) {
        return new FallbackDecisionCommand(UUID.randomUUID(), UUID.randomUUID(), tenant, selection, candidates, "ref-v1", DECIDED_AT);
    }

    private static FallbackDecisionEvidenceRecorder recorder() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing");
        return new FallbackDecisionEvidenceRecorder(new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper());
    }

    private static void assertTenantEvidenceCount(UUID tenant, String table, UUID decisionId, int expected) throws SQLException {
        try (Connection connection = routing(tenant); Statement statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM %s WHERE %s = '%s'".formatted(
                        table, table.equals("routing.route_decisions") ? "id" : "decision_id", decisionId))) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(expected);
        }
    }

    private static void assertCountsZeroExceptRouting(UUID decisionId) throws SQLException {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            Map<String, Integer> untouched = Map.of(
                    "payment.payments", count(statement, "payment.payments"),
                    "settlement.settlement_attempts", count(statement, "settlement.settlement_attempts"),
                    "ledger.journal_entries", count(statement, "ledger.journal_entries"),
                    "iso.iso_messages", count(statement, "iso.iso_messages"),
                    "egress.outbound_messages", count(statement, "egress.outbound_messages"));
            assertThat(untouched).allSatisfy((table, rowCount) -> assertThat(rowCount).as(table).isZero());
            assertThat(count(statement, "routing.route_decisions WHERE id = '" + decisionId + "'")).isEqualTo(1);
        }
    }

    private static int count(Statement statement, String tableOrWhereClause) throws SQLException {
        try (var result = statement.executeQuery("SELECT count(*) FROM " + tableOrWhereClause)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection referenceData() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role", "dev-only-reference-data"); }
    private static Connection routing(UUID tenant) throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "routing_role", "dev-only-routing");
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL app.tenant_id = '%s'".formatted(tenant));
        }
        return connection;
    }
}
