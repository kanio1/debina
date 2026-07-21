# ADR-W8-01 — Source-owned same-transaction command audit boundary

**Status:** Accepted (2026-07-21)

## Context

The frozen security source requires an application-audit row for every command, in the same
transaction as a successful command's domain write. The ownership source assigns `audit.audit_log`
to `evidence-audit`; payment-lifecycle may not receive unrestricted direct DML on that schema.
The approval decisions are synchronous writes which must also release the existing payment outbox
atomically where appropriate.

## Options compared

| Option | Atomic with domain command | Ownership / fabrication boundary | Decision |
|---|---|---|---|
| A. Direct `INSERT` grant from `sepa_app` to `audit.audit_log` | Yes | Breaks one-writer-per-schema and permits unbounded table writes | Rejected |
| B. Evidence-audit Java adapter joining the caller transaction | Potentially | This checkout has one `sepa_app` runtime role and no separate audit datasource/role; a Java repository would still need the forbidden direct cross-schema grant | Rejected for this checkout |
| C. Typed `SECURITY DEFINER` append function owned by evidence-audit | Yes, on the caller's Spring-managed connection | No table DML grant; fixed signature, validated transaction context, no dynamic SQL, fixed safe search path and narrow execute grant | Accepted |
| D. After-commit/event/Kafka audit | No | Loses audit on crash and creates a second transaction | Rejected |
| E. Shared broad application writer role | Yes | Violates one-writer-per-schema and least privilege | Rejected |

## Decision

`evidence-audit` owns `audit.audit_log` and `audit.append_command_audit(...)`. The function is
owned by a NOLOGIN, NOSUPERUSER, NOBYPASSRLS audit role and runs as `SECURITY DEFINER` with a
fixed `pg_catalog, audit, pg_temp` search path, fully qualified objects, no dynamic SQL and no
transaction control. `PUBLIC` cannot execute it; `sepa_app` gets only schema `USAGE` and exactly
this function's `EXECUTE`, never audit table DML.

The typed Java `CommandAuditPort` calls the function through the existing transaction-bound
`JdbcTemplate`; it must never open a second connection, use `REQUIRES_NEW`, or catch-and-ignore
an audit failure. The function validates tenant/branch GUC alignment, required fields, object JSON
snapshots and the system expiry actor context. JSONB is the canonical deterministic snapshot
representation. This decision covers successful command audit only; denied-command recording is a
separate evidence-audit-owned path because it has no successful domain transaction to join.

## Consequences

- Audit append failure rolls back the domain mutation, outbox and successful idempotency completion.
- Evidence-audit retains ownership while payment-lifecycle depends only on its public DTO/port.
- Tests must prove one transaction/connection, direct DML rejection, `PUBLIC EXECUTE` denial,
  hostile search-path resistance, tenant/branch/RLS behavior and rollback.
- This does not add hash chaining, retention policy, a Kafka topic, an audit HTTP/GraphQL adapter,
  raw payload retention or a broad auditor write privilege.
