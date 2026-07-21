package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.SepaNexusApplication;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EPIC-19 Story 19.1: JSON_DIRECT REST ingestion is idempotent (PG18 two-step, §4.2) and records
 * identifiers/lineage through the seeded JSON_DIRECT pseudo message-version (§2.2a, ADR-N7).
 */
@SpringBootTest(classes = SepaNexusApplication.class)
class JsonDirectIngestionTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);
    private static boolean initialized;

    @Autowired
    private PaymentService paymentService;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payment_approvals, payment.payments, payment.outbox_events, "
                    + "payment.payment_status_history, payment.payment_events, "
                    + "ingress.idempotency_keys, ingress.raw_inbound_messages, "
                    + "iso.payment_iso_identifiers, iso.message_lineage, iso.iso_messages CASCADE");
        }
    }

    @Test
    @WithMockUser(roles = "payment_submitter")
    void repeatedSubmissionWithSameIdempotencyKeyReturnsSamePaymentId() {
        UUID tenantId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        SubmitPaymentCommand command = new SubmitPaymentCommand(tenantId, null, "e2e-json-direct-1",
                new BigDecimal("10.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                idempotencyKey);

        PaymentEntitySnapshot first = snapshot(paymentService.submitPayment(command));
        PaymentEntitySnapshot second = snapshot(paymentService.submitPayment(command));

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    @WithMockUser(roles = "payment_submitter")
    void sameIdempotencyKeyDifferentRequestBodyIsRejected() {
        UUID tenantId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        SubmitPaymentCommand original = new SubmitPaymentCommand(tenantId, null, "e2e-json-direct-2",
                new BigDecimal("10.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                idempotencyKey);
        SubmitPaymentCommand differentAmount = new SubmitPaymentCommand(tenantId, null, "e2e-json-direct-2",
                new BigDecimal("99.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                idempotencyKey);

        paymentService.submitPayment(original);

        assertThatThrownBy(() -> paymentService.submitPayment(differentAmount))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @WithMockUser(roles = "payment_submitter")
    void identifiersAndLineageRecordedForJsonDirectChannel() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SubmitPaymentCommand command = new SubmitPaymentCommand(tenantId, null, "e2e-json-direct-3",
                new BigDecimal("10.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                UUID.randomUUID().toString());

        PaymentEntitySnapshot payment = snapshot(paymentService.submitPayment(command));

        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT source_message_type, end_to_end_id FROM iso.payment_iso_identifiers WHERE payment_id = ?
                """)) {
            statement.setObject(1, payment.id());
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("source_message_type")).isEqualTo("JSON_DIRECT");
                assertThat(result.getString("end_to_end_id")).isEqualTo("e2e-json-direct-3");
                assertThat(result.next()).isFalse();
            }
        }

        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM iso.message_lineage WHERE payment_id = ? AND lineage_role = 'ORIGINAL_INSTRUCTION'
                """)) {
            statement.setObject(1, payment.id());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    /**
     * EPIC-21 Story 21.2: {@code EndToEndId} is an ISO lineage identifier, not a payment
     * uniqueness key (sepa-nexus-message-flow-and-data-blueprint.md §4.3 v2 patch — the old
     * {@code (tenant_id, end_to_end_id)} unique index is removed with no replacement). Two
     * genuinely distinct requests (different {@code Idempotency-Key}) that happen to reuse the
     * same business {@code EndToEndId} are two distinct payments, each with its own independent
     * identifier/lineage row.
     */
    @Test
    @WithMockUser(roles = "payment_submitter")
    void twoDifferentIdempotencyKeysWithSameEndToEndIdCreateTwoPayments() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String sharedEndToEndId = "e2e-reused-across-requests";
        SubmitPaymentCommand first = new SubmitPaymentCommand(tenantId, null, sharedEndToEndId,
                new BigDecimal("10.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                UUID.randomUUID().toString());
        SubmitPaymentCommand second = new SubmitPaymentCommand(tenantId, null, sharedEndToEndId,
                new BigDecimal("20.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                UUID.randomUUID().toString());

        PaymentEntitySnapshot firstPayment = snapshot(paymentService.submitPayment(first));
        PaymentEntitySnapshot secondPayment = snapshot(paymentService.submitPayment(second));

        assertThat(firstPayment.id()).isNotEqualTo(secondPayment.id());
        assertThat(count("SELECT count(*) FROM payment.payments WHERE tenant_id = ?", tenantId)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = ?",
                sharedEndToEndId)).isEqualTo(2);
        assertThat(count("SELECT count(DISTINCT iso_message_id) FROM iso.payment_iso_identifiers WHERE end_to_end_id = ?",
                sharedEndToEndId)).as("each payment gets its own iso_message_id, never shared").isEqualTo(2);
        assertThat(count("SELECT count(*) FROM iso.message_lineage WHERE payment_id IN (?, ?) "
                + "AND lineage_role = 'ORIGINAL_INSTRUCTION'", firstPayment.id(), secondPayment.id())).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id IN (?, ?)",
                firstPayment.id(), secondPayment.id())).isEqualTo(2);
    }

    /**
     * OQ-12 / EPIC-11 Story 11.1: creation records the {@code RECEIVED} baseline atomically with
     * the payment row, sharing one domain event ID across {@code payment_events.id} and
     * {@code payment_status_history.event_ref} (§35 "event identity").
     */
    @Test
    @WithMockUser(roles = "payment_submitter")
    void receivedHistoryAndEventRecordedForJsonDirectChannel() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SubmitPaymentCommand command = new SubmitPaymentCommand(tenantId, null, "e2e-json-direct-history",
                new BigDecimal("10.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                UUID.randomUUID().toString());

        PaymentEntitySnapshot payment = snapshot(paymentService.submitPayment(command));

        assertThat(count("SELECT count(*) FROM payment.payment_status_history WHERE payment_id = ?", payment.id()))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payment_status_history WHERE payment_id = ? "
                + "AND seq = 1 AND from_status IS NULL AND to_status = 'RECEIVED' AND is_final = false "
                + "AND source_type = 'INTERNAL' AND event_ref IS NOT NULL", payment.id())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payment_events WHERE payment_id = ?", payment.id()))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payment_status_history h "
                + "JOIN payment.payment_events e ON e.id = h.event_ref "
                + "WHERE h.payment_id = ?", payment.id()))
                .as("history.event_ref must resolve to the actual payment_events row, not a dangling id")
                .isEqualTo(1);
    }

    private static int count(String sql, Object... params) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private record PaymentEntitySnapshot(UUID id) {
    }

    private static PaymentEntitySnapshot snapshot(com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity entity) {
        return new PaymentEntitySnapshot(entity.getId());
    }

    static synchronized void initializeDatabase() {
        if (initialized) {
            return;
        }
        POSTGRES.start();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception);
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        initialized = true;
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
