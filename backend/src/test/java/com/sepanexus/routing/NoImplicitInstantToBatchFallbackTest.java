package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The labels are test data only; §4.10 permits a fallback only through an explicit rule. */
@org.junit.jupiter.api.Tag("fast")
class NoImplicitInstantToBatchFallbackTest {

    @Test
    void aProfileRelationshipWithoutAnExplicitRuleCannotSelectAnyFallback() {
        UUID instantLikeProfile = UUID.randomUUID();
        UUID batchLikeProfile = UUID.randomUUID();

        assertThat(new FallbackPolicy().select(instantLikeProfile, List.of())).isEmpty();
        assertThat(batchLikeProfile).isNotEqualTo(instantLikeProfile);
    }
}
