package com.sepanexus.modules.paymentlifecycle.service;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key '%s' was already used with a different request body".formatted(idempotencyKey));
    }
}
