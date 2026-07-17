package com.sepanexus.settlement;

/**
 * EPIC-35 Story 35.1: the strategy classes named in
 * sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-2..7) — the {@code *Strategy}
 * suffix dropped since this is the resolved kind, not the implementation class itself (which
 * later stories, EPIC-36..41, build one at a time).
 */
public enum SettlementStrategyKind {
    GROSS_INSTANT,
    NET_DEFERRED,
    INTERNAL_BOOK,
    FILE_BATCH,
    BULK_CGS_LIKE,
    PREFUNDED_INSTANT
}
