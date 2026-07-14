package com.sepanexus.modules.paymentlifecycle.service;

public class DuplicatePaymentException extends RuntimeException {

    public DuplicatePaymentException(String endToEndId) {
        super("Payment with endToEndId '%s' already exists for this tenant".formatted(endToEndId));
    }
}
