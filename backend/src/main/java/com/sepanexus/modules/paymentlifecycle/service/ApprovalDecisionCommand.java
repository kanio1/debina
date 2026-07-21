package com.sepanexus.modules.paymentlifecycle.service;

import java.util.UUID;

/** Trusted command context is built by the REST adapter from the validated JWT and request context. */
public record ApprovalDecisionCommand(UUID tenantId, UUID branchId, UUID paymentId, String checkerUserId,
        String sessionId, UUID correlationId, String idempotencyKey, String decisionComment, Decision decision) {
    public enum Decision { APPROVE, REJECT }
}
