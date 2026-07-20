# HANDOFF

## Zadanie

Completed active Goal: deliver the strongest source-backed synthetic gross-instant slice under the
user-approved frozen ADR-N11 option—one physical PostgreSQL transaction through narrow,
module-owned `SECURITY DEFINER` commands—with PostgreSQL 18/Testcontainers, migration, security,
concurrency, mutation, ownership and governance evidence. No push was requested or performed.

## Zrobione

- ADR-N11 remains the binding approval. The historical transaction audit is preserved and explicitly
  marked superseded by the approved Option 1, not rewritten.
- `GrossInstantStrategy` now starts a `TransactionTemplate` on the dedicated executor transaction
  manager and safely retries SQLSTATE `40001`/`40P01` only by restarting the complete transaction.
- Added PostgreSQL 18 evidence: `GrossInstantOneTxFlowTest` (9 cases),
  `GrossInstantSecurityTest` (5), `GrossInstantMigrationUpgradePathTest` (V34→V43), and
  `GrossInstantMutationContractTest` (6), alongside ownership, egress and Modulith tests.
- `GROSS-INSTANT-ADR-N11-EXECUTION-EVIDENCE.md` contains the PASS database review and proof matrix.
  Fresh and upgrade Flyway paths, hostile search-path mutation, RLS/cross-tenant rollback,
  idempotency/conflict, internal-function failure rollback, deterministic locking and exact grants
  are covered.
- Focused evidence run passed `36/0/0`; two consecutive clean `./mvnw -f backend test` regressions
  passed on 2026-07-20. Governance, story inventory and capability graph validators pass. Planning
  EPIC-13 Story 13.2, EPIC-33 and EPIC-36 have been updated.

## Utknęliśmy na

Nothing is blocked. The verified continuation changes are ready for the required local commit.

## Plan na następny krok

Commit the reviewed ADR-N11 evidence continuation locally, verify `git status --short` is empty,
and report completion. If new scope arises, preserve the ADR-N11 boundary: no saga/XA/`SET LOCAL
ROLE`, direct cross-schema DML, reversal, real CSM or inferred finality.

## Pułapki, których nie wolno powtórzyć

- V35–V43 are append-only; correct only with a higher migration.
- Executor has function `EXECUTE` only—never table DML or function-owner membership. Owners stay
  module-scoped, no-login, no-superuser and no-BYPASSRLS.
- Any retry must recreate the whole Spring transaction; never retry a single command function after
  an abort.
- Preserve the independent business/ISO/finality/transport/receipt statuses and settlement-owned
  ON_LEDGER_POST finality.
- `build/generated-spring-modulith/javadoc.json` is intentionally restored to its baseline after
  Maven runs; do not commit Maven's generated replacement unless it is deliberately reviewed.
