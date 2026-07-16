---
name: sepa-nexus-flyway-safe-change
description: Use when writing a new Flyway migration, changing a schema, adding a constraint or index, changing a column, doing a backfill, or changing grants/RLS in this repo — the impact-analysis checklist and Testcontainers-first verification a migration needs before it's safe to add.
---
# Flyway safe-change discipline

Migrations live under `backend/src/main/resources/db/migration/<schema>/V<N>__<name>.sql` (see existing `iso/`, `payment/`, `reference_data/`, `signature/` subdirectories plus root `V1__roles.sql`). Flyway migrations are **append-only** — never edit an already-applied migration (see `infra/AGENTS.md`); a correction is always a new, higher-numbered migration.

## Before writing the migration: fill in the impact analysis

```markdown
## Migration impact

- Schemat/właściciel:
- Tabele:
- Obecna najwyższa migracja:
- Czy migracja była już stosowana:
- Przewidywany lock:
- Ryzyko wielkości tabeli:
- Backward compatibility:
- Expand/contract:
- RLS/grants impact:
- Forward-fix:
- Fresh DB verification:
- Upgrade verification:
```

Details per row: `references/expand-contract.md` (backward compatibility / expand-contract), `references/postgres-lock-analysis.md` (predicting lock severity by DDL shape), `references/forward-fix.md` (what "forward-fix" means here and why rollback migrations aren't the mechanism).

## Hard prohibitions

- Never edit an already-applied migration — see `references/append-only-migrations.md`.
- Never `DROP`/`TRUNCATE` without an explicit, recorded decision (not a default cleanup step).
- Never add a large `NOT NULL DEFAULT` column without lock analysis first — see `references/postgres-lock-analysis.md`.
- Never create an index without a stated query pattern it serves — see `references/concurrent-indexes.md`.
- Never use `CREATE INDEX CONCURRENTLY` inside a transactional Flyway migration (it cannot run inside a transaction block; Flyway wraps each migration in one by default) — see `references/concurrent-indexes.md` for the split-migration pattern instead.
- Never use `FLOAT`/`DOUBLE PRECISION` for money — `numeric` with explicit precision/scale only (see `sepa-nexus-payments-data-integrity` skill).
- Never run an automatic destructive migration (data-loss DDL applied without a preceding, explicitly-reviewed decision).

## Testcontainers-first migration verification

Every new migration is checked on at least:

1. an empty PostgreSQL Testcontainers instance (migrations apply cleanly from zero);
2. a Testcontainers instance seeded to the *previous* migration version, then upgraded (the actual upgrade path, not just a fresh-apply);
3. optionally, the long-lived local Compose database as *additional* real-upgrade-history evidence — never a substitute for 1/2.

Full pattern and Testcontainers snippets: `references/upgrade-verification.md`.

## Constraint-addition pattern for existing large tables

Add `NOT VALID` first, validate separately, to avoid a table-wide lock for the duration of validation — see `references/constraints-not-valid.md`.

## References

- `references/append-only-migrations.md`
- `references/expand-contract.md`
- `references/postgres-lock-analysis.md`
- `references/constraints-not-valid.md`
- `references/concurrent-indexes.md`
- `references/forward-fix.md`
- `references/upgrade-verification.md`
