# Upgrade-path migration test

Proves a migration is safe against *existing, non-empty* state — the check a fresh-database-only test cannot provide. See `sepa-nexus-flyway-safe-change` skill's `upgrade-verification.md` for the full rationale (lock behavior, backfill correctness, expand/contract safety only show up here).

## Shape

```java
@Test
void migrationAppliesOnTopOfPriorSchemaWithExistingData() {
    // 1. Flyway.configure()....target(MigrationVersion.parse("<previous version>")).load().migrate();
    // 2. insert representative pre-existing rows -- the shape that would already
    //    be in production before this migration ships (not empty, not trivial)
    // 3. Flyway.configure()....load().migrate();  // advances to latest
    // 4. assert: migration succeeded AND the pre-existing rows are correct
    //    post-migration (survived unchanged, or correctly transformed/backfilled
    //    -- whichever the migration is supposed to do)
}
```

## What "representative" pre-existing data means

Not just one trivial row — include the edge cases the new migration's DDL could plausibly interact badly with: a `NULL` in a column about to gain `NOT NULL`, a value at a boundary a new `CHECK` constraint enforces, a row that would collide with a new unique index, multiple tenants if the migration touches an RLS-bearing table. A test that seeds one clean row and calls it "upgrade-tested" has not actually exercised the risk this check exists for.

## Failure mode this catches that fresh-database testing cannot

`ADD COLUMN ... NOT NULL` with no default, against a table Testcontainers just created empty, always succeeds trivially (zero existing rows to fail the constraint against) — but the identical migration against a table with existing rows fails immediately. Only the upgrade-path test exercises this; a fresh-only test suite would ship this exact regression undetected.
