# HANDOFF

## Zadanie

Active Goal: deliver the ADR-N11-authorized synthetic gross-instant path. User approved Option 1:
one PostgreSQL transaction through a dedicated executor datasource and narrow module-owned
`SECURITY DEFINER` functions. Do not repeat the accepted pre-ADR transaction-boundary audit.

## Zrobione

- Added frozen `ADR-N11-gross-instant-single-transaction-security-definer.md` and README index.
- Added executor and separate ledger/settlement/payment NOLOGIN function-owner roles plus narrow
  command migrations V35–V43. The executor has no direct domain-table DML or owner membership.
- Implemented typed `GrossInstantStrategy`, transaction-bound JDBC adapters and the dedicated
  executor datasource. Settlement invokes public typed ports, never table DML or a raw connection.
- `GrossInstantOneTxFlowTest` (PostgreSQL 18/Testcontainers): success uses one `txid_current()` and
  backend PID for RESERVE, POST, finality and projection; replay is duplicate-free; insufficient
  liquidity is atomic; injected before/after command-boundary faults roll back durable success state.
- `GrossInstantSecurityTest` (PostgreSQL 18/Testcontainers): grants, role isolation, function
  metadata, revoked PUBLIC execution and hostile search-path object creation denial pass.
- Fresh migrations pass through V43. Planning story inventory and capability-graph validators pass;
  EPIC-33/36 now reflect ADR-N11-authorized verified work. Two consecutive `./mvnw -f backend test`
  regressions passed after the legacy PaymentFinalityPort qualifier fix.

## Utknęliśmy na

Nothing externally blocked. The Goal is not complete: no dedicated upgrade-path test from a V34
database to V43 yet; no gross-instant concurrent identical/conflicting-command proof or retry
policy; no mutation suite for the new functions; no dedicated payment RLS/cross-tenant execution
test; and no independent database-review verdict yet. These are evidence gaps, not architecture gates.

## Plan na następny krok

Highest-risk remaining invariant: concurrent identical command handling. Add PostgreSQL 18
Testcontainers tests for concurrent same/different commands and deterministic lock ordering; decide
from observed `40001` behavior whether a bounded whole-transaction retry is required. Then add
upgrade/mutation/RLS proofs and run the mandated database review before considering completion.

## Pułapki, których nie wolno powtórzyć

- V35–V43 are append-only: use a higher migration for every correction.
- Do not give the executor table DML, membership in a function-owner role, `SET LOCAL ROLE`, XA/JTA,
  saga/compensation, direct cross-schema DML, reversal, real CSM or invented profile mapping.
- Finality remains settlement-owned and distinct from business status, ISO status, transport and receipt.
- `build/generated-spring-modulith/javadoc.json` is a baseline artifact Maven rewrites; restore it
  from `/tmp/FINALITY-TRUTH-AND-READINESS-PROGRAM/javadoc-baseline.json` before final commit.
