package com.sepanexus.modules.paymentlifecycle.ingress;

import java.util.UUID;

public record IdempotencyClaim(Outcome outcome, UUID existingPaymentId, Integer existingResponseCode) {

    public enum Outcome { CLAIMED, REPLAY, CONFLICT }

    public static IdempotencyClaim claimed() {
        return new IdempotencyClaim(Outcome.CLAIMED, null, null);
    }

    public static IdempotencyClaim replay(UUID paymentId, int responseCode) {
        return new IdempotencyClaim(Outcome.REPLAY, paymentId, responseCode);
    }

    public static IdempotencyClaim conflict() {
        return new IdempotencyClaim(Outcome.CONFLICT, null, null);
    }
}
