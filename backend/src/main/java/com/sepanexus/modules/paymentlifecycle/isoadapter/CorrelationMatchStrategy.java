package com.sepanexus.modules.paymentlifecycle.isoadapter;

/**
 * EPIC-27 Story 27.2B: the controlled vocabulary for {@code iso.iso_message_correlation.matched_by}
 * (Story 27.2C) — one value per implemented ordered strategy (blueprint §2.4/§4.3b steps 1–4).
 * Never free text: a review finding "matched_by holds ad-hoc strings" is a binding-rule violation,
 * not a style nit (see the readiness audit's score/matched_by decision).
 *
 * <p>Step 5 (fallback {@code EndToEndId+amount+currency+participant+time-window}) has no member
 * here — it is explicitly not implemented (see `EPIC-27-iso-correlation-engine.md` Story 27.2's
 * `[OPEN-QUESTION]`), not silently subsumed into one of the four below.
 */
public enum CorrelationMatchStrategy {

    /** Step 1: {@code OrgnlMsgId+OrgnlTxId}. */
    ORGNL_MSG_ID_ORGNL_TX_ID,

    /** Step 2: {@code OrgnlMsgId+OrgnlInstrId+OrgnlEndToEndId}. */
    ORGNL_MSG_ID_ORGNL_INSTR_ID_ORGNL_END_TO_END_ID,

    /** Step 3: {@code UETR}, only when it resolves to exactly one candidate. */
    UETR,

    /** Step 4: {@code OrgnlMsgId+OrgnlEndToEndId}. */
    ORGNL_MSG_ID_ORGNL_END_TO_END_ID
}
