package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.modules.paymentlifecycle.domain.IllegalPaymentTransitionException;
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

    @Autowired
    private InboxConsumer inboxConsumer;

    @Test
    void appliesSameEventOnlyOnceUsingDatabaseInboxConstraint() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        insertPayment(paymentId, tenantId);
        String payload = eventPayload(eventId, paymentId, tenantId);

        kafkaTemplate.send(PaymentLifecycleTopicConfig.TOPIC, paymentId.toString(), payload).get();
        kafkaTemplate.send(PaymentLifecycleTopicConfig.TOPIC, paymentId.toString(), payload).get();

        eventually(() -> {
            assertThat(inboxCount(eventId)).isEqualTo(1);
            assertThat(paymentStatus(paymentId)).isEqualTo("VALIDATED");
            assertThat(historyCount(paymentId, "VALIDATED"))
                    .as("duplicate redelivery of the same event must not create a second history row")
                    .isEqualTo(1);
        });
    }

    /**
     * OQ-12 / EPIC-11 Story 11.1: the VALIDATED transition records a history row atomically with
     * the status change, referencing the triggering Kafka event's own ID as {@code event_ref}
     * (the same event is never re-logged into {@code payment_events} a second time — see
     * {@code PaymentHistoryRecorder} javadoc).
     */
    @Test
    void validatedTransitionRecordsHistoryRowReferencingTriggeringEvent() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        insertPayment(paymentId, tenantId);
        String payload = eventPayload(eventId, paymentId, tenantId);

        kafkaTemplate.send(PaymentLifecycleTopicConfig.TOPIC, paymentId.toString(), payload).get();

        eventually(() -> {
            assertThat(paymentStatus(paymentId)).isEqualTo("VALIDATED");
            assertThat(historyRowMatches(paymentId, "RECEIVED", "VALIDATED", eventId)).isEqualTo(1);
        });
    }

    /**
     * Direct method invocation (still through the real Spring-managed, {@code @Transactional}-proxied
     * bean — not {@code new InboxConsumer(...)}) rather than real Kafka redelivery: a poison-pill
     * message that keeps failing would otherwise retry indefinitely through the real listener
     * container, which is not a safe thing to provoke in a test. {@code IllegalPaymentTransitionException}
     * propagates directly since nothing in {@code consume()} catches it — proving the whole method's
     * transaction (inbox dedup row, status mutation, history insert) rolls back together.
     */
    @Test
    void illegalTransitionRollsBackWithoutChangingStatusOrHistory() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        insertPayment(paymentId, tenantId);
        String firstPayload = eventPayload(UUID.randomUUID(), paymentId, tenantId);
        kafkaTemplate.send(PaymentLifecycleTopicConfig.TOPIC, paymentId.toString(), firstPayload).get();
        eventually(() -> assertThat(paymentStatus(paymentId)).isEqualTo("VALIDATED"));

        int historyCountBefore = historyCount(paymentId, "VALIDATED");
        int inboxCountBefore = totalInboxCount();

        UUID secondEventId = UUID.randomUUID();
        String secondPayload = eventPayload(secondEventId, paymentId, tenantId);
        assertThatThrownBy(() -> inboxConsumer.consume(secondPayload))
                .isInstanceOf(IllegalPaymentTransitionException.class);

        assertThat(paymentStatus(paymentId)).isEqualTo("VALIDATED");
        assertThat(historyCount(paymentId, "VALIDATED")).isEqualTo(historyCountBefore);
        assertThat(totalInboxCount()).as("the failed attempt's own inbox dedup row must roll back too")
                .isEqualTo(inboxCountBefore);
        assertThat(inboxCount(secondEventId)).isZero();
    }

    private static String eventPayload(UUID eventId, UUID paymentId, UUID tenantId) {
        return "{\"eventId\":\"%s\",\"aggregateId\":\"%s\",\"tenantId\":\"%s\",\"eventType\":\"payment.submitted.v1\"}"
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

    private static int inboxCount(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.inbox_events WHERE source_event_id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    private static int totalInboxCount() throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.inbox_events")) {
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

    private static int historyCount(UUID paymentId, String toStatus) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.payment_status_history WHERE payment_id = ? AND to_status = ?")) {
            statement.setObject(1, paymentId);
            statement.setString(2, toStatus);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    private static int historyRowMatches(UUID paymentId, String fromStatus, String toStatus, UUID eventRef)
            throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM payment.payment_status_history
                WHERE payment_id = ? AND from_status = ? AND to_status = ? AND event_ref = ?
                """)) {
            statement.setObject(1, paymentId);
            statement.setString(2, fromStatus);
            statement.setString(3, toStatus);
            statement.setObject(4, eventRef);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }
}
