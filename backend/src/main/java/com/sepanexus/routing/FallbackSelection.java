package com.sepanexus.routing;

import java.util.Objects;
import java.util.UUID;

/** Explicit, unconditional fallback selected from a source-profile rule. */
public record FallbackSelection(UUID ruleId, UUID sourceProfileId, UUID fallbackProfileId, short priority) {

    public FallbackSelection {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(sourceProfileId, "sourceProfileId");
        Objects.requireNonNull(fallbackProfileId, "fallbackProfileId");
    }

    public String routeDecisionOutcome() {
        return "FALLBACK_SELECTED";
    }
}
