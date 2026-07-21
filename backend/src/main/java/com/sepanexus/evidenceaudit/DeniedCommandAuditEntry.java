package com.sepanexus.evidenceaudit;

import java.time.Instant;
import java.util.UUID;

/** Trusted, safe-category evidence for a refused command attempt; never a successful command. */
public record DeniedCommandAuditEntry(UUID tenantId, UUID branchId, String actorId, String authorizedRole,
        String sessionId, UUID correlationId, String commandType, String targetType, UUID targetId,
        UUID paymentId, String denialReason, Instant occurredAt) { }
