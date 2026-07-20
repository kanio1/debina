package com.sepanexus.modules;

import java.time.Instant;
import java.util.UUID;

/** Narrow settlement-facing projection port; settlement never writes {@code payment.*} directly. */
public interface PaymentFinalityPort {

    ProjectionResult project(UUID tenantId, UUID paymentId, UUID finalityRecordId, Instant finalityAt);

    enum ProjectionResult {
        PROJECTED,
        ALREADY_PROJECTED
    }
}
