package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.OutboxEvent;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentLifecycleEvent;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentStatus;
import com.sepanexus.modules.paymentlifecycle.repository.OutboxEventRepository;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import com.sepanexus.shared.ClockPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The "insert payment row + write outbox(payment.submitted) + record RECEIVED history/event in one
 * unit" step shared by every ingress channel (JSON_DIRECT, pain.001) — extracted so the two
 * channels' services don't each re-implement the same insert + event-serialization pair. The
 * domain event ID generated here is shared across three places (OQ-12 "event identity", EPIC-11
 * Story 11.1): {@link PaymentLifecycleEvent#eventId()} (what {@code InboxConsumer} dedups on),
 * {@code payment.payment_events.id}, and {@code payment.payment_status_history.event_ref}.
 */
@Component
class PaymentCreationWriter {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentHistoryRecorder paymentHistoryRecorder;
    private final ClockPort clockPort;
    private final ObjectMapper objectMapper;

    PaymentCreationWriter(PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository,
            PaymentHistoryRecorder paymentHistoryRecorder, ClockPort clockPort, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentHistoryRecorder = paymentHistoryRecorder;
        this.clockPort = clockPort;
        this.objectMapper = objectMapper;
    }

    PaymentEntity create(UUID tenantId, UUID branchId, BigDecimal amount, String currency,
            String debtorIban, String creditorIban) {
        Instant now = clockPort.now();
        PaymentEntity payment = paymentRepository.save(PaymentEntity.received(tenantId, branchId, amount,
                currency, debtorIban, creditorIban, now));

        UUID eventId = UUID.randomUUID();
        String payload = eventPayload(eventId, payment, tenantId);
        outboxEventRepository.save(OutboxEvent.paymentSubmitted(payment.getId(), payload, UUID.randomUUID(), now));
        paymentHistoryRecorder.recordEvent(eventId, payment.getId(), OutboxEvent.PAYMENT_SUBMITTED, payload, now);
        paymentHistoryRecorder.recordTransition(payment.getId(), null, PaymentStatus.RECEIVED, "INTERNAL",
                eventId, OutboxEvent.PAYMENT_SUBMITTED, now);

        return payment;
    }

    private String eventPayload(UUID eventId, PaymentEntity payment, UUID tenantId) {
        try {
            return objectMapper.writeValueAsString(new PaymentLifecycleEvent(
                    eventId, payment.getId(), tenantId, OutboxEvent.PAYMENT_SUBMITTED));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not serialize payment lifecycle event", exception);
        }
    }
}
