package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sepanexus.shared.ClockPort;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * EPIC-27 Story 27.4: the {@code ORPHANED} branch of {@link Pacs002CorrelationService} — a
 * terminal operator/DLQ signal, distinct from the {@code MATCHED}-only {@code
 * iso.message.correlated} publication already proven by {@link Pacs002CorrelationPersistenceTest}.
 * Real, isolated Testcontainers PostgreSQL (see {@code sepa-nexus-database-testing} skill) — no
 * Kafka container here, since this story's persistence half only needs to prove the {@code
 * iso.outbox_events} row is written correctly; real broker routing is {@link
 * IsoOutboxTopicRoutingTest}'s concern.
 */
@Testcontainers
class OrphanDlqTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    private static final Instant FIXED_NOW = Instant.parse("2026-07-16T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

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
    void orphanedResultPersistsExactlyOneOrphanOutboxEventKeyedByIsoMessageId() {
        UUID tenantId = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);

        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-ORPHAN-1", "TX-ORPHAN-1", "INSTR-ORPHAN-1",
                "E2E-ORPHAN-1", "UETR-ORPHAN-1");
        CorrelationDecision decision = service().correlate(tenantId, currentIsoMessageId, input);

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.ORPHANED);

        Map<String, Object> correlationRow = correlationRow(currentIsoMessageId);
        assertThat(correlationRow.get("status")).isEqualTo("ORPHANED");
        assertThat(correlationRow.get("matched_payment_id")).isNull();

        List<Map<String, Object>> outboxRows = jdbcTemplate().queryForList(
                "SELECT * FROM iso.outbox_events WHERE aggregate_id = ?", currentIsoMessageId);
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).get("event_type")).isEqualTo(IsoOutboxEventType.MESSAGE_ORPHANED.eventType());
        assertThat(outboxRows.get(0).get("aggregate_id")).isEqualTo(currentIsoMessageId);
    }

    @Test
    void orphanPayloadCarriesNoPaymentIdOrOtherDomainData() {
        UUID tenantId = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);

        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-ORPHAN-2", null, null, "E2E-ORPHAN-2", null);
        service().correlate(tenantId, currentIsoMessageId, input);

        String payload = (String) jdbcTemplate().queryForObject(
                "SELECT payload::text FROM iso.outbox_events WHERE aggregate_id = ?", String.class, currentIsoMessageId);

        assertThat(payload).contains("isoMessageId").contains(currentIsoMessageId.toString());
        assertThat(payload).contains("tenantId").contains(tenantId.toString());
        assertThat(payload).doesNotContain("paymentId");
        assertThat(payload).doesNotContainIgnoringCase("amount");
        assertThat(payload).doesNotContainIgnoringCase("currency");
    }

    @Test
    void matchedResultPublishesOnlyCorrelatedEventNeverOrphaned() {
        UUID tenantId = UUID.randomUUID();
        UUID candidatePaymentId = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);
        insertCandidate(tenantId, candidatePaymentId, "MSG-MATCH-1", null, "E2E-MATCH-1", null);

        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-MATCH-1", null, null, "E2E-MATCH-1", null);
        CorrelationDecision decision = service().correlate(tenantId, currentIsoMessageId, input);

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.MATCHED);

        List<Map<String, Object>> outboxRows = jdbcTemplate().queryForList(
                "SELECT * FROM iso.outbox_events WHERE aggregate_id IN (?, ?)", candidatePaymentId, currentIsoMessageId);
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).get("event_type")).isEqualTo(IsoOutboxEventType.MESSAGE_CORRELATED.eventType());
    }

    @Test
    void ambiguousResultPublishesNeitherCorrelatedNorOrphanedEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID paymentA = UUID.randomUUID();
        UUID paymentB = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);
        insertCandidate(tenantId, paymentA, "MSG-AMBIG-1", null, "E2E-AMBIG-1", null);
        insertCandidate(tenantId, paymentB, "MSG-AMBIG-1", null, "E2E-AMBIG-1", null);

        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-AMBIG-1", null, null, "E2E-AMBIG-1", null);
        CorrelationDecision decision = service().correlate(tenantId, currentIsoMessageId, input);

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.AMBIGUOUS);

        assertThat(jdbcTemplate().queryForList(
                "SELECT * FROM iso.outbox_events WHERE aggregate_id IN (?, ?, ?)",
                paymentA, paymentB, currentIsoMessageId)).isEmpty();
    }

    @Test
    void serializationFailureRollsBackTheCorrelationRowTooNoPartialWrite() {
        UUID tenantId = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);
        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-ATOMIC-1", "TX-ATOMIC-1", "INSTR-ATOMIC-1",
                "E2E-ATOMIC-1", "UETR-ATOMIC-1");

        DriverManagerDataSource dataSource = appDataSource();
        JdbcTemplate sharedJdbcTemplate = new JdbcTemplate(dataSource);
        Pacs002CorrelationPolicy policy = new Pacs002CorrelationPolicy(new JdbcCorrelationCandidateLookup(sharedJdbcTemplate));
        ClockPort fixedClock = () -> FIXED_NOW;
        Pacs002CorrelationService brokenService = new Pacs002CorrelationService(policy,
                new IsoMessageCorrelationRecorder(sharedJdbcTemplate), new IsoOutboxRecorder(sharedJdbcTemplate),
                fixedClock, poisonedObjectMapper());

        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status ->
                brokenService.correlate(tenantId, currentIsoMessageId, input)));

        assertThat(jdbcTemplate().queryForList(
                "SELECT * FROM iso.iso_message_correlation WHERE iso_message_id = ?", currentIsoMessageId)).isEmpty();
        assertThat(jdbcTemplate().queryForList(
                "SELECT * FROM iso.outbox_events WHERE aggregate_id = ?", currentIsoMessageId)).isEmpty();
    }

    // -- helpers -------------------------------------------------------------------------------

    private Pacs002CorrelationService service() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        Pacs002CorrelationPolicy policy = new Pacs002CorrelationPolicy(new JdbcCorrelationCandidateLookup(jdbcTemplate));
        ClockPort fixedClock = () -> FIXED_NOW;
        return new Pacs002CorrelationService(policy, new IsoMessageCorrelationRecorder(jdbcTemplate),
                new IsoOutboxRecorder(jdbcTemplate), fixedClock, JSON);
    }

    /**
     * An {@link ObjectMapper} that always fails to serialize {@link IsoMessageOrphanedEvent} —
     * forces the failure path in item 8 of Story 27.4's test-first list ("błąd serializacji ...
     * rollbackuje correlation row").
     */
    private static ObjectMapper poisonedObjectMapper() {
        SimpleModule throwingOnOrphanEvent = new SimpleModule().addSerializer(
                IsoMessageOrphanedEvent.class, new ValueSerializer<>() {
                    @Override
                    public void serialize(IsoMessageOrphanedEvent value, JsonGenerator gen, SerializationContext ctxt) {
                        throw new IllegalStateException("forced serialization failure (Story 27.4 atomicity proof)");
                    }
                });
        return JsonMapper.builder().addModule(throwingOnOrphanEvent).build();
    }

    private static DriverManagerDataSource appDataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }

    private static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(appDataSource());
    }

    private Map<String, Object> correlationRow(UUID isoMessageId) {
        List<Map<String, Object>> rows = jdbcTemplate().queryForList(
                "SELECT * FROM iso.iso_message_correlation WHERE iso_message_id = ?", isoMessageId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private UUID insertCurrentPacs002Message(UUID tenantId) {
        UUID rawMessageId = insertRawMessage(tenantId);
        UUID isoMessageId = UUID.randomUUID();
        jdbcTemplate().update("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status, raw_message_id, tenant_id)
                VALUES (?, 'INBOUND', 'pacs.002', 'PARSED', ?, ?)
                """, isoMessageId, rawMessageId, tenantId);
        return isoMessageId;
    }

    private void insertCandidate(UUID tenantId, UUID paymentId, String msgId, String instrId, String endToEndId, String uetr) {
        UUID rawMessageId = insertRawMessage(tenantId);
        UUID isoMessageId = UUID.randomUUID();
        jdbcTemplate().update("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status, raw_message_id, tenant_id)
                VALUES (?, 'INBOUND', 'pain.001', 'PARSED', ?, ?)
                """, isoMessageId, rawMessageId, tenantId);
        jdbcTemplate().update("""
                INSERT INTO iso.payment_iso_identifiers
                    (payment_id, source_message_type, iso_message_id, msg_id, instr_id, end_to_end_id, uetr)
                VALUES (?, 'pain.001', ?, ?, ?, ?, ?)
                """, paymentId, isoMessageId, msgId, instrId, endToEndId, uetr);
    }

    private UUID insertRawMessage(UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate().update("""
                INSERT INTO ingress.raw_inbound_messages (id, channel, tenant_id, message_type, payload, payload_sha256)
                VALUES (?, 'bank-xml', ?, 'pacs.002', '\\x00', '\\x00')
                """, id, tenantId);
        return id;
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
