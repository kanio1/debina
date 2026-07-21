package com.sepanexus.graphql;

import com.sepanexus.modules.ApprovalQueueQuery;
import com.sepanexus.modules.PaymentIsoEvidenceQuery;
import com.sepanexus.evidenceaudit.AuditQueryPort;
import com.sepanexus.evidenceaudit.CommandAuditOutcome;
import java.time.Instant;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

/** Query adapter only: it depends solely on payment-lifecycle's public read API. */
@Controller
class ApprovalGraphQlController {
    private final ApprovalQueueQuery approvalQueue;
    private final AuditQueryPort auditQuery;
    private final PaymentIsoEvidenceQuery paymentIsoEvidenceQuery;

    ApprovalGraphQlController(ApprovalQueueQuery approvalQueue, AuditQueryPort auditQuery,
            PaymentIsoEvidenceQuery paymentIsoEvidenceQuery) {
        this.approvalQueue = approvalQueue;
        this.auditQuery = auditQuery;
        this.paymentIsoEvidenceQuery = paymentIsoEvidenceQuery;
    }

    @QueryMapping
    @PreAuthorize("hasRole('payment_approver')")
    ApprovalQueueQuery.QueuePage approvalQueue(@Argument int first, @Argument String after) {
        Jwt jwt = currentJwt();
        return approvalQueue.pending(UUID.fromString(jwt.getClaimAsString("tenant_id")), branchId(jwt), first, after);
    }

    @QueryMapping
    @PreAuthorize("hasRole('payment_approver')")
    ApprovalQueueQuery.ApprovalDetail approval(@Argument UUID paymentId) {
        Jwt jwt = currentJwt();
        return approvalQueue.approval(UUID.fromString(jwt.getClaimAsString("tenant_id")), branchId(jwt), paymentId);
    }

    @QueryMapping
    AuditQueryPort.AuditPage paymentAuditTrail(@Argument UUID paymentId, @Argument int first, @Argument String after) {
        return auditQuery.paymentTrail(paymentId, first, after);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('payment_viewer','payment_submitter','payment_approver','operator','auditor')")
    PaymentIsoEvidenceQuery.PaymentIsoEvidence paymentIsoEvidence(@Argument UUID paymentId) {
        Jwt jwt = currentJwt();
        return paymentIsoEvidenceQuery.evidence(UUID.fromString(jwt.getClaimAsString("tenant_id")), branchId(jwt), paymentId);
    }

    @QueryMapping
    AuditQueryPort.AuditPage auditEntries(@Argument AuditEntryFilterInput filter, @Argument int first, @Argument String after) {
        return auditQuery.search(new AuditQueryPort.AuditSearchFilter(filter.tenantId(), filter.branchId(),
                filter.targetType(), filter.targetId(), filter.paymentId(), filter.batchId(), filter.actorId(),
                filter.commandType(), filter.outcome() == null ? null : CommandAuditOutcome.valueOf(filter.outcome()),
                filter.correlationId(), parseInstant(filter.occurredFrom()), parseInstant(filter.occurredTo())), first, after);
    }

    @SchemaMapping(typeName = "Approval", field = "decisionComment")
    String decisionComment(ApprovalQueueQuery.ApprovalDetail approval) {
        return approval.decisionComment();
    }

    @SchemaMapping(typeName = "Approval", field = "decidedAt")
    java.time.Instant decidedAt(ApprovalQueueQuery.ApprovalDetail approval) {
        return approval.decidedAt();
    }

    private static Jwt currentJwt() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) throw new IllegalStateException("JWT principal required");
        return jwt;
    }

    private static UUID branchId(Jwt jwt) {
        String value = jwt.getClaimAsString("branch_id");
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    record AuditEntryFilterInput(UUID tenantId, UUID branchId, String targetType, UUID targetId, UUID paymentId,
                                 UUID batchId, String actorId, String commandType, String outcome,
                                 UUID correlationId, String occurredFrom, String occurredTo) { }
}
