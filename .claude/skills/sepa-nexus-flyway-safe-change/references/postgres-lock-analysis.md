# Predicting lock severity before writing the migration

PostgreSQL DDL takes an `ACCESS EXCLUSIVE` lock for most `ALTER TABLE` forms by default — this blocks all reads and writes on the table for the duration. On a table with meaningful row count or concurrent traffic, this is the single most common cause of a "simple migration" turning into an incident.

## Operations that need explicit thought

| Operation | Lock behavior | Safer alternative |
|---|---|---|
| `ADD COLUMN ... DEFAULT <constant>` (PG 11+) | Metadata-only, fast, no table rewrite — safe as-is | — |
| `ADD COLUMN ... NOT NULL DEFAULT <constant>` | Same as above in PG 11+ (constant default) | — |
| `ADD COLUMN ... NOT NULL` without a default, on existing rows | Requires a value for every existing row — either backfill first (expand/contract) or use a default | Add nullable, backfill, then `SET NOT NULL` in a later migration (validated via `NOT VALID` pattern, see `constraints-not-valid.md`) |
| `ALTER COLUMN ... TYPE` | Full table rewrite in most cases (exceptions: some compatible type changes) — `ACCESS EXCLUSIVE` for the duration | Expand/contract: new column, backfill, swap, drop old |
| `ADD CONSTRAINT ... CHECK (...)` | Full table scan to validate, `ACCESS EXCLUSIVE`-ish for that scan unless split | Add `NOT VALID`, validate separately — see `constraints-not-valid.md` |
| `CREATE INDEX` | `SHARE` lock — blocks writes, not reads, for the build duration | `CREATE INDEX CONCURRENTLY` outside a transaction — see `concurrent-indexes.md` |
| `DROP COLUMN` | Metadata-only (PG marks it dropped, doesn't rewrite immediately) but still needs `ACCESS EXCLUSIVE` briefly | Usually fine at low traffic; still note it in the impact analysis |

## Table-size risk factor

The lock's *duration* (not just its type) is what matters for a table's write availability. A metadata-only `ACCESS EXCLUSIVE` lock lasting milliseconds is very different from one triggered by a full table rewrite lasting minutes on a large table. State the current approximate row count for the target table in the migration impact analysis, and flag explicitly if a rewrite-triggering operation would run against a table expected to be large by the time this migration ships (payment/ledger tables grow monotonically; reference-data tables usually don't).

## `lock_timeout` as a safety net

For any DDL against a table with concurrent traffic, prefer running with a short `lock_timeout` set so the migration fails fast and retries rather than queueing behind (and blocking) application traffic indefinitely:

```sql
SET lock_timeout = '2s';
ALTER TABLE payment.payments ADD COLUMN ...;
```

Document this decision when used — it changes the failure mode from "migration blocks forever" to "migration fails, needs a retry," which the deploy process must actually handle.
