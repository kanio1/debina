# Testcontainers-first migration verification

## The two required checks

### 1. Fresh database

```java
@Testcontainers
class MigrationFreshDatabaseTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    // Spring Boot auto-runs Flyway against the container on context startup via
    // @DynamicPropertySource wiring the JDBC URL -- migrations apply from V1 forward.

    @Test
    void allMigrationsApplyCleanlyFromEmpty() {
        // assert flyway_schema_history has no failed rows, highest version matches
        // the newest migration file, target schema/table/column exists as expected
    }
}
```

This proves the migration is syntactically correct and self-consistent with everything before it, starting from nothing — the same path a brand-new environment takes.

### 2. Upgrade path (previous version → new version)

```java
@Testcontainers
class MigrationUpgradePathTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Test
    void migrationAppliesOnTopOfPriorSchemaWithExistingData() {
        // 1. run Flyway target=<previous version>
        // 2. insert representative existing data (the shape that would already be
        //    in production before this migration ships)
        // 3. run Flyway migrate() to bring it to <new version>
        // 4. assert the migration succeeded AND the pre-existing data survived /
        //    was correctly transformed (not just that no exception was thrown)
    }
}
```

This is the check that actually exercises lock behavior, backfill correctness, and expand/contract safety against non-empty state — a fresh-database-only test can pass while an upgrade against real data would fail (e.g. a `NOT NULL` addition with no default, fine on an empty table, breaks immediately against existing rows).

## Optional third check: long-lived local Compose database

May be used as *additional* evidence that a migration behaves correctly against a database with real accumulated upgrade history (many migrations, real usage patterns) — but this is never a substitute for either Testcontainers check above, and a migration verified only against the Compose database is not considered verified by this skill's standard. See `infra/AGENTS.md`'s Compose-vs-Testcontainers section and the `sepa-nexus-database-testing` skill.

## What to report after verification

State explicitly, for the migration under review: fresh-DB result (PASS/FAIL), upgrade-path result (PASS/FAIL), and whether the long-lived Compose database was additionally checked. A migration is not "done" until both Testcontainers checks pass — see `sepa-nexus-database-review` skill's migration-review-checklist for the reviewer-side version of this same requirement.
