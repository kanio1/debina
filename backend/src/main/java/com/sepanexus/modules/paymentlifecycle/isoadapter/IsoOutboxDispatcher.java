package com.sepanexus.modules.paymentlifecycle.isoadapter;

import com.sepanexus.shared.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * EPIC-27 Story 27.2C/27.4: polls {@code iso.outbox_events} and publishes each row to the topic
 * {@link IsoOutboxEventType} resolves for its {@code event_type} — the same polling {@code
 * @Scheduled} dispatcher mechanics as {@code payment}'s {@code OutboxDispatcher}
 * (§2.5/§4.4/ADR-N5), a second instance of the same per-schema pattern applied to the {@code iso}
 * schema, never a competing event mechanism. A row whose {@code event_type} does not resolve to a
 * known {@link IsoOutboxEventType} is never published to any topic and is left unpublished
 * (logged by id/event_type only — never the payload, per the payments-data-integrity skill's
 * no-full-payload-in-logs rule).
 */
@Component
public class IsoOutboxDispatcher {

    private final JdbcTemplate relayJdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ClockPort clockPort;

    public IsoOutboxDispatcher(@Qualifier("outboxRelayJdbcTemplate") JdbcTemplate relayJdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate, ClockPort clockPort) {
        this.relayJdbcTemplate = relayJdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.clockPort = clockPort;
    }

    /** A caller controls scheduling; this method is one atomic claim/publish/mark batch. */
    @Transactional(transactionManager = "outboxRelayTransactionManager")
    public void dispatch() {
        List<Map<String, Object>> unpublished = relayJdbcTemplate.query("""
                SELECT id, aggregate_id, event_type, payload, created_at
                FROM iso.outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 50
                """, this::mapRow);

        for (Map<String, Object> row : unpublished) {
            UUID id = (UUID) row.get("id");
            String eventType = (String) row.get("event_type");
            Optional<IsoOutboxEventType> resolved = IsoOutboxEventType.fromEventType(eventType);
            if (resolved.isEmpty()) {
                throw new IllegalArgumentException("Unrecognized iso outbox event type " + eventType + " for event " + id);
            }

            UUID aggregateId = (UUID) row.get("aggregate_id");
            String payload = (String) row.get("payload");
            publish(id, resolved.get().topic(), aggregateId, payload);
            relayJdbcTemplate.update("""
                    UPDATE iso.outbox_events
                    SET published_at = ?
                    WHERE id = ?
                      AND published_at IS NULL
                    """, Timestamp.from(clockPort.now()), id);
        }
    }

    private void publish(UUID id, String topic, UUID aggregateId, String payload) {
        try {
            kafkaTemplate.send(topic, aggregateId.toString(), payload).get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka publication failed for iso outbox event " + id, exception);
        }
    }

    private Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Map.of(
                "id", UUID.fromString(rs.getString("id")),
                "aggregate_id", UUID.fromString(rs.getString("aggregate_id")),
                "event_type", rs.getString("event_type"),
                "payload", rs.getString("payload"));
    }
}
