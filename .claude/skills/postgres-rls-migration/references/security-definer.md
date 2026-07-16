# `SECURITY DEFINER` — when and how

`SECURITY DEFINER` runs a function with the privileges of its *owner*, not its caller. This is a deliberately higher-privilege mechanism than the app role otherwise has — treat every use as a decision, not a default.

## Decision gate first

Do not add a new `SECURITY DEFINER` function without checking whether it needs the same explicit-decision treatment as `EPIC-10`'s transaction-coordination gate (`gate.EPIC-10.10_1.security-definer` in `planning/capabilities.yaml`, status tracked in `planning/decisions/` or the epic file). `SECURITY DEFINER` was evaluated there specifically to coordinate a single-transaction, one-writer-per-schema-respecting write across two schemas — re-derive whether a new use case is the same shape before assuming it's automatically justified.

## Minimal-function rule

One function, one narrow purpose. Do not build a general-purpose "elevated access" function — each `SECURITY DEFINER` function should do exactly the one cross-schema (or cross-role) operation it exists for, nothing broader.

## Required hardening, every time

```sql
CREATE FUNCTION iso.record_lineage_and_payment(...)
RETURNS ...
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, iso, payment  -- explicit, minimal, schema-qualified
AS $$
...
$$;

REVOKE ALL ON FUNCTION iso.record_lineage_and_payment FROM PUBLIC;
GRANT USAGE ON SCHEMA iso TO sepa_app;       -- explicit USAGE, not implied by EXECUTE
GRANT EXECUTE ON FUNCTION iso.record_lineage_and_payment TO sepa_app;
```

- **`SET search_path` is mandatory, not optional.** Without it, the function resolves unqualified identifiers using the *caller's* `search_path` at call time — a CVE-2007-2138-class attack: a malicious caller creates an object shadowing one the function references, in a schema earlier in their own `search_path`, and the `SECURITY DEFINER` function silently operates on the attacker's object with the owner's privileges. Always pin `search_path` explicitly and use fully schema-qualified names inside the function body as defense in depth even with the pin in place.
- **`PUBLIC EXECUTE` must be revoked**, then `EXECUTE` granted narrowly to the specific role(s) that need it.
- **`USAGE` on the schema is a separate grant from `EXECUTE` on the function** — granting only `EXECUTE` without `USAGE` on the containing schema still fails; grant both explicitly, don't assume one implies the other.
- **Function owner** should hold the minimum privileges needed to perform the function body's work — not superuser, not the migration role if avoidable. A narrowly-scoped owner role limits blast radius if the function itself has a bug.

## Required test matrix

- Foreign role calling the function (should be rejected — no `EXECUTE` grant).
- Commit path: transaction commits, both schemas' writes visible atomically.
- Rollback path: transaction rolls back, neither schema's write persists.
- GUC state after the transaction ends (commit and rollback) — `set_config(..., true)` (transaction-local) reverts to `''` after both, not `NULL`; assert the actual observed value, don't assume.
- `search_path` hijacking attempt: create a shadow object in a schema the calling role controls, call the function, assert it operated on the *intended* schema-qualified object, not the shadow.
- No secrets or full payloads logged from inside the function body.

See `SecurityDefinerPrivilegeProofTest`/`SecurityDefinerTransactionProofTest`/`SecurityDefinerSearchPathProofTest`/`SecurityDefinerPoolIsolationProofTest`/`SecurityDefinerJpaFlushProofTest` (18/18 PASS, `EPIC-10` proof suite) as the worked example of this full matrix against a real Testcontainers PostgreSQL.
