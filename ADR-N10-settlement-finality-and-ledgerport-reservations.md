# ADR-N10 — Settlement Finality Authority and LedgerPort Reservations

## Status

Frozen

## Context

The repository freezes finality as explicit and profile-configured, keeps egress transport-only,
and requires all money movement to pass through `LedgerPort`. The completed safety correction removed
the erroneous derivation of `payment.payment_status_history.is_final` from business-FSM terminality,
but deliberately did not invent the missing finality authority or reservation lifecycle.

The product owner has now approved the synthetic-laboratory authority, record shapes, reservation
model, journal components, idempotency rules, and physical producer ownership boundary below.

## Decision

### Finality authority

`[FREEZE]` `settlement` owns authoritative finality. `reference_data.finality_rules` is a versioned
catalog. Each settlement attempt freezes an immutable `settlement_profile_snapshot`, and
`settlement.settlement_finality_records` is the append-only source of truth.

A narrow `PaymentFinalityPort` projects an authoritative result to
`payment.payments.finality_at` and its finality-record reference. `payment_status_history.is_final`
is deprecated compatibility data, not an authority; business-status transitions never write it as
`true`.

The finality record contains at least `id`, `payment_id`, `settlement_attempt_id`,
`profile_snapshot_id`, `finality_rule_code`, `finality_rule_version`, `source_type`, `source_id`,
`source_occurred_at`, `finality_at`, `evidence_hash`, and `recorded_at`.

One authoritative finality record exists per payment. Source identity and evidence hash make replay
idempotent; identical evidence returns the existing result. Conflicting evidence fails closed and
never overwrites finality. `finality_at` comes from the authoritative settlement or ledger event,
not receipt time. Snapshots and records are immutable, and the payment projection is idempotent.

The approved laboratory rules are `ON_LEDGER_POST` (synthetic gross-instant),
`ON_CYCLE_SETTLED` (synthetic deferred), `ON_NET_POSITION_SETTLED` (P1), and
`ON_INTERNAL_BOOK_POST` (internal book). `ON_CSM_ACCEPTED`, egress delivery, receipts, ISO status,
and transport confirmation never establish or remove finality. Transport failure never reverses or
unsets it.

### LedgerPort reservation lifecycle

`[FREEZE]` `ledger.reservations` is the durable reservation model with `id`,
`settlement_attempt_id`, `payment_id`, `debtor_account_id`, `amount_minor`, `currency`, `state`
(`ACTIVE | POSTED | RELEASED`), `reserve_entry_id`, `terminal_entry_id`, `terminal_command_id`,
`version`, `created_at`, and `completed_at`.

There is one reservation per settlement attempt, its amount is positive, its currency equals the
liquidity-account currency, and reserve/terminal entries are unique. A reservation may transition
only once from `ACTIVE` to `POSTED` or `RELEASED`; it can never be both. Journal lines remain
append-only and settlement retains no direct ledger-table write grant.

Journal lines include a balance component of `AVAILABLE` or `RESERVED`; every journal entry balances
to zero per currency. `RESERVE` moves debtor `AVAILABLE -amount` and `RESERVED +amount`; `RELEASE`
moves debtor `RESERVED -amount` and `AVAILABLE +amount`; `POST` moves debtor `RESERVED -amount` and
creditor `AVAILABLE +amount`.

`reserve(settlementAttemptId, debtorAccountId, amount)` returns
`Reserved(reservationId, reserveEntryId)` or `InsufficientLiquidity`. Insufficient liquidity is a
typed result, never an exception. `post(reservationId, creditorAccountId, commandId)` and
`release(reservationId, commandId)` lock the reservation, lock affected accounts in deterministic
order, create the journal effect, update balances, and transition reservation state atomically in
one ledger transaction. Same command replay returns the prior result; a different command against
an already terminal reservation is rejected and cannot create a second money effect.

`reverse()` remains a separate internal-booking correction allowed only before finality. A business
return after finality is a new opposite-direction payment and never a ledger reversal.

### Ownership boundary

Egress retains zero write access to `payment.*`; delivery, receipt, `ACSC`, `DISPATCHED`, and
`DELIVERED` never set or unset finality. The current physical `payment.received` writer remains in
`payment-lifecycle` temporarily. Its topic-catalog mismatch is accepted technical debt requiring a
separate ADR; this decision does not move the writer or add a cross-schema write.

## Consequences

- `reference_data`, `settlement`, `payment`, and `ledger` each receive only their own schema changes;
  cross-module effects use public ports/events and do not bypass one-writer-per-schema.
- Finality and reservation operations require immutable records, database constraints, role/grant
  tests, idempotency/concurrency proofs, and PostgreSQL upgrade-path evidence.
- Existing frozen prohibition remains: egress and settlement never write ledger tables directly;
  only the ledger module's `LedgerPort` implementation writes `ledger.*`.

## Alternatives Rejected

- **Business-FSM terminality or `payment_status_history.is_final` as finality authority** — rejected:
  business status and settlement finality are independent axes.
- **Receipt, delivery, transport confirmation, ISO status, or `ON_CSM_ACCEPTED` as finality** —
  rejected: none is settlement authority.
- **An ephemeral reservation or implicit `journal_entries` state model** — rejected: it cannot
  represent the approved terminal-command idempotency and one-terminal-transition invariants.
- **Moving `payment.received` now** — rejected: it would require a separate ownership ADR and could
  create a cross-schema writer.
