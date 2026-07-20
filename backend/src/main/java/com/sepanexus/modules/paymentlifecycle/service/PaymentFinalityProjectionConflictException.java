package com.sepanexus.modules.paymentlifecycle.service;

import java.util.UUID;

/** A payment already projects a different authoritative settlement finality record. */
public class PaymentFinalityProjectionConflictException extends RuntimeException {

    public PaymentFinalityProjectionConflictException(UUID paymentId) {
        super("Payment " + paymentId + " already projects a different finality record");
    }
}
