---
name: sepa-nexus-database-review
description: Use as a second, independent pass after a database-touching change (migration, schema, query, or security change) is implemented and its own tests pass — reviews for source-fidelity, ownership, grants, RLS, data types, constraints, indexes, locks, transactions, idempotency, append-only, rollback/forward-fix, and Testcontainers evidence, then reports a PASS/CHANGES REQUIRED verdict.
---
# Database change review

This skill does not implement the change under review — it's the second phase after implementation:

```text
implementation
  → targeted direct Maven tests
  → Testcontainers integration tests
  → independent database review (this skill)
  → fixes
  → full direct Maven regression
  → done
```

## What the reviewer checks

- **Source fidelity** — does the change match what the epic/story/blueprint actually specifies, with no invented architecture? Cross-check against the story's `source:` citation.
- **Ownership** — does the change respect one-writer-per-schema (no cross-schema writes, no repository reaching into another module's tables)?
- **Grants** — positive (owning role) and negative (foreign role rejected) both present and passing? `PUBLIC` explicitly revoked on anything new?
- **RLS** — `USING`/`WITH CHECK` both present where needed, `FORCE ROW LEVEL SECURITY` set, empty-GUC/invalid-GUC behavior correct?
- **Data types** — `numeric` for money (never `float`/`double`), explicit currency, correct ISO-identifier field usage (see `sepa-nexus-payments-data-integrity` skill)?
- **Constraints** — correct, and added via the `NOT VALID` + `VALIDATE CONSTRAINT` split on any table where that matters?
- **Indexes** — each new index tied to a stated query pattern; `CONCURRENTLY` used correctly outside a transactional migration wrapper where needed?
- **Locks** — lock severity considered and stated for any DDL against a table with real row count/traffic?
- **Transactions** — atomicity boundaries correct (one business operation, one transaction); no cross-schema single-transaction write outside an accepted decision gate (see `EPIC-10`)?
- **Idempotency** — explicit key/scope, concurrent-duplicate case actually tested against real PostgreSQL, not just sequential?
- **Append-only** — evidence/audit/ledger tables have no `UPDATE`/`DELETE` grant to the app role, verified by a negative test, not just by convention?
- **Rollback/forward-fix** — if this change corrects an earlier mistake, is it a new migration (forward-fix), never an edit to an already-applied one?
- **Testcontainers evidence** — fresh-DB test + upgrade-path test both present and passing, not just tested against the long-lived local Compose database?
- **Non-vacuous tests** — did the implementer's own tests survive a mutation check (see `sepa-nexus-database-testing` skill), or is there a real risk a negative test passes vacuously?

Full checklists: `references/migration-review-checklist.md`, `references/schema-review-checklist.md`, `references/query-review-checklist.md`, `references/security-review-checklist.md`.

## No production DB change to review

If the change under review genuinely touches no DDL/DML (e.g. a pure extraction/parsing story with no database write), say so explicitly — `NO DATABASE DDL/DML CHANGE` — but still review anything data-integrity-adjacent it does touch (identifier handling, whether a database dependency was added where none was needed, correlation-boundary discipline) rather than skipping the review entirely.

## Required output format

```markdown
# Database review

## Verdict
PASS / CHANGES REQUIRED

## Blocking findings

## Non-blocking findings

## Security findings

## Migration findings

## Testcontainers evidence

## Required fixes
```

Report `CHANGES REQUIRED` if an integration test depends on a pre-existing, manually-prepared environment (the long-lived Compose database, `infra_postgres_1`) instead of an isolated Testcontainers fixture, without an explicit, stated reason (see `infra/AGENTS.md`'s Compose-vs-Testcontainers section and `sepa-nexus-database-testing` skill) — this is treated as a blocking finding, not a style note, because it silently narrows what the test actually proves.

## References

- `references/migration-review-checklist.md`
- `references/schema-review-checklist.md`
- `references/query-review-checklist.md`
- `references/security-review-checklist.md`
