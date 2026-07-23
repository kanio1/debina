package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("fast")
class RoutingFixturesTest {

    @Test
    void fixedFixturesDriveDeterministicCandidateAndExplicitFallbackReplay() {
        RouteCandidatesQuery query = new RouteCandidatesQuery("SEPA", "URGP", "EUR", RoutingFixtures.BUSINESS_DATE);
        RouteCandidateResolver resolver = RoutingFixtures.candidateResolver();
        FallbackPolicy fallbackPolicy = new FallbackPolicy();

        List<RouteCandidate> firstCandidates = resolver.findCandidates(query);
        List<RouteCandidate> replayCandidates = resolver.findCandidates(query);
        FallbackSelection firstFallback = fallbackPolicy.select(RoutingFixtures.PRIMARY_PROFILE, RoutingFixtures.fallbackRules()).orElseThrow();
        FallbackSelection replayFallback = fallbackPolicy.select(RoutingFixtures.PRIMARY_PROFILE, RoutingFixtures.fallbackRules()).orElseThrow();

        assertThat(firstCandidates).isEqualTo(replayCandidates);
        assertThat(firstCandidates).extracting(RouteCandidate::profileId)
                .containsExactly(RoutingFixtures.PRIMARY_PROFILE, RoutingFixtures.FALLBACK_PROFILE);
        assertThat(firstFallback).isEqualTo(replayFallback);
        assertThat(firstFallback.fallbackProfileId()).isEqualTo(RoutingFixtures.FALLBACK_PROFILE);
    }
}
