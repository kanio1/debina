# ISO 20022 identifiers

## Raw vs. canonical

The raw inbound XML (`ingress.raw_inbound_messages`) and the canonical, parsed model (`iso.iso_messages` / `iso.payment_iso_identifiers`) are two distinct representations, kept both — the raw form is the audit-grade source of truth (see `sepa-nexus-signature-module-blueprint.md`'s verify-before-parse ordering and `append-only-evidence.md`), the canonical model is what the rest of the system operates on. Never derive one from the other lossily in a way that can't be checked back against the original.

## The identifier family — never conflate

| Identifier | Meaning | Carried by |
|---|---|---|
| `MsgId` | The message envelope's own identifier (`GrpHdr.MsgId`) | Every ISO message type |
| `PmtInfId` | Payment-information-block identifier (pain.001) | pain.001 |
| `InstrId` | Instruction identifier, optional | pain.001, pacs.008 |
| `EndToEndId` | The end-to-end identifier the originator assigned, meant to survive the full payment chain | pain.001, pacs.008 |
| `TxId` | Transaction identifier — **not a pain.001 field** (`PaymentIdentification6` has no `TxId`); this is a pacs.008 concept | pacs.008 |
| `UETR` | Universally unique end-to-end transaction reference (UUID), optional, pain.001.001.09+ | pain.001, pacs.008 |
| `OrgnlMsgId` / `OrgnlEndToEndId` / `OrgnlInstrId` / `OrgnlTxId` | The *original* message/transaction's identifiers, as echoed back by a status/return/reject message | pacs.002, R-messages |

## Never generate a missing identifier

If a given channel doesn't carry a field (e.g. pain.001 has no `TxId`), leave it genuinely absent (nullable column, absent from the canonical model for that record) — do not synthesize a placeholder value. A synthetic identifier masquerading as a real one is worse than a visible gap: it can silently participate in correlation/matching logic as if it were authoritative. Precedent already in this repo: `EPIC-26` Story 26.3 explicitly built only the fields pain.001 actually carries, leaving `TxId`/`Orgnl*` genuinely absent rather than empty-string/zero placeholders, "waiting for the channel that actually carries them."

## `Orgnl*` is correlation input, not a new identifier

The `Orgnl*` family on a pacs.002/R-message exists specifically to let the correlation engine (`iso-adapter`, see `correlation-integrity.md`) find the original payment/message being referenced — never treat an `OrgnlEndToEndId` as if it were the *current* message's own `EndToEndId`. Extracting it (see `EPIC-27` Story 27.1) is a pure read/parse operation; deciding what it correlates *to* is a separate, later concern (`EPIC-27` Story 27.2), owned by the correlation policy, not the extraction step.

## Uniqueness

Uniqueness scope for any identifier is whatever the current, frozen decision states — check `planning/epics/EPIC-21-iso-identifier-refactor.md` Story 21.2 for this project's specific resolved conflict (the tenant+`end_to_end_id` uniqueness index was deliberately removed; `Idempotency-Key` is now the sole exactly-once guarantee, not any ISO identifier). Do not reintroduce a uniqueness constraint on an ISO identifier without checking whether this decision still applies.
