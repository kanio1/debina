package com.sepanexus.modules.paymentlifecycle.event;

import java.util.UUID;

/** Controlled failure boundary for a relay batch; payloads are intentionally not retained. */
public class OutboxRelayPublicationException extends RuntimeException {

    public OutboxRelayPublicationException(String schema, UUID eventId, Throwable cause) {
        super("Kafka publication failed for " + schema + " outbox event " + eventId, cause);
    }
}
