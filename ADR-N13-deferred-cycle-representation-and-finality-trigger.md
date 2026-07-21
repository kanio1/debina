# ADR-N13 — Deferred-cycle representation and finality trigger

## Status

Accepted technical decision for Wave 5; it does not supersede any frozen ADR.

## Context

The authoritative blueprint §4.6 defines `settlement_cycles`, `settlement_items`
and `settlement_positions`, its G6 lock rule, and one-statement netting. Section
4.11 binds the existing typed `NET_DEFERRED` + `ISOLATED_SUBACCOUNT` strategy to
cycle assignment, then makes `ON_CYCLE_SETTLED` finality executable only from a
real cycle-settled fact. Current V32 already owns immutable finality snapshots and
records in `settlement`, but it has no cycle representation or per-item stable
source identity. ADR-N11 applies only to gross-instant coordination and cannot be
generalised to this deferred flow.

## Decision matrix

| Criterion | A: use a cycle UUID as every finality source | B: add immutable cycle-item identity | C: new Kafka contract |
|---|---|---|---|
| Correctness | Fails: V32 allows one source ID only | Supports one authority fact per payment | Needs an absent contract |
| Atomicity/failure recovery | Ambiguous for multi-payment cycles | Durable SETTLED fact; idempotent per-item recovery | Adds broker crash windows |
| Security/least privilege | No extra privilege | settlement-only tables and role | New producer/consumer permissions |
| Ownership | Technically incomplete | settlement owns all new state | Requires event owner/catalog row |
| Migration safety | Small but unusable | Additive V51 | New external contract |
| Operability/observability | Cannot identify affected item | Typed retry/replay per immutable item | More operational surface |
| Complexity/reversibility | Low but invalid | Small, source-compatible extension | Disproportionate |
| Testability | Cannot prove per-payment replay | G6, netting, finality and recovery are testable | Kafka proof required without authority |
| Existing-project fit | Conflicts with V32 unique source key | Reuses V32 authority model | Violates ADR-N8 source boundary |

Choose B. V51 adds a UUID technical identity to each immutable cycle item and a
minimal `deferred_cycle_attempts`/event lineage so a settled item can be the
unique authoritative source for its payment. The pre-existing
`settlement_attempts` representation is ADR-N11 gross-instant evidence: it is
RLS-protected, restricted to `FINAL`/`REJECTED`, and is written only through its
approved SECURITY DEFINER path. Extending or writing it would alter that frozen
transaction/security boundary. The deferred representation remains settlement
owned and does not add a business status, calendar policy, profile mapping, CSM
semantics or queue policy.

## Cycle mechanics

- The cycle FSM is `OPEN → CLOSING → CLOSED → NETTED → SETTLED`; no backward
  transition is legal. `RECONCILED` remains outside this slice.
- Assignment locks the named cycle row (`SELECT … FOR UPDATE`) and accepts only
  `OPEN`; it never creates or selects a next cycle. A non-open target returns a
  typed fail-closed outcome.
- Closing is deliberately two commands: `OPEN → CLOSING`, then `CLOSING → CLOSED`.
  The row lock drains an already-running assignment before close progresses.
- Netting locks a `CLOSED` cycle and performs one `INSERT … SELECT` aggregation
  over immutable items. Replaying a completed net is idempotent; a changed cycle
  cannot be netted.
- `SETTLED` is the real trigger fact. The finality writer verifies both SETTLED
  state and item membership before recording `ON_CYCLE_SETTLED`. A failure after
  the durable SETTLED transition leaves no invented finality and is retried by the
  same idempotent settlement command; it is observable as an explicit command
  failure, not silent eventual consistency.

## Consequences

- Settlement writes only `settlement.*`; no ledger or payment DML/grant is added.
- Existing `SettlementFinalityService` retains V32's immutable record, replay and
  conflict behavior and projects only via `PaymentFinalityPort`.
- No Kafka topic is created: ADR-N8 contains no applicable complete contract.
- Automatic cycle creation/rollover, cutoff policy, calendar behavior and the
  profile snapshot mapping in EPIC-35 Story 35.3 remain deliberately absent.
