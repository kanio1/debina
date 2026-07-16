# Security review checklist

- [ ] App role is never `BYPASSRLS`, never the migration/superuser role, never used for both DDL and business-transaction DML (`postgres-rls-migration` skill's `grant-matrix.md`).
- [ ] `PUBLIC` has no residual privilege on any new schema/table/function.
- [ ] Any new `SECURITY DEFINER` function: minimal single-purpose scope, explicit `SET search_path` (schema-qualified, minimal), `PUBLIC EXECUTE` revoked, explicit `USAGE` on schema *and* `EXECUTE` on function granted narrowly, and — critically — that this is genuinely warranted by the `EPIC-10`-style decision-gate reasoning, not just "seemed convenient" (`postgres-rls-migration` skill's `security-definer.md`).
- [ ] `search_path` hijacking attempt tested for any `SECURITY DEFINER` function (create a shadow object, call the function, confirm it operated on the intended schema-qualified object).
- [ ] No secrets, tokens, or full payment/message payloads (XML, PII fields) logged anywhere in the changed code, including inside function bodies, exception messages, and test output that might be captured by CI (`sepa-nexus-payments-data-integrity` skill's `append-only-evidence.md`).
- [ ] Cross-tenant and empty-GUC/invalid-GUC RLS tests present and passing for every RLS-bearing table touched (`sepa-nexus-database-testing` skill's `rls-negative-tests.md`) — not just the same-tenant happy path.
- [ ] Foreign-writer-role negative test present for every schema/grant change (`grants-and-writer-isolation.md`).
- [ ] No new code path grants a role access to another module's schema, even read-only, without going through that module's published port/event/read model (one-writer-per-schema is a read *and* write isolation rule at the grant level, not write-only).
- [ ] Authorization decision (role check) lives in the service layer alongside the business rule, not in the controller and not solely relied upon via RLS — RLS and role-based authorization are two independent layers; confirm neither was weakened under the assumption "the other layer already covers it."
