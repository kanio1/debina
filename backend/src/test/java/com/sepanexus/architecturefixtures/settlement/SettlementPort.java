package com.sepanexus.architecturefixtures.settlement;

/**
 * Test-only fixture (EPIC-11 Story 11.3): simulates {@code settlement}'s public port — the one
 * integration surface {@code payment-lifecycle} is allowed to depend on. Not real production code.
 */
public interface SettlementPort {

    void requestSettlement(String paymentId);
}
