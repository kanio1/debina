package com.sepanexus.modules.paymentlifecycle.isoadapter;

import com.sepanexus.shared.ClockPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * EPIC-27 Story 27.2C/27.4: the single atomic unit tying Story 27.2B's pure policy to persistence
 * and event publication — one transaction: run {@link Pacs002CorrelationPolicy}, persist the
 * decision to {@code iso.iso_message_correlation} (every outcome), and publish exactly one outbox
 * event for {@code MATCHED} ({@code iso.message.correlated}) or {@code ORPHANED} ({@code
 * iso.message.orphaned}) — never both, never for {@code AMBIGUOUS} (that outcome's manual-
 * correlation operator read model is a separate, {@code [P1]} capability, Story 27.5, not built
 * here). {@code iso-adapter} correlates only; it never mutates {@code payment.*} or calls into
 * {@code payment-lifecycle}'s FSM (root {@code AGENTS.md} binding rule) — that consumption is a
 * separate capability (blocked {@code EPIC-20} Story 20.3), not built here.
 */
@Service
public class Pacs002CorrelationService {

    private final Pacs002CorrelationPolicy policy;
    private final IsoMessageCorrelationRecorder correlationRecorder;
    private final IsoOutboxRecorder outboxRecorder;
    private final ClockPort clockPort;
    private final ObjectMapper objectMapper;

    public Pacs002CorrelationService(Pacs002CorrelationPolicy policy, IsoMessageCorrelationRecorder correlationRecorder,
            IsoOutboxRecorder outboxRecorder, ClockPort clockPort, ObjectMapper objectMapper) {
        this.policy = policy;
        this.correlationRecorder = correlationRecorder;
        this.outboxRecorder = outboxRecorder;
        this.clockPort = clockPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CorrelationDecision correlate(UUID tenantId, UUID isoMessageId, Pacs002CorrelationInput input) {
        CorrelationDecision decision = policy.correlate(tenantId, input);
        Instant now = clockPort.now();

        correlationRecorder.recordPacs002Correlation(isoMessageId, decision, now);

        if (decision.outcome() == CorrelationOutcome.MATCHED) {
            UUID eventId = UUID.randomUUID();
            IsoMessageCorrelatedEvent event = new IsoMessageCorrelatedEvent(
                    eventId, isoMessageId, decision.matchedPaymentId(), tenantId, decision.matchedBy().name());
            outboxRecorder.record(decision.matchedPaymentId(), IsoOutboxRecorder.MESSAGE_CORRELATED,
                    serialize(event), eventId, now);
        } else if (decision.outcome() == CorrelationOutcome.ORPHANED) {
            UUID eventId = UUID.randomUUID();
            IsoMessageOrphanedEvent event = new IsoMessageOrphanedEvent(eventId, isoMessageId, tenantId);
            outboxRecorder.record(isoMessageId, IsoOutboxEventType.MESSAGE_ORPHANED.eventType(),
                    serialize(event), eventId, now);
        }

        return decision;
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not serialize iso outbox event", exception);
        }
    }
}
