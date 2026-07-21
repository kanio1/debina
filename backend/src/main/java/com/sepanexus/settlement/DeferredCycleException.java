package com.sepanexus.settlement;

/** A cycle command is invalid for the authoritative persisted state and therefore fails closed. */
public final class DeferredCycleException extends RuntimeException {
    public DeferredCycleException(String message) {
        super(message);
    }
}
