# `USING` vs `WITH CHECK`

`USING` filters rows an existing operation is *allowed to see/target* (`SELECT`, and the pre-image of `UPDATE`/`DELETE`). `WITH CHECK` constrains what a new or post-update row is *allowed to become* (`INSERT`, and the post-image of `UPDATE`). They are independent clauses — a policy with only `USING` lets a writer `INSERT`/`UPDATE` a row into a different tenant's partition undetected, because nothing validates the row *after* the write.

## Read-only tables

`USING` alone is correct — no app-role `INSERT`/`UPDATE`/`DELETE` ever happens (e.g. a table only written by a Kafka consumer running under a different, non-RLS-bound role).

```sql
CREATE POLICY tenant_isolation ON iso.iso_messages
  USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

## Writable tables

Both clauses required, and for `UPDATE`, they must agree — otherwise a cross-tenant migration-via-update is possible (read one tenant's row, `USING` passes, then `UPDATE ... SET tenant_id = other_tenant` succeeds because nothing checked the *new* value).

```sql
ALTER TABLE payment.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment.payments FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payment.payments
  USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

## Common mistake this catches

A policy written once, before a table gained an `UPDATE` endpoint, and never revisited. If a table starts read-only and later gains a write path, the migration that adds the write path must also add `WITH CHECK` — do not assume the original `CREATE POLICY` still covers it. Grep for `CREATE POLICY` on the target table across all prior migrations before writing a new one; `ALTER POLICY` (or a new named policy) is required, an already-applied migration is never edited (append-only rule).

## Test obligation

Every writable-table policy needs an explicit `WITH CHECK` test: attempt an `INSERT`/`UPDATE` that would place a row outside the current session's tenant, assert it is rejected (not silently redirected, not silently accepted). See `role-switching-tests.md`.
