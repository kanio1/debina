package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxRelayKafkaFailureTest extends KafkaIntegrationSupport {

    @Autowired
    @Qualifier("outboxRelayJdbcTemplate")
    private JdbcTemplate relayJdbcTemplate;

    @Autowired
    @Qualifier("outboxRelayTransactionManager")
    private PlatformTransactionManager relayTransactionManager;

    @Autowired
    private OutboxDispatcher healthyDispatcher;

    @Test
    void failedKafkaAcknowledgementRollsBackTheBatchLeavesRowUnpublishedAndAHealthyRunCanRedeliver() throws Exception {
        UUID eventId = insertOutboxRow();
        KafkaTemplate<String, String> failingKafkaTemplate = mock(KafkaTemplate.class);
        when(failingKafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("induced Kafka acknowledgement failure")));
        OutboxDispatcher failingDispatcher = new OutboxDispatcher(relayJdbcTemplate,
                failingKafkaTemplate, () -> Instant.parse("2026-01-01T00:00:00Z"));

        assertThatThrownBy(() -> new TransactionTemplate(relayTransactionManager)
                .executeWithoutResult(status -> failingDispatcher.dispatch()))
                .isInstanceOf(OutboxRelayPublicationException.class);
        assertThat(publishedAt(eventId)).isNull();

        healthyDispatcher.dispatch();
        eventually(() -> assertThat(publishedAt(eventId)).isNotNull());
    }

    private static UUID insertOutboxRow() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                VALUES (?, ?, 'payment.received.v1', CAST(? AS jsonb), ?)
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, "{\"eventId\":\"%s\"}".formatted(id));
            statement.setObject(4, UUID.randomUUID());
            statement.executeUpdate();
        }
        return id;
    }

    private static Object publishedAt(UUID id) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT published_at FROM payment.outbox_events WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1);
            }
        }
    }
}
