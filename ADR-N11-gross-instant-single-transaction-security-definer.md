# ADR-N11 — Gross-Instant Single-Transaction Security-Definer Coordination

## Status

Frozen

## Context

The source-backed gross-instant path requires reserve → post → settlement outcome/status outbox
and finality to commit as one PostgreSQL transaction; ADR-N10 requires dedicated module ownership,
LedgerPort-only money access, settlement-owned finality, a narrow payment projection, and no
settlement table grant on ledger or payment. The accepted PostgreSQL 18 transaction-boundary proof
established that the prior dedicated connections committed those actions separately.

## Decision

`[FREEZE]` The synthetic gross-instant command uses one Spring-managed JDBC transaction on a
dedicated `gross_instant_executor_role` datasource. The executor has only `CONNECT`, required
schema `USAGE`, and `EXECUTE` on the named command functions; it has no direct DML grant on
`ledger.*`, `settlement.*`, or `payment.*` and is never a member of a function-owner role.

The command is coordinated through public Java ports backed by transaction-bound infrastructure:
`LedgerPort` invokes the ledger command, settlement invokes its own finality/outcome command, and
`PaymentFinalityPort` invokes payment's projection/status-report command. No public domain port
exposes a raw JDBC `Connection`; no adapter opens a new `DriverManager` connection or commits or
rolls back internally. Settlement never issues direct ledger/payment table DML.

Each module owns a separate explicit PostgreSQL command function and a separate `NOLOGIN`,
`NOSUPERUSER`, `NOBYPASSRLS`, `NOINHERIT` function-owner role. Functions are `SECURITY DEFINER`,
`VOLATILE`, `PARALLEL UNSAFE`, schema-qualified, fixed to a trusted `search_path` that excludes
`public` and ends with `pg_temp`, use exact types, validate required command values, use no dynamic
SQL and contain no transaction control. `PUBLIC` execute is revoked and only the executor receives
explicit `EXECUTE`. Function owners receive only the privileges their own command body requires;
they neither inherit nor receive grants to another module's tables.

The ledger command performs ADR-N10 reserve then post in the caller transaction, retaining account
locks in deterministic UUID order, durable reservation and journal evidence, typed insufficient
liquidity, same-command replay and different-command conflict rejection. It never implements
`reverse()`.

The settlement command writes the source-backed attempt/event outcome and, for POST, the immutable
`ON_LEDGER_POST` authority record. The payment command validates RLS tenant ownership, projects
the authority idempotently and records exactly one source-backed `payment.status.reported` outbox
fact (`ACSC` after post/finality; `RJCT` on gross insufficient liquidity). It does not derive
settlement finality from a business, ISO, transport or receipt status, and egress remains
transport-only.

All actions succeed or roll back together. A same command returns its prior typed outcome without a
second reservation, journal effect, finality projection, business event or outbox event; a
different command fails closed. Gross insufficient liquidity creates no ledger reservation or
money effect, no finality, and no successful payment projection; its rejected-attempt/RJCT outcome
is committed atomically.

## Consequences

- A new append-only Flyway slice provisions roles, functions, grants, settlement attempt/outbox
  storage and any payment outbox idempotency constraint needed by the explicit commands.
- Tests must prove one connection/transaction ID, failure injection on both sides of every command
  call, internal function failure, replay/conflict/concurrency, RLS, grants, hostile search-path
  mutation, fresh/upgrade migration and the established ownership/Modulith boundaries.
- No saga, compensation protocol, XA/JTA, `SET LOCAL ROLE`, generic elevated-SQL function,
  direct cross-schema DML grant, scheme-profile mapping, CSM behavior, certification claim,
  receipt-derived finality or ISO-status-derived finality is introduced.
