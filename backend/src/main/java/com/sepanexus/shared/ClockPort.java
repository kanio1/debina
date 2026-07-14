package com.sepanexus.shared;

import java.time.Instant;

/**
 * The shared-kernel clock (sepa-nexus-message-flow-and-data-blueprint.md §3.3/§8 EPIC-XCUT-1, G8):
 * no component reads the system clock directly — everything reads time through this port, so
 * deterministic simulation (Iteration 3) can substitute a controlled clock.
 */
public interface ClockPort {

    Instant now();
}
