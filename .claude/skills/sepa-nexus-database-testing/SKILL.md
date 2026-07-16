---
name: sepa-nexus-database-testing
description: Use when writing or reviewing a PostgreSQL/Kafka integration test for this backend — how to structure a Testcontainers-first test, which matrix of cases a given kind of change (DDL, grants, RLS, idempotency, append-only, indexes, SECURITY DEFINER, correlation, Kafka) requires, and how to avoid a vacuous test.
---
# Database testing (Testcontainers-first)

## Core rule

Automated integration tests never use the long-lived local Compose database (`infra_postgres_1`) as a shared fixture. Every test method/class gets its own isolated PostgreSQL/Kafka Testcontainers instance:

```text
test method/class
  → isolated Testcontainers PostgreSQL/Kafka
  → Flyway applies migrations
  → test data created by the test itself
  → assertions
  → container disposed
```

The long-lived Compose database (see `infra/AGENTS.md`) is for manual development, debugging, UI work, and — only as *additional*, never sole, evidence — confirming a migration's real-upgrade-history behavior.

Never use `act` to run these tests or as a substitute for either Testcontainers check — see root/`backend/AGENTS.md`.

## Test matrix by change kind

| Change kind | Required tests |
|---|---|
| DDL/schema | fresh Testcontainers DB + upgrade-path Testcontainers DB (see `sepa-nexus-flyway-safe-change` skill) |
| Grants | positive (owning role can do the intended operation) + negative (foreign role rejected, `42501`) |
| RLS | same-tenant + cross-tenant + empty-GUC + invalid-GUC + `WITH CHECK` (writable tables) — see `postgres-rls-migration` skill |
| Idempotency | sequential duplicate request + **concurrent** duplicate request (real race, not just two sequential calls) |
| Append-only | `INSERT` succeeds, `UPDATE`/`DELETE` rejected at the grant level |
| Index/query optimization | `EXPLAIN` (or `EXPLAIN ANALYZE`) evidence against representative Testcontainers data, tied to the stated query pattern (see `sepa-nexus-flyway-safe-change` skill's `concurrent-indexes.md`) |
| `SECURITY DEFINER` | privilege matrix (foreign role rejected) + `search_path` hijacking attempt + commit path + rollback path — see `postgres-rls-migration` skill's `security-definer.md` |
| Correlation | `MATCHED` + `AMBIGUOUS` + `ORPHANED` + duplicate + out-of-order — see `sepa-nexus-payments-data-integrity` skill's `correlation-integrity.md` |
| Kafka flow | real Kafka Testcontainer where broker semantics (ordering, redelivery, partition assignment) actually matter to the behavior under test |

Never mock PostgreSQL or Kafka when the test's actual purpose is to verify one of their real properties (a lock, a constraint, RLS policy evaluation, broker redelivery semantics) — a mock that returns "success" proves nothing about whether the real system would.

## Non-vacuous tests

Every test carries a real risk of passing vacuously (asserting something trivially true regardless of whether the code under test works) unless it includes a mutation/negative check. See `references/non-vacuous-tests.md` for the pattern and this repo's own mutation-proof convention (already used for `EPIC-27` Story 27.1 — see the implementation session for a worked example).

## References

- `references/testcontainers-postgres.md`
- `references/testcontainers-kafka.md`
- `references/migration-fresh-database.md`
- `references/migration-upgrade-path.md`
- `references/grants-and-writer-isolation.md`
- `references/rls-negative-tests.md`
- `references/concurrency-and-idempotency.md`
- `references/non-vacuous-tests.md`
