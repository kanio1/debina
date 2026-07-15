package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.service.PaymentService.PaymentSummary;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentSummaryResponse(
        UUID id, String endToEndId, BigDecimal amount, String currency, String status) {

    static PaymentSummaryResponse from(PaymentSummary summary) {
        return new PaymentSummaryResponse(
                summary.payment().getId(),
                summary.endToEndId(),
                summary.payment().getAmount(),
                summary.payment().getCurrency(),
                summary.payment().getStatus().name());
    }
}
