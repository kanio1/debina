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
   For any table that also accepts `INSERT`/`UPDATE` from the app role, add an explicit `WITH CHECK` clause too — `USING` alone does not constrain what a writer can create. See `references/policy-using-with-check.md`.
3. **Empty-GUC-zero-rows is mandatory**: `current_setting('app.tenant_id', true)` with `true` (missing_ok) returns NULL if unset, and `tenant_id = NULL` matches zero rows — never write a policy that falls back to "show everything" when the GUC is unset. An invalid (non-UUID) GUC value must raise, not silently expose rows.
4. The application DB role is never `BYPASSRLS` and never the migration/superuser role. Two distinct roles: a migration role (runs Flyway, owns DDL) and an app role (runs application queries, RLS-bound). Grant details: `references/grant-matrix.md`.
5. `SECURITY DEFINER` functions are a separate, higher-risk mechanism — only use one after an explicit decision (see the `EPIC-10` decision gate in `planning/capabilities.yaml`), and only per `references/security-definer.md`.
6. Every RLS-bearing migration needs, at minimum: a same-tenant positive test, a cross-tenant negative test, an empty-GUC test, and (if the table accepts writes) a `WITH CHECK` test — see `references/role-switching-tests.md`.
7. **Testcontainers-first**: RLS tests run against an isolated PostgreSQL Testcontainers instance, never against the long-lived local `infra_postgres_1` Compose database and never through `act`. See `references/rls-testcontainers.md`.
8. After any migration: run `./mvnw -f backend test -Dtest=*RlsTest` — the empty-GUC and cross-tenant negative tests must pass.

## References

- `references/policy-using-with-check.md` — `USING` vs `WITH CHECK`, why both are needed on writable tables.
- `references/grant-matrix.md` — migration role vs. app role vs. `PUBLIC`, per-schema grant matrix, one-writer-per-schema.
- `references/security-definer.md` — when (rarely) to use `SECURITY DEFINER`, `search_path` hijacking defense, privilege minimization.
- `references/role-switching-tests.md` — the required test matrix (same-tenant / cross-tenant / empty-GUC / WITH CHECK / foreign writer role).
- `references/rls-testcontainers.md` — why RLS tests must run on isolated Testcontainers PostgreSQL, not the long-lived Compose database.
