package com.sepanexus.settlement;

import java.util.UUID;

/** Evidence for an already-final payment or source differs, so authority fails closed. */
public class FinalityConflictException extends RuntimeException {
    public FinalityConflictException(UUID paymentId) {
        super("Conflicting finality evidence for payment " + paymentId);
    }
}
