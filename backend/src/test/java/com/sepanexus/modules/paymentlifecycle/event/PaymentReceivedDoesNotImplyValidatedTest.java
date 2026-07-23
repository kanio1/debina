package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@org.junit.jupiter.api.Tag("testcontainers")
class PaymentReceivedDoesNotImplyValidatedTest extends KafkaIntegrationSupport {

    @Autowired
    private InboxConsumer inboxConsumer;

    @Test
    void receivedFactIsDeduplicatedButDoesNotCreateValidatedStateOrHistory() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        insertPayment(paymentId, tenantId);

        inboxConsumer.consume(payload(eventId, paymentId, tenantId));
        inboxConsumer.consume(payload(eventId, paymentId, tenantId));

        assertThat(paymentStatus(paymentId)).isEqualTo("RECEIVED");
        assertThat(inboxCount(eventId)).isEqualTo(1);
        assertThat(validatedHistoryCount(paymentId)).isZero();
        assertThat(validatedOutboxCount(paymentId)).isZero();
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
            statement.setObject(1, id);
            statement.setObject(2, tenantId);
            statement.setBigDecimal(3, new BigDecimal("10.00"));
            statement.setString(4, "DE89370400440532013000");
            statement.setString(5, "FR7630006000011234567890189");
            statement.executeUpdate();
        }
    }

    private static String paymentStatus(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM payment.payments WHERE id = ?")) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static int inboxCount(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.inbox_events WHERE source_event_id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int validatedHistoryCount(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.payment_status_history WHERE payment_id = ? AND to_status = 'VALIDATED'")) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int validatedOutboxCount(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM payment.outbox_events
                WHERE aggregate_id = ? AND event_type = 'payment.validated.v1'
                """)) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
