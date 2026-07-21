package com.sepanexus.modules.paymentlifecycle.service;

import java.util.UUID;

public record Pain001SubmissionCommand(
        UUID tenantId,
        UUID branchId,
        byte[] xmlBytes,
        byte[] signatureBytes,
        UUID declaredSignerId,
        String algo,
        String makerUserId,
        String idempotencyKey) {
}
