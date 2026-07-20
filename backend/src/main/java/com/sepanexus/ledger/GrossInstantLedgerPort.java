package com.sepanexus.ledger;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-N11's one-call gross-instant extension of {@link LedgerPort}. It deliberately exposes a
 * domain command rather than a JDBC connection: reserve and post remain ledger-owned operations
 * but execute in the transaction the settlement coordinator already opened.
 */
public interface GrossInstantLedgerPort extends LedgerPort {

    GrossInstantResult reserveAndPost(GrossInstantCommand command);

    record GrossInstantCommand(UUID tenantId, UUID settlementAttemptId, UUID paymentId, UUID debtorAccountId,
            UUID creditorAccountId, long amountMinor, String currency, UUID commandId, Instant occurredAt) { }

    sealed interface GrossInstantResult permits Posted, InsufficientLiquidity { }

    record Posted(UUID reservationId, UUID postEntryId, Instant occurredAt, boolean replayed)
            implements GrossInstantResult { }

    record InsufficientLiquidity(boolean replayed) implements GrossInstantResult { }
}
