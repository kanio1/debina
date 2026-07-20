package com.sepanexus.routing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal routing command; it creates no payment, settlement, finality, or transport effect. */
public record FallbackDecisionCommand(
        UUID decisionId,
        UUID paymentId,
        UUID tenantId,
        FallbackSelection selection,
        List<RouteCandidateEvidence> candidates,
        String referenceDataVersion,
        Instant decidedAt) {

    public FallbackDecisionCommand {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(selection, "selection");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(referenceDataVersion, "referenceDataVersion");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (candidates.stream().noneMatch(candidate -> candidate.passed()
                && candidate.profileId().equals(selection.fallbackProfileId()))) {
            throw new IllegalArgumentException("fallback profile must be a passed candidate");
        }
    }
}
