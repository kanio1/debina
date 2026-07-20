package com.sepanexus.routing;

import java.util.Objects;
import java.util.UUID;

/** One immutable candidate result supplied to a routing decision evidence write. */
public record RouteCandidateEvidence(UUID profileId, short sequence, boolean passed, String rejectReason) {

    public RouteCandidateEvidence {
        Objects.requireNonNull(profileId, "profileId");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (passed && rejectReason != null) {
            throw new IllegalArgumentException("a passed candidate cannot have a reject reason");
        }
    }
}
