package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoIdentifierLookup.IsoIdentifierView;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService.PaymentDetail;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PaymentDetailResponse(
        UUID id,
        String endToEndId,
        BigDecimal amount,
        String currency,
        String status,
        String debtorIban,
        String creditorIban,
        List<IsoIdentifierResponse> isoIdentifiers) {

    static PaymentDetailResponse from(PaymentDetail detail) {
        return new PaymentDetailResponse(
                detail.payment().getId(),
                detail.payment().getEndToEndId(),
                detail.payment().getAmount(),
                detail.payment().getCurrency(),
                detail.payment().getStatus().name(),
                detail.payment().getDebtorIban(),
                detail.payment().getCreditorIban(),
                detail.isoIdentifiers().stream().map(IsoIdentifierResponse::from).toList());
    }

    public record IsoIdentifierResponse(String sourceMessageType, String endToEndId, UUID isoMessageId) {
        static IsoIdentifierResponse from(IsoIdentifierView view) {
            return new IsoIdentifierResponse(view.sourceMessageType(), view.endToEndId(), view.isoMessageId());
        }
    }
}
