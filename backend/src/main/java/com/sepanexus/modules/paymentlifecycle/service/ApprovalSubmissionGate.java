package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.ApprovalStatus;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentApprovalEntity;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentApprovalRepository;
import com.sepanexus.shared.ClockPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Payment-lifecycle's source-defined submission prefix gate; it never starts the FSM for pending approval. */
@Component
class ApprovalSubmissionGate {

    private static final long APPROVAL_EXPIRY_SECONDS = 24 * 60 * 60;

    private final ApprovalMatrixEvaluator matrixEvaluator;
    private final PaymentApprovalRepository approvalRepository;
    private final PaymentCreationWriter paymentCreationWriter;
    private final ClockPort clockPort;

    ApprovalSubmissionGate(ApprovalMatrixEvaluator matrixEvaluator, PaymentApprovalRepository approvalRepository,
            PaymentCreationWriter paymentCreationWriter, ClockPort clockPort) {
        this.matrixEvaluator = matrixEvaluator;
        this.approvalRepository = approvalRepository;
        this.paymentCreationWriter = paymentCreationWriter;
        this.clockPort = clockPort;
    }

    PaymentSubmissionResult create(UUID tenantId, UUID branchId, BigDecimal amount, String currency,
            String debtorIban, String creditorIban, String makerUserId, Consumer<UUID> recordLineage) {
        Instant now = clockPort.now();
        ApprovalMatrixEvaluator.Evaluation evaluation = matrixEvaluator.evaluate(tenantId, amount, now);
        PaymentEntity payment = evaluation.requiresApproval()
                ? paymentCreationWriter.createAwaitingApproval(tenantId, branchId, amount, currency, debtorIban, creditorIban)
                : paymentCreationWriter.createReceived(tenantId, branchId, amount, currency, debtorIban, creditorIban);
        recordLineage.accept(payment.getId());

        ApprovalStatus status = evaluation.requiresApproval() ? ApprovalStatus.PENDING_APPROVAL : ApprovalStatus.NOT_REQUIRED;
        PaymentApprovalEntity approval = evaluation.requiresApproval()
                ? PaymentApprovalEntity.pending(payment.getId(), makerUserId, evaluation.ruleId(), now,
                        now.plusSeconds(APPROVAL_EXPIRY_SECONDS))
                : PaymentApprovalEntity.notRequired(payment.getId(), makerUserId, evaluation.ruleId());
        approvalRepository.save(approval);
        if (!evaluation.requiresApproval()) {
            paymentCreationWriter.releaseReceived(payment, tenantId);
        }
        return new PaymentSubmissionResult(payment, status);
    }

    PaymentSubmissionResult replay(PaymentEntity payment) {
        PaymentApprovalEntity approval = approvalRepository.findByPaymentId(payment.getId())
                .orElseThrow(() -> new IllegalStateException("Idempotency replay has no approval record: " + payment.getId()));
        return new PaymentSubmissionResult(payment, approval.getStatus());
    }
}
