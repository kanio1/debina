---
name: spring-modulith-module
description: Use when creating or changing a Debina Spring Modulith module, module port, or cross-module transaction boundary; do not use it to bypass schema ownership or replace accepted outbox decisions.
---
# Spring Modulith module conventions

Keep `web → service → repository`: controllers contain no business/security decisions; services own both; repositories remain thin and schema-scoped. Publish an explicit, narrow public API; keep infrastructure package-private where possible. No public repository, `JdbcTemplate`, raw Connection, or `DriverManager` leakage across modules, and no god module.

For each interaction classify one of: synchronous command port, synchronous query port, domain event, outbox event, read-model projection. Do not use events to evade a synchronous invariant or synchronous calls where a frozen outbox/inbox decision applies.

Name the transaction owner, physical connection owner, commit/rollback boundaries, and whether an adapter joins its caller. Transaction-bound adapters use Spring infrastructure; they do not internally commit/roll back unless they own the whole transaction, and never open a second `DriverManager` connection while claiming one transaction.

Each module writes its own schema only. A module-owned command function implements that module’s port; settlement may coordinate public ports but never direct cross-schema ledger/payment DML. Verify as applicable with `ApplicationModules.verify()`, `@ApplicationModuleTest`, Modulith tests, ArchUnit allowed/forbidden dependencies, public API and event contract tests, non-vacuous fixtures, and transaction-boundary tests. Favor deep modules: useful behavior behind a stable narrow interface, not pass-through wrappers that hide transaction or finality semantics.
