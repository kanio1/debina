package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.UUID;

/**
 * EPIC-27 Story 27.2C: the {@code iso.message.correlated} payload — published only for {@link
 * CorrelationOutcome#MATCHED} (blueprint §2.4: "exactly 1 → status=MATCHED ... [Event]
 * iso.message.correlated (key=payment_id)"). {@code AMBIGUOUS}/{@code ORPHANED} never publish
 * this event.
 */
public record IsoMessageCorrelatedEvent(
        UUID eventId,
        UUID isoMessageId,
        UUID paymentId,
        UUID tenantId,
        String matchedBy) {
}
