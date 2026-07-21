package com.sepanexus.evidenceaudit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evidence-audit's typed, read-only history boundary. Callers supply only query dimensions; the
 * implementation derives tenant/branch or auditor context from the authenticated principal.
 */
public interface AuditQueryPort {

    AuditPage paymentTrail(UUID paymentId, Integer requestedLimit, String afterCursor);

    AuditPage search(AuditSearchFilter filter, Integer requestedLimit, String afterCursor);

    record AuditPage(List<AuditEntry> items, String nextCursor) { }

    record AuditSearchFilter(UUID tenantId, UUID branchId, String targetType, UUID targetId, UUID paymentId,
                             UUID batchId, String actorId, String commandType, CommandAuditOutcome outcome,
                             UUID correlationId, Instant occurredFrom, Instant occurredTo) { }

    /** Snapshot fields are source-authorized, never a generic audit JSON dump. */
    record AuditEntry(UUID auditEntryId, UUID tenantId, UUID branchId, Instant occurredAt, ActorType actorType,
                      String actorId, String authorizedRole, UUID correlationId, String commandType,
                      String targetType, UUID targetId, UUID paymentId, UUID batchId,
                      CommandAuditOutcome outcome, String decisionComment, AuditSnapshot beforeState,
                      AuditSnapshot afterState) { }

    record AuditSnapshot(String approvalId, String approvalStatus) { }
}
