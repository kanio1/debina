# HANDOFF

## Zadanie

SEPA Nexus is a synthetic payment-testing platform with frozen one-writer-per-schema, LedgerPort,
and explicit-finality rules. The current request attempted EPIC-33/EPIC-36 synthetic
gross-instant settlement, whose source requires reserve→post→FINAL in one real transaction.

## Zrobione

- Read HANDOFF, ADR-N9/N10, EPIC-13/33/35/36/39, backend and planning instructions before work.
- Added `backend/src/test/java/com/sepanexus/settlement/GrossInstantTransactionBoundaryProofTest.java`.
  On isolated PostgreSQL 18 Testcontainers it records four different `txid_current()` values for
  the actual `JdbcLedgerPort.reserve`, `JdbcLedgerPort.post`, settlement finality, and payment
  projection path. Its injected projection failure proves a committed POST and settlement
  finality record can remain while the payment projection is rolled back.
- Reran the independent READY evidence: 50 tests passed, including ledger reserve/post/release,
  concurrency and rollback; V34 and V31–V34 fresh/upgrade migrations; finality replay/conflict;
  payment projection idempotency/rollback; ledger grants; and Modulith boundaries.
- Added `GROSS-INSTANT-TRANSACTION-COORDINATION-DECISION.md`, with source facts, actual failure
  window, viable coordination choices, the SECURITY DEFINER recommendation, and exact ADR input.
- Updated `planning/epics/EPIC-33-instant-settlement.md` and
  `planning/epics/EPIC-36-settlement-gross-instant.md`: Story 33.1 / 36.1 and both epics are
  accurately `blocked`; shared EPIC-36 Story 36.3 is `done` with rerun evidence. Updated
  `planning/README.md` and regenerated `planning/story-inventory.json`.
- Planning governance passes: story-inventory generator check, story validator, capability-graph
  validator, and `git diff --check`. Generated Spring Modulith Javadoc was restored to its prior
  baseline after Maven.

## Utknęliśmy na

The current implementation cannot meet the source phrase "in one transaction": each LedgerPort
method opens and commits a new `ledger_role` connection, settlement finality opens and commits a
new `settlement_role` connection, and `JdbcPaymentFinalityProjection` owns a separate Spring
`sepa_app` transaction. The PostgreSQL proof establishes this is four transactions, not an
assumption. No approved ADR authorizes a cross-role coordination mechanism for this slice. Do not
implement `GrossInstantStrategy` as a sequence of those commits, and do not silently add
SECURITY DEFINER, SET LOCAL ROLE, XA/JTA, direct cross-schema grants, a saga, compensation, or
reverse behavior. Two full backend regressions are not claimed because the requested production
slice is correctly decision-blocked before it can be completed.

## Plan na następny krok

Obtain one explicit ADR-level decision using
`GROSS-INSTANT-TRANSACTION-COORDINATION-DECISION.md` (recommended: narrowly scoped PostgreSQL
SECURITY DEFINER command functions with one physical coordinator transaction); only then open
EPIC-33 Story 33.1 and implement the approved `GrossInstantStrategy` plus its full evidence
matrix and two clean regressions.

## Pułapki, których nie wolno powtórzyć

- Never label the current reserve→post→finality→projection sequence one transaction: the proof
  records four committed PostgreSQL transaction IDs and a real POST/finality-to-projection
  recovery window.
- Settlement must retain zero direct ledger/payment table grants; money remains only behind
  LedgerPort and payment projection only behind PaymentFinalityPort.
- Do not infer finality from business/ISO/transport/receipt status, add reverse(), real CSM
  behavior, certification claims, or invent a scheme-profile mapping.
- Maven rewrites `build/generated-spring-modulith/javadoc.json`; restore its baseline before
  ending a session.
