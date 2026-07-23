package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@org.junit.jupiter.api.Tag("testcontainers")
class OutboxDispatcherTest extends KafkaIntegrationSupport {

    @Autowired
    private OutboxDispatcher dispatcher;

    @Test
    void publishesUnpublishedOutboxEventAndMarksItPublished() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String payload = "{\"eventId\":\"%s\",\"aggregateId\":\"%s\",\"tenantId\":\"%s\",\"eventType\":\"payment.received.v1\"}"
                .formatted(eventId, aggregateId, tenantId);
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                VALUES (?, ?, 'payment.received.v1', CAST(? AS jsonb), ?)
                """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, aggregateId);
            statement.setString(3, payload);
            statement.setObject(4, UUID.randomUUID());
            statement.executeUpdate();
        }

        dispatcher.dispatch();
        eventually(() -> assertThat(publishedAt(eventId)).isNotNull());

        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-verify-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(PaymentLifecycleTopicConfig.RECEIVED_TOPIC));
            boolean received = false;
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!received && System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                for (var record : records.records(PaymentLifecycleTopicConfig.RECEIVED_TOPIC)) {
                    if (record.value().contains(eventId.toString())) {
                        received = true;
                        break;
                    }
                }
            }
            assertThat(received).isTrue();
        }
    }

    private static Object publishedAt(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT published_at FROM payment.outbox_events WHERE id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1);
            }
        }
    }
}
