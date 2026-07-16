package com.sepanexus.signature;

/**
 * EPIC-31 Story 31.3A: the controlled failure model for {@link SignatureSigningPort#sign} — never
 * a single generic {@code IllegalStateException} for every input/key/persistence problem. The
 * message is always exactly the reason code; it never carries {@code privateMaterialRef}, decoded
 * key bytes, or any other private material.
 */
public class SigningException extends RuntimeException {

    public static final String REASON_INVALID_ARTIFACT_BYTES = "INVALID_ARTIFACT_BYTES";
    public static final String REASON_INVALID_SIGNING_KEY_REF = "INVALID_SIGNING_KEY_REF";
    public static final String REASON_SIGNING_KEY_NOT_FOUND = "SIGNING_KEY_NOT_FOUND";
    public static final String REASON_SIGNING_KEY_NOT_ACTIVE = "SIGNING_KEY_NOT_ACTIVE";
    public static final String REASON_SIGNING_KEY_WRONG_PURPOSE = "SIGNING_KEY_WRONG_PURPOSE";
    public static final String REASON_UNSUPPORTED_SIGNING_ALGORITHM = "UNSUPPORTED_SIGNING_ALGORITHM";
    public static final String REASON_PRIVATE_KEY_MATERIAL_UNAVAILABLE = "PRIVATE_KEY_MATERIAL_UNAVAILABLE";
    public static final String REASON_INVALID_PRIVATE_KEY_MATERIAL = "INVALID_PRIVATE_KEY_MATERIAL";
    public static final String REASON_SIGNATURE_PERSISTENCE_FAILED = "SIGNATURE_PERSISTENCE_FAILED";

    private final String reasonCode;

    public SigningException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public SigningException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
