package com.sepanexus.modules.paymentlifecycle.isoadapter;

/**
 * EPIC-27 Story 27.2A: the full set of original-message identifiers a single pacs.002
 * {@code TxInfAndSts} entry can carry, per the real pacs.002.001.10 schema — the richer
 * counterpart to {@link Pacs002OriginalIdentifiers} (Story 27.1's minimal
 * {@code orgnlMsgId}+{@code orgnlEndToEndId} pair, left untouched). {@code orgnlMsgId} is
 * carried once per message at {@code OrgnlGrpInfAndSts} level and repeated here per
 * transaction entry; {@code orgnlTxId}, {@code orgnlInstrId} and {@code orgnlUetr} are
 * per-entry and optional in the ISO schema — {@code null} when the reporting bank did not
 * echo them back. These are correlation *inputs* only (Story 27.2A); deciding what
 * payment/message they correlate to is the ordered policy in Story 27.2B.
 */
public record Pacs002CorrelationInput(
        String orgnlMsgId,
        String orgnlTxId,
        String orgnlInstrId,
        String orgnlEndToEndId,
        String orgnlUetr) {
}
