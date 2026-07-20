package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteCandidatesReadModelTest {

    @Test
    void moduleOwnedReadModelReturnsOnlyActiveCandidatesWithoutAnyDecisionOrMutationSurface() {
        UUID active = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID expired = UUID.fromString("00000000-0000-0000-0000-000000000102");
        RouteCandidatesReadModel readModel = new RouteCandidateResolver((scheme, serviceLevel, currency) -> {
            assertThat(scheme).isEqualTo("SEPA");
            assertThat(serviceLevel).isEqualTo("URGP");
            assertThat(currency).isEqualTo("EUR");
            return List.of(
                    new RouteCandidate(expired, scheme, serviceLevel, currency, (short) 1,
                            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
                    new RouteCandidate(active, scheme, serviceLevel, currency, (short) 10,
                            LocalDate.of(2026, 1, 1), null));
        });

        assertThat(readModel.findCandidates(new RouteCandidatesQuery(
                "SEPA", "URGP", "EUR", LocalDate.of(2026, 7, 20))))
                .extracting(RouteCandidate::profileId)
                .containsExactly(active);
    }
}
