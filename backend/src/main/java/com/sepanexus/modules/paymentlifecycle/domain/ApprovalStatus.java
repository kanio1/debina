package com.sepanexus.modules.paymentlifecycle.domain;

/**
 * The maker–checker prefix axis.  It is deliberately not a {@link PaymentStatus}: pending
 * approval exists before the payment business FSM starts.
 */
public enum ApprovalStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    NOT_REQUIRED
}
