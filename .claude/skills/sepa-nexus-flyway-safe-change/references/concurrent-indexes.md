# Indexes: query-pattern justification and `CONCURRENTLY`

## Every new index needs a stated query pattern

Before adding an index, name the actual query (existing or about to be added) it serves — a repository method, a WHERE/ORDER BY/JOIN shape. An index added speculatively ("this might help later") is dead weight: it costs write throughput and storage on every insert/update to the table, with no read benefit until (if ever) a matching query exists. State the query pattern in the PR/commit description or the migration impact analysis, not just in your own head.

```sql
-- Serves PaymentRepository.findByTenantAndStatus(tenantId, status),
-- called from the operator worklist screen's list endpoint.
CREATE INDEX idx_payments_tenant_status ON payment.payments (tenant_id, status);
```

## `CREATE INDEX CONCURRENTLY` cannot run inside a Flyway transaction

Flyway wraps each migration in a transaction by default, and PostgreSQL forbids `CREATE INDEX CONCURRENTLY` inside a transaction block (`ERROR: CREATE INDEX CONCURRENTLY cannot run inside a transaction block`). Two ways to reconcile this:

1. **Small/low-traffic table**: skip `CONCURRENTLY`, accept the brief `SHARE` lock from a plain `CREATE INDEX`. State this reasoning in the impact analysis.
2. **Large/high-traffic table**: mark the specific migration non-transactional so Flyway doesn't wrap it:
   ```sql
   -- V<N>__add_payments_status_index.sql
   -- Flyway: this migration must NOT run inside a transaction (CONCURRENTLY requirement)
   CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_tenant_status
     ON payment.payments (tenant_id, status);
   ```
   Configure this migration's non-transactional behavior via Flyway's per-migration mechanism appropriate to the Flyway version in use (check `backend/pom.xml`'s Flyway version before assuming a specific config key name) — never by disabling transactional wrapping globally, which would remove atomicity from every other migration too.

## `CONCURRENTLY` failure mode

If a `CONCURRENTLY` index build fails partway (e.g. the migration process is killed), PostgreSQL leaves an **invalid** index behind rather than rolling back cleanly (this is the tradeoff for not taking a blocking lock). A follow-up migration must `DROP INDEX CONCURRENTLY IF EXISTS <name>` and retry, or confirm via `\d <table>` / `pg_index.indisvalid` that the index is valid before assuming the migration fully succeeded.
