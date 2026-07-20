package com.sepanexus.routing;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * EPIC-51 Stories 51.1–51.2: resolves configured route candidates by the exact static
 * scheme/service-level/currency key and excludes profiles outside their configured validity
 * window. It records no route decision and performs no eligibility, reachability, settlement or
 * payment-state action; those remain subsequent pipeline stages in blueprint §4.10.
 */
public final class RouteCandidateResolver implements RouteCandidatesReadModel {

    private static final Comparator<RouteCandidate> CANDIDATE_ORDER =
            Comparator.comparingInt(RouteCandidate::priority).thenComparing(RouteCandidate::profileId);

    private final RouteCandidateLookup lookup;

    public RouteCandidateResolver(RouteCandidateLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public List<RouteCandidate> resolve(String scheme, String serviceLevel, String currency, LocalDate businessDate) {
        return findCandidates(new RouteCandidatesQuery(scheme, serviceLevel, currency, businessDate));
    }

    @Override
    public List<RouteCandidate> findCandidates(RouteCandidatesQuery query) {
        Objects.requireNonNull(query, "query");
        return lookup.findBySchemeServiceLevelAndCurrency(query.scheme(), query.serviceLevel(), query.currency()).stream()
                .filter(candidate -> candidate.isActiveOn(query.businessDate()))
                .sorted(CANDIDATE_ORDER)
                .toList();
    }
}
