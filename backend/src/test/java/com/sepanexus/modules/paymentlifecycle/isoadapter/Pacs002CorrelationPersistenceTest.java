package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sepanexus.shared.ClockPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * EPIC-27 Story 27.2C: exercises the real {@link Pacs002CorrelationService}/{@link
 * JdbcCorrelationCandidateLookup}/{@link IsoMessageCorrelationRecorder}/{@link IsoOutboxRecorder}
 * against a real, isolated Testcontainers PostgreSQL — never a mock, since the property under
 * test (tenant-scoped SQL joins, append-only grants, deterministic persistence) only means
 * anything against a real database (see {@code sepa-nexus-database-testing} skill). No Kafka
 * Testcontainer: this test proves the outbox row is written, not broker semantics (see that
 * skill's guidance on when a Kafka container is actually needed) — {@link
 * IsoOutboxDispatcher}/actual publication is out of this story's scope.
 */
@Testcontainers
class Pacs002CorrelationPersistenceTest {

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
    void matchedResultIsPersistedWithMatchedByScoreNullAndOutboxRow() {
        UUID tenantId = UUID.randomUUID();
        UUID candidatePaymentId = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);
        insertCandidate(tenantId, candidatePaymentId, "MSG-1", null, "E2E-1", null, null);

        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-1", null, null, "E2E-1", null);
        CorrelationDecision decision = service().correlate(tenantId, currentIsoMessageId, input);

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.MATCHED);
        assertThat(decision.matchedPaymentId()).isEqualTo(candidatePaymentId);

        Map<String, Object> row = correlationRow(currentIsoMessageId);
        assertThat(row.get("status")).isEqualTo("MATCHED");
        assertThat(row.get("matched_payment_id")).isEqualTo(candidatePaymentId);
        assertThat(row.get("matched_by")).isEqualTo("ORGNL_MSG_ID_ORGNL_END_TO_END_ID");
        assertThat(row.get("score")).isNull();
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(FIXED_NOW);

        List<Map<String, Object>> outboxRows = jdbcTemplate().queryForList(
                "SELECT * FROM iso.outbox_events WHERE aggregate_id = ?", candidatePaymentId);
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).get("event_type")).isEqualTo(IsoOutboxRecorder.MESSAGE_CORRELATED);
    }

    @Test
    void ambiguousResultIsPersistedWithoutMatchedPaymentAndNoOutboxRow() {
        UUID tenantId = UUID.randomUUID();
        UUID paymentA = UUID.randomUUID();
        UUID paymentB = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);
        insertCandidate(tenantId, paymentA, "MSG-2", null, "E2E-2", null, null);
        insertCandidate(tenantId, paymentB, "MSG-2", null, "E2E-2", null, null);

        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-2", null, null, "E2E-2", null);
        CorrelationDecision decision = service().correlate(tenantId, currentIsoMessageId, input);

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.AMBIGUOUS);

        Map<String, Object> row = correlationRow(currentIsoMessageId);
        assertThat(row.get("status")).isEqualTo("AMBIGUOUS");
        assertThat(row.get("matched_payment_id")).isNull();
        assertThat(row.get("matched_by")).isNull();
        assertThat(row.get("score")).isNull();

        assertThat(jdbcTemplate().queryForList(
                "SELECT * FROM iso.outbox_events WHERE aggregate_id IN (?, ?)", paymentA, paymentB)).isEmpty();
    }

    @Test
    void orphanedResultIsPersistedWithoutMatchedPaymentWhenNoCandidateExists() {
        UUID tenantId = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);

        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-NONE", "TX-NONE", "INSTR-NONE", "E2E-NONE", "UETR-NONE");
        CorrelationDecision decision = service().correlate(tenantId, currentIsoMessageId, input);

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.ORPHANED);

        Map<String, Object> row = correlationRow(currentIsoMessageId);
        assertThat(row.get("status")).isEqualTo("ORPHANED");
        assertThat(row.get("matched_payment_id")).isNull();
        assertThat(row.get("matched_by")).isNull();
    }

    @Test
    void candidateLookupIsTenantScoped() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID candidatePaymentId = UUID.randomUUID();
        insertCandidate(tenantA, candidatePaymentId, "MSG-3", null, "E2E-3", null, null);

        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantB);
        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-3", null, null, "E2E-3", null);
        CorrelationDecision decision = service().correlate(tenantB, currentIsoMessageId, input);

        assertThat(decision.outcome())
                .as("a same-identifier candidate in a different tenant must never match")
                .isEqualTo(CorrelationOutcome.ORPHANED);
    }

    @Test
    void candidateLookupStrategiesOnlyMatchTheirOwnColumns() {
        UUID tenantId = UUID.randomUUID();
        UUID step2Payment = UUID.randomUUID();
        insertCandidate(tenantId, step2Payment, "MSG-4", "INSTR-4", "E2E-4", null, null);
        JdbcCorrelationCandidateLookup lookup = new JdbcCorrelationCandidateLookup(jdbcTemplate());

        assertThat(lookup.findByOrgnlMsgIdAndOrgnlInstrIdAndOrgnlEndToEndId(tenantId, "MSG-4", "INSTR-4", "E2E-4"))
                .extracting(CorrelationCandidate::paymentId)
                .containsExactly(step2Payment);
        assertThat(lookup.findByOrgnlMsgIdAndOrgnlTxId(tenantId, "MSG-4", "TX-does-not-exist")).isEmpty();
        assertThat(lookup.findByUetr(tenantId, "uetr-does-not-exist")).isEmpty();
    }

    @Test
    void sepaAppCannotUpdateOrDeleteCorrelationRows() {
        UUID tenantId = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);
        Pacs002CorrelationInput input = new Pacs002CorrelationInput("MSG-5", null, null, "E2E-5", null);
        service().correlate(tenantId, currentIsoMessageId, input);

        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE iso.iso_message_correlation SET status = 'MATCHED' WHERE iso_message_id = '"
                        + currentIsoMessageId + "'");
            }
        });
        assertInsufficientPrivilege(() -> {
            try (Connection connection = sepaAppConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "DELETE FROM iso.iso_message_correlation WHERE iso_message_id = '" + currentIsoMessageId + "'");
            }
        });
    }

    @Test
    void foreignModuleRoleCannotInsertCorrelationRows() {
        UUID tenantId = UUID.randomUUID();
        UUID currentIsoMessageId = insertCurrentPacs002Message(tenantId);

        assertInsufficientPrivilege(() -> {
            try (Connection connection = signatureRoleConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO iso.iso_message_correlation (iso_message_id, correlation_type, status, created_at)
                        VALUES ('%s', 'PACS002_TO_PAYMENT', 'ORPHANED', now())
                        """.formatted(currentIsoMessageId));
            }
        });
    }

    // -- helpers -------------------------------------------------------------------------------

    private Pacs002CorrelationService service() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        Pacs002CorrelationPolicy policy = new Pacs002CorrelationPolicy(new JdbcCorrelationCandidateLookup(jdbcTemplate));
        ClockPort fixedClock = () -> FIXED_NOW;
        return new Pacs002CorrelationService(policy, new IsoMessageCorrelationRecorder(jdbcTemplate),
                new IsoOutboxRecorder(jdbcTemplate), fixedClock, JSON);
    }

    private static JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
        return new JdbcTemplate(dataSource);
    }

    private Map<String, Object> correlationRow(UUID isoMessageId) {
        List<Map<String, Object>> rows = jdbcTemplate().queryForList(
                "SELECT * FROM iso.iso_message_correlation WHERE iso_message_id = ?", isoMessageId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    /** The incoming pacs.002 message itself — the row {@code iso_message_id} in the correlation table points at. */
    private UUID insertCurrentPacs002Message(UUID tenantId) {
        UUID rawMessageId = insertRawMessage(tenantId);
        UUID isoMessageId = UUID.randomUUID();
        jdbcTemplate().update("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status, raw_message_id, tenant_id)
                VALUES (?, 'INBOUND', 'pacs.002', 'PARSED', ?, ?)
                """, isoMessageId, rawMessageId, tenantId);
        return isoMessageId;
    }

    /** An earlier original-instruction message (pain.001/JSON_DIRECT) a payment can be correlated against. */
    private void insertCandidate(UUID tenantId, UUID paymentId, String msgId, String instrId, String endToEndId,
            String uetr, String txId) {
        UUID rawMessageId = insertRawMessage(tenantId);
        UUID isoMessageId = UUID.randomUUID();
        jdbcTemplate().update("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status, raw_message_id, tenant_id)
                VALUES (?, 'INBOUND', 'pain.001', 'PARSED', ?, ?)
                """, isoMessageId, rawMessageId, tenantId);
        jdbcTemplate().update("""
                INSERT INTO iso.payment_iso_identifiers
                    (payment_id, source_message_type, iso_message_id, msg_id, instr_id, end_to_end_id, uetr, tx_id)
                VALUES (?, 'pain.001', ?, ?, ?, ?, ?, ?)
                """, paymentId, isoMessageId, msgId, instrId, endToEndId, uetr, txId);
    }

    private UUID insertRawMessage(UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate().update("""
                INSERT INTO ingress.raw_inbound_messages (id, channel, tenant_id, message_type, payload, payload_sha256)
                VALUES (?, 'bank-xml', ?, 'pacs.002', '\\x00', '\\x00')
                """, id, tenantId);
        return id;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertInsufficientPrivilege(ThrowingRunnable runnable) {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new RuntimeException(e);
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection sepaAppConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }

    private static Connection signatureRoleConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "signature_role", "dev-only-signature");
    }
}
