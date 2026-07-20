package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayCrashWindowRedeliveryTest extends KafkaIntegrationSupport {

    @Autowired @Qualifier("outboxRelayJdbcTemplate") private JdbcTemplate relayJdbcTemplate;
    @Autowired @Qualifier("outboxRelayTransactionManager") private PlatformTransactionManager relayTransactionManager;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private OutboxDispatcher healthyDispatcher;

    @Test
    void acknowledgementThenRollbackLeavesTheSameEventUnpublishedAndRedeliveryCreatesOneInboxIdentity() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        insertPayment(paymentId, tenantId);
        insertOutbox(eventId, paymentId, tenantId);
        OutboxDispatcher crashWindowDispatcher = new OutboxDispatcher(relayJdbcTemplate, kafkaTemplate,
                () -> Instant.parse("2026-07-20T10:00:00Z"),
                () -> { throw new IllegalStateException("forced rollback after broker acknowledgement"); });

        assertThatThrownBy(() -> new TransactionTemplate(relayTransactionManager)
                .executeWithoutResult(status -> crashWindowDispatcher.dispatch()))
                .isInstanceOf(OutboxRelayPublicationException.class);
        assertThat(publishedAt(eventId)).isNull();

        healthyDispatcher.dispatch();
        eventually(() -> {
            assertThat(inboxCount(eventId)).isEqualTo(1);
            assertThat(paymentStatus(paymentId)).isEqualTo("RECEIVED");
        });
        assertThat(publishedAt(eventId)).isNotNull();
    }

    private static void insertPayment(UUID id, UUID tenantId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, debtor_iban, creditor_iban)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, id); statement.setObject(2, tenantId);
            statement.setBigDecimal(3, new BigDecimal("10.00"));
            statement.setString(4, "DE89370400440532013000"); statement.setString(5, "FR7630006000011234567890189");
            statement.executeUpdate();
        }
    }

    private static void insertOutbox(UUID eventId, UUID paymentId, UUID tenantId) throws Exception {
        String payload = "{\"eventId\":\"%s\",\"aggregateId\":\"%s\",\"tenantId\":\"%s\",\"eventType\":\"payment.received.v1\"}"
                .formatted(eventId, paymentId, tenantId);
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                VALUES (?, ?, 'payment.received.v1', CAST(? AS jsonb), ?)
                """)) {
            statement.setObject(1, eventId); statement.setObject(2, paymentId);
            statement.setString(3, payload); statement.setObject(4, UUID.randomUUID()); statement.executeUpdate();
        }
    }

    private static Object publishedAt(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("SELECT published_at FROM payment.outbox_events WHERE id = ?")) {
            statement.setObject(1, eventId); try (ResultSet result = statement.executeQuery()) { result.next(); return result.getObject(1); }
        }
    }
    private static int inboxCount(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM payment.inbox_events WHERE source_event_id = ?")) {
            statement.setObject(1, eventId); try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }
    private static String paymentStatus(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("SELECT status FROM payment.payments WHERE id = ?")) {
            statement.setObject(1, paymentId); try (ResultSet result = statement.executeQuery()) { result.next(); return result.getString(1); }
        }
    }
}
