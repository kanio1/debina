package com.sepanexus.graphql;

import com.sepanexus.modules.ApprovalQueueQuery;
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

    ApprovalGraphQlController(ApprovalQueueQuery approvalQueue) { this.approvalQueue = approvalQueue; }

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
}
