package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.ApprovalStatus;
import java.time.Instant;
import java.util.UUID;

public record ApprovalDecisionResult(UUID paymentId, UUID approvalId, ApprovalStatus approvalStatus,
        Instant decidedAt, String decisionComment) { }
