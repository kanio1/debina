---
name: postgres-rls-migration
description: Use for Debina Flyway migrations, PostgreSQL RLS, roles, grants, or tenant GUC changes; delegate approved cross-role one-transaction command functions to debina-postgres-transaction-coordination.
---
# PostgreSQL RLS and migration conventions

Identify separately: migration role, schema/table owner, runtime module writer, transaction-coordinator executor, `SECURITY DEFINER` owner, scheduler/consumer role, and read-only role. Enforce one-writer-per-schema: no shared DML role, application superuser, owner-bypass reliance, or runtime `BYPASSRLS`.

Review schema `USAGE`; table, sequence and function privileges; `ALTER DEFAULT PRIVILEGES`; and `PUBLIC`. Tenant tables require `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY` where project convention requires it, and both `USING` and `WITH CHECK`. Prove same tenant, cross-tenant denial, empty/absent GUC behavior, and real application, worker and scheduler identities; verify tenant GUC cleanup and pool isolation.

Use PostgreSQL 18 Testcontainers: fresh and upgrade migrations with representative data, forward-only behavior, constraints/indexes/grants, applicable rollback safety, and real DML privilege tests. Never edit an already-applied migration. For relevant contention, prove deterministic lock order, `FOR UPDATE`, `SKIP LOCKED`, advisory-lock ownership, no unexpected deadlock, and concurrent replay/conflict behavior.

If a migration creates or changes a `SECURITY DEFINER` command function, invoke or defer to `debina-postgres-transaction-coordination`. Still require fixed safe `search_path`, schema-qualified references, `PUBLIC` execute revoked, narrow `EXECUTE`, owner review, same-transaction function/grant creation, and no user-writable lookup path.

## References

- `references/policy-using-with-check.md` — `USING` vs `WITH CHECK`, why both are needed on writable tables.
- `references/grant-matrix.md` — migration role vs. app role vs. `PUBLIC`, per-schema grant matrix, one-writer-per-schema.
- `references/security-definer.md` — when (rarely) to use `SECURITY DEFINER`, `search_path` hijacking defense, privilege minimization.
- `references/role-switching-tests.md` — the required test matrix (same-tenant / cross-tenant / empty-GUC / WITH CHECK / foreign writer role).
- `references/rls-testcontainers.md` — why RLS tests must run on isolated Testcontainers PostgreSQL, not the long-lived Compose database.
