package com.sepanexus.modules.paymentlifecycle.domain;

import java.util.UUID;

public record PaymentLifecycleEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        String eventType) {
}
