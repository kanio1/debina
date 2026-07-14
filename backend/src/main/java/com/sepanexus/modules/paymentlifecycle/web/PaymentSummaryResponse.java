package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentSummaryResponse(
        UUID id, String endToEndId, BigDecimal amount, String currency, String status) {

    static PaymentSummaryResponse from(PaymentEntity payment) {
        return new PaymentSummaryResponse(
                payment.getId(),
                payment.getEndToEndId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name());
    }
}
