package com.sepanexus.modules.paymentlifecycle.ingress;

/**
 * Raised by {@link com.sepanexus.modules.paymentlifecycle.service.Pain001IngestionService} when
 * {@link com.sepanexus.signature.Verdict.Result#FAILED} — the raw bytes and the verdict are
 * already durably archived (Story 19.2 ordering) before this is thrown; no payment is created.
 */
public class SignatureVerificationFailedException extends RuntimeException {

    private final String reasonCode;

    public SignatureVerificationFailedException(String reasonCode) {
        super("Signature verification failed: " + reasonCode);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
