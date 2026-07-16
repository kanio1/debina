package com.sepanexus.architecturefixtures.paymentlifecycle.forbidden;

import com.sepanexus.architecturefixtures.routing.internal.InternalRoutingRepository;

/**
 * Test-only fixture (EPIC-11 Story 11.3): simulates a {@code payment-lifecycle} class that reaches
 * directly into {@code routing}'s internal implementation instead of going through a public port.
 * Not real production code.
 */
public class ForbiddenRoutingCaller {

    private final InternalRoutingRepository repository = new InternalRoutingRepository();

    public void run(String paymentId) {
        repository.writeRoutingRow(paymentId);
    }
}
