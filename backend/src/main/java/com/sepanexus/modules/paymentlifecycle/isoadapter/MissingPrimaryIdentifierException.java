package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.UUID;

/**
 * EPIC-21 Story 21.2: every payment is written together with its {@code ORIGINAL_INSTRUCTION}
 * identifier row in one unit ({@code JsonDirectLineageRecorder}/{@code Pain001LineageRecorder}), so
 * a payment with none is a data-integrity violation, not a normal "not found" case — it must never
 * be silently masked with a placeholder value at the read layer.
 */
public class MissingPrimaryIdentifierException extends RuntimeException {

    public MissingPrimaryIdentifierException(UUID paymentId) {
        super("Payment " + paymentId + " has no primary ORIGINAL_INSTRUCTION identifier");
    }
}
