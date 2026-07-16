package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.UUID;

/**
 * EPIC-27 Story 27.2B: the result of {@link Pacs002CorrelationPolicy#correlate}. {@code
 * matchedPaymentId}/{@code matchedBy} are set only for {@link CorrelationOutcome#MATCHED} — never
 * populated as a "best guess" for {@code AMBIGUOUS}/{@code ORPHANED} (see correlation-integrity
 * skill reference: no best-guess fallback is ever folded into MATCHED).
 */
public record CorrelationDecision(CorrelationOutcome outcome, UUID matchedPaymentId, CorrelationMatchStrategy matchedBy) {

    public static CorrelationDecision matched(UUID paymentId, CorrelationMatchStrategy strategy) {
        return new CorrelationDecision(CorrelationOutcome.MATCHED, paymentId, strategy);
    }

    public static CorrelationDecision ambiguous() {
        return new CorrelationDecision(CorrelationOutcome.AMBIGUOUS, null, null);
    }

    public static CorrelationDecision orphaned() {
        return new CorrelationDecision(CorrelationOutcome.ORPHANED, null, null);
    }
}
