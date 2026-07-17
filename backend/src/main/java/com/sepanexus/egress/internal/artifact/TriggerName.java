package com.sepanexus.egress.internal.artifact;

import java.util.Objects;

/**
 * A source event name from §6.9's "Trigger" column. Deliberately neutral — it does NOT classify a
 * trigger as a Kafka topic versus an internal domain event, because the source table mixes both
 * without a consistent naming convention: some entries (e.g. {@code settlement.completed},
 * {@code payment.routed}, {@code route.failed}, {@code egress.dead_lettered}, {@code case.resolved})
 * match real, registered {@code infra/asyncapi/asyncapi.yaml} topic addresses, while others
 * ({@code FileProcessed}, {@code ReturnInitiated}, prose like "ingress rejection") are not
 * registered topics at all. Classifying every entry consistently is not possible from source alone
 * — per this story's own scope rule, a neutral value type is used instead of guessing.
 */
public record TriggerName(String value) {

    public TriggerName {
        Objects.requireNonNull(value, "value");
    }
}
