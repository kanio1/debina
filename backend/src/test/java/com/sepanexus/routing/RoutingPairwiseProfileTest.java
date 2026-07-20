package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pairwise profile test lab. Dimensions are source-profile match (matching/unrelated), condition
 * (null/non-null) and input order (low-priority-first/high-priority-first). The four rows cover
 * every pair of dimension values; they do not invent amount, currency-limit, CSM, or settlement
 * semantics.
 */
class RoutingPairwiseProfileTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairwiseScenarios")
    void pairwiseProfileCasesExerciseExplicitFallbackSelection(Scenario scenario) {
        FallbackPolicy policy = new FallbackPolicy();
        if (scenario.expectUnsupportedCondition()) {
            assertThatThrownBy(() -> policy.select(scenario.requestedProfile(), scenario.rules()))
                    .isInstanceOf(UnsupportedFallbackConditionException.class);
        } else {
            assertThat(policy.select(scenario.requestedProfile(), scenario.rules()).isPresent())
                    .isEqualTo(scenario.expectSelection());
        }
    }

    private static Stream<Scenario> pairwiseScenarios() {
        UUID requested = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID unrelated = UUID.fromString("00000000-0000-0000-0000-000000000302");
        return Stream.of(
                scenario("matching/null/low-first", requested, requested, null, true, false, true),
                scenario("matching/non-null/high-first", requested, requested, "unsupported", false, true, true),
                scenario("unrelated/null/high-first", requested, unrelated, null, false, false, false),
                scenario("unrelated/non-null/low-first", requested, unrelated, "unsupported", true, false, false));
    }

    private static Scenario scenario(String name, UUID requested, UUID ruleSource, String condition,
            boolean lowFirst, boolean expectUnsupportedCondition, boolean expectSelection) {
        FallbackRule low = new FallbackRule(UUID.randomUUID(), ruleSource, UUID.randomUUID(), (short) 10, condition);
        FallbackRule high = new FallbackRule(UUID.randomUUID(), ruleSource, UUID.randomUUID(), (short) 20, null);
        return new Scenario(name, requested, lowFirst ? List.of(low, high) : List.of(high, low),
                expectUnsupportedCondition, expectSelection);
    }

    private record Scenario(String name, UUID requestedProfile, List<FallbackRule> rules,
            boolean expectUnsupportedCondition, boolean expectSelection) {
        @Override public String toString() { return name; }
    }
}
