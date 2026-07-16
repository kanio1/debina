package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.List;

/**
 * Result of {@link Pacs002IdentifierExtractor#extractCorrelationInputs}. Mirrors
 * {@link Pacs002IdentifierExtractionResult}'s whole-document success/failure shape.
 */
public record Pacs002CorrelationExtractionResult(
        boolean success, List<Pacs002CorrelationInput> inputs, MappingError error) {

    public static Pacs002CorrelationExtractionResult success(List<Pacs002CorrelationInput> inputs) {
        return new Pacs002CorrelationExtractionResult(true, inputs, null);
    }

    public static Pacs002CorrelationExtractionResult failure(MappingErrorCode code, String fieldPath, String detail) {
        return new Pacs002CorrelationExtractionResult(false, null, new MappingError(code, fieldPath, detail));
    }
}
