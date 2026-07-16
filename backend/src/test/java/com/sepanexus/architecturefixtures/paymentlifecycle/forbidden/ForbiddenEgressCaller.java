package com.sepanexus.architecturefixtures.paymentlifecycle.forbidden;

import com.sepanexus.architecturefixtures.egress.internal.InternalEgressRepository;

/**
 * Test-only fixture (EPIC-11 Story 11.3): simulates a {@code payment-lifecycle} class that reaches
 * directly into {@code egress}'s internal implementation instead of going through a public port.
 * Not real production code.
 */
public class ForbiddenEgressCaller {

    private final InternalEgressRepository repository = new InternalEgressRepository();

    public void run(String paymentId) {
        repository.writeEgressRow(paymentId);
    }
}
