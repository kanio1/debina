# Fresh-database migration test

Proves a migration (and everything before it) applies cleanly from zero — the path a brand-new environment takes. See `sepa-nexus-flyway-safe-change` skill's `upgrade-verification.md` for the full pattern; this file is the test-writing checklist specifically.

## What to assert, beyond "no exception thrown"

- `flyway_schema_history` has zero `success = false` rows.
- The highest applied version matches the newest migration file on disk (catches a migration that silently didn't get picked up by Flyway's file-scanning location config).
- The specific new table/column/constraint/index/policy exists with the exact expected shape — query `information_schema` or `pg_catalog` directly, don't just trust that "Flyway didn't throw" implies the DDL did what was intended.

```java
try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
     ResultSet rs = statement.executeQuery(
         "SELECT column_name, data_type, numeric_precision, numeric_scale " +
         "FROM information_schema.columns WHERE table_schema='payment' AND table_name='payments'")) {
    // assert the exact expected columns/types, not just "table exists"
}
```

## Common false-positive this catches

A migration file with a typo in its version-number prefix that causes Flyway to silently skip it (wrong naming convention, e.g. `V10_x__foo.sql` instead of `V10__foo.sql`) — the test suite might still pass if nothing else depended on that specific migration's effect, while the migration itself never actually ran. Asserting the exact expected schema shape (not just "Flyway didn't throw") catches this.
