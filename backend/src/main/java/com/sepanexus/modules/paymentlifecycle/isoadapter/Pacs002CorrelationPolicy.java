package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * EPIC-27 Story 27.2B: the ordered candidate-match policy — blueprint §2.4/§4.3b steps 1–4, 6, 7.
 * Runs each implemented strategy in binding order; the first strategy that produces any candidate
 * decides the outcome immediately (never falls through to a weaker strategy after an ambiguous
 * match, never best-guesses among multiple candidates). Step 5 (fallback) is not implemented —
 * see `EPIC-27-iso-correlation-engine.md` Story 27.2's `[OPEN-QUESTION]`. Steps 8/9 (duplicate,
 * late/weaker-vs-FSM) are Story 27.3, not this policy.
 *
 * <p>Pure decision logic: no DB write, no event publication, no FSM call — {@code iso-adapter}
 * correlates, {@code payment-lifecycle} transitions (root {@code AGENTS.md} binding rule).
 */
@Component
public class Pacs002CorrelationPolicy {

    private final CorrelationCandidateLookup candidateLookup;

    public Pacs002CorrelationPolicy(CorrelationCandidateLookup candidateLookup) {
        this.candidateLookup = candidateLookup;
    }

    public CorrelationDecision correlate(UUID tenantId, Pacs002CorrelationInput input) {
        if (input.orgnlTxId() != null) {
            CorrelationDecision decision = decide(
                    candidateLookup.findByOrgnlMsgIdAndOrgnlTxId(tenantId, input.orgnlMsgId(), input.orgnlTxId()),
                    CorrelationMatchStrategy.ORGNL_MSG_ID_ORGNL_TX_ID);
            if (decision != null) {
                return decision;
            }
        }

        if (input.orgnlInstrId() != null) {
            CorrelationDecision decision = decide(
                    candidateLookup.findByOrgnlMsgIdAndOrgnlInstrIdAndOrgnlEndToEndId(
                            tenantId, input.orgnlMsgId(), input.orgnlInstrId(), input.orgnlEndToEndId()),
                    CorrelationMatchStrategy.ORGNL_MSG_ID_ORGNL_INSTR_ID_ORGNL_END_TO_END_ID);
            if (decision != null) {
                return decision;
            }
        }

        if (input.orgnlUetr() != null) {
            CorrelationDecision decision = decide(
                    candidateLookup.findByUetr(tenantId, input.orgnlUetr()),
                    CorrelationMatchStrategy.UETR);
            if (decision != null) {
                return decision;
            }
        }

        CorrelationDecision decision = decide(
                candidateLookup.findByOrgnlMsgIdAndOrgnlEndToEndId(tenantId, input.orgnlMsgId(), input.orgnlEndToEndId()),
                CorrelationMatchStrategy.ORGNL_MSG_ID_ORGNL_END_TO_END_ID);
        if (decision != null) {
            return decision;
        }

        return CorrelationDecision.orphaned();
    }

    /** {@code null} means "no candidate at this strategy, try the next one" — never a decision itself. */
    private CorrelationDecision decide(List<CorrelationCandidate> candidates, CorrelationMatchStrategy strategy) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return CorrelationDecision.matched(candidates.get(0).paymentId(), strategy);
        }
        return CorrelationDecision.ambiguous();
    }
}
