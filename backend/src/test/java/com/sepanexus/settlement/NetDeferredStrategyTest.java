package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Structural RED for EPIC-37 Story 37.1: the resolved typed kind needs a real deferred strategy. */
@org.junit.jupiter.api.Tag("fast")
class NetDeferredStrategyTest {

    @Test
    void declaresTheOnlyTypedPairItImplements() {
        NetDeferredStrategy strategy = new NetDeferredStrategy();

        assertThat(strategy.basis()).isEqualTo(SettlementBasis.NET_DEFERRED);
        assertThat(strategy.liquidityMode()).isEqualTo(LiquidityMode.ISOLATED_SUBACCOUNT);
        assertThat(strategy.finalityRule()).isEqualTo(FinalityRulePolicy.ON_CYCLE_SETTLED);
    }
}
