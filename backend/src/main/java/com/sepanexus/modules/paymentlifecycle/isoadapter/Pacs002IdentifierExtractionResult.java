package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.List;

/**
 * Result of {@link Pacs002IdentifierExtractor#extract}. Whole-document success/failure, mirroring
 * {@link CanonicalMappingResult}'s shape — one malformed/missing element fails the whole
 * extraction rather than returning a partial list, consistent with how {@link Pain001CanonicalMapper}
 * treats a single missing required field.
 */
public record Pacs002IdentifierExtractionResult(
        boolean success, List<Pacs002OriginalIdentifiers> identifiers, MappingError error) {

    public static Pacs002IdentifierExtractionResult success(List<Pacs002OriginalIdentifiers> identifiers) {
        return new Pacs002IdentifierExtractionResult(true, identifiers, null);
    }

    public static Pacs002IdentifierExtractionResult failure(MappingErrorCode code, String fieldPath, String detail) {
        return new Pacs002IdentifierExtractionResult(false, null, new MappingError(code, fieldPath, detail));
    }
}
