---
status: not-started
depends_on: [EPIC-44-egress-profile-artifact-taxonomy]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-2, line 1304), [MVP]"
---

# EPIC-45 — Egress: cykl życia wiadomości wychodzącej (EPIC-EGRESS-2)

## Story 45.1 — Ujednolicony cykl życia REQUESTED→…→CLOSED

status: not-started (`[CAPABILITY-BLOCKED]`)
depends_on: []

`[CAPABILITY-BLOCKED 2026-07-17 — egress ownership train, Phase B]`: canonical lifecycle per §6.2 is `REQUESTED→RENDERED→SIGNED→CLAIMED_FOR_DELIVERY→DELIVERED→RECEIPT_RECEIVED→CLOSED` (also the exact `CHECK` values already used by `egress.outbound_messages`, Story 43.1). But the branch `RENDERED→SIGNED` versus `RENDERED→CLAIMED_FOR_DELIVERY` (when `signing_required = false`) is decided by the `egress_profile` snapshot — which does not exist (`EPIC-44` Story 44.1, `[CAPABILITY-BLOCKED]`). Without it, there is no source-backed way to implement the branching condition; guessing a default would invent policy the source explicitly assigns to the profile model. Not implemented.

Taski:
- [ ] **Zaimplementuj FSM `REQUESTED→…→CLOSED`.** `[CAPABILITY-BLOCKED]`
      `verify: ./mvnw -f backend test -Dtest=*OutboundMessageLifecycleFsmTest*`

## Story 45.2 — Render→sign→deliver

status: not-started (`[CAPABILITY-BLOCKED]` — transitive + overlap)
depends_on: [Story 45.1, EPIC-31-signature-module/Story 31.3A]

`[REPOINTED 2026-07-16]`: `EPIC-31-signature-module/Story 31.3` was split into `31.3A`/the egress-integration detail (see `EPIC-31-signature-module.md`, `planning/BACKLOG-REDESIGN.md`) — this reference updated to the surviving story ID, minimally, without re-deriving this epic's own scope (not one of this session's deep-dived epics).

`[CAPABILITY-BLOCKED 2026-07-17 — transitive from Story 45.1]`, plus `[PLANNING-OVERLAP]`: this story's "render→sign→deliver chain" and `EPIC-43` Story 43.2's "renderer (Prowide) + `SignatureSigningPort` integration" describe the same chain from two different epics. Neither has been implemented (both blocked independently — 43.2 on the §6.4-vs-§6.7 render/transport field-overlap conflict and the missing `ArtifactRenderPort`/Prowide decision; 45.2 transitively on 45.1). Recorded here so a future session does not build the same chain twice under two different story IDs — whichever is unblocked first should absorb the other's scope, not duplicate it.

Taski:
- [ ] **Łańcuch render→sign→deliver.** `[CAPABILITY-BLOCKED]`, `[PLANNING-OVERLAP]` z `EPIC-43` Story 43.2
      `verify: ./mvnw -f backend test -Dtest=*RenderSignDeliverChainTest*`

## Story 45.3 — Dispatcher SKIP LOCKED

status: done

depends_on: [EPIC-43-egress-rail-outbound-dispatch/Story 43.1]

`[DEPENDENCY NARROWED 2026-07-17 — egress ownership train, Phase B]`: was `depends_on: [Story 45.1]` (now `[CAPABILITY-BLOCKED]`). This story is explicitly `[SHARED CAPABILITY]` with `EPIC-43` Story 43.1 — the dispatcher (`OutboundMessageDispatcher.claimPendingBatch`, `FOR UPDATE SKIP LOCKED`) already exists, is mutation-proofed (4/4), and does not depend on the full lifecycle FSM Story 45.1 would add — claiming a row from `REQUESTED` to `CLAIMED_FOR_DELIVERY` needs no `RENDERED`/`SIGNED` intermediate state to exist first. Narrowed directly to `Story 43.1`.

`[DONE 2026-07-17]`: `[SHARED CAPABILITY]`, implemented by `EPIC-43` Story 43.1, re-verified again in this session (not re-implemented — no second `OutboundMessageDispatcher`, no second `DoubleDispatcherTest`). Rerun this session, unchanged: real concurrent claim via two threads with disjoint batches and no double-claim, rollback-releases-for-retry, claim-limit respected, state-predicate regression, plus a dedicated deterministic split-SELECT/UPDATE regression proof. Confirms the existing implementation still exactly covers this story's own capability after this session's unrelated `V28` migration (`outbox_dispatcher_role` grant extension, Story 18.2) was added on top — no interaction, no regression.

`5/5 PASS` (unchanged from Story 43.1's own record — see that story for the full mutation-proof history, 4/4 caught: missing `SKIP LOCKED`, split SELECT/UPDATE, missing state predicate, `outbox_dispatcher_role` granted a domain-table write). Not re-mutated in this session — re-mutating an already mutation-proofed shared capability without any code change would add no new evidence.

Story 45.1 remains `[CAPABILITY-BLOCKED]` — not marked `done`, not implemented, per this story's own scope boundary.

Opis: współdzielony z EPIC-43 Story 43.1.

Taski:
- [x] **Dispatcher `SKIP LOCKED` na poziomie cyklu życia wiadomości.**
      `verify: ./mvnw -f backend test -Dtest=*DoubleDispatcherTest*` → `Tests run: 5, Failures: 0, Errors: 0` — PASS (2026-07-17, re-verified). Współdzielony z EPIC-43.

## Story 45.4 — Asercja tylko-transport

status: not-started
depends_on: [Story 45.1, EPIC-14-egress-boundary-ownership/Story 14.2]

Taski:
- [ ] **Test: cykl życia wiadomości nie zmienia stanu finalności/płatności.**
      `verify: ./mvnw -f backend test -Dtest=*EgressTransportOnlyTest*`
