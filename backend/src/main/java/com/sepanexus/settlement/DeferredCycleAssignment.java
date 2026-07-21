package com.sepanexus.settlement;

import java.util.Objects;
import java.util.UUID;

/** Explicit typed input; it is not an automatic scheme-profile resolution path. */
public record DeferredCycleAssignment(
        UUID cycleId,
        UUID settlementAttemptId,
        UUID paymentId,
        UUID profileId,
        UUID debtorParticipantId,
        UUID creditorParticipantId,
        long amountMinor) {

    public DeferredCycleAssignment {
        Objects.requireNonNull(cycleId, "cycleId");
        Objects.requireNonNull(settlementAttemptId, "settlementAttemptId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(debtorParticipantId, "debtorParticipantId");
        Objects.requireNonNull(creditorParticipantId, "creditorParticipantId");
        if (amountMinor <= 0) throw new IllegalArgumentException("amountMinor must be positive");
    }
}
