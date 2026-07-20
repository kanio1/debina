package com.sepanexus.modules.paymentlifecycle.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The FSM as data (sepa-nexus-message-flow-and-data-blueprint.md §3.5/§8 EPIC-CORE-1): a forbidden
 * (from, to) pair throws, it never silently no-ops.
 */
public final class PaymentTransitionTable {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(PaymentStatus.RECEIVED, EnumSet.of(PaymentStatus.VALIDATED, PaymentStatus.REJECTED));
        ALLOWED.put(PaymentStatus.VALIDATED, EnumSet.of(PaymentStatus.DISPATCHED, PaymentStatus.REJECTED));
        ALLOWED.put(PaymentStatus.REJECTED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(PaymentStatus.DISPATCHED, EnumSet.noneOf(PaymentStatus.class));
    }

    private PaymentTransitionTable() {
    }

    public static void requireLegal(PaymentStatus from, PaymentStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalPaymentTransitionException(from, to);
        }
    }

    /**
     * Reports only the topology of the current, deliberately thin payment business-state FSM.
     * A state with no legal outgoing transition is not settlement or legal finality: settlement
     * owns that independent, profile-configured fact.
     */
    public static boolean hasNoLegalOutgoingTransitions(PaymentStatus status) {
        return ALLOWED.getOrDefault(status, Set.of()).isEmpty();
    }
}
