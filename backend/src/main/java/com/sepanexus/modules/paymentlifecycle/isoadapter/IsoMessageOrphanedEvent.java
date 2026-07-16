package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.UUID;

/**
 * EPIC-27 Story 27.4: the {@code iso.message.orphaned} payload — published only for {@link
 * CorrelationOutcome#ORPHANED} (blueprint §2.4: "0 → status=ORPHANED → [Event]
 * iso.message.orphaned → DLQ/operator queue"). Carries no payment ID (there is none — that is the
 * definition of orphaned), no amount/currency/participant, and no raw XML — a terminal
 * operator/DLQ signal only, never a business decision (root {@code AGENTS.md}: {@code
 * iso-adapter} correlates, it never guesses).
 */
public record IsoMessageOrphanedEvent(UUID eventId, UUID isoMessageId, UUID tenantId) {
}
