---
name: postgres-rls-migration
description: Use when writing a Flyway migration that creates or alters a table needing tenant isolation, or when wiring the GUC session variable that PostgreSQL RLS depends on.
---
# PostgreSQL RLS migration conventions

1. Every tenant-scoped table gets `tenant_id uuid NOT NULL`, `ENABLE ROW LEVEL SECURITY`, and `FORCE ROW LEVEL SECURITY`.
2. Policy pattern (copy exactly, substitute table name):
   ```sql
   ALTER TABLE payment.payments ENABLE ROW LEVEL SECURITY;
   ALTER TABLE payment.payments FORCE ROW LEVEL SECURITY;
   CREATE POLICY tenant_isolation ON payment.payments
     USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
   ```
3. **Empty-GUC-zero-rows is mandatory**: `current_setting('app.tenant_id', true)` with `true` (missing_ok) returns NULL if unset, and `tenant_id = NULL` matches zero rows — never write a policy that falls back to "show everything" when the GUC is unset.
4. The application DB role is never `BYPASSRLS` and never the migration/superuser role. Two distinct roles: a migration role (runs Flyway, owns DDL) and an app role (runs application queries, RLS-bound).
5. After any migration: run `./mvnw -f backend test -Dtest=*RlsTest` — the empty-GUC and cross-tenant negative tests must pass.
