package com.sepanexus.modules.paymentlifecycle.service;

public class ApprovalDecisionConflictException extends RuntimeException {
    public ApprovalDecisionConflictException(String message) { super(message); }
}
