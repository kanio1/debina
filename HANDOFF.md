# HANDOFF

## Zadanie

`PAYMENT-SPINE-CORRECTNESS-PROGRAM`: recovery ledger/outbox runtime spine, source-backed event routing, scheduler operational truth and payment safety without inventing validation, finality, receipt or egress-profile contracts.

- Start: `main` at `fc5be93d82038530db52a2a48e487a7f035ce6c2`, aligned with `origin/main`.
- Current pre-handoff-commit HEAD: `6787905`; local is 3 commits ahead of `origin/main`. No fetch/pull/push occurred.
- Codex ran in `/home/suso/debina`, `danger-full-access`; Podman socket active and Testcontainers PASS. No `act`.
- Evidence plan: `/tmp/PAYMENT-SPINE-CORRECTNESS-PROGRAM/execution-plan.md`; protected javadoc baseline: `/tmp/PAYMENT-SPINE-CORRECTNESS-PROGRAM/javadoc-pre-session.json`.

## Zrobione

- Committed `bd2899c feat(ledger): enforce journal currency and reversal integrity`:
  V29 creates composite account/currency integrity, one-currency deferred-balance trigger behavior and reversal pointer/self/uniqueness rules. Fresh and V28→V29 upgrade proof PASS; 28 ledger tests PASS; independent review PASS in `/tmp/PAYMENT-SPINE-CORRECTNESS-PROGRAM/database-review-v29.md`. Existing five V29 mutation proofs were verified as restored.
- Committed `0dc30e1 feat(outbox): harden relay runtime and event truth`:
  dedicated `outbox_dispatcher_role` datasource/JDBC/transaction manager; primary JPA transaction manager explicitly retained; payment and ISO relays claim with `FOR UPDATE SKIP LOCKED`, await ACK and mark only `published_at`; domain writes are denied.
- Scheduler defaults are isolated in `backend/src/test/resources/application.properties`; only `OutboxRelayScheduler` is scheduled, and a dedicated enabled context proves health UP → DOWN on permission/broker failure → UP after success without uncontrolled scheduler exception. Egress remains an explicit claim mechanism, never a scheduled one.
- Source-backed event safety: payment creation emits `payment.received.v1`, routing is by known `event_type`, unknown types have no default topic and remain unpublished. The payment inbox consumes received only, applies tenant/branch context and preserves `RECEIVED`; it neither validates nor creates a validated outbox fact.
- Deterministic crash-window proof PASS: ACK then forced DB rollback leaves `published_at` NULL; the next relay uses the same event ID; payment inbox records one identity and the business status remains `RECEIVED`.
- WalkingSkeleton no longer depends on local Keycloak/Compose; it uses a test-context `JwtDecoder`, Testcontainers PostgreSQL/Kafka, and explicit relay dispatch. ISO routing tests truncate their outbox per test and use deterministic failed acknowledgment rather than a fake localhost broker.
- Relay mutation: removing production `SKIP LOCKED` produced the expected `RuntimeDatasourceOwnershipTest` failure and was restored. Scheduler-disabled and crash-window mutations were likewise expected failures and restored. No mutation marker remains.
- Committed `6787905 chore(planning): record payment spine evidence`; planning generator, inventory, graph and governance validators PASS.
- Final regressions:
  - `final-regression-1.log`: 379 tests, 0 failures, 0 errors, BUILD SUCCESS (2:25).
  - `final-regression-2.log`: 379 tests, 0 failures, 0 errors, BUILD SUCCESS (2:23).
  Both required operational scans are empty for scheduler errors, denied relay access, fixed localhost ports and failed Kafka connections. `build/generated-spring-modulith/javadoc.json` was restored and is not changed/staged.

## Utknęliśmy na

No infrastructure blocker. These are intentionally `CAPABILITY BLOCKED`, not implementation work:

1. EPIC-20 Story 20.4 full validation/rejection: no source-backed validation verdict or rejection model. Keep the RECEIVED-only safety behavior.
2. EPIC-27 ISO inbox beyond generic dedup: no `csm.response.received` payload, status/reason semantics or FSM handoff contract.
3. EPIC-14/39/47 finality, receipt and five-axis model: no finality catalog/policy/records or receipt artifact source contract. Do not create placeholder enums/DDL/tests.
4. EPIC-44 profile/snapshot/artifact persistence: retry policy, allowed-artifact representation and version/snapshot shape remain undecided.
5. `payment.received` is still physically written from `payment-lifecycle`; blueprint topic ownership names ingress. Do not move it or create cross-schema writes without a source-backed boundary decision.

## Plan na następny krok

Start with the highest READY capability outside the blocked list after checking `planning/README.md`, `capabilities.yaml`, direct dependencies and executable `verify:`. A likely safe next analytical task is recording the physical producer-owner conflict as an explicit source question; do not alter modules until a superior ADR/source resolves it.

## Pułapki, których nie wolno powtórzyć

- Never treat received/accepted/dispatched/delivered as finality or as validation.
- Keep relay identity separate from `sepa_app`; do not inject relay infrastructure into domain services or use the primary transaction manager for relay work.
- Keep scheduler activation opt-in in ordinary test contexts. Testcontainers mapped random localhost ports are legitimate; fixed `localhost:9092`, `localhost:5432` or fake `127.0.0.1:1` are not.
- Do not claim exactly-once: the proved contract is at-least-once publication plus inbox dedup.
- Do not use `act`, Compose as test fixture, fetch/pull/push, reset/clean/stash/restore, or destructive Git operations.
- Restore `build/generated-spring-modulith/javadoc.json` from the protected baseline before full tests/commits and never stage it.
