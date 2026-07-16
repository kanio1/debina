# Append-only migrations

Flyway computes a checksum of every applied migration and refuses to start if an already-applied file's content changed underneath it (`FlywayValidateException`). This is not just a Flyway implementation detail this repo happens to rely on — it's the actual mechanism that makes "what ran in production" a fact, not a guess.

## What counts as "already applied"

Any migration whose version number is `<=` the highest version recorded in `flyway_schema_history` on any environment that matters (including a teammate's long-lived local Compose database, and CI history if migrations are re-run there). When in doubt, treat a migration as applied and write a new one.

## What to do instead of editing

1. **Typo/formatting only, not yet applied anywhere**: safe to edit directly, but check `git log` on the file first — if it was committed more than a session ago, treat it as applied.
2. **Applied, needs a behavior change**: write a new migration (`V<N+1>__...sql`) that performs the correction — `ALTER TABLE ... ADD CONSTRAINT`, a new `UPDATE` backfill, a follow-up `CREATE POLICY`, etc.
3. **Applied, was simply wrong (e.g. wrong column type)**: still a new migration — `ALTER COLUMN ... TYPE ...` with an explicit `USING` cast, not a rewrite of the original `CREATE TABLE`.

## Why this matters more here than in a typical project

Evidence/audit tables (`evidence.*`, `audit.*`) and the ledger (`ledger.*`, once it exists) are themselves append-only by architecture (see `sepa-nexus-payments-data-integrity` skill's `append-only-evidence.md`/`ledger-invariants.md`). A migration history that isn't itself append-only would be an inconsistency between how the project treats its own schema history and how it treats the data inside that schema — the same discipline applies at both levels.
