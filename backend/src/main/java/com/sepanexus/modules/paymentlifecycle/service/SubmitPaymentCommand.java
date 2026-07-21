package com.sepanexus.modules.paymentlifecycle.service;

import java.math.BigDecimal;
import java.util.UUID;

public record SubmitPaymentCommand(
        UUID tenantId,
        UUID branchId,
        String endToEndId,
        BigDecimal amount,
        String currency,
        String debtorIban,
        String creditorIban,
        String makerUserId,
        String idempotencyKey) {

    public SubmitPaymentCommand(UUID tenantId, UUID branchId, String endToEndId, BigDecimal amount, String currency,
            String debtorIban, String creditorIban, String idempotencyKey) {
        this(tenantId, branchId, endToEndId, amount, currency, debtorIban, creditorIban, "test-maker", idempotencyKey);
    }
}
