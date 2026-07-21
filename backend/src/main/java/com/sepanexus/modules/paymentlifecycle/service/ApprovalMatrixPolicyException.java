package com.sepanexus.modules.paymentlifecycle.service;

/** A fail-closed matrix result: policy exists but cannot safely release a payment. */
public class ApprovalMatrixPolicyException extends RuntimeException {

    public ApprovalMatrixPolicyException(String message) {
        super(message);
    }
}
