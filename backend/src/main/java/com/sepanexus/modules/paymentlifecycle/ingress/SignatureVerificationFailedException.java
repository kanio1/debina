package com.sepanexus.modules.paymentlifecycle.ingress;

import com.sepanexus.signature.DetachedEd25519ProfileV1;

/**
 * Raised by {@link com.sepanexus.modules.paymentlifecycle.service.Pain001IngestionService} when
 * {@link com.sepanexus.signature.Verdict.Result#FAILED} — the raw bytes and the verdict are
 * already durably archived (Story 19.2 ordering) before this is thrown; no payment is created.
 */
public class SignatureVerificationFailedException extends RuntimeException {

    private final String reasonCode;
    private final DetachedEd25519ProfileV1.ProfileOutcome profileOutcome;

    public SignatureVerificationFailedException(String reasonCode,
            DetachedEd25519ProfileV1.ProfileOutcome profileOutcome) {
        super("Signature verification failed: " + reasonCode);
        this.reasonCode = reasonCode;
        this.profileOutcome = profileOutcome;
    }

    public String reasonCode() {
        return reasonCode;
    }

    public DetachedEd25519ProfileV1.ProfileOutcome profileOutcome() {
        return profileOutcome;
    }
}
