package com.sepanexus.modules.paymentlifecycle.event;

public enum OutboxRelayFailureCategory {
    DATABASE_PERMISSION,
    DATABASE_CONNECTIVITY,
    BROKER_UNAVAILABLE,
    UNKNOWN_EVENT_TYPE,
    UNEXPECTED
}
