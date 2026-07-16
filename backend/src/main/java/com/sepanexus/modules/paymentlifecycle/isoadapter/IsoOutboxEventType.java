package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.Arrays;
import java.util.Optional;

/**
 * EPIC-27 Story 27.4: the controlled {@code event_type} → Kafka topic routing every {@code
 * iso.outbox_events} row must resolve through before {@link IsoOutboxDispatcher} publishes it.
 * Deliberately not {@code topic = eventType.replace(".v1", "")} or any other implicit derivation —
 * every publishable event type is named here explicitly, and {@link IsoOutboxDispatcher} refuses
 * to publish (and refuses to guess a default topic for) any {@code event_type} not listed.
 */
public enum IsoOutboxEventType {

    MESSAGE_CORRELATED("iso.message.correlated.v1", "iso.message.correlated"),
    MESSAGE_ORPHANED("iso.message.orphaned.v1", "iso.message.orphaned");

    private final String eventType;
    private final String topic;

    IsoOutboxEventType(String eventType, String topic) {
        this.eventType = eventType;
        this.topic = topic;
    }

    public String eventType() {
        return eventType;
    }

    public String topic() {
        return topic;
    }

    public static Optional<IsoOutboxEventType> fromEventType(String eventType) {
        return Arrays.stream(values()).filter(candidate -> candidate.eventType.equals(eventType)).findFirst();
    }
}
