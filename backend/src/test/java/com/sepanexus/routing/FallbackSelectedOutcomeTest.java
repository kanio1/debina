package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FallbackSelectedOutcomeTest {

    private final FallbackPolicy policy = new FallbackPolicy();

    @Test
    void selectsOnlyTheLowestPriorityExplicitUnconditionalRuleForTheRequestedProfile() {
        UUID source = UUID.randomUUID();
        FallbackRule lowerPriority = rule(source, (short) 20, null);
        FallbackRule selected = rule(source, (short) 10, null);
        FallbackRule unrelated = rule(UUID.randomUUID(), (short) 1, null);

        assertThat(policy.select(source, List.of(lowerPriority, unrelated, selected)))
                .contains(new FallbackSelection(selected.id(), source, selected.fallbackProfileId(), (short) 10));
        assertThat(policy.select(source, List.of(lowerPriority, unrelated, selected)).orElseThrow().routeDecisionOutcome())
                .isEqualTo("FALLBACK_SELECTED");
    }

    @Test
    void noConfiguredRuleMeansNoFallbackSelection() {
        assertThat(policy.select(UUID.randomUUID(), List.of())).isEmpty();
    }

    @Test
    void unsupportedSelectedConditionFailsClosedWithoutTryingAnotherRule() {
        UUID source = UUID.randomUUID();
        FallbackRule unsupported = rule(source, (short) 10, "not-authorized-language");
        FallbackRule laterUnconditional = rule(source, (short) 20, null);

        assertThatThrownBy(() -> policy.select(source, List.of(unsupported, laterUnconditional)))
                .isInstanceOf(UnsupportedFallbackConditionException.class)
                .hasMessageContaining(unsupported.id().toString());
    }

    private static FallbackRule rule(UUID source, short priority, String condition) {
        return new FallbackRule(UUID.randomUUID(), source, UUID.randomUUID(), priority, condition);
    }
}
