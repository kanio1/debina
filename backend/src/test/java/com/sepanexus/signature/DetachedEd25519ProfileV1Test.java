package com.sepanexus.signature;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("fast")
class DetachedEd25519ProfileV1Test {

    @Test
    void mapsVerdictsToStableProfileOutcomes() {
        assertThat(DetachedEd25519ProfileV1.toProfileOutcome(
                new Verdict(Verdict.Result.VERIFIED, null, "Ed25519", null)))
                .isEqualTo(DetachedEd25519ProfileV1.ProfileOutcome.VERIFIED);
        assertThat(DetachedEd25519ProfileV1.toProfileOutcome(
                new Verdict(Verdict.Result.FAILED, null, "Ed25519", Verdict.REASON_MISSING_REQUIRED_SIGNATURE)))
                .isEqualTo(DetachedEd25519ProfileV1.ProfileOutcome.MISSING_SIGNATURE);
        assertThat(DetachedEd25519ProfileV1.toProfileOutcome(
                new Verdict(Verdict.Result.FAILED, null, "Ed25519", Verdict.REASON_MALFORMED_SIGNATURE)))
                .isEqualTo(DetachedEd25519ProfileV1.ProfileOutcome.MALFORMED_SIGNATURE);
        assertThat(DetachedEd25519ProfileV1.toProfileOutcome(
                new Verdict(Verdict.Result.FAILED, null, "Ed25519", Verdict.REASON_TAMPERED_OR_INVALID)))
                .isEqualTo(DetachedEd25519ProfileV1.ProfileOutcome.INVALID_SIGNATURE);
        assertThat(DetachedEd25519ProfileV1.toProfileOutcome(
                new Verdict(Verdict.Result.FAILED, null, "Ed25519", Verdict.REASON_KEY_NOT_FOUND_OR_INACTIVE)))
                .isEqualTo(DetachedEd25519ProfileV1.ProfileOutcome.UNKNOWN_SIGNER);
    }
}
