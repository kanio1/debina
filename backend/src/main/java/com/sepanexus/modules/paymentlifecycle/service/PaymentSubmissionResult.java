package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.ApprovalStatus;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;

/** The submission result keeps the pre-FSM approval axis explicit at the command boundary. */
public record PaymentSubmissionResult(PaymentEntity payment, ApprovalStatus approvalStatus) {
}
