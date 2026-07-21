package com.sepanexus.evidenceaudit;

import java.util.UUID;

/** Separate evidence-audit-owned boundary for denied requests, which have no domain transaction. */
public interface DeniedCommandAuditPort {
    UUID appendDenied(DeniedCommandAuditEntry entry);
}
