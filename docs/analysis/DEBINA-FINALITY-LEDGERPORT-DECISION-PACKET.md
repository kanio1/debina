# Debina finality, LedgerPort, and producer-ownership decision packet

Status: **PROPOSED — requires product/team decisions where noted; not an ADR and does not accept any option.**

## Purpose and source hierarchy

This packet separates a completed safety repair (business-FSM terminality no longer asserts
settlement finality) from decisions that the repository has not yet made executable. Binding
sources are `README.md` frozen architecture, `sepa-nexus-message-flow-and-data-blueprint.md`
§§3.6, 3.7, 4.3, 4.5, 4.11 and 4.14, `sepa-nexus-blueprint-ownership-integration.md` §§3.3/3.6,
and `sepa-nexus-decision-gate.md`. Existing code and tests are subordinate evidence.

The forward-only safety repair is `payment/V30__clear_terminal_business_status_false_finality.sql`
plus `PaymentHistoryRecorder`: every current payment-lifecycle history transition is non-final.
It does **not** implement a finality policy or a five-axis persistence model.

## Decision A — product-scope impact

The repository defines Debina as a synthetic, deterministic Credit Transfer/ISO 20022 learning lab,
not a real bank, CSM, payment hub, or compliance claim (`AGENTS.md`; comprehensive assessment §2).
The assessment explicitly rates the current implementation as a partial learning lab, not an
integration-ready full payment hub, and identifies scope as a stop-the-line decision (assessment
§§2, C.3–C.4).

| Concern | Synthetic learning lab | Full payment hub / concentrator |
|---|---|---|
| Finality | Profile-rule model and negative safety proofs are required; one deliberately synthetic profile can be a later learning slice. | Legally/contractually authoritative per rail/profile finality evidence, conflict/late-evidence policy, operational authority, and durable audit traceability are required. |
| EPC traceability | Public standards may inform exercises; no conformance claim. | Versioned standards-to-requirements traceability, effective dates, participant-specific documentation, and certification evidence are required. |
| CSM profiles | Synthetic rows and simulator lessons can be sufficient. | Selected CSM/UDFS/service contracts, authenticated adapters, change/version governance, and certification packs are required. |
| Incoming flows | May remain explicitly out of scope while learning goals are met. | Incoming SCT/SCT Inst (and any approved products) need source-backed ownership, parsing, validation, correlation, lifecycle, reconciliation, and exception paths. |
| Receipts and reconciliation | Transport-only receipt exercises and read-only mismatch detection can remain staged. | Contracted receipt semantics, evidence retention, operational recovery, reconciliation closure, and accountable escalation are required. |

No option is selected here. The implication is material: choosing the full-hub column would broaden
the required authority and evidence, and cannot be inferred from an implementation convenience.

## Decision B — finality persistence authority

### Source facts

- Settlement owns finality and applies the selected profile's `finality_rule`; `FinalityPolicy`
  reads reference data (`message-flow` §§3.10–3.11, 4.11, especially lines 973–1005).
- The source rules are `ON_LEDGER_POST` (gross/internal), `ON_CYCLE_SETTLED` (deferred/file),
  `ON_NET_POSITION_SETTLED` (P1), and `ON_INTERNAL_BOOK_POST`; `ON_CSM_ACCEPTED` and waiting for
  egress confirmation are rejected (`message-flow:1003`).
- `FINAL` writes `settlement_finality_records` and sets `payment.finality_at` through a
  payment-lifecycle port (`message-flow:999`); settlement freezes a profile snapshot per attempt
  (`message-flow:1005`).
- `DELIVERED`/`CONFIRMED`, a technical receipt, and ISO `ACSC` are not an authority for finality;
  failed delivery does not un-settle a payment (`message-flow:370-374`; ownership blueprint:42).
- A return after finality is a new opposite-direction payment. A ledger reversal is only an
  internal booking correction before finality (`message-flow:388-390`).

### What is still missing from sources

The repository does not define the columns/constraints of `settlement_finality_records`, the exact
`finality_rule` catalog shape/versioning, a profile-snapshot schema, the authoritative clock/evidence
for `finality_at`, or the late/conflicting-evidence algorithm. No receipt can fill those gaps.

### Source-compatible options (none accepted)

