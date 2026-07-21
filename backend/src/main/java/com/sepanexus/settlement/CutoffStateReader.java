package com.sepanexus.settlement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Settlement-owned read boundary for static configured cutoff plus runtime cycle facts. */
public interface CutoffStateReader {
    Optional<CutoffState> read(CutoffStateQuery query);

    record CutoffStateQuery(UUID profileId, LocalDate businessDate, short sessionNo) { }
    record CutoffState(UUID cycleId, DeferredCycleState cycleState, Instant cutoffAt, LocalDate businessDate, short sessionNo) {
        public boolean membershipAvailable() { return cycleState == DeferredCycleState.OPEN; }
    }
}
