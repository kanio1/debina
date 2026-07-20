---
name: debina-iso20022-validation-lineage
description: Use for ISO 20022, pain.001, pacs.002/pacs.004/pacs.008, camt, AppHdr/Business Application Header, BizMsgIdr, MsgId, InstrId, EndToEndId, TxId, UETR, XSD, EPC/TVS validation, signed-message handling, message versioning, raw evidence, correlation, lineage, duplicates, or replay; do not use for generic XML utilities, unrelated JSON APIs, generic migrations, or payment finality without an ISO-message concern.
---
# ISO 20022 validation and lineage

Preserve source precedence: accepted ADR and `[FREEZE]` → authoritative source → accepted decision → `HANDOFF.md` → capability/readiness → implementation evidence. Read the applicable repository source before changing a rule. Treat an unavailable EPC, CSM, participant, or certification source as a blocker, not permission to infer it.

## Work sequence

1. Identify the exact message type, direction, namespace, version, signed-evidence requirement, validation profile, and authoritative source. Use the exact version registry and fail unsupported versions; never fall back to a “closest” XSD.
2. Keep the layers in [validation-layer-model.md](references/validation-layer-model.md) separate. Configure secure XML parsing independently of XSD validity. Where the accepted source requires it, enforce signature-before-parse over raw inbound evidence.
3. Label every proposed rule or mapping with a source class from [source-and-compliance-boundaries.md](references/source-and-compliance-boundaries.md). Do not claim EPC, CSM, participant, certification, legal, or settlement compliance without its required evidence.
4. Preserve immutable raw inbound/outbound evidence and its original identifiers. Model source message, derived message, response, and business payment without overwriting identity dimensions; use [message-lineage-identifiers.md](references/message-lineage-identifiers.md).
5. Classify duplicate, replay, conflict, and business resubmission explicitly. Define deterministic idempotency only with a documented key, scope, payload comparison, and outcome.
6. Verify the relevant negative proofs from [negative-proof-matrix.md](references/negative-proof-matrix.md), including tenant isolation and mutation/source-traceability evidence where state is affected.

## Non-negotiable boundaries

Do not equate secure parsing with schema validation, XSD validation with EPC compliance, or a profile-free message with CSM compliance. Apply `DO-NOT-CLAIM-COMPLIANCE` when source or certification evidence is absent. Do not silently accept an unsupported version, regenerate an identifier that must be preserved, collapse `BizMsgIdr`, `MsgId`, `EndToEndId`, `TxId`, and `UETR`, or invent ISO-to-business-status mappings.

Keep ISO/message status, business status, settlement finality, transport/delivery status, and receipt/reconciliation status independent. `ACSC`, a receipt, delivery, and signature success do not establish settlement finality. Do not derive legal or settlement finality from ISO status, or claim certification readiness without certification evidence.

Respect the `iso-adapter` boundary: it records parse/validation/identifier/correlation facts in `iso.*`; it does not decide payment business status, routing, settlement, ledger effects, egress delivery, or finality. Preserve ADR-N7 `JSON_DIRECT` as its explicit synthetic lineage path rather than a nullable or bypassed lineage shortcut.

## References

- [validation layer model](references/validation-layer-model.md)
- [message lineage identifiers](references/message-lineage-identifiers.md)
- [source and compliance boundaries](references/source-and-compliance-boundaries.md)
- [negative proof matrix](references/negative-proof-matrix.md)
