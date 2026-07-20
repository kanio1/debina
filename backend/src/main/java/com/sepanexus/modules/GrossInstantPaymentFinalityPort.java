package com.sepanexus.modules;

import java.time.Instant;
import java.util.UUID;

/** ADR-N11 payment-owned commands required by the source's gross-instant outcomes. */
public interface GrossInstantPaymentFinalityPort extends PaymentFinalityPort {

    ProjectionResult projectGrossInstant(UUID tenantId, UUID paymentId, UUID commandId, UUID finalityRecordId,
            Instant finalityAt, Instant occurredAt);

    RejectionResult recordGrossInstantInsufficientLiquidity(UUID tenantId, UUID paymentId, UUID commandId,
            Instant occurredAt);

    enum RejectionResult {
        REJECTED,
        ALREADY_REJECTED
    }
}
