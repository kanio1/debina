package com.sepanexus.routing;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Static route-profile candidate facts read from reference-data; routing never owns this catalog. */
public record RouteCandidate(
        UUID profileId,
        String scheme,
        String serviceLevel,
        String currency,
        short priority,
        LocalDate validFrom,
        LocalDate validTo) {

    public RouteCandidate {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(validFrom, "validFrom");
    }

    boolean isActiveOn(LocalDate businessDate) {
        return !businessDate.isBefore(validFrom) && (validTo == null || !businessDate.isAfter(validTo));
    }
}
