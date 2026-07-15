package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.OutboxEvent;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentLifecycleEvent;
import com.sepanexus.modules.paymentlifecycle.repository.OutboxEventRepository;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import com.sepanexus.shared.ClockPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The "insert payment row + write outbox(payment.submitted) in one unit" step shared by every
 * ingress channel (JSON_DIRECT today, pain.001 from Story 19.4) — extracted so the two channels'
 * services don't each re-implement the same insert + event-serialization pair.
 */
@Component
class PaymentCreationWriter {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClockPort clockPort;
    private final ObjectMapper objectMapper;

    PaymentCreationWriter(PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository,
            ClockPort clockPort, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.clockPort = clockPort;
        this.objectMapper = objectMapper;
    }

    PaymentEntity create(UUID tenantId, UUID branchId, String endToEndId, BigDecimal amount, String currency,
            String debtorIban, String creditorIban) {
        PaymentEntity payment = paymentRepository.save(PaymentEntity.received(tenantId, branchId, endToEndId, amount,
                currency, debtorIban, creditorIban, clockPort.now()));
        outboxEventRepository.save(OutboxEvent.paymentSubmitted(payment.getId(), eventPayload(payment, tenantId),
                UUID.randomUUID(), clockPort.now()));
        return payment;
    }

    private String eventPayload(PaymentEntity payment, UUID tenantId) {
        try {
            return objectMapper.writeValueAsString(new PaymentLifecycleEvent(
                    UUID.randomUUID(), payment.getId(), tenantId, OutboxEvent.PAYMENT_SUBMITTED));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not serialize payment lifecycle event", exception);
        }
    }
}
