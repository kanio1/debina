package com.sepanexus.architecturefixtures.paymentlifecycle.allowed;

import com.sepanexus.architecturefixtures.settlement.SettlementPort;

/**
 * Test-only fixture (EPIC-11 Story 11.3): simulates a {@code payment-lifecycle} class that
 * integrates with {@code settlement} correctly, through its public port only — the rule must not
 * reject this. Not real production code.
 */
public class AllowedSettlementPortCaller {

    private final SettlementPort settlementPort;

    public AllowedSettlementPortCaller(SettlementPort settlementPort) {
        this.settlementPort = settlementPort;
    }

    public void run(String paymentId) {
        settlementPort.requestSettlement(paymentId);
    }
}
