package com.sepanexus.modules.paymentlifecycle.isoadapter;

/**
 * EPIC-27 Story 27.2B: the three, and only three, correlation outcomes (see
 * {@code sepa-nexus-payments-data-integrity} skill's correlation-integrity reference) —
 * {@code IGNORED_DUPLICATE}/{@code IGNORED_LATE} (blueprint §4.3c's other two
 * {@code iso.iso_message_correlation.status} values) belong to Story 27.3, never produced here.
 */
public enum CorrelationOutcome {
    MATCHED,
    AMBIGUOUS,
    ORPHANED
}
