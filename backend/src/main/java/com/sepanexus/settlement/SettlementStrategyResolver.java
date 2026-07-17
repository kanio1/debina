package com.sepanexus.settlement;

import java.util.Map;

/**
 * EPIC-35 Story 35.1: resolves a {@link SettlementStrategyKind} from exactly
 * {@code (settlement_basis, liquidity_mode)} — never a profile/CSM name switch (`[FREEZE]`,
 * sepa-nexus-message-flow-and-data-blueprint.md §4.11). Pure, stateless, deterministic: no
 * database, no Kafka, no Spring context.
 *
 * <p>The map below is deliberately not the full basis×mode cartesian product — it contains only
 * the pairs {@code sepa-nexus-message-flow-and-data-blueprint.md} §4.11 explicitly binds via its
 * liquidity-mode-taxonomy annotations ("DCA_POOL (gross)", "ISOLATED_SUBACCOUNT (deferred/file)",
 * "NONE_INTERNAL (internal book)"). Any other pair — including the P1 strategies' own liquidity
 * modes, which the source never binds to a basis — is unsupported and fails closed.
 */
public final class SettlementStrategyResolver {

    private static final Map<StrategyKey, SettlementStrategyKind> LEGAL_COMBINATIONS = Map.of(
            new StrategyKey(SettlementBasis.GROSS_INSTANT, LiquidityMode.DCA_POOL), SettlementStrategyKind.GROSS_INSTANT,
            new StrategyKey(SettlementBasis.NET_DEFERRED, LiquidityMode.ISOLATED_SUBACCOUNT), SettlementStrategyKind.NET_DEFERRED,
            new StrategyKey(SettlementBasis.ACH_FILE_BATCH, LiquidityMode.ISOLATED_SUBACCOUNT), SettlementStrategyKind.FILE_BATCH,
            new StrategyKey(SettlementBasis.INTERNAL_BOOK, LiquidityMode.NONE_INTERNAL), SettlementStrategyKind.INTERNAL_BOOK);

    public SettlementStrategyKind resolve(SettlementBasis settlementBasis, LiquidityMode liquidityMode) {
        SettlementStrategyKind resolved = LEGAL_COMBINATIONS.get(new StrategyKey(settlementBasis, liquidityMode));
        if (resolved == null) {
            throw new UnsupportedSettlementStrategyCombinationException(settlementBasis, liquidityMode);
        }
        return resolved;
    }

    private record StrategyKey(SettlementBasis settlementBasis, LiquidityMode liquidityMode) {
    }
}
