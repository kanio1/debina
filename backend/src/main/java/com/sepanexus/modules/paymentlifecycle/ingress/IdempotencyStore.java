package com.sepanexus.modules.paymentlifecycle.ingress;

import java.util.UUID;

/**
 * PG18 two-step idempotency (sepa-nexus-message-flow-and-data-blueprint.md §4.2): the PG19
 * single-statement {@code ON CONFLICT DO SELECT} form is a lab-only seam, not the MVP path.
 */
public interface IdempotencyStore {

    IdempotencyClaim claim(UUID sourceId, String idempotencyKey, byte[] requestHash);

    void complete(UUID sourceId, String idempotencyKey, UUID paymentId, int responseCode);
}
