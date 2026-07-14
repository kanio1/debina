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

class InboxConsumerIdempotencyTest extends KafkaIntegrationSupport {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void appliesSameEventOnlyOnceUsingDatabaseInboxConstraint() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        insertPayment(paymentId, tenantId);
        String payload = "{\"eventId\":\"%s\",\"aggregateId\":\"%s\",\"tenantId\":\"%s\",\"eventType\":\"payment.submitted.v1\"}"
                .formatted(eventId, paymentId, tenantId);

        kafkaTemplate.send(PaymentLifecycleTopicConfig.TOPIC, paymentId.toString(), payload).get();
        kafkaTemplate.send(PaymentLifecycleTopicConfig.TOPIC, paymentId.toString(), payload).get();

        eventually(() -> {
            assertThat(inboxCount(eventId)).isEqualTo(1);
            assertThat(paymentStatus(paymentId)).isEqualTo("VALIDATED");
        });
    }

    private static void insertPayment(UUID id, UUID tenantId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, end_to_end_id, amount, debtor_iban, creditor_iban)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tenantId);
            statement.setString(3, "e2e-" + id);
            statement.setBigDecimal(4, new BigDecimal("10.00"));
            statement.setString(5, "DE89370400440532013000");
            statement.setString(6, "FR7630006000011234567890189");
            statement.executeUpdate();
        }
    }

    private static int inboxCount(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.inbox_events WHERE source_event_id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    private static String paymentStatus(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM payment.payments WHERE id = ?")) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getString(1); }
        }
    }
}
