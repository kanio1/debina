package com.sepanexus.modules;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public payment-lifecycle query port consumed by the read-only GraphQL adapter. */
public interface ApprovalQueueQuery {
    QueuePage pending(UUID tenantId, UUID branchId, Integer requestedLimit, String afterCursor);
    ApprovalDetail approval(UUID tenantId, UUID branchId, UUID paymentId);

    record QueuePage(List<QueueItem> items, String nextCursor) { }
    record QueueItem(UUID approvalId, UUID paymentId, String approvalStatus, String makerUserId,
                     Instant submittedAt, Instant expiresAt, UUID matrixRuleId, BigDecimal amount,
                     String currency, String debtorIban, String creditorIban, boolean expiredButUnprocessed) { }
    record ApprovalDetail(UUID approvalId, UUID paymentId, String approvalStatus, String makerUserId,
                          Instant submittedAt, Instant expiresAt, UUID matrixRuleId, BigDecimal amount,
                          String currency, String debtorIban, String creditorIban,
                          boolean expiredButUnprocessed, String decisionComment, Instant decidedAt) { }
}
