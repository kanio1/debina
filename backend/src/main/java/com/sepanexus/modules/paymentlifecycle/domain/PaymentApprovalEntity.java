package com.sepanexus.modules.paymentlifecycle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment-lifecycle's own approval gate record.  Database constraints enforce the critical
 * status/timestamp and maker/checker invariants; this entity does not turn approval into a
 * business-lifecycle status.
 */
@Entity
@Table(name = "payment_approvals", schema = "payment")
public class PaymentApprovalEntity {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "batch_id", updatable = false)
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(name = "maker_user_id", nullable = false, updatable = false)
    private String makerUserId;

    @Column(name = "checker_user_id")
    private String checkerUserId;

    @Column(name = "decision_comment")
    private String decisionComment;

    @Column(name = "matrix_rule_id", updatable = false)
    private UUID matrixRuleId;

    @Column(name = "submitted_for_approval_at", updatable = false)
    private Instant submittedForApprovalAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    protected PaymentApprovalEntity() {
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getBatchId() { return batchId; }
    public ApprovalStatus getStatus() { return status; }
    public String getMakerUserId() { return makerUserId; }
    public String getCheckerUserId() { return checkerUserId; }
    public String getDecisionComment() { return decisionComment; }
    public UUID getMatrixRuleId() { return matrixRuleId; }
    public Instant getSubmittedForApprovalAt() { return submittedForApprovalAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
