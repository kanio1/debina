package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EPIC-27 Story 27.4: proves {@link IsoOutboxDispatcher}'s real routing behavior against a real
 * Kafka broker (see {@code sepa-nexus-database-testing} skill — broker semantics genuinely matter
 * here: which topic a message actually lands on, and whether {@code published_at} is only ever
 * set after a real broker acknowledgement). {@link IsoOutboxDispatcher} is called explicitly in
 * every test, never relying on the {@code @Scheduled} trigger firing on its own.
 */
@org.junit.jupiter.api.Tag("testcontainers")
class IsoOutboxTopicRoutingTest extends IsoKafkaIntegrationSupport {

    @Autowired
    private IsoOutboxDispatcher dispatcher;

    @BeforeEach
    void isolateTheClaimBatch() throws Exception {
        try (Connection connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE iso.outbox_events");
        }
    }

    @Test
    void correlatedEventIsPublishedOnlyToTheCorrelatedTopic() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        insertOutboxRow(eventId, paymentId, IsoOutboxEventType.MESSAGE_CORRELATED.eventType(),
                "{\"eventId\":\"%s\",\"paymentId\":\"%s\"}".formatted(eventId, paymentId));

        dispatcher.dispatch();
        eventually(() -> assertThat(publishedAt(eventId)).isNotNull());

        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_CORRELATED.topic(), eventId)).isPresent();
        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_ORPHANED.topic(), eventId)).isEmpty();
    }

    @Test
    void orphanedEventIsPublishedOnlyToTheOrphanedTopicKeyedByIsoMessageId() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID isoMessageId = UUID.randomUUID();
        insertOutboxRow(eventId, isoMessageId, IsoOutboxEventType.MESSAGE_ORPHANED.eventType(),
                "{\"eventId\":\"%s\",\"isoMessageId\":\"%s\"}".formatted(eventId, isoMessageId));

        dispatcher.dispatch();
        eventually(() -> assertThat(publishedAt(eventId)).isNotNull());

        var record = consumeOne(IsoOutboxEventType.MESSAGE_ORPHANED.topic(), eventId);
        assertThat(record).isPresent();
        assertThat(record.get().key()).isEqualTo(isoMessageId.toString());
        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_CORRELATED.topic(), eventId)).isEmpty();
    }

    @Test
    void unknownEventTypeIsNeverPublishedAndStaysUnpublished() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        insertOutboxRow(eventId, aggregateId, "iso.message.something-else.v1",
                "{\"eventId\":\"%s\"}".formatted(eventId));

        assertThatThrownBy(dispatcher::dispatch)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized iso outbox event type");

        assertThat(publishedAt(eventId)).isNull();
        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_CORRELATED.topic(), eventId)).isEmpty();
        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_ORPHANED.topic(), eventId)).isEmpty();
    }

    /**
     * The dispatcher gets a deterministic failed acknowledgement rather than a fake localhost
     * broker. Real topic routing and successful acknowledgements are covered by the other tests
     * in this Testcontainers suite; this test isolates the transaction invariant that a failed
     * acknowledgement cannot mark the row published.
     */
    @Test
    void failedKafkaAcknowledgementLeavesTheEventUnpublishedForRetry() throws Exception {
        UUID eventId = UUID.randomUUID();
        insertOutboxRow(eventId, UUID.randomUUID(), IsoOutboxEventType.MESSAGE_ORPHANED.eventType(),
                "{\"eventId\":\"%s\"}".formatted(eventId));

        KafkaTemplate<String, String> failingKafkaTemplate = mock(KafkaTemplate.class);
        when(failingKafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("induced Kafka acknowledgement failure")));
        IsoOutboxDispatcher failingDispatcher = new IsoOutboxDispatcher(
                new JdbcTemplate(appDataSource()), failingKafkaTemplate, () -> java.time.Instant.now());

        assertThatThrownBy(failingDispatcher::dispatch)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kafka publication failed");
        assertThat(publishedAt(eventId)).isNull();
    }

    // -- helpers -------------------------------------------------------------------------------

    private static DriverManagerDataSource appDataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }

    private void insertOutboxRow(UUID id, UUID aggregateId, String eventType, String payloadJson) {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO iso.outbox_events (id, aggregate_id, event_type, payload, correlation_id, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, now())
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, aggregateId);
            statement.setString(3, eventType);
            statement.setString(4, payloadJson);
            statement.setObject(5, UUID.randomUUID());
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object publishedAt(UUID eventId) {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT published_at FROM iso.outbox_events WHERE id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private java.util.Optional<ConsumedRecord> consumeOne(String topic, UUID eventId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "iso-outbox-routing-verify-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                for (var record : records.records(topic)) {
                    if (record.value() != null && record.value().contains(eventId.toString())) {
                        return java.util.Optional.of(new ConsumedRecord(record.key(), record.value()));
                    }
                }
            }
            return java.util.Optional.empty();
        }
    }

    private record ConsumedRecord(String key, String value) {
    }
}
