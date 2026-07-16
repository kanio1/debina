package com.sepanexus.modules.paymentlifecycle.isoadapter;

/**
 * The original-message identifiers a single pacs.002 {@code TxInfAndSts} entry echoes back
 * (sepa-nexus-message-flow-and-data-blueprint.md §2.4/§8 EPIC-ISO-2 Story 1: "pacs.002 identifier
 * extraction"). {@code orgnlMsgId} is carried once per message at {@code OrgnlGrpInfAndSts} level
 * and repeated here per transaction entry for convenience; {@code orgnlEndToEndId} is per-entry.
 * These are correlation *inputs*, not a correlation result — deciding what payment/message they
 * refer to is Story 27.2 ({@code iso-adapter} correlates, {@code payment-lifecycle} transitions;
 * see root {@code AGENTS.md}). Never conflate with this status message's own {@code GrpHdr/MsgId}.
 */
public record Pacs002OriginalIdentifiers(String orgnlMsgId, String orgnlEndToEndId) {
}
