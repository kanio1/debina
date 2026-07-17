---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OUT-1, line 1253), [MVP]"
---

# EPIC-43 — Egress: szyna wychodząca (EPIC-OUT-1)

## Story 43.1 — `outbound_messages` + dispatcher SKIP LOCKED

status: done

depends_on: []

Opis: test na podwójny dyspozytor.

`[DONE 2026-07-17]`: readiness full PASS — EPIC-09 pattern established, no decision gate, `verify:` executable as written. Built `com.sepanexus.egress` (new top-level Modulith module, `allowedDependencies = {}`): `egress.outbound_messages` (§6.4 DDL; `state` CHECK uses §6.2's fuller named lifecycle — `REQUESTED`/`RENDERED`/`SIGNED`/`CLAIMED_FOR_DELIVERY`/`DELIVERED`/`RECEIPT_RECEIVED`/`CLOSED`/`DELIVERY_FAILED`/`RETRY_SCHEDULED`/`DEAD_LETTERED`/`MANUAL_INTERVENTION` — the more detailed, more recently-elaborated of the two source descriptions, and "CLAIMED_FOR_DELIVERY" textually matches this story's own claim/dispatch terminology), two-level tenant/branch RLS (`payments`' V12 precedent) plus a narrow `system_relay` dispatcher policy; `egress.outbox_events`/`inbox_events` (ADR-N5 pair, required alongside egress's own first migration) with `outbox_dispatcher_role` created for the first time in this repository, scoped only to `egress` (retrofitting `payment.outbox_events` is `EPIC-18`'s own, separate, not-started scope — not touched here).

Deliberately deferred to their own later stories' migrations rather than built speculatively now: `payment_id`/`cycle_id`/`file_id` correlation, `signature bytea` (43.2), `attempts`/`next_retry_at` (43.3), `batch_group` (43.4), `delivered_at` (43.4/43.5).

`OutboundMessageDispatcher.claimPendingBatch`: one atomic CTE statement (`SELECT ... FOR UPDATE SKIP LOCKED` then `UPDATE`, never two separate statements) — horizontally safe against any number of concurrent dispatchers. No rendering, signing, transport, retry, or receipts — claim-only, per this story's own scope boundary.

Test-first: `DoubleDispatcherTest` (real concurrent claim via two threads with disjoint batches and no double-claim, rollback-releases-for-retry via `DataSourceTransactionManager`/`TransactionTemplate`, claim-limit respected, state-predicate regression) — GREEN on first run. Also includes a dedicated, deterministic proof that splitting the claim SELECT and UPDATE across two separate auto-committing statements allows a double-claim: a genuine thread-based mutation of the concurrency test turned out to be racy in *both* directions (`SKIP LOCKED` can correctly prevent the overlap if both SELECTs are issued closely enough together in real time, so the naive "mutate the dispatcher, rerun the same concurrency test" approach did not reliably fail — caught empirically after 3 non-deterministic runs) — the dedicated test instead forces the exact dangerous ordering sequentially, deterministically, with no thread-scheduling luck involved. Mutation-proof, 4/4 caught then reverted: (1) missing `SKIP LOCKED` → `twoDispatchersClaimDisjointBatchesWithNoDoubleClaim` FAIL; (2) split SELECT/UPDATE (verified via the dedicated deterministic test, not the concurrency test); (3) missing `state = 'REQUESTED'` predicate → `rowsNotInRequestedStateAreNeverClaimed` FAIL; (4) `outbox_dispatcher_role` granted a domain-table write → `outboxDispatcherRoleCannotWriteOutboundMessages` FAIL.

`EgressOwnershipTest` (fresh-migration evidence, `egress_role` positive / foreign role negative, `outbox_dispatcher_role`'s narrow claim-only grant proven not to extend to `outbound_messages` or `payment.payments`) + `EgressMigrationUpgradePathTest` (V21-with-representative-prior-payment-data → V24, data survives, schema immediately usable) — both GREEN. Caught and fixed a real bug during test-first development: `outbox_dispatcher_role` was initially missing `GRANT USAGE ON SCHEMA egress`, without which the table-level grant was unreachable.

Targeted: `DoubleDispatcherTest` 5/5, `EgressOwnershipTest` 6/6, `EgressMigrationUpgradePathTest` 1/1. `ModularityTest`/`OwnershipArchRulesTest`/`PaymentNoGodModuleTest` 9/9. Full regression (as part of this session's multi-phase run): all green (see `HANDOFF.md`).

Taski:
- [x] **Migracja `egress.outbound_messages` + dispatcher `SKIP LOCKED`.**
      `verify: ./mvnw -f backend test -Dtest=*DoubleDispatcherTest*` → `Tests run: 5, Failures: 0, Errors: 0` — PASS (2026-07-17).

## Story 43.2 — Renderer (Prowide) + integracja z SignatureSigningPort

status: not-started
depends_on: [Story 43.1, EPIC-31-signature-module/Story 31.3A]

`[SPLIT 2026-07-16 — dual-agent governance/backlog-redesign session, H1]`: `depends_on` narrowed from `EPIC-31-signature-module/Story 31.3` to `Story 31.3A` — the old, unsplit `Story 31.3` itself depended on the whole of this epic (`EPIC-43`), which transitively includes this very story, i.e. a real cycle (31.3 → EPIC-43 → 43.2 → 31.3). `Story 31.3A` is the narrowed, standalone half of the old 31.3 (signing capability only, no `EPIC-43` dependency — see `EPIC-31-signature-module.md`). The invocation-from-egress detail formerly also described (redundantly) in the old Story 31.3's task text now lives only here, since it's this story's own scope. See `planning/BACKLOG-REDESIGN.md` for the full writeup.

`[READINESS NOTE 2026-07-17]`: analytically `READY` — both dependencies (`Story 43.1`, `EPIC-31 Story 31.3A`) are `done`. Deliberately not started this session (out of this session's explicitly forbidden scope: renderer, signing integration, transport).

Taski:
- [ ] **Renderer oparty o Prowide + wywołanie `SignatureSigningPort` z `egress`, guardowane flagą `signing_required`, podpis detached przechowany na outbound message.**
      `verify: ./mvnw -f backend test -Dtest=*EgressRendererSignerTest*`

## Story 43.3 — Retry/backoff/ABANDONED + DLQ

status: not-started
depends_on: [Story 43.1]

Taski:
- [ ] **Polityka retry/backoff, stan `ABANDONED`, DLQ.**
      `verify: ./mvnw -f backend test -Dtest=*EgressRetryBackoffDlqTest*`

## Story 43.4 — Kolektor wsadowy + pliki wychodzące

status: not-started
depends_on: [Story 43.1]

Taski:
- [ ] **Kolektor wsadowy budujący `outbound_files`.**
      `verify: ./mvnw -f backend test -Dtest=*BatchCollectorTest*`

## Story 43.5 — Korelacja potwierdzenia dostawy

status: not-started
depends_on: [Story 43.1]

Taski:
- [ ] **Korelacja `delivery_receipts` z odpowiadającym `outbound_message`.**
      `verify: ./mvnw -f backend test -Dtest=*DeliveryConfirmationCorrelationTest*`
