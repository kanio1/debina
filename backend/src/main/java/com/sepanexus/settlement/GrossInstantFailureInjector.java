package com.sepanexus.settlement;

/** Test seam for proving rollback at each ADR-N11 command-function boundary. */
@FunctionalInterface
public interface GrossInstantFailureInjector {

    void at(Phase phase);

    enum Phase {
        BEFORE_LEDGER, AFTER_LEDGER, BEFORE_SETTLEMENT, AFTER_SETTLEMENT, BEFORE_PAYMENT, AFTER_PAYMENT
    }
}
