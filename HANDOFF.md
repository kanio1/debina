# HANDOFF

## Zadanie

`PAYMENT-SPINE-CORRECTNESS-PROGRAM`: recovery ledger/outbox runtime spine, source-backed event routing, scheduler operational truth and payment safety without inventing validation, finality, receipt or egress-profile contracts.

- Start: `main` at `fc5be93d82038530db52a2a48e487a7f035ce6c2`, aligned with `origin/main`.
- Pre-handoff-update HEAD: `5a7c2a0`; local is 5 commits ahead of `origin/main`, with no fetch/pull/push.
- Codex ran in `/home/suso/debina` with `danger-full-access`; Podman socket was active and Testcontainers passed. No `act`.
- Evidence plan: `/tmp/PAYMENT-SPINE-CORRECTNESS-PROGRAM/execution-plan.md`; protected javadoc baseline: `/tmp/PAYMENT-SPINE-CORRECTNESS-PROGRAM/javadoc-pre-session.json`.

## Zrobione

- `bd2899c feat(ledger): enforce journal currency and reversal integrity`: V29 account/currency, one-currency deferred balance, reversal pointer/self/uniqueness and immutable-line integrity. Fresh and V28→V29 upgrade proof, 28-test ledger regression and independent database review PASS (`/tmp/PAYMENT-SPINE-CORRECTNESS-PROGRAM/database-review-v29.md`). Existing five V29 mutations were verified restored.
- `0dc30e1 feat(outbox): harden relay runtime and event truth`: dedicated `outbox_dispatcher_role` datasource/JDBC/transaction manager; payment and ISO relay claim with `FOR UPDATE SKIP LOCKED`, wait for Kafka ACK and update only `published_at`; domain writes are denied while the primary JPA manager remains separate.
- Scheduler defaults are isolated in test configuration. The dedicated enabled context proves health UP → DOWN for permission/broker failures → UP after success, with unpublished rows retained and no uncontrolled scheduler exception. Egress is an explicit claim mechanism, not a scheduled one.
- Payment creation emits `payment.received.v1`; routing is controlled by known `event_type`; unknown events have no default topic and remain unpublished. Payment inbox deduplicates, applies tenant/branch context and preserves `RECEIVED`; it neither validates nor produces a validated fact.
- ACK→rollback crash-window proof passed with real PostgreSQL/Kafka Testcontainers: the same event ID is redelivered after rollback, one inbox identity is retained, and the only business effect keeps `RECEIVED`.
- `5a7c2a0 test(outbox): prove acknowledgement precedes published mark`: a dedicated ACK-order test holds the broker future open and proves the database mark cannot occur first. Removing production `.get(5, TimeUnit.SECONDS)` was an EXPECTED FAIL; it was fully restored. Related regression: 4 tests, 0 failures/errors.
- Planning/instruction/database-skill validators PASS. `post-ack-final-regression-1.log` and `post-ack-final-regression-2.log` each passed 380 tests with 0 failures and 0 errors; prescribed scheduler/permission/fixed-localhost/broker-error scans are empty. Generated javadoc was restored from the protected baseline and is not staged.

## Utknęliśmy na

No infrastructure blocker. These remain intentionally `CAPABILITY BLOCKED`:

1. EPIC-20 Story 20.4 full validation/rejection needs a source-backed validation verdict and rejection model; keep the RECEIVED-only safety behavior.
2. EPIC-27 ISO inbox beyond generic dedup needs `csm.response.received` payload, status/reason semantics and FSM-handoff contract.
3. EPIC-14/39/47 finality, receipt and five-axis model need a finality catalog/policy/records and receipt artifact source contract; do not invent placeholders.
4. EPIC-44 profile persistence needs settled retry, allowed-artifact, versioning and snapshot semantics.
5. `payment.received` is physically written by `payment-lifecycle` while blueprint topic ownership names ingress. Do not move it or introduce cross-schema writes without a source-backed boundary decision.

## Plan na następny krok

Start from `planning/README.md`, `capabilities.yaml`, direct dependencies and executable `verify:` to choose the next READY capability outside the blocked list. A safe analytical follow-up is to record the physical producer-owner conflict as a source question; do not change module ownership until a superior ADR/source resolves it.

## Pułapki, których nie wolno powtórzyć

- Never treat received/accepted/dispatched/delivered as validation or finality.
- Keep relay identity separate from `sepa_app`; relay code must not use the primary transaction manager or domain repositories.
- Ordinary test contexts must opt out of scheduler/admin/listener activation. Testcontainers mapped random localhost ports are valid; fixed `localhost:9092`, `localhost:5432` and fake local brokers are not.
- Do not claim exactly-once: the proved contract is at-least-once publication plus inbox dedup.
- Do not use `act`, Compose test fixtures, fetch/pull/push, reset/clean/stash/restore or destructive Git operations.
- Restore `build/generated-spring-modulith/javadoc.json` from the protected baseline before full tests/commits and never stage it.
