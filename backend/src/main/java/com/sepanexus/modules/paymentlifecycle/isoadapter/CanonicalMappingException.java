package com.sepanexus.modules.paymentlifecycle.isoadapter;

public class CanonicalMappingException extends RuntimeException {

    private final MappingError error;

    public CanonicalMappingException(MappingError error) {
        super("Canonical mapping failed: %s (%s)".formatted(error.code(), error.fieldPath()));
        this.error = error;
    }

    public MappingError error() {
        return error;
    }
}
