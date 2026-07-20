package com.sepanexus.routing;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * §4.10's explicit ordered fallback selection. It never derives a fallback from profile family,
 * CSM, settlement basis, or any other implicit relationship. The source defines {@code condition}
 * storage but no language, so a would-be selected non-null condition fails closed.
 */
public final class FallbackPolicy {

    private static final Comparator<FallbackRule> RULE_ORDER =
            Comparator.comparingInt(FallbackRule::priority).thenComparing(FallbackRule::id);

    public Optional<FallbackSelection> select(UUID sourceProfileId, List<FallbackRule> configuredRules) {
        Objects.requireNonNull(sourceProfileId, "sourceProfileId");
        Objects.requireNonNull(configuredRules, "configuredRules");

        return configuredRules.stream()
                .filter(rule -> sourceProfileId.equals(rule.profileId()))
                .sorted(RULE_ORDER)
                .findFirst()
                .map(rule -> {
                    if (rule.condition() != null) {
                        throw new UnsupportedFallbackConditionException(rule);
                    }
                    return new FallbackSelection(rule.id(), rule.profileId(), rule.fallbackProfileId(), rule.priority());
                });
    }
}
