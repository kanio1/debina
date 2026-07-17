/**
 * Settlement module (EPIC-35, sepa-nexus-message-flow-and-data-blueprint.md §3.10/§4.11): resolves
 * which settlement strategy applies to a payment strictly by the pair
 * {@code (settlement_basis, liquidity_mode)} — never by profile name or CSM name
 * (TIPS/RT1/STEP2/STET/KIR/ELIXIR are configuration rows, not classes). This slice
 * ({@link com.sepanexus.settlement.SettlementStrategyResolver}, Story 35.1) only resolves the
 * strategy type; it never executes settlement, never touches money, and money — when a future
 * story adds strategy execution — will only ever move through {@code LedgerPort}, never a direct
 * write to {@code ledger.*}. This module owns no schema yet and performs no cross-schema writes.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sepanexus.settlement;
