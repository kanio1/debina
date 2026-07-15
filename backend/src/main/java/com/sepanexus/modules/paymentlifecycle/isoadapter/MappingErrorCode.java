package com.sepanexus.modules.paymentlifecycle.isoadapter;

/**
 * Controlled taxonomy for {@link CanonicalMapper} failures — an ISO mapping rejection, never a
 * business status (sepa-nexus-message-flow-and-data-blueprint.md §4.3b rule 6: business status
 * changes applied only by {@code payment-lifecycle}). No payment is ever created for any of these.
 */
public enum MappingErrorCode {
    UNSUPPORTED_MESSAGE_TYPE,
    UNSUPPORTED_MESSAGE_VERSION,
    UNSUPPORTED_TRANSACTION_COUNT,
    MISSING_REQUIRED_ELEMENT,
    INVALID_FIELD_FORMAT,
    MAPPING_FAILED
}
