package com.sepanexus.architecturefixtures.settlementselection.forbidden;

/**
 * EPIC-35 Story 35.2 fixture: exactly the `[REJECT]` pattern from
 * sepa-nexus-message-flow-and-data-blueprint.md §4.11 — a per-CSM engine class
 * (`Tips/Rt1/Step2/Stet/Kir/ElixirSettlementEngine`). Must be detected by {@code
 * NoProfileNameSwitchTest}'s class-naming rule.
 */
public class TipsSettlementEngine {

    public String settle() {
        return "GROSS_INSTANT";
    }
}
