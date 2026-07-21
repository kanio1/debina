package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyClaim;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyStore;
import com.sepanexus.modules.paymentlifecycle.isoadapter.CanonicalPaymentCommand;
import com.sepanexus.modules.paymentlifecycle.isoadapter.Pain001LineageRecorder;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import com.sepanexus.shared.ClockPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The atomic tail of pain.001 ingestion (EPIC-19 Story 19.4) — idempotency claim, payment row,
 * ISO identifiers/lineage, outbox, idempotency completion, all in one transaction. Deliberately a
 * separate Spring bean from {@link Pain001IngestionService}: the archive/verify/parse/map stages
 * that precede this must survive even when this transaction rolls back (raw evidence and signature
 * evidence are never lost just because mapping fails or an idempotency conflict is detected), and a
 * {@code @Transactional} method only honors that boundary when called through the Spring proxy —
 * i.e. from a different bean, never via self-invocation.
 */
@Service
public class Pain001PersistenceService {

    private static final int SUBMIT_RESPONSE_CODE = 201;

    private final PaymentRepository paymentRepository;
    private final IdempotencyStore idempotencyStore;
    private final Pain001LineageRecorder pain001LineageRecorder;
    private final TenantGucConfigurer tenantGucConfigurer;
    private final ClockPort clockPort;
    private final ApprovalSubmissionGate approvalSubmissionGate;

    public Pain001PersistenceService(PaymentRepository paymentRepository, IdempotencyStore idempotencyStore,
            Pain001LineageRecorder pain001LineageRecorder, TenantGucConfigurer tenantGucConfigurer,
            ClockPort clockPort, ApprovalSubmissionGate approvalSubmissionGate) {
        this.paymentRepository = paymentRepository;
        this.idempotencyStore = idempotencyStore;
        this.pain001LineageRecorder = pain001LineageRecorder;
        this.tenantGucConfigurer = tenantGucConfigurer;
        this.clockPort = clockPort;
        this.approvalSubmissionGate = approvalSubmissionGate;
    }

    @Transactional
    public PaymentSubmissionResult persist(UUID tenantId, UUID branchId, String makerUserId, String idempotencyKey,
            byte[] requestHash, UUID rawMessageId, CanonicalPaymentCommand canonical) {
        tenantGucConfigurer.apply(tenantId, branchId);

        IdempotencyClaim claim = idempotencyStore.claim(tenantId, idempotencyKey, requestHash);
        if (claim.outcome() == IdempotencyClaim.Outcome.CONFLICT) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
        if (claim.outcome() == IdempotencyClaim.Outcome.REPLAY) {
            return approvalSubmissionGate.replay(paymentRepository.findById(claim.existingPaymentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency replay points at a payment that no longer exists: " + claim.existingPaymentId())));
        }

        PaymentSubmissionResult result = approvalSubmissionGate.create(tenantId, branchId, canonical.amount(),
                canonical.currency(), canonical.debtorIban(), canonical.creditorIban(), makerUserId,
                paymentId -> pain001LineageRecorder.record(paymentId, tenantId, rawMessageId, canonical,
                        clockPort.now()));
        idempotencyStore.complete(tenantId, idempotencyKey, result.payment().getId(),
                result.approvalStatus().name().equals("PENDING_APPROVAL") ? 202 : SUBMIT_RESPONSE_CODE);

        return result;
    }
}
