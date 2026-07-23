package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("fast")
class RouteCandidateResolutionTest {

    private final UUID activeHigherPriority = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID activeLowerPriority = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID expired = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final RouteCandidateResolver resolver = new RouteCandidateResolver((scheme, serviceLevel, currency) -> {
        assertThat(scheme).isEqualTo("SEPA");
        assertThat(serviceLevel).isEqualTo("URGP");
        assertThat(currency).isEqualTo("EUR");
        return List.of(
                candidate(activeLowerPriority, 20, LocalDate.of(2026, 1, 1), null),
                candidate(expired, 1, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
                candidate(activeHigherPriority, 10, LocalDate.of(2026, 1, 1), null));
    });

    @Test
    void returnsOnlyActiveCandidatesInConfiguredPriorityOrder() {
        assertThat(resolver.resolve("SEPA", "URGP", "EUR", LocalDate.of(2026, 7, 20)))
                .extracting(RouteCandidate::profileId)
                .containsExactly(activeHigherPriority, activeLowerPriority);
    }

    @Test
    void includesAProfileOnBothValidityWindowBoundaries() {
        RouteCandidate bounded = candidate(activeHigherPriority, 10, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));
        RouteCandidateResolver boundedResolver = new RouteCandidateResolver((scheme, serviceLevel, currency) -> List.of(bounded));

        assertThat(boundedResolver.resolve("SEPA", "URGP", "EUR", LocalDate.of(2026, 7, 20))).containsExactly(bounded);
        assertThat(boundedResolver.resolve("SEPA", "URGP", "EUR", LocalDate.of(2026, 7, 21))).containsExactly(bounded);
    }

    @Test
    void excludesAProfileOutsideItsValidityWindow() {
        RouteCandidate bounded = candidate(activeHigherPriority, 10, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));
        RouteCandidateResolver boundedResolver = new RouteCandidateResolver((scheme, serviceLevel, currency) -> List.of(bounded));

        assertThat(boundedResolver.resolve("SEPA", "URGP", "EUR", LocalDate.of(2026, 7, 19))).isEmpty();
        assertThat(boundedResolver.resolve("SEPA", "URGP", "EUR", LocalDate.of(2026, 7, 22))).isEmpty();
    }

    private RouteCandidate candidate(UUID profileId, int priority, LocalDate validFrom, LocalDate validTo) {
        return new RouteCandidate(profileId, "SEPA", "URGP", "EUR", (short) priority, validFrom, validTo);
    }
}
