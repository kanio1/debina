package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.ApprovalStatus;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;

/** The submission result keeps the pre-FSM approval axis explicit at the command boundary. */
public record PaymentSubmissionResult(PaymentEntity payment, ApprovalStatus approvalStatus, int submissionResponseCode) {

    public static final int CREATED_RESPONSE_CODE = 201;
    public static final int ACCEPTED_RESPONSE_CODE = 202;

    public static PaymentSubmissionResult fromSubmission(PaymentEntity payment, ApprovalStatus approvalStatus) {
        int responseCode = approvalStatus == ApprovalStatus.PENDING_APPROVAL ? ACCEPTED_RESPONSE_CODE : CREATED_RESPONSE_CODE;
        return new PaymentSubmissionResult(payment, approvalStatus, responseCode);
    }

    /** Replays the immutable HTTP submission outcome recorded in {@code ingress.idempotency_keys}. */
    public static PaymentSubmissionResult frozenReplay(PaymentEntity payment, int storedResponseCode) {
        ApprovalStatus frozenApprovalStatus = storedResponseCode == ACCEPTED_RESPONSE_CODE
                ? ApprovalStatus.PENDING_APPROVAL
                : ApprovalStatus.NOT_REQUIRED;
        return new PaymentSubmissionResult(payment, frozenApprovalStatus, storedResponseCode);
    }
}
