package com.sepanexus.settlement;

/** The smallest source-backed cycle FSM from blueprint §4.6/§4.11. */
public enum DeferredCycleState {
    OPEN,
    CLOSING,
    CLOSED,
    NETTED,
    SETTLED
}