1. **Settlement record plus payment projection (recommended).** Settlement persists the authoritative,
   profile-snapshot-linked finality record, then uses a narrow payment-lifecycle port to project
   `payment.finality_at`. This directly follows `message-flow:999,1005`, preserves one-writer-per-
   schema, and makes the history/read model a projection rather than authority.
2. **Settlement record only, with a read projection.** Keep legal finality exclusively in settlement
   and expose it through a settlement-owned/read-model projection. This can preserve ownership but
   needs a source-backed answer for the blueprint's explicit `payment.finality_at` projection.
3. **History marker as projection only.** Retain `payment_status_history.is_final` as a downstream
   marker written only after a settlement authority event. This needs an explicit source decision on
   its relation to `settlement_finality_records`; it cannot replace the settlement record.

Recommendation: option 1, conditional on a source-backed catalog, immutable snapshot and record
shape. It is the only option stated directly by the authoritative blueprint. It is not accepted by
this packet.

## Decision C — LedgerPort `RESERVE` / `POST` / `RELEASE`

### Source-backed facts

- `LedgerPort` is settlement's only money path; settlement has no `ledger.*` write grant. The port
  is `reserve/post/release/reverse` (`message-flow:231-234,366,1001`; ownership blueprint:40,79-80).
- `reserve(account, amount)` obtains a row lock, checks available liquidity, moves available to
  reserved and returns `reservationId | INSUFFICIENT`; the source identifies a RESERVE journal entry.
- `post` creates a balanced POST entry, debits debtor available/reserved and credits creditor
  available. `release(reservationId)` is a pre-finality cancel. `reverse(entryId, reason)` is a
  pre-finality internal-booking correction only (`message-flow:1001`).
- Existing implemented DDL protects a one-currency, balanced, append-only journal and structural
  reversal links. It does not create a reservation runtime model (`planning/epics/EPIC-32-ledger-core.md`
  Story 32.2).

### Facts still missing — no implementation authority

| Contract question | Status |
|---|---|
| `reservationId` | Source suggests the RESERVE journal entry identity, but no durable reservation lifecycle/schema is specified. |
| Account locking / insufficient result | Defined: row lock; `available ≥ amount`; return `INSUFFICIENT`, not an invented exception. |
| Available/reserved movement | Defined for RESERVE and POST at a high level; no complete transaction/constraint design is supplied. |
| POST and RELEASE journal lines | POST is described as balanced; RESERVE/RELEASE line shape is not. A single-account available↔reserved move does not by itself specify balanced `journal_lines`. |
| Double `post()` / `release()` prevention | Missing. Current `entry_status='POSTED'` cannot distinguish a fresh reserve from a consumed one. |
| New table/columns | Missing. Do not create `ledger.reservations` or invent journal-entry fields. |
| Business return | Never `LedgerPort.reverse`; it is a new opposite-direction payment after finality. |

No option is accepted. The required decision is a source-backed reservation lifecycle and the
reserve/release journal-line semantics, including a no-double-consumption invariant. Until then,
EPIC-32 Story 32.2 remains `CAPABILITY-BLOCKED` and no partial port is safe.

## Decision D — physical producer ownership of `payment.received`

### Observed mismatch

The authoritative topic catalog names `ingress` as the producer-owner and contract owner of
`payment.received` (`message-flow:315-319`). The current physical outbox writer is
`PaymentCreationWriter` under `modules.paymentlifecycle`, which writes the `payment` aggregate,
history and `payment.outbox_events`. The current source flow itself shows one transaction inserting
the payment and `outbox(payment.received)` (`message-flow:62-68`).

### Consequences and options (none selected)

1. **Retain the physical writer temporarily and document the mismatch.** It avoids a new cross-schema
   write and preserves the already-tested atomic payment/outbox transaction, but leaves catalog
   ownership inconsistent.
2. **Move the physical producer to ingress.** This needs a source-backed command/event handoff that
   lets ingress own its outbox without writing `payment.*` directly; ingress is forbidden from direct
   payment/settlement/ledger/egress writes (`message-flow:289`).
3. **Change the topic-owner catalogue.** This changes the frozen producer-owns-topic source and
   requires a superseding ADR/source decision, not a code-only correction.

No move was made in this program. Moving an outbox write across the current module/schema boundary
would either violate one-writer-per-schema or require a new authority/hand-off contract. The mismatch
remains an open decision, not a reason to introduce a cross-schema write.
