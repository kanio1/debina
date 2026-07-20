package com.sepanexus.modules.paymentlifecycle.event;

import java.util.Arrays;
import java.util.Optional;

/**
 * Controlled event-type routing for the two payment facts named in the frozen topic catalog.
 * There is intentionally no fallback topic: an unresolved row remains unpublished.
 */
public enum PaymentOutboxEventType {
    PAYMENT_RECEIVED("payment.received.v1", PaymentLifecycleTopicConfig.RECEIVED_TOPIC),
    PAYMENT_VALIDATED("payment.validated.v1", PaymentLifecycleTopicConfig.VALIDATED_TOPIC);

    private final String eventType;
    private final String topic;

    PaymentOutboxEventType(String eventType, String topic) {
        this.eventType = eventType;
        this.topic = topic;
    }

    public String eventType() {
        return eventType;
    }

    public String topic() {
        return topic;
    }

    public static Optional<PaymentOutboxEventType> fromEventType(String eventType) {
        return Arrays.stream(values()).filter(value -> value.eventType.equals(eventType)).findFirst();
    }
}
