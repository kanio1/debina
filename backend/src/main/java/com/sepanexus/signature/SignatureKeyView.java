package com.sepanexus.signature;

import java.time.Instant;
import java.util.UUID;

public record SignatureKeyView(UUID id, UUID participantId, KeyPurpose purpose, String algo, String publicMaterial,
        Instant validFrom, Instant validTo, KeyStatus status) {
}
