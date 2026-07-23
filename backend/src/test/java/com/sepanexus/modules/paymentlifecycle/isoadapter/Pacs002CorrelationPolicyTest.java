package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * EPIC-27 Story 27.2B: {@link Pacs002CorrelationPolicy}'s ordered strategy behavior — pure
 * decision logic, no Spring context, no PostgreSQL (unlike Story 27.2C's
 * {@code Pacs002CorrelationPersistenceTest}, which proves the real JDBC {@link
 * CorrelationCandidateLookup} implementation against Testcontainers).
 */
@org.junit.jupiter.api.Tag("fast")
class Pacs002CorrelationPolicyTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PAYMENT_A = UUID.randomUUID();
    private static final UUID PAYMENT_B = UUID.randomUUID();

    @Test
    void step1UniqueMatchStopsThePolicyImmediately() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();
        lookup.byOrgnlMsgIdAndOrgnlTxId = List.of(new CorrelationCandidate(PAYMENT_A));

        CorrelationDecision decision = new Pacs002CorrelationPolicy(lookup)
                .correlate(TENANT_ID, input("MSG-1", "TX-1", "INSTR-1", "E2E-1", "UETR-1"));

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.MATCHED);
        assertThat(decision.matchedPaymentId()).isEqualTo(PAYMENT_A);
        assertThat(decision.matchedBy()).isEqualTo(CorrelationMatchStrategy.ORGNL_MSG_ID_ORGNL_TX_ID);
        assertThat(lookup.calls).containsExactly("step1");
    }

    @Test
    void step1AmbiguousStopsThePolicyWithoutTryingWeakerStrategies() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();
        lookup.byOrgnlMsgIdAndOrgnlTxId = List.of(new CorrelationCandidate(PAYMENT_A), new CorrelationCandidate(PAYMENT_B));
        lookup.byOrgnlMsgIdAndOrgnlEndToEndId = List.of(new CorrelationCandidate(PAYMENT_A));

        CorrelationDecision decision = new Pacs002CorrelationPolicy(lookup)
                .correlate(TENANT_ID, input("MSG-1", "TX-1", "INSTR-1", "E2E-1", "UETR-1"));

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.AMBIGUOUS);
        assertThat(decision.matchedPaymentId()).isNull();
        assertThat(decision.matchedBy()).isNull();
        assertThat(lookup.calls).containsExactly("step1");
    }

    @Test
    void step1EmptyFallsThroughToStep2() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();
        lookup.byOrgnlMsgIdAndOrgnlTxId = List.of();
        lookup.byOrgnlMsgIdAndOrgnlInstrIdAndOrgnlEndToEndId = List.of(new CorrelationCandidate(PAYMENT_A));

        CorrelationDecision decision = new Pacs002CorrelationPolicy(lookup)
                .correlate(TENANT_ID, input("MSG-1", "TX-1", "INSTR-1", "E2E-1", null));

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.MATCHED);
        assertThat(decision.matchedBy()).isEqualTo(CorrelationMatchStrategy.ORGNL_MSG_ID_ORGNL_INSTR_ID_ORGNL_END_TO_END_ID);
        assertThat(lookup.calls).containsExactly("step1", "step2");
    }

    @Test
    void step3UetrUniqueMatches() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();
        lookup.byUetr = List.of(new CorrelationCandidate(PAYMENT_A));

        CorrelationDecision decision = new Pacs002CorrelationPolicy(lookup)
                .correlate(TENANT_ID, input("MSG-1", null, null, "E2E-1", "UETR-1"));

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.MATCHED);
        assertThat(decision.matchedBy()).isEqualTo(CorrelationMatchStrategy.UETR);
        assertThat(lookup.calls).containsExactly("step3");
    }

    @Test
    void step3NonUniqueUetrIsAmbiguous() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();
        lookup.byUetr = List.of(new CorrelationCandidate(PAYMENT_A), new CorrelationCandidate(PAYMENT_B));

        CorrelationDecision decision = new Pacs002CorrelationPolicy(lookup)
                .correlate(TENANT_ID, input("MSG-1", null, null, "E2E-1", "UETR-1"));

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.AMBIGUOUS);
        assertThat(lookup.calls).containsExactly("step3");
    }

    @Test
    void step4UniqueMatchWhenNoOptionalIdentifiersPresent() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();
        lookup.byOrgnlMsgIdAndOrgnlEndToEndId = List.of(new CorrelationCandidate(PAYMENT_A));

        CorrelationDecision decision = new Pacs002CorrelationPolicy(lookup)
                .correlate(TENANT_ID, input("MSG-1", null, null, "E2E-1", null));

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.MATCHED);
        assertThat(decision.matchedBy()).isEqualTo(CorrelationMatchStrategy.ORGNL_MSG_ID_ORGNL_END_TO_END_ID);
        assertThat(lookup.calls).containsExactly("step4");
    }

    @Test
    void allStrategiesEmptyIsOrphaned() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();

        CorrelationDecision decision = new Pacs002CorrelationPolicy(lookup)
                .correlate(TENANT_ID, input("MSG-1", "TX-1", "INSTR-1", "E2E-1", "UETR-1"));

        assertThat(decision.outcome()).isEqualTo(CorrelationOutcome.ORPHANED);
        assertThat(decision.matchedPaymentId()).isNull();
        assertThat(decision.matchedBy()).isNull();
        assertThat(lookup.calls).containsExactly("step1", "step2", "step3", "step4");
    }

    @Test
    void strategiesRunInBindingOrderRegardlessOfWhichOneEventuallyMatches() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();
        lookup.byOrgnlMsgIdAndOrgnlEndToEndId = List.of(new CorrelationCandidate(PAYMENT_A));

        new Pacs002CorrelationPolicy(lookup).correlate(TENANT_ID, input("MSG-1", "TX-1", "INSTR-1", "E2E-1", "UETR-1"));

        assertThat(lookup.calls).containsExactly("step1", "step2", "step3", "step4");
    }

    @Test
    void optionalIdentifiersAbsentSkipTheirStrategyWithoutQuerying() {
        FakeCandidateLookup lookup = new FakeCandidateLookup();
        lookup.byOrgnlMsgIdAndOrgnlEndToEndId = List.of(new CorrelationCandidate(PAYMENT_A));

        new Pacs002CorrelationPolicy(lookup).correlate(TENANT_ID, input("MSG-1", null, null, "E2E-1", null));

        assertThat(lookup.calls).containsExactly("step4");
    }

    private static Pacs002CorrelationInput input(
            String orgnlMsgId, String orgnlTxId, String orgnlInstrId, String orgnlEndToEndId, String orgnlUetr) {
        return new Pacs002CorrelationInput(orgnlMsgId, orgnlTxId, orgnlInstrId, orgnlEndToEndId, orgnlUetr);
    }

    /** No FSM call, no duplicate handling, no DB access — a pure in-memory fake, proven by its own shape. */
    private static final class FakeCandidateLookup implements CorrelationCandidateLookup {
        List<CorrelationCandidate> byOrgnlMsgIdAndOrgnlTxId = List.of();
        List<CorrelationCandidate> byOrgnlMsgIdAndOrgnlInstrIdAndOrgnlEndToEndId = List.of();
        List<CorrelationCandidate> byUetr = List.of();
        List<CorrelationCandidate> byOrgnlMsgIdAndOrgnlEndToEndId = List.of();
        final List<String> calls = new ArrayList<>();

        @Override
        public List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlTxId(UUID tenantId, String orgnlMsgId, String orgnlTxId) {
            calls.add("step1");
            return byOrgnlMsgIdAndOrgnlTxId;
        }

        @Override
        public List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlInstrIdAndOrgnlEndToEndId(
                UUID tenantId, String orgnlMsgId, String orgnlInstrId, String orgnlEndToEndId) {
            calls.add("step2");
            return byOrgnlMsgIdAndOrgnlInstrIdAndOrgnlEndToEndId;
        }

        @Override
        public List<CorrelationCandidate> findByUetr(UUID tenantId, String uetr) {
            calls.add("step3");
            return byUetr;
        }

        @Override
        public List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlEndToEndId(UUID tenantId, String orgnlMsgId, String orgnlEndToEndId) {
            calls.add("step4");
            return byOrgnlMsgIdAndOrgnlEndToEndId;
        }
    }
}
