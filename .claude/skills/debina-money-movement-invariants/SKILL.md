---
name: debina-money-movement-invariants
description: Use for LedgerPort, liquidity accounts, reservations, reserve/post/release/reverse, journal entries or lines, AVAILABLE/RESERVED balances, money movement, or settlement strategy; not status-only changes, read-only reporting, or unrelated migrations.
---
# Money movement invariants

All money movement uses `LedgerPort`; only ledger-owned code writes `ledger.*`, journals are append-only, each entry balances to zero per currency, currency/accounts/amounts/signs are explicit, and settlement never performs direct ledger DML.

A reservation is positive, one per settlement attempt, and transitions exactly once: `ACTIVE → POSTED` or `ACTIVE → RELEASED`. Reserve moves `AVAILABLE → RESERVED`; release moves `RESERVED → AVAILABLE`; post moves debtor `RESERVED` to creditor `AVAILABLE`. Insufficiency is typed with no reservation/effect. Same command replay returns its original effect; a different terminal command fails closed. Prevent double post, post-after-release, release-after-post and over-reservation through deterministic account lock order.

Ledger posting and settlement finality are separate. Only approved policy may make a post authoritative; `finality_at` uses authoritative event time. A return after finality is a new opposite-direction payment. Do not implement `reverse()` until its command, authorization, narrow pre-finality read, idempotency, accounting evidence and conflict behavior are source-defined.

Require PostgreSQL 18 tests for fresh/upgrade migration, real role, journal balance and immutability, rollback, concurrent reserve/post/release, replay/conflict, lock order, mutation proofs and no duplicate effect. Read [reservation state machine](references/reservation-state-machine.md), [journal matrix](references/journal-effect-matrix.md), and [concurrency matrix](references/concurrency-proof-matrix.md).
