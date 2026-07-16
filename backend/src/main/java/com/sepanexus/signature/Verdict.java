package com.sepanexus.signature;

import java.util.UUID;

/**
 * The outcome of {@link SignatureVerificationPort#verify}. {@code FAILED} is data, not a decision — rejecting
 * the payment is {@code ingress}'s action, driven by this verdict, never {@code signature}'s own
 * (sepa-nexus-signature-module-blueprint.md §1).
 */
public record Verdict(Result result, UUID keyId, String algo, String reasonCode) {

    public enum Result {
        VERIFIED,
        FAILED,
        NOT_APPLICABLE
    }

    /** Cryptographic mismatch — tampered bytes, tampered signature, or a wrong (but active) key. */
    public static final String REASON_TAMPERED_OR_INVALID = "TAMPERED_OR_INVALID";
    /** Blueprint §6's "KEY_*" wildcard: unknown, expired, future, or revoked key all collapse to
     * the same {@link KeyRegistryPort#lookup} empty result, so they share one reason code. */
    public static final String REASON_KEY_NOT_FOUND_OR_INACTIVE = "KEY_NOT_FOUND_OR_INACTIVE";
    public static final String REASON_MISSING_REQUIRED_SIGNATURE = "MISSING_REQUIRED_SIGNATURE";
    public static final String REASON_UNSUPPORTED_ALGORITHM = "UNSUPPORTED_ALGORITHM";
}
