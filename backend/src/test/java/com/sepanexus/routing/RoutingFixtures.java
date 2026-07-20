package com.sepanexus.routing;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Deterministic test-lab data that drives the real candidate and fallback policies. */
final class RoutingFixtures {

    static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 20);
    static final UUID PRIMARY_PROFILE = UUID.fromString("00000000-0000-0000-0000-000000000201");
    static final UUID FALLBACK_PROFILE = UUID.fromString("00000000-0000-0000-0000-000000000202");
    static final UUID EXPIRED_PROFILE = UUID.fromString("00000000-0000-0000-0000-000000000203");
    static final UUID FALLBACK_RULE = UUID.fromString("00000000-0000-0000-0000-000000000204");

    private RoutingFixtures() {}

    static RouteCandidateResolver candidateResolver() {
        return new RouteCandidateResolver((scheme, serviceLevel, currency) -> List.of(
                new RouteCandidate(FALLBACK_PROFILE, scheme, serviceLevel, currency, (short) 20,
                        LocalDate.of(2026, 1, 1), null),
                new RouteCandidate(EXPIRED_PROFILE, scheme, serviceLevel, currency, (short) 1,
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
                new RouteCandidate(PRIMARY_PROFILE, scheme, serviceLevel, currency, (short) 10,
                        LocalDate.of(2026, 1, 1), null)));
    }

    static List<FallbackRule> fallbackRules() {
        return List.of(new FallbackRule(FALLBACK_RULE, PRIMARY_PROFILE, FALLBACK_PROFILE, (short) 10, null));
    }
}
