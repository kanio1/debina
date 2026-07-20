package com.sepanexus.settlement;

import java.time.Instant;
import java.util.UUID;

/** Settlement-owned ADR-N11 command surface, implemented only through settlement functions. */
public interface GrossInstantSettlementCommandPort {

    FinalityResult recordPosted(UUID tenantId, UUID attemptId, UUID paymentId, UUID commandId, UUID reserveEntryId,
            UUID postEntryId, long amountMinor, String currency, Instant occurredAt, byte[] evidenceHash);

    RejectedAttemptResult recordInsufficientLiquidity(UUID tenantId, UUID attemptId, UUID paymentId, UUID commandId,
            long amountMinor, String currency, Instant occurredAt);

    record FinalityResult(UUID finalityRecordId, Instant finalityAt, boolean replayed) { }
    record RejectedAttemptResult(UUID attemptId, boolean replayed) { }
}
