package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.math.BigDecimal;

/**
 * The canonical, channel-agnostic payment shape that {@link CanonicalMapper} produces
 * (sepa-nexus-message-flow-and-data-blueprint.md §3.1: {@code CanonicalMapper}, "implemented by
 * iso-adapter"). Deliberately carries no HTTP, JPA, or parsed-XML types — {@code payment-lifecycle}
 * consumes only this shape, never a {@code CdtTrfTxInf} DOM node. {@code instrId} and {@code uetr}
 * are optional in pain.001.001.09 (PaymentIdentification6); every other field is ISO-mandatory.
 */
public record CanonicalPaymentCommand(
        String msgId,
        String pmtInfId,
        String instrId,
        String endToEndId,
        String uetr,
        BigDecimal amount,
        String currency,
        String debtorIban,
        String creditorIban) {
}
