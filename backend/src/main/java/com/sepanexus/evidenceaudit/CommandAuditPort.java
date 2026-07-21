package com.sepanexus.evidenceaudit;

import java.util.UUID;

/**
 * Evidence-audit's synchronous successful-command append port. Implementations join the caller's
 * transaction; callers must allow any append failure to roll the whole command back.
 */
public interface CommandAuditPort {

    UUID append(CommandAuditEntry entry);
}
