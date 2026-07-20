# ADR-N11 gross-instant execution evidence

## Scope

This record is the post-implementation evidence for the user-approved ADR-N11 option: one physical
PostgreSQL transaction through module-owned `SECURITY DEFINER` commands. It supplements, but does
not replace, the accepted historical pre-ADR boundary proof in
`GrossInstantTransactionBoundaryProofTest` and `GROSS-INSTANT-TRANSACTION-COORDINATION-DECISION.md`.

# Database review

## Verdict

PASS

## Blocking findings

None after the executed hostile-object and per-schema create-denial proofs were added.

## Non-blocking findings

The migrations are intentionally a synthetic/lab slice. The two partial idempotency indexes in
V40 are tied to the command functions' exact `(payment_id, type, payload.commandId)` and
`(aggregate_id, event_type, correlation_id)` replay lookups. They are created transactionally,
which is appropriate for this additive PostgreSQL 18 lab schema; no production-size/online-index
claim is made.

## Security findings

- `gross_instant_executor_role` is `NOINHERIT`, `NOSUPERUSER`, `NOBYPASSRLS`, has no table DML and
  no membership in any function-owner role.
- Each module command has its own `NOLOGIN` owner. `PUBLIC EXECUTE` is revoked; executor access is
  limited to the five named function signatures.
- Every effective function search path is fixed, begins with `pg_catalog`, excludes `public` and
  ends with `pg_temp`. `GrossInstantSecurityTest` both denies untrusted object creation in ledger,
  settlement, payment and reference-data schemas and executes a payment command while an injected
  hostile `payment.current_setting(text,boolean)` exists; the hostile function is not invoked.
- Payment's forced RLS rejects empty tenant context and cross-tenant projection; the latter rolls
  back the already-called ledger and settlement functions in the same transaction.

## Migration findings

All ADR-N11 migrations are additive and append-only:

| Migration | Change / compatibility / verification |
|---|---|
| V35 | Adds coordinator and three non-login owners; catalog-only, no data rewrite. |
| V36/V37 | Adds then forward-fixes the ledger command; V37 replaces the body to lock accounts in UUID order. V36 was never edited after application. |
| V38/V39 | Adds tenant-RLS settlement attempt/event evidence and finality command; V39 is a forward replay-evidence correction. |
| V40 | Adds payment command functions and partial replay indexes; no existing payment rows are rewritten. |
| V41/V42/V43 | Adds insufficiency command, then minimal forward fixes for the lock privilege and event column. Earlier files remain untouched. |

`GrossInstantOneTxFlowTest` and `GrossInstantSecurityTest` provide fresh PostgreSQL 18/Flyway
coverage through V43. `GrossInstantMigrationUpgradePathTest` migrates a populated V34 database to
V43, preserves the existing payment, and executes the newly upgraded ledger, settlement and payment
command surfaces as the executor role.

## Testcontainers evidence

- `GrossInstantOneTxFlowTest` — 9 cases: one physical connection/transaction ID/backend PID across
  reserve, post, finality and projection; success; insufficient liquidity with rejection event;
  sequential replay; changed-evidence conflict; rollback injection immediately before and after each
  command boundary; internal payment-function failure rollback; concurrent identical whole-transaction
  retry; concurrent conflict fail-closed; deterministic crossed-account lock order; and cross-tenant
  payment RLS rollback.
- `GrossInstantSecurityTest` — 5 cases: grants/owners, function metadata/PUBLIC revoke,
  all-effective-schema hostile-object denial, executed shadow-object proof, empty-GUC rejection.
- `GrossInstantMigrationUpgradePathTest` — V34→V43 data-preserving upgrade and command invocation.
- `GrossInstantMutationContractTest` — 6 mutation guards for row locks, expected state, finality
  evidence, `SECURITY DEFINER`, search paths, PUBLIC revokes, direct grants, duplicate-event keys,
  raw connections/commits and direct settlement cross-schema DML.
- `SettlementRoleNoLedgerGrantTest`, `EgressCannotWritePaymentStatusTest` and `ModularityTest` add
  independent ownership, no-egress-write and Modulith-boundary evidence.
- Focused direct Maven run: `36/0/0 PASS` on 2026-07-20. Two subsequent, consecutive clean
  `./mvnw -f backend test` regressions passed on 2026-07-20.

## Required fixes

None from this review.

## Planning and governance

EPIC-13 Story 13.2, EPIC-33 Stories 33.1/33.2 and EPIC-36 Story 36.1 now cite their executable
evidence. Story inventory regeneration and validation, the capability-graph validator, and the
complete repository governance validator all pass on 2026-07-20.
