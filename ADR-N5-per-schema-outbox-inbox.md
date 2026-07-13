# ADR-N5 — Per-Schema Outbox/Inbox Tables + Shared Dispatcher Role

## Status

Frozen

## Context

The main blueprint states the *principle* ("module-local outbox writer, shared relay," §3.2) but never defines the *table ownership*. §4.4 defines a single, schema-flat `outbox_events` / `inbox_events` pair with no schema prefix and no row in the §3.6.2 module-ownership table. This is a direct contradiction of the one-writer-per-schema rule (§3.6.1 rule 1: "a module writes only to its own schema; cross-schema writes are an architecture violation") — a shared, unowned outbox table is exactly the kind of object the ownership contract exists to prevent. It also blocks Flyway folder-per-module layout and the SQL grant-matrix tests, both Iteration 0 deliverables, because neither can be written against an unowned table.

## Decision

`[FREEZE]` **Every module schema that publishes events owns its own `<schema>.outbox_events` table; every module schema that consumes events owns its own `<schema>.inbox_events` table.** Example: `payment.outbox_events`, `settlement.outbox_events`, `egress.outbox_events`, `reconciliation.outbox_events`, `case.outbox_events`, and a matching `<module>.inbox_events` per consumer. A single **shared-kernel dispatcher role** (`outbox_dispatcher_role`) is granted explicit `SELECT`/`UPDATE` (claim, mark-published) across every module's outbox table — but **no** grant on any domain table. The dispatcher is infrastructure (a shared-kernel mechanism, per §4.7's own distinction between RLS-protected tenant tables and ownership-protected queue tables), not a business owner; it never appears as a schema-owning row in §3.6.2.

## Consequences

- One-writer-per-schema is preserved without exception: each outbox/inbox table has exactly one writer (its owning module) and one additional narrowly-scoped reader/updater (the dispatcher).
- Flyway migrations for `outbox_events`/`inbox_events` live in each module's own migration folder, created with that module's first iteration — consistent with the "schemas created per module, per iteration" rule already frozen for domain tables.
- New mandatory SQL grant tests: a module's writer role cannot write another module's outbox table; the dispatcher role cannot write any domain table (`payments`, `journal_lines`, `outbound_artifacts`, etc.); consumer inbox tables deduplicate on `message_id`; Kafka redelivery does not duplicate a domain effect (replay-safe).
- The polling `@Scheduled` outbox dispatcher (already designed in §2.5) now iterates N per-schema tables instead of one global table — a mechanical, not architectural, change to its implementation.
- Closes R-11 from the blueprint review and B4 from the decision gate.

## Alternatives Rejected

- **One global, schema-flat `outbox_events`/`inbox_events` pair** (as currently written in §4.4) — rejected: it is an unowned table, violates rule 1 of the module's own ownership contract, and cannot pass a "module X cannot write schema Y" grant test because it belongs to no schema.
- **A dedicated `messaging` schema owned by no domain module, written by all** — rejected: same violation in a different shape; still gives every module write access to a table it does not own.
