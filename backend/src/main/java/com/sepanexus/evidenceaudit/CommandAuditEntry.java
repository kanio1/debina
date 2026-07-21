package com.sepanexus.evidenceaudit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Public, immutable evidence-audit command record. It deliberately excludes tokens, HTTP headers,
 * raw payment payloads, arbitrary exception traces and mutable persistence entities.
 */
public record CommandAuditEntry(
        UUID tenantId,
        UUID branchId,
        ActorType actorType,
        String actorId,
        String authorizedRole,
        String sessionId,
        UUID correlationId,
        String commandType,
        String targetType,
        UUID targetId,
        UUID paymentId,
        UUID batchId,
        String decisionComment,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        CommandAuditOutcome outcome,
        UUID commandExecutionId,
        Instant occurredAt) {

    public CommandAuditEntry {
        beforeState = Map.copyOf(beforeState);
        afterState = Map.copyOf(afterState);
    }
}
