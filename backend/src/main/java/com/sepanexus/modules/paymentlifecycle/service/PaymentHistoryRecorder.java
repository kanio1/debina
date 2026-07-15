package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentStatus;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentTransitionTable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * OQ-12 resolution (EPIC-11 Story 11.1): {@code payment.payment_status_history} (canonical,
 * ordered timeline of business-status transitions) and {@code payment.payment_events} (the
 * domain-event log) — both owned by {@code payment-lifecycle}
 * (sepa-nexus-blueprint-ownership-integration.md §3.6.2), distinct from {@code payment.outbox_events}
 * (Kafka delivery transport, not a read model). {@code seq} is derived from
 * {@code COALESCE(max(seq), 0) + 1} under the same transaction as the caller's other writes — the
 * {@code (payment_id, seq)} primary key is the correctness backstop against a genuine race; the
 * only two callers today (payment creation, and the Kafka inbox consumer gated by
 * {@code payment.inbox_events} dedup) never race against each other for the same payment.
 */
@Component
public class PaymentHistoryRecorder {

    private final JdbcTemplate jdbcTemplate;

    public PaymentHistoryRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records a domain event exactly once, at the point it first occurs. A later transition
     * caused by observing/consuming this same event (e.g. the walking-skeleton's outbox→Kafka→inbox
     * round-trip) references it again via {@code eventRef} in {@link #recordTransition}, rather than
     * logging the same event a second time. */
    public void recordEvent(UUID eventId, UUID paymentId, String eventType, String payloadJson, Instant at) {
        jdbcTemplate.update("""
                INSERT INTO payment.payment_events (id, payment_id, type, payload, at)
                VALUES (?, ?, ?, ?::jsonb, ?)
                """, eventId, paymentId, eventType, payloadJson, Timestamp.from(at));
    }

    public void recordTransition(UUID paymentId, PaymentStatus fromStatus, PaymentStatus toStatus, String sourceType,
            UUID eventRef, String eventType, Instant at) {
        int seq = nextSeq(paymentId);
        jdbcTemplate.update("""
                INSERT INTO payment.payment_status_history
                    (payment_id, seq, from_status, to_status, status_code, source_type, actor_type, is_final,
                     event_type, event_ref, at)
                VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM', ?, ?, ?, ?)
                """, paymentId, seq, fromStatus == null ? null : fromStatus.name(), toStatus.name(), toStatus.name(),
                sourceType, PaymentTransitionTable.isTerminal(toStatus), eventType, eventRef, Timestamp.from(at));
    }

    private int nextSeq(UUID paymentId) {
        Integer maxSeq = jdbcTemplate.queryForObject(
                "SELECT COALESCE(max(seq), 0) FROM payment.payment_status_history WHERE payment_id = ?",
                Integer.class, paymentId);
        return maxSeq + 1;
    }
}
