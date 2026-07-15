package com.sepanexus.modules.paymentlifecycle.isoadapter;

public record CanonicalMappingResult(boolean success, CanonicalPaymentCommand command, MappingError error) {

    public static CanonicalMappingResult success(CanonicalPaymentCommand command) {
        return new CanonicalMappingResult(true, command, null);
    }

    public static CanonicalMappingResult failure(MappingErrorCode code, String fieldPath, String detail) {
        return new CanonicalMappingResult(false, null, new MappingError(code, fieldPath, detail));
    }
}
