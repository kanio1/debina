package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

@org.junit.jupiter.api.Tag("testcontainers")
class InboxConsumerIdempotencyTest extends KafkaIntegrationSupport {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void deduplicatesReceivedRedeliveryWithoutCreatingValidatedState() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        insertPayment(paymentId, tenantId);
        String payload = payload(eventId, paymentId, tenantId);

        kafkaTemplate.send(PaymentLifecycleTopicConfig.RECEIVED_TOPIC, paymentId.toString(), payload).get();
        kafkaTemplate.send(PaymentLifecycleTopicConfig.RECEIVED_TOPIC, paymentId.toString(), payload).get();

        eventually(() -> {
            assertThat(inboxCount(eventId)).isEqualTo(1);
            assertThat(paymentStatus(paymentId)).isEqualTo("RECEIVED");
            assertThat(validatedHistoryCount(paymentId)).isZero();
        });
    }

    private static String payload(UUID eventId, UUID paymentId, UUID tenantId) {
        return "{\"eventId\":\"%s\",\"aggregateId\":\"%s\",\"tenantId\":\"%s\",\"eventType\":\"payment.received.v1\"}"
                .formatted(eventId, paymentId, tenantId);
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

    private static int inboxCount(UUID eventId) throws Exception { return count("SELECT count(*) FROM payment.inbox_events WHERE source_event_id = ?", eventId); }
    private static int validatedHistoryCount(UUID paymentId) throws Exception { return count("SELECT count(*) FROM payment.payment_status_history WHERE payment_id = ? AND to_status = 'VALIDATED'", paymentId); }
    private static int count(String sql, UUID id) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id); try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }
    private static String paymentStatus(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("SELECT status FROM payment.payments WHERE id = ?")) {
            statement.setObject(1, paymentId); try (ResultSet result = statement.executeQuery()) { result.next(); return result.getString(1); }
        }
    }
}
