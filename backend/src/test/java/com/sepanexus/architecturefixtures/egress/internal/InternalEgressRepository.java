package com.sepanexus.architecturefixtures.egress.internal;

/**
 * Test-only fixture (EPIC-11 Story 11.3): simulates a forbidden {@code egress} module
 * implementation class that no other module may depend on directly. Not real production code.
 */
public class InternalEgressRepository {

    public void writeEgressRow(String paymentId) {
        // fixture only — no real persistence
    }
}
