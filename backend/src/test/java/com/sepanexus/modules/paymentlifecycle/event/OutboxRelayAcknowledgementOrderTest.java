package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/** Proves a row is marked only after its Kafka future has acknowledged publication. */
@org.junit.jupiter.api.Tag("fast")
class OutboxRelayAcknowledgementOrderTest {

    @Test
    void doesNotMarkPublishedUntilKafkaAcknowledges() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        JdbcTemplate relayJdbcTemplate = mock(JdbcTemplate.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CountDownLatch sendStarted = new CountDownLatch(1);
        CompletableFuture<SendResult<String, String>> acknowledgement = new CompletableFuture<>();
        stubOneUnpublishedRow(relayJdbcTemplate, eventId, aggregateId);
        when(kafkaTemplate.send(eq(PaymentLifecycleTopicConfig.RECEIVED_TOPIC), eq(aggregateId.toString()), anyString()))
                .thenAnswer(invocation -> {
                    sendStarted.countDown();
                    return acknowledgement;
                });
        OutboxDispatcher dispatcher = new OutboxDispatcher(relayJdbcTemplate, kafkaTemplate,
                () -> Instant.parse("2026-07-20T12:00:00Z"));

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var dispatch = executor.submit(dispatcher::dispatch);
            assertThat(sendStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatch.isDone()).isFalse();
            verify(relayJdbcTemplate, never()).update(anyString(), any(), eq(eventId));

            acknowledgement.complete(mock(SendResult.class));
            dispatch.get(1, TimeUnit.SECONDS);
        }

        verify(relayJdbcTemplate).update(anyString(), any(), eq(eventId));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void stubOneUnpublishedRow(JdbcTemplate jdbcTemplate, UUID eventId, UUID aggregateId) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(eventId);
        when(row.getObject("aggregate_id", UUID.class)).thenReturn(aggregateId);
        when(row.getString("event_type")).thenReturn("payment.received.v1");
        when(row.getString("payload")).thenReturn("{\"eventId\":\"%s\"}".formatted(eventId));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
    }
}
