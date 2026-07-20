# Debina finality, LedgerPort, and producer-ownership decision packet

Status: **ACCEPTED DECISION RECORD — implemented through ADR-N9 and ADR-N10; this packet remains
supporting analysis, not an ADR.**

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

**Accepted:** the synthetic learning-lab column, frozen by [ADR-N9](../../ADR-N9-synthetic-credit-transfer-learning-platform-scope.md).
The full-hub column remains a separate product-scope ADR gate.

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

### Accepted authority

**Accepted by ADR-N10:** option 1, settlement record plus payment projection. ADR-N10 defines the
catalog, immutable snapshot and record fields, one-record-per-payment/idempotency/conflict rules,
authoritative event time, and the prohibition on delivery/receipt/ISO-derived finality.

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

### Accepted authority

**Accepted by ADR-N10:** `ledger.reservations`, its fields and state machine, AVAILABLE/RESERVED
journal components, zero-sum RESERVE/RELEASE/POST lines, deterministic row locking, transactional
terminal transitions, and command-id idempotency. `reverse()` remains a separate, pre-finality
internal correction; a business return remains a new opposite-direction payment.

## Decision D — physical producer ownership of `payment.received`

### Observed mismatch

The authoritative topic catalog names `ingress` as the producer-owner and contract owner of
`payment.received` (`message-flow:315-319`). The current physical outbox writer is
`PaymentCreationWriter` under `modules.paymentlifecycle`, which writes the `payment` aggregate,
history and `payment.outbox_events`. The current source flow itself shows one transaction inserting
the payment and `outbox(payment.received)` (`message-flow:62-68`).

**Accepted technical debt:** ADR-N9/N10 retain the physical writer temporarily in
`payment-lifecycle`. No writer move or cross-schema write is part of this program. A separate ADR is
required before changing the frozen topic-catalog ownership.
