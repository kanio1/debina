package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.domain.ApprovalStatus;
import java.util.UUID;

/** Returned only for an approval-gated submission; ordinary submissions preserve the 201 contract. */
public record PaymentSubmissionResponse(UUID paymentId, ApprovalStatus approvalStatus) {
}
