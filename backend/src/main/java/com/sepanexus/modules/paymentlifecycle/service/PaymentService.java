package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.domain.OutboxEvent;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentLifecycleEvent;
import com.sepanexus.modules.paymentlifecycle.repository.OutboxEventRepository;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TenantGucConfigurer tenantGucConfigurer;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository,
            TenantGucConfigurer tenantGucConfigurer, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tenantGucConfigurer = tenantGucConfigurer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @PreAuthorize("hasRole('payment_submitter')")
    public PaymentEntity submitPayment(SubmitPaymentCommand command) {
        UUID tenantId = command.tenantId();
        tenantGucConfigurer.apply(tenantId);
        if (paymentRepository.existsByTenantIdAndEndToEndId(tenantId, command.endToEndId())) {
            throw new DuplicatePaymentException(command.endToEndId());
        }

        PaymentEntity payment = paymentRepository.save(PaymentEntity.received(
                tenantId,
                command.endToEndId(),
                command.amount(),
                command.currency(),
                command.debtorIban(),
                command.creditorIban()));
        outboxEventRepository.save(OutboxEvent.paymentSubmitted(payment.getId(), eventPayload(payment, tenantId), UUID.randomUUID()));
        return payment;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('payment_submitter')")
    public List<PaymentEntity> visiblePayments(String tenantIdClaim) {
        tenantGucConfigurer.apply(tenantIdClaim == null ? null : UUID.fromString(tenantIdClaim));
        return paymentRepository.findAll();
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
