package com.sepanexus.signature;

/**
 * Debina E1 candidate detached Ed25519 cryptographic/evidence profile. Covers the exact archived
 * HTTP request body bytes before parsing or normalization; verification runs before parse (BR-SCT-003).
 * HTTP header names and signer-identifier transport are implementation evidence only — not a frozen
 * public contract (E1-DEVELOPMENT-ADMISSION).
 * <p>
 * Educational limitation: inactive, expired or revoked/unavailable keys currently collapse to
 * {@link ProfileOutcome#UNKNOWN_SIGNER}; {@link ProfileOutcome#EXPIRED_KEY} and
 * {@link ProfileOutcome#REVOKED_KEY} are reserved and not independently emitted yet. Production
 * differentiation remains {@code BLOCKED_FOR_PRODUCTION}.
 */
public final class DetachedEd25519ProfileV1 {

    public static final String PROFILE_ID = "DEBINA-E1-DETACHED-ED25519-V1";

    public static final int ED25519_SIGNATURE_LENGTH_BYTES = 64;

    public enum ProfileOutcome {
        MISSING_SIGNATURE,
        MALFORMED_SIGNATURE,
        INVALID_SIGNATURE,
        UNKNOWN_SIGNER,
        UNTRUSTED_KEY,
        EXPIRED_KEY,
        REVOKED_KEY,
        VERIFIED
    }

    private DetachedEd25519ProfileV1() {
    }

    public static ProfileOutcome toProfileOutcome(Verdict verdict) {
        if (verdict.result() == Verdict.Result.VERIFIED) {
            return ProfileOutcome.VERIFIED;
        }
        if (verdict.result() == Verdict.Result.NOT_APPLICABLE) {
            return ProfileOutcome.MISSING_SIGNATURE;
        }
        String reason = verdict.reasonCode();
        if (Verdict.REASON_MISSING_REQUIRED_SIGNATURE.equals(reason)) {
            return ProfileOutcome.MISSING_SIGNATURE;
        }
        if (Verdict.REASON_MALFORMED_SIGNATURE.equals(reason)) {
            return ProfileOutcome.MALFORMED_SIGNATURE;
        }
        if (Verdict.REASON_UNSUPPORTED_ALGORITHM.equals(reason)) {
            return ProfileOutcome.UNTRUSTED_KEY;
        }
        if (Verdict.REASON_KEY_NOT_FOUND_OR_INACTIVE.equals(reason)) {
            return ProfileOutcome.UNKNOWN_SIGNER;
        }
        return ProfileOutcome.INVALID_SIGNATURE;
    }
}
