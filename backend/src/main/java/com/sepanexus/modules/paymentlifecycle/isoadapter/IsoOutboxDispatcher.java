package com.sepanexus.modules.paymentlifecycle.isoadapter;

import com.sepanexus.shared.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * EPIC-27 Story 27.2C: polls {@code iso.outbox_events} and publishes to {@link
 * IsoCorrelationTopicConfig#TOPIC} — the same polling {@code @Scheduled} dispatcher mechanics as
 * {@code payment}'s {@code OutboxDispatcher} (§2.5/§4.4/ADR-N5), a second instance of the same
 * per-schema pattern applied to the {@code iso} schema, never a competing event mechanism.
 */
@Component
public class IsoOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IsoOutboxDispatcher.class);

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ClockPort clockPort;

    public IsoOutboxDispatcher(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate, ClockPort clockPort) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.clockPort = clockPort;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void dispatch() {
        List<Map<String, Object>> unpublished = jdbcTemplate.query("""
                SELECT id, aggregate_id, payload
                FROM iso.outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at ASC
                LIMIT 50
                """, this::mapRow);

        for (Map<String, Object> row : unpublished) {
            UUID id = (UUID) row.get("id");
            UUID aggregateId = (UUID) row.get("aggregate_id");
            String payload = (String) row.get("payload");
            try {
                kafkaTemplate.send(IsoCorrelationTopicConfig.TOPIC, aggregateId.toString(), payload)
                        .get(5, TimeUnit.SECONDS);
                jdbcTemplate.update("UPDATE iso.outbox_events SET published_at = ? WHERE id = ?",
                        Timestamp.from(clockPort.now()), id);
            } catch (Exception exception) {
                log.warn("iso outbox event {} remains unpublished after Kafka publication failure", id, exception);
            }
        }
    }

    private Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Map.of(
                "id", UUID.fromString(rs.getString("id")),
                "aggregate_id", UUID.fromString(rs.getString("aggregate_id")),
                "payload", rs.getString("payload"));
    }
}
