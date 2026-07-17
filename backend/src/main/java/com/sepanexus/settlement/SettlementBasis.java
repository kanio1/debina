package com.sepanexus.settlement;

/**
 * EPIC-35 Story 35.1: the settlement_basis taxonomy, sepa-nexus-message-flow-and-data-blueprint.md
 * §4.11 strategy taxonomy table (column {@code settlement_basis}).
 */
public enum SettlementBasis {
    GROSS_INSTANT,
    NET_DEFERRED,
    INTERNAL_BOOK,
    ACH_FILE_BATCH,
    BULK_CGS_LIKE,
    SIMULATED_PREFUNDED_INSTANT
}
