package com.sepanexus.ledger;

import java.util.UUID;

/** A distinct command may not consume an already terminal reservation. */
public class ReservationTerminalConflictException extends RuntimeException {
    public ReservationTerminalConflictException(UUID reservationId, UUID commandId) {
        super("Reservation " + reservationId + " is already terminal; command " + commandId + " is rejected");
    }
}
