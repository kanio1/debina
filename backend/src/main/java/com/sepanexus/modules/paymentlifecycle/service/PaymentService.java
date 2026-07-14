package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.domain.OutboxEvent;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentLifecycleEvent;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyClaim;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyStore;
import com.sepanexus.modules.paymentlifecycle.ingress.RawMessageArchive;
import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoIdentifierLookup;
import com.sepanexus.modules.paymentlifecycle.isoadapter.JsonDirectLineageRecorder;
import com.sepanexus.modules.paymentlifecycle.repository.OutboxEventRepository;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import com.sepanexus.shared.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {

    private static final int SUBMIT_RESPONSE_CODE = 201;

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TenantGucConfigurer tenantGucConfigurer;
    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;
    private final RawMessageArchive rawMessageArchive;
    private final JsonDirectLineageRecorder jsonDirectLineageRecorder;
    private final ClockPort clockPort;
    private final IsoIdentifierLookup isoIdentifierLookup;

    public PaymentService(PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository,
            TenantGucConfigurer tenantGucConfigurer, ObjectMapper objectMapper, IdempotencyStore idempotencyStore,
            RawMessageArchive rawMessageArchive, JsonDirectLineageRecorder jsonDirectLineageRecorder,
            ClockPort clockPort, IsoIdentifierLookup isoIdentifierLookup) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tenantGucConfigurer = tenantGucConfigurer;
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
        this.rawMessageArchive = rawMessageArchive;
        this.jsonDirectLineageRecorder = jsonDirectLineageRecorder;
        this.clockPort = clockPort;
        this.isoIdentifierLookup = isoIdentifierLookup;
    }

    @Transactional
    @PreAuthorize("hasRole('payment_submitter')")
    public PaymentEntity submitPayment(SubmitPaymentCommand command) {
        UUID tenantId = command.tenantId();
        tenantGucConfigurer.apply(tenantId, command.branchId());

        byte[] requestPayload = canonicalRequestBytes(command);
        UUID rawMessageId = rawMessageArchive.archive("REST_JSON", tenantId,
                JsonDirectLineageRecorder.MESSAGE_TYPE, requestPayload);

        IdempotencyClaim claim = idempotencyStore.claim(tenantId, command.idempotencyKey(), sha256(requestPayload));
        if (claim.outcome() == IdempotencyClaim.Outcome.CONFLICT) {
            throw new IdempotencyConflictException(command.idempotencyKey());
        }
        if (claim.outcome() == IdempotencyClaim.Outcome.REPLAY) {
            return paymentRepository.findById(claim.existingPaymentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency replay points at a payment that no longer exists: " + claim.existingPaymentId()));
        }

        if (paymentRepository.existsByTenantIdAndEndToEndId(tenantId, command.endToEndId())) {
            throw new DuplicatePaymentException(command.endToEndId());
        }

        PaymentEntity payment = paymentRepository.save(PaymentEntity.received(
                tenantId,
                command.branchId(),
                command.endToEndId(),
                command.amount(),
                command.currency(),
                command.debtorIban(),
                command.creditorIban(),
                clockPort.now()));
        outboxEventRepository.save(OutboxEvent.paymentSubmitted(payment.getId(), eventPayload(payment, tenantId),
                UUID.randomUUID(), clockPort.now()));
        jsonDirectLineageRecorder.record(payment.getId(), rawMessageId, command.endToEndId());
        idempotencyStore.complete(tenantId, command.idempotencyKey(), payment.getId(), SUBMIT_RESPONSE_CODE);

        return payment;
    }

    private byte[] canonicalRequestBytes(SubmitPaymentCommand command) {
        String canonical = "%s|%s|%s|%s|%s".formatted(command.endToEndId(), command.amount(), command.currency(),
                command.debtorIban(), command.creditorIban());
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('payment_submitter')")
    public List<PaymentEntity> visiblePayments(String tenantIdClaim) {
        tenantGucConfigurer.apply(tenantIdClaim == null ? null : UUID.fromString(tenantIdClaim));
        return paymentRepository.findAll();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('payment_submitter')")
    public PaymentDetail paymentDetail(String tenantIdClaim, UUID paymentId) {
        tenantGucConfigurer.apply(tenantIdClaim == null ? null : UUID.fromString(tenantIdClaim));
        // RLS on payment.payments already scopes this lookup to the caller's tenant (and branch, if
        // set) — a payment belonging to another tenant is indistinguishable from a non-existent one.
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return new PaymentDetail(payment, isoIdentifierLookup.findByPaymentId(paymentId));
    }

    public record PaymentDetail(PaymentEntity payment, List<IsoIdentifierLookup.IsoIdentifierView> isoIdentifiers) {
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
