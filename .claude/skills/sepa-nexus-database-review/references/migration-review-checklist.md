# Migration review checklist

- [ ] Migration is a new file, not an edit to an already-applied one (`git log` on the file — if it predates this change set and is referenced in `flyway_schema_history` anywhere that matters, it's applied; see `sepa-nexus-flyway-safe-change` skill's `append-only-migrations.md`).
- [ ] Version number is the next available in its schema directory, no gaps/collisions with a concurrently-authored migration (check `git diff` against `main`/base branch for other new migrations in the same schema directory).
- [ ] Migration impact analysis (schema/owner, tables, current highest migration, already-applied?, predicted lock, table-size risk, backward compatibility, expand/contract, RLS/grants impact, forward-fix, fresh-DB verification, upgrade verification) is present and each row is actually answered, not left as a template placeholder.
- [ ] Lock severity matches the DDL shape used — a large `ALTER COLUMN ... TYPE` or `ADD COLUMN ... NOT NULL` without a constant default on a table with real row count needs the expand/contract or lock-analysis treatment, not a plain single-step migration (`sepa-nexus-flyway-safe-change` skill's `postgres-lock-analysis.md`).
- [ ] New constraints on existing tables with real row count use `NOT VALID` + separate `VALIDATE CONSTRAINT`, not a combined single-statement add (`constraints-not-valid.md`).
- [ ] New indexes state their query pattern; `CONCURRENTLY` used correctly if the target table has real traffic, and the migration is correctly marked non-transactional if so (`concurrent-indexes.md`).
- [ ] `PUBLIC` explicitly revoked on any new schema/table (`postgres-rls-migration` skill's `grant-matrix.md`).
- [ ] No `DROP`/`TRUNCATE` without an explicit, separately recorded decision — never a default "cleanup" step folded into an unrelated migration.
- [ ] No `FLOAT`/`DOUBLE PRECISION` used for any money-shaped column.
- [ ] Fresh-database Testcontainers test exists and passes.
- [ ] Upgrade-path Testcontainers test (previous version → this version, against representative pre-existing data) exists and passes — not just the fresh-DB test.
- [ ] If this migration is itself a forward-fix for an earlier mistake, the review confirms the earlier migration was left untouched and the fix is minimal and correctly targeted (`forward-fix.md`).
