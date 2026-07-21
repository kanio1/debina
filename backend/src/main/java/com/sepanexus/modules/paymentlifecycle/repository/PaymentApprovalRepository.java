package com.sepanexus.modules.paymentlifecycle.repository;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentApprovalEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Schema-scoped repository; cross-module catalog access remains read-only. */
public interface PaymentApprovalRepository extends JpaRepository<PaymentApprovalEntity, UUID> {

    Optional<PaymentApprovalEntity> findByPaymentId(UUID paymentId);

    /**
     * The frozen first-writer-wins transition.  Do not replace this with an entity save: the
     * pending predicate is the concurrency boundary shared with expiry.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE payment.payment_approvals
               SET status = :status,
                   checker_user_id = :checkerUserId,
                   decision_comment = :decisionComment,
                   decided_at = :decidedAt
             WHERE payment_id = :paymentId
               AND status = 'PENDING_APPROVAL'
               AND expires_at > :now
            """, nativeQuery = true)
    int decidePending(@Param("paymentId") UUID paymentId, @Param("status") String status,
            @Param("checkerUserId") String checkerUserId, @Param("decisionComment") String decisionComment,
            @Param("decidedAt") java.time.Instant decidedAt, @Param("now") java.time.Instant now);
}
