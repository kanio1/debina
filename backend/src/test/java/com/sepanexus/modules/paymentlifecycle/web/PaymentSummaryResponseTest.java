package com.sepanexus.modules.paymentlifecycle.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService.PaymentDetail;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService.PaymentSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("fast")
class PaymentSummaryResponseTest {

    @Test
    void mapsApprovalGatedPaymentBeforeBusinessLifecycleStarts() {
        PaymentEntity payment = PaymentEntity.awaitingApproval(
                UUID.randomUUID(),
                null,
                new BigDecimal("10.00"),
                "EUR",
                "DE89370400440532013000",
                "FR7630006000011234567890189",
                Instant.parse("2026-07-23T10:00:00Z"));
        PaymentSummary summary = new PaymentSummary(payment, "E2E-PENDING");

        assertThatCode(() -> PaymentSummaryResponse.from(summary)).doesNotThrowAnyException();
        assertThat(PaymentSummaryResponse.from(summary).status()).isNull();
    }

    @Test
    void mapsApprovalGatedPaymentDetailBeforeBusinessLifecycleStarts() {
        PaymentEntity payment = awaitingApproval();
        PaymentDetail detail = new PaymentDetail(payment, "E2E-PENDING", List.of());

        assertThatCode(() -> PaymentDetailResponse.from(detail)).doesNotThrowAnyException();
        assertThat(PaymentDetailResponse.from(detail).status()).isNull();
    }

    private static PaymentEntity awaitingApproval() {
        return PaymentEntity.awaitingApproval(
                UUID.randomUUID(),
                null,
                new BigDecimal("10.00"),
                "EUR",
                "DE89370400440532013000",
                "FR7630006000011234567890189",
                Instant.parse("2026-07-23T10:00:00Z"));
    }
}
