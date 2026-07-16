package com.sepanexus.signature.internal;

import com.sepanexus.signature.KeyPurpose;
import com.sepanexus.signature.KeyStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal-only view of a {@code signature.signature_keys} row for the signing path — unlike the
 * public {@link com.sepanexus.signature.SignatureKeyView}, this one carries {@code
 * privateMaterialRef} because {@link Ed25519SignatureSigner} needs it to resolve a private key.
 * Never returned from a public port, never logged.
 */
record SigningKeyRecord(UUID id, KeyPurpose purpose, String algo, String privateMaterialRef, Instant validFrom,
        Instant validTo, KeyStatus status) {
}
