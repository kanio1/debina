---
name: debina-postgres-transaction-coordination
description: Use for one transaction, cross-schema transaction, multiple PostgreSQL roles, SECURITY DEFINER, SET LOCAL ROLE, transaction coordinator, module-owned command functions, or gross-instant reserve→post→finality→projection; not ordinary repository transactions, generic migrations, accepted outbox flows, or unapproved architecture.
---
# PostgreSQL transaction coordination

Read current ADRs, decision packets, `HANDOFF.md`, and capability gates first. Determine whether one physical transaction is necessary; inventory connection, role, transaction owner, commit, rollback, idempotency, crash windows, replay, and partial durable states. Prove the existing path with `txid_current()` before calling it atomic. Multiple commits are never one transaction. If no mechanism is approved, write a decision packet and stop; after approval implement only that mechanism.

When an ADR authorizes `SECURITY DEFINER`, require one Spring-managed physical connection (one physical connection) and one PostgreSQL transaction ID; a dedicated executor with no direct DML; module-specific typed immutable command functions; and no omnipotent function or generic `execute_sql`. Owners are separate `NOLOGIN`, `NOSUPERUSER`, `NOBYPASSRLS` roles; executor has no owner membership. Require exact `EXECUTE`, `PUBLIC` revoke, fixed trusted `search_path` with `pg_temp` last, schema-qualified objects, no ambiguous overload/unsafe conversion/dynamic SQL, no function transaction control, and validated input/state/tenant/replay/conflict handling.

Prove the full matrix in `references/failure-injection-matrix.md`: same connection/`txid_current()`, rollback around every command, function failure, replay/conflict/concurrency, no duplicate money/finality, grants/role/owner scope/direct-DML rejection, hostile search_path and security mutations, second-connection proof, fresh/upgrade migration, pool and tenant isolation, deadlock absence, and full regression evidence.

Never silently adopt `SET LOCAL ROLE`, direct coordinator DML grants, XA/JTA, idempotent multi-transaction sequencing as ACID, a saga/compensation, changed finality semantics, or `reverse()` without its source contract.

Read [security-definer-checklist](references/security-definer-checklist.md), [transaction inventory](references/transaction-boundary-inventory.md), [failure matrix](references/failure-injection-matrix.md), and [forbidden patterns](references/forbidden-patterns.md) as needed.
