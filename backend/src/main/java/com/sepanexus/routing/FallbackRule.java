package com.sepanexus.routing;

import java.util.Objects;
import java.util.UUID;

/** Immutable static fallback configuration read by routing from reference-data. */
public record FallbackRule(
        UUID id, UUID profileId, UUID fallbackProfileId, short priority, String condition) {

    public FallbackRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(fallbackProfileId, "fallbackProfileId");
    }
}
