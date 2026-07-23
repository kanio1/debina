package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

/**
 * EPIC-35 Story 35.1: pure unit test, no Spring context, no Testcontainers — the resolver is a
 * deterministic {@code (settlement_basis, liquidity_mode) -> strategy_kind} function with zero
 * infrastructure dependency.
 */
@org.junit.jupiter.api.Tag("fast")
class SettlementStrategyResolverTest {

    private final SettlementStrategyResolver resolver = new SettlementStrategyResolver();

    @ParameterizedTest
    @MethodSource("legalCombinations")
    void resolvesLegalCombination(SettlementBasis basis, LiquidityMode mode, SettlementStrategyKind expected) {
        assertThat(resolver.resolve(basis, mode)).isEqualTo(expected);
    }

    static Stream<Arguments> legalCombinations() {
        return Stream.of(
                Arguments.of(SettlementBasis.GROSS_INSTANT, LiquidityMode.DCA_POOL, SettlementStrategyKind.GROSS_INSTANT),
                Arguments.of(SettlementBasis.NET_DEFERRED, LiquidityMode.ISOLATED_SUBACCOUNT, SettlementStrategyKind.NET_DEFERRED),
                Arguments.of(SettlementBasis.ACH_FILE_BATCH, LiquidityMode.ISOLATED_SUBACCOUNT, SettlementStrategyKind.FILE_BATCH),
                Arguments.of(SettlementBasis.INTERNAL_BOOK, LiquidityMode.NONE_INTERNAL, SettlementStrategyKind.INTERNAL_BOOK));
    }

    @ParameterizedTest
    @MethodSource("illegalCombinations")
    void rejectsIllegalCombination(SettlementBasis basis, LiquidityMode mode) {
        assertThatThrownBy(() -> resolver.resolve(basis, mode))
                .isInstanceOf(UnsupportedSettlementStrategyCombinationException.class)
                .hasMessageContaining(String.valueOf(basis))
                .hasMessageContaining(String.valueOf(mode));
    }

    static Stream<Arguments> illegalCombinations() {
        return Stream.of(
                Arguments.of(SettlementBasis.GROSS_INSTANT, LiquidityMode.NONE_INTERNAL),
                Arguments.of(SettlementBasis.GROSS_INSTANT, LiquidityMode.ISOLATED_SUBACCOUNT),
                Arguments.of(SettlementBasis.NET_DEFERRED, LiquidityMode.DCA_POOL),
                Arguments.of(SettlementBasis.INTERNAL_BOOK, LiquidityMode.DCA_POOL),
                Arguments.of(SettlementBasis.INTERNAL_BOOK, LiquidityMode.ISOLATED_SUBACCOUNT),
                Arguments.of(SettlementBasis.ACH_FILE_BATCH, LiquidityMode.DCA_POOL));
    }

    @ParameterizedTest
    @MethodSource("deliberatelyUnsupportedP1Combinations")
    void rejectsDeliberatelyUnsupportedP1Combination(SettlementBasis basis, LiquidityMode mode) {
        assertThatThrownBy(() -> resolver.resolve(basis, mode))
                .isInstanceOf(UnsupportedSettlementStrategyCombinationException.class);
    }

    static Stream<Arguments> deliberatelyUnsupportedP1Combinations() {
        return Stream.of(
                Arguments.of(SettlementBasis.BULK_CGS_LIKE, LiquidityMode.TECHNICAL_ACCOUNT_LIKE),
                Arguments.of(SettlementBasis.SIMULATED_PREFUNDED_INSTANT, LiquidityMode.PREFUNDED_RESERVE));
    }

    @Test
    void rejectsNullBasis() {
        assertThatThrownBy(() -> resolver.resolve(null, LiquidityMode.DCA_POOL))
                .isInstanceOf(UnsupportedSettlementStrategyCombinationException.class);
    }

    @Test
    void rejectsNullLiquidityMode() {
        assertThatThrownBy(() -> resolver.resolve(SettlementBasis.GROSS_INSTANT, null))
                .isInstanceOf(UnsupportedSettlementStrategyCombinationException.class);
    }

    @Test
    void rejectsBothNull() {
        assertThatThrownBy(() -> resolver.resolve(null, null))
                .isInstanceOf(UnsupportedSettlementStrategyCombinationException.class);
    }

    @Test
    void resolutionIsDeterministicAcrossRepeatedCalls() {
        SettlementStrategyKind first = resolver.resolve(SettlementBasis.GROSS_INSTANT, LiquidityMode.DCA_POOL);
        SettlementStrategyKind second = resolver.resolve(SettlementBasis.GROSS_INSTANT, LiquidityMode.DCA_POOL);
        assertThat(first).isEqualTo(second).isEqualTo(SettlementStrategyKind.GROSS_INSTANT);
    }

    @Test
    void exceptionMessageNamesTheExactPairNeverAGenericFallbackMessage() {
        assertThatThrownBy(() -> resolver.resolve(SettlementBasis.NET_DEFERRED, LiquidityMode.DCA_POOL))
                .isInstanceOf(UnsupportedSettlementStrategyCombinationException.class)
                .hasMessageContaining("NET_DEFERRED")
                .hasMessageContaining("DCA_POOL");
    }
}
