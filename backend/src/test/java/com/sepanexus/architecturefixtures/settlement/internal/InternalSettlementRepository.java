package com.sepanexus.architecturefixtures.settlement.internal;

/**
 * Test-only fixture (EPIC-11 Story 11.3): simulates a forbidden {@code settlement} module
 * implementation class (e.g. a repository) that no other module may depend on directly. Not real
 * production code.
 */
public class InternalSettlementRepository {

    public void writeSettlementRow(String paymentId) {
        // fixture only — no real persistence
    }
}
