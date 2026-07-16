package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * EPIC-27 Story 27.2C: writes {@code iso.outbox_events} — the same per-schema transactional-outbox
 * pattern already used for {@code payment.outbox_events} (§4.4/ADR-N5), applied to the {@code iso}
 * schema (explicitly named in §4.4's own per-schema example list). Uses {@link JdbcTemplate}
 * rather than a JPA entity to match this package's existing {@code iso.*} write convention
 * ({@link JsonDirectLineageRecorder}, {@link Pain001LineageRecorder}, {@link
 * IsoMessageCorrelationRecorder} are all plain JDBC — no JPA entity exists anywhere in {@code
 * iso-adapter} today).
 */
@Component
public class IsoOutboxRecorder {

    public static final String MESSAGE_CORRELATED = "iso.message.correlated.v1";

    private final JdbcTemplate jdbcTemplate;

    public IsoOutboxRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** {@code aggregateId} is the Kafka key — {@code payment_id}, per the §3.7 v2 topic catalog. */
    public void record(UUID aggregateId, String eventType, String payload, UUID correlationId, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO iso.outbox_events (aggregate_id, event_type, payload, correlation_id, created_at)
                VALUES (?, ?, ?::jsonb, ?, ?)
                """, aggregateId, eventType, payload, correlationId, Timestamp.from(createdAt));
    }
}
