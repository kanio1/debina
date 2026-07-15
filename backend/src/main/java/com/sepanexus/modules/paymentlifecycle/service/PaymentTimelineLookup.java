package com.sepanexus.modules.paymentlifecycle.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * OQ-12 resolution (EPIC-11 Story 11.1, EPIC-24 Story 24.2 timeline): reads
 * {@code payment.payment_status_history} only — never {@code payment.payment_events} (a separate,
 * lower-level domain-event log, not the timeline read model) and never {@code payment.outbox_events}
 * (Kafka delivery transport). Ordered strictly by {@code seq}, never by timestamp alone (two
 * transitions can share a millisecond).
 */
@Component
public class PaymentTimelineLookup {

    private final JdbcTemplate jdbcTemplate;

    public PaymentTimelineLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TimelineEntry> find(UUID paymentId, int limit, int afterSeq) {
        return jdbcTemplate.query("""
                SELECT seq, from_status, to_status, status_code, reason_code, source_type, actor_type,
                       is_final, event_type, event_ref, at
                FROM payment.payment_status_history
                WHERE payment_id = ? AND seq > ?
                ORDER BY seq ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new TimelineEntry(
                        rs.getInt("seq"),
                        rs.getString("from_status"),
                        rs.getString("to_status"),
                        rs.getString("status_code"),
                        rs.getString("reason_code"),
                        rs.getString("source_type"),
                        rs.getString("actor_type"),
                        rs.getBoolean("is_final"),
                        rs.getString("event_type"),
                        rs.getObject("event_ref", UUID.class),
                        toInstant(rs.getTimestamp("at"))),
                paymentId, afterSeq, limit);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record TimelineEntry(int seq, String fromStatus, String toStatus, String statusCode, String reasonCode,
            String sourceType, String actorType, boolean isFinal, String eventType, UUID eventRef, Instant at) {
    }
}
