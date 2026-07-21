package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.evidenceaudit.ActorType;
import com.sepanexus.evidenceaudit.CommandAuditEntry;
import com.sepanexus.evidenceaudit.CommandAuditOutcome;
import com.sepanexus.evidenceaudit.CommandAuditPort;
import com.sepanexus.modules.paymentlifecycle.domain.ApprovalStatus;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentApprovalEntity;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyClaim;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyStore;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentApprovalRepository;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import com.sepanexus.shared.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Approve/reject command owner; audit and state mutation deliberately share this transaction. */
@Service
public class ApprovalDecisionService {
    private final PaymentApprovalRepository approvals;
    private final PaymentRepository payments;
    private final PaymentCreationWriter creationWriter;
    private final IdempotencyStore idempotency;
    private final CommandAuditPort audit;
    private final TenantGucConfigurer tenantGuc;
    private final ClockPort clock;

    public ApprovalDecisionService(PaymentApprovalRepository approvals, PaymentRepository payments,
            PaymentCreationWriter creationWriter, IdempotencyStore idempotency, CommandAuditPort audit,
            TenantGucConfigurer tenantGuc, ClockPort clock) {
        this.approvals = approvals; this.payments = payments; this.creationWriter = creationWriter;
        this.idempotency = idempotency; this.audit = audit; this.tenantGuc = tenantGuc; this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('payment_approver')")
    public ApprovalDecisionResult decide(ApprovalDecisionCommand command) {
        tenantGuc.apply(command.tenantId(), command.branchId());
        UUID source = UUID.nameUUIDFromBytes((command.tenantId() + ":approval:" + command.decision()).getBytes(StandardCharsets.UTF_8));
        IdempotencyClaim claim = idempotency.claim(source, command.idempotencyKey(), requestHash(command));
        if (claim.outcome() == IdempotencyClaim.Outcome.CONFLICT) throw new IdempotencyConflictException(command.idempotencyKey());
        if (claim.outcome() == IdempotencyClaim.Outcome.REPLAY) return result(claim.existingPaymentId());

        PaymentEntity payment = payments.findById(command.paymentId()).orElseThrow(() -> new PaymentNotFoundException(command.paymentId()));
        PaymentApprovalEntity approval = approvals.findByPaymentId(payment.getId()).orElseThrow(() -> new ApprovalDecisionConflictException("Payment has no approval"));
        Instant now = clock.now().truncatedTo(ChronoUnit.MICROS);
        if (approval.getStatus() != ApprovalStatus.PENDING_APPROVAL) throw new ApprovalDecisionConflictException("Approval is no longer pending");
        if (!approval.getExpiresAt().isAfter(now)) throw new ApprovalDecisionConflictException("Approval has expired");
        ApprovalStatus decision = command.decision() == ApprovalDecisionCommand.Decision.APPROVE ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        int changed = approvals.decidePending(payment.getId(), decision.name(), command.checkerUserId(),
                command.decisionComment(), now, now);
        if (changed != 1) throw new ApprovalDecisionConflictException("Approval is no longer pending");
        audit.append(new CommandAuditEntry(command.tenantId(), command.branchId(), ActorType.HUMAN, command.checkerUserId(),
                "payment_approver", command.sessionId(), command.correlationId(), "PAYMENT_APPROVAL_" + decision,
                "PAYMENT_APPROVAL", approval.getId(), payment.getId(), null, command.decisionComment(),
                snapshot(approval.getId(), ApprovalStatus.PENDING_APPROVAL, approval.getMakerUserId(), null, approval.getSubmittedForApprovalAt(), approval.getExpiresAt(), null),
                snapshot(approval.getId(), decision, approval.getMakerUserId(), command.checkerUserId(), approval.getSubmittedForApprovalAt(), approval.getExpiresAt(), now),
                CommandAuditOutcome.SUCCESS, UUID.randomUUID(), now));
        if (decision == ApprovalStatus.APPROVED) creationWriter.releaseReceived(payment, command.tenantId());
        idempotency.complete(source, command.idempotencyKey(), payment.getId(), 200);
        return result(payment.getId());
    }
    private ApprovalDecisionResult result(UUID paymentId) {
        PaymentApprovalEntity approval = approvals.findByPaymentId(paymentId).orElseThrow();
        return new ApprovalDecisionResult(paymentId, approval.getId(), approval.getStatus(), approval.getDecidedAt(), approval.getDecisionComment());
    }
    private static Map<String,Object> snapshot(UUID id, ApprovalStatus status, String maker, String checker, Instant submitted, Instant expires, Instant decided) {
        return Map.of("approvalId", id.toString(), "approvalStatus", status.name(), "makerIdentity", maker,
                "checkerIdentity", checker == null ? "" : checker, "submittedAt", submitted == null ? "" : submitted.toString(),
                "expiresAt", expires == null ? "" : expires.toString(), "decidedAt", decided == null ? "" : decided.toString());
    }
    private static byte[] requestHash(ApprovalDecisionCommand c) {
        try { return MessageDigest.getInstance("SHA-256").digest((c.paymentId()+"|"+c.decision()+"|"+(c.decisionComment()==null?"":c.decisionComment())).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
