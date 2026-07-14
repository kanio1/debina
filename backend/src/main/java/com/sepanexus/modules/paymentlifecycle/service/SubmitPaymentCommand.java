package com.sepanexus.modules.paymentlifecycle.service;

import java.math.BigDecimal;
import java.util.UUID;

public record SubmitPaymentCommand(
        UUID tenantId,
        String endToEndId,
        BigDecimal amount,
        String currency,
        String debtorIban,
        String creditorIban,
        String idempotencyKey) {
}
