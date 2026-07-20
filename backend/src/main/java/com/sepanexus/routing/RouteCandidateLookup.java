package com.sepanexus.routing;

import java.util.List;

/** Read-only port through which routing reads reference-data candidate priorities. */
public interface RouteCandidateLookup {
    List<RouteCandidate> findBySchemeServiceLevelAndCurrency(String scheme, String serviceLevel, String currency);
}
