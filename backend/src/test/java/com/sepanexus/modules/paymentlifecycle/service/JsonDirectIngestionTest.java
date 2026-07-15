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
            .withPassword("test_admin");
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
            statement.execute("TRUNCATE payment.payments, payment.outbox_events, "
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
