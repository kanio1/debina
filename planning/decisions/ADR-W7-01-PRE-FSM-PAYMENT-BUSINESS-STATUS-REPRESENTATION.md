# ADR-W7-01 — Pre-FSM payment business-status representation

**Status:** Accepted (2026-07-21)

## Context

`sepa-nexus-message-flow-and-data-blueprint.md` §2.2b is frozen: a payment awaiting maker–checker approval has a `PENDING_APPROVAL` approval status but no business status because `payment.received` has not been released and the business FSM has not started.  The existing `payment.payments.status` column is `NOT NULL` and the Java entity always creates `RECEIVED`; retaining it for a pending approval would invent a business state contrary to the source.

## Options compared

| Option | Correctness | Ownership / atomicity | Migration safety | Testability | Decision |
|---|---|---|---|---|---|
| Make `payment.payments.status` nullable only before the FSM | Directly models the frozen distinction; payment-lifecycle remains sole owner | One payment-row transaction; no new writer | Forward-only nullable alteration, existing rows unchanged | Pending and normal paths are observable in PostgreSQL | Accepted |
| Keep `RECEIVED` for pending approvals | Creates a false business-FSM state | Superficially simple, but breaks the prefix gate | No migration | Tests would encode the wrong behavior | Rejected |
| Add a separate staged-payment aggregate/table | Could model pre-FSM, but duplicates the source-required payment row and complicates idempotency/lineage | Adds extra ownership and promotion failure windows | Larger migration / replay surface | More paths to prove | Rejected |

## Decision

`payment.payments.status` is nullable only for a pre-FSM approval-gated payment.  Existing no-approval ingress continues to create `RECEIVED`; a pending approval never receives a fake lifecycle status.  `payment-lifecycle` continues to own the row, lifecycle history is written only when the FSM starts, and approval status remains a separate `payment.payment_approvals.status` axis.

## Consequences

- This does not create an externally visible business status or alter finality, ISO, transport, receipt, or reconciliation axes.
- A migration and Testcontainers fresh/upgrade proof must show existing payments remain compatible.
- Read models must represent an absent business status honestly for pending approvals.
