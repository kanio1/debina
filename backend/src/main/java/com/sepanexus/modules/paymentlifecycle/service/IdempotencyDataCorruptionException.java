package com.sepanexus.modules.paymentlifecycle.service;

/**
 * A completed {@code ingress.idempotency_keys} row carries an unsupported {@code response_code} —
 * a data-integrity violation, not a client payload conflict. Replay must fail deterministically
 * rather than derive HTTP status from mutable payment state.
 */
public class IdempotencyDataCorruptionException extends RuntimeException {

    public IdempotencyDataCorruptionException(int storedResponseCode) {
        super("Idempotency record has unsupported stored response code: " + storedResponseCode);
    }
}
