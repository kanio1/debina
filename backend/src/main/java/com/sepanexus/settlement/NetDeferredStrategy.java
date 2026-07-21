package com.sepanexus.settlement;

/**
 * Source-backed deferred strategy identity. Cycle assignment, netting and finality execution are
 * intentionally kept in settlement-owned services so selecting this typed strategy never depends
 * on a profile/CSM name or opens a money-movement path.
 */
public final class NetDeferredStrategy {

    public SettlementBasis basis() {
        return SettlementBasis.NET_DEFERRED;
    }

    public LiquidityMode liquidityMode() {
        return LiquidityMode.ISOLATED_SUBACCOUNT;
    }

    public String finalityRule() {
        return FinalityRulePolicy.ON_CYCLE_SETTLED;
    }
}
