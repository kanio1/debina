package com.sepanexus.routing;

import java.time.LocalDate;
import java.util.Objects;

/** Exact static-key candidate query defined by the routing pipeline's first stage. */
public record RouteCandidatesQuery(String scheme, String serviceLevel, String currency, LocalDate businessDate) {

    public RouteCandidatesQuery {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(businessDate, "businessDate");
    }
}
