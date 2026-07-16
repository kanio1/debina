package com.sepanexus.architecturefixtures.paymentlifecycle.forbidden;

import com.sepanexus.architecturefixtures.settlement.internal.InternalSettlementRepository;

/**
 * Test-only fixture (EPIC-11 Story 11.3): simulates a {@code payment-lifecycle} class that reaches
 * directly into {@code settlement}'s internal implementation instead of going through a public
 * port — exactly what {@code PaymentNoGodModuleTest}'s rule must reject. Not real production code.
 */
public class ForbiddenSettlementCaller {

    private final InternalSettlementRepository repository = new InternalSettlementRepository();

    public void run(String paymentId) {
        repository.writeSettlementRow(paymentId);
    }
}
