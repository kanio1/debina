package com.sepanexus.architecturefixtures.routing.internal;

/**
 * Test-only fixture (EPIC-11 Story 11.3): simulates a forbidden {@code routing} module
 * implementation class that no other module may depend on directly. Not real production code.
 */
public class InternalRoutingRepository {

    public void writeRoutingRow(String paymentId) {
        // fixture only — no real persistence
    }
}
