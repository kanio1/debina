package com.sepanexus.signature;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code participantId} is {@code null} for a platform key
 * (sepa-nexus-signature-module-blueprint.md §8).
 */
public record SignatureKeyRegistration(UUID participantId, KeyPurpose purpose, String algo, String publicMaterial,
        String privateMaterialRef, Instant validFrom, Instant validTo) {
}
