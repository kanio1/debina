package com.sepanexus.ledger;

import java.time.Instant;
import java.util.UUID;

/** ADR-N10's only settlement-facing money movement contract. */
public interface LedgerPort {

    ReserveResult reserve(UUID settlementAttemptId, UUID paymentId, UUID debtorAccountId, long amountMinor);

    TerminalResult post(UUID reservationId, UUID creditorAccountId, UUID commandId);

    TerminalResult release(UUID reservationId, UUID commandId);

    sealed interface ReserveResult permits Reserved, InsufficientLiquidity { }

    record Reserved(UUID reservationId, UUID reserveEntryId) implements ReserveResult { }

    record InsufficientLiquidity() implements ReserveResult { }

    record TerminalResult(UUID reservationId, UUID terminalEntryId, ReservationState state, Instant sourceOccurredAt) { }
}
