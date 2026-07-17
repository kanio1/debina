package com.sepanexus.architecturefixtures.settlementselection.allowed;

import com.sepanexus.settlement.LiquidityMode;
import com.sepanexus.settlement.SettlementBasis;
import com.sepanexus.settlement.SettlementStrategyKind;

/**
 * EPIC-35 Story 35.2 fixture: the allowed shape — selection strictly by the typed {@code
 * (SettlementBasis, LiquidityMode)} pair, mirroring {@link
 * com.sepanexus.settlement.SettlementStrategyResolver}. Must pass every {@code
 * NoProfileNameSwitchTest} check.
 */
public class TypedPairSelector {

    public SettlementStrategyKind resolve(SettlementBasis basis, LiquidityMode mode) {
        if (basis == SettlementBasis.GROSS_INSTANT && mode == LiquidityMode.DCA_POOL) {
            return SettlementStrategyKind.GROSS_INSTANT;
        }
        throw new IllegalArgumentException("unsupported: " + basis + "/" + mode);
    }
}
