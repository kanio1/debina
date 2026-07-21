package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.service.ApprovalDecisionResult;
import java.time.Instant;
import java.util.UUID;

public record ApprovalDecisionResponse(UUID paymentId, UUID approvalId, String approvalStatus, Instant decidedAt,
        String decisionComment) {
    static ApprovalDecisionResponse from(ApprovalDecisionResult result) {
        return new ApprovalDecisionResponse(result.paymentId(), result.approvalId(), result.approvalStatus().name(),
                result.decidedAt(), result.decisionComment());
    }
}
