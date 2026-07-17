package com.sepanexus.settlement;

/**
 * EPIC-35 Story 35.1: the liquidity_mode taxonomy, sepa-nexus-message-flow-and-data-blueprint.md
 * §4.11 ("Liquidity-mode taxonomy" paragraph). {@code CENTRAL_BANK_MONEY_LIKE}/
 * {@code COMMERCIAL_BANK_MONEY_LIKE} are risk labels the source never binds to a strategy — they
 * exist in this taxonomy but resolve to no legal combination in {@link SettlementStrategyResolver}
 * until a source document defines one. {@code [SYNTHETIC]} all values are educational labels only.
 */
public enum LiquidityMode {
    DCA_POOL,
    ISOLATED_SUBACCOUNT,
    CENTRAL_BANK_MONEY_LIKE,
    COMMERCIAL_BANK_MONEY_LIKE,
    NONE_INTERNAL,
    PREFUNDED_RESERVE,
    TECHNICAL_ACCOUNT_LIKE,
    LAB_SYNTHETIC
}
