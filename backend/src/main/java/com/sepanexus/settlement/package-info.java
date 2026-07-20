/**
 * Settlement module (EPIC-35, sepa-nexus-message-flow-and-data-blueprint.md §3.10/§4.11): resolves
 * which settlement strategy applies to a payment strictly by the pair
 * {@code (settlement_basis, liquidity_mode)} — never by profile name or CSM name
 * (TIPS/RT1/STEP2/STET/KIR/ELIXIR are configuration rows, not classes). This slice
 * ({@link com.sepanexus.settlement.SettlementStrategyResolver}, Story 35.1) only resolves the
 * strategy type; it never writes ledger tables, and money moves only through {@code LedgerPort}.
 * ADR-N10 adds its own settlement finality authority and reaches payment only through the narrow
 * {@code PaymentFinalityPort}; it never performs a cross-schema SQL write.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"ledger", "modules"})
package com.sepanexus.settlement;
