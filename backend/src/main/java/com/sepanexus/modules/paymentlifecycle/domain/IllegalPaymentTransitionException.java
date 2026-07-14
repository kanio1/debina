package com.sepanexus.modules.paymentlifecycle.domain;

public class IllegalPaymentTransitionException extends RuntimeException {

    public IllegalPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Illegal payment lifecycle transition: %s -> %s".formatted(from, to));
    }
}
