package com.sepanexus.settlement;

/**
 * EPIC-35 Story 35.1: thrown by {@link SettlementStrategyResolver#resolve} for any
 * {@code (settlement_basis, liquidity_mode)} pair that is not an explicit, source-backed legal
 * combination — fail-closed, never a default/first-match/basis-only/mode-only fallback.
 */
public class UnsupportedSettlementStrategyCombinationException extends RuntimeException {

    public UnsupportedSettlementStrategyCombinationException(SettlementBasis settlementBasis, LiquidityMode liquidityMode) {
        super("Unsupported settlement strategy combination: settlementBasis=" + settlementBasis
                + ", liquidityMode=" + liquidityMode);
    }
}
