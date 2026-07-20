# HANDOFF

## Zadanie

`FINALITY-TRUTH-AND-READINESS-PROGRAM`: source-backed correction of false settlement finality derived from the payment FSM, with database, mutation, boundary, runtime and documentation-truth proof. Pre-handoff HEAD is `a46bdb4` (`chore(handoff): record finality audit`); local `main` is four commits ahead of `origin/main` and no push was attempted.

## Zrobione

- V30 (`backend/src/main/resources/db/migration/payment/V30__clear_terminal_business_status_false_finality.sql`) is a forward-only correction: it preserves history rows and clears `is_final` only for REJECTED/DISPATCHED terminal-business false positives.
- `PaymentHistoryRecorder` now writes `is_final=false` for every current lifecycle transition; `PaymentTransitionTable.hasNoLegalOutgoingTransitions` is explicitly FSM topology only.
- RED proof was recorded: old terminality coupling failed for REJECTED/DISPATCHED and V29 upgrade data. GREEN evidence: fresh V19→V30 and V29→V30 PostgreSQL 18 Testcontainers tests, runtime legacy-row correction, unrelated non-terminal row preservation, egress grant boundary, and 387-test full regressions twice.
- Mutation evidence: restored topology coupling, forced DISPATCHED, forced REJECTED, temporary egress history-insert grant, and narrowed V30 predicate each failed as expected and were reverted. No scratch migration or mutation marker remains.
- Decision packet: `docs/analysis/DEBINA-FINALITY-LEDGERPORT-DECISION-PACKET.md`; planning evidence added to EPIC-20/32/39/42/47 without marking finality/receipt/LedgerPort stories done.
- Program evidence: `/tmp/FINALITY-TRUTH-AND-READINESS-PROGRAM/` (`finality-inventory.md`, `database-review.md`, `execution-plan.md`, final logs). Governance, story-inventory and capability-graph validators passed. `final-regression-1.log` and `final-regression-2.log`: 387 tests, zero failures/errors.
- Continuation audit found and repaired stale analysis claims that current FSM terminality still writes finality: `DEBINA-COMPREHENSIVE-PAYMENTS-ASSESSMENT.md`, the gap-risk backlog, lifecycle traceability, and artifact catalog now distinguish V30 safety behavior from the still-absent finality authority. Current targeted PostgreSQL 18/finality/boundary/architecture suite: 31 tests, zero failures/errors; governance validators pass.

## Utknęliśmy na

All executable work in this program is complete and committed (`7fde172`, `9c3b3c0`, `2edf97e`, `a46bdb4`). This same gate was re-audited for the third consecutive goal turn: the repository has no source-backed finality catalog/record/snapshot authority and lacks LedgerPort reservation lifecycle facts (RESERVE/RELEASE journal lines and no-double-post/release). Product scope and physical `payment.received` producer ownership are also documented but unresolved; do not invent a policy, receipt contract, CSM profile, reservation table or cross-schema writer. The active goal is now formally blocked pending one consolidated user/team decision.

## Plan na następny krok

Obtain one user/team decision against `docs/analysis/DEBINA-FINALITY-LEDGERPORT-DECISION-PACKET.md` selecting the product/finality/LedgerPort decision package; only then re-check EPIC-32 Story 32.2 and EPIC-39 Stories 39.1–39.2 for readiness.

## Pułapki, których nie wolno powtórzyć

- FSM terminality, REJECTED, DISPATCHED, POSTED, DELIVERED and receipts are not settlement finality.
- Never edit V20; V30 is the required forward fix. Preserve history rows rather than deleting evidence.
- Egress must retain zero payment-schema write access; delivery/receipt cannot write history finality.
- Restore `build/generated-spring-modulith/javadoc.json` from `/tmp/FINALITY-TRUTH-AND-READINESS-PROGRAM/javadoc-baseline.json` before commit/final verification and never stage it.
- Full logs contain expected negative-test permission/DOCTYPE/scheduler evidence; distinguish these from unexpected operational errors before declaring a regression clean.
