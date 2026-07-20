# DEBINA AUTONOMOUS CAPABILITY WAVE 3

## Baseline and historical reconciliation

- Actual baseline: `4ce7197e10bf22ec47f2d1b4e588a8c94c946c86` (`main`, clean worktree).
- `4ce7197` is descended from `ac9aa47`; its root handoff is skill-activation-local only. The
  prior seven verified backend stories remain completed and were not replayed.
- Stable source hashes at start: `README.md` `0d44b730ad1f26dda693e01b79d3d7ec38c5973b`, planning
  index `fb2a3fbeaadf686439faed64c432318721f679e0`, capability graph
  `de00c4f08ea5249e18313a1d5715d1080f5e5edb`, story inventory
  `65401715b7e081134f72838a0797615273116023`.

## Active skills

- `artifact-derived-planning`, `debina-runtime-proof-testing`, `spring-modulith-module`,
  `postgres-rls-migration`, `sepa-nexus-flyway-safe-change`, `sepa-nexus-database-testing`,
  `sepa-nexus-database-review`, and `sepa-nexus-payments-data-integrity` guided this slice.
- ISO lineage and Kafka payment-contract registry entries are `ACTIVE`; authoring paths are under
  `.claude/skills`, `.agents/skills -> ../.claude/skills` resolves, each `SKILL.md` and its four
  direct references exist, and both routing/regression eval files exist. No implicit routing was
  observed in this session.

## One-pass blocker revalidation

- EPIC-33 Story 33.3 remains `SOURCE-BLOCKED`: the blueprint and ADR-N8 blobs are identical to
  `4460586` (`f8667131109858da5ff7f3d3a92d74d31a1df900` and
  `e781dcdd63de3c5ac1b9510478d113902304cc35`). The narrative has `sla_seconds` and an e2e target,
  but no topic-catalog row, producer, consumer, payload, key, timer start/pause/cancellation, or
  breach consequence for `payment.sla.breached`.
- EPIC-42 Story 42.1 remains `CAPABILITY-BLOCKED`: the source requires case-owned request intake
  through `ReturnPaymentRequestPort`, but no case schema/port/normal intake contract exists. No
  return transition, reversal, or eligibility rule was invented.
- EPIC-29 Story 29.1 / EPIC-44 remain `SOURCE-BLOCKED`: §6.7/§6.8 name concepts but do not supply
  complete outbound-artifact DDL or a source-compatible immutable render/egress profile snapshot
  representation. No Class B ADR is defensible before that representation exists.

## Candidate audit and queues

- Completed primary: EPIC-51 Stories 51.1 and 51.2; EPIC-52 Stories 52.1–52.3; EPIC-53
  Stories 53.1–53.3. This is the locked eight-story maximum; Story 53.4 remains gated by the
  GraphQL read-model capability and is outside this database tranche.
- Rejected/source-blocked: EPIC-51 Story 51.3 (missing allowed-message-set representation),
  EPIC-28.2–28.4 (explicit Iteration-5 validation-catalog gate), EPIC-32.4 (no reversal command
  contract), EPIC-33.3, EPIC-42.1, EPIC-29.1/44, and egress continuation dependent on those gaps.
- Reserve audit: EPIC-57 profile story has source ownership but no DDL contract; EPIC-65 case
  schema names tables but does not provide a DDL contract. Both require source-backed table shapes
  rather than guessed schemas.
- EPIC-73 Story 73.1 is `SOURCE-BLOCKED`: §3.4 names raw-file archive fields but the inbound
  `SubmitFile` boundary leaves acceptance metadata, business-date derivation, and file-signature
  policy unspecified. Its declared `sender_id`/`branch_id` archive facts cannot be inferred from
  the current raw-archive surface without defining that external intake contract.

## Implemented capability

- `reference_data.profile_route_priorities` implements source §4.10's exact candidate ordering
  key. `reference_data_role` is sole writer; `routing_role` can read only.
- `routing.RouteCandidateResolver` uses a narrow read port, filters only active profile windows,
  and orders candidate output by source priority. It makes no decision, payment/FSM, settlement,
  money, Kafka, or finality mutation.
- Migration impact: additive V45, new empty catalog, no backfill or existing-table lock, no RLS
  because static reference-data uses ownership grants. The only new index-like structure is the
  source-specified primary key used by exact candidate lookups.
- V46 adds the source-defined static participant capability and eligibility-rule catalogs; V47
  creates the routing-owned per-profile reachability state and ADR-N5 outbox/inbox boundary.
  Neither migration defines an eligibility-rule vocabulary, routing decision, fallback, or Kafka
  business contract.
- V48 adds §4.10's immutable route decision, candidate evidence, and explanation snapshot tables.
  The Class A choice to scope child rows through their tenant-scoped decision FK preserves the
  source shapes without denormalizing tenant facts. It introduces no route command, selection
  behavior, payment mutation, `payment.routed` payload, or `route.failed` Kafka contract.

## Verification and review

- Initial RED: targeted suite failed 2/6 because test isolation/boundary expectations were wrong;
  fixes made each invariant explicit.
- GREEN: `./mvnw -f backend test -Dtest=RouteCandidateResolutionTest,RouteCandidateCatalogMigrationTest,RouteCandidateCatalogMigrationUpgradePathTest` → 6 tests, 0 failures, 0 errors.
- PostgreSQL 18 Testcontainers proof covers fresh migration, V44→V45 representative upgrade,
  owner write, routing read, foreign/routing writer denial (`42501`), and duplicate catalog key
  rejection (`23505`).
- Mutation proof: replace the active-window filter with `true`; `RouteCandidateResolutionTest`
  failed 2/3. The exact filter was immediately restored; green suite rerun is pending final gate.
- Independent database review: **PASS**. The additive V45 matches §4.10's exact key and ownership;
  it has no money, tenant row, source mutation, cross-schema write, security-definer function, or
  unreviewed index. `PUBLIC` is revoked, the owner/foreign negative grants are runtime-proven, and
  fresh plus V44→V45 Testcontainers evidence is present. No Kafka/ISO contract surface changed.
- Regression 1: `./mvnw -f backend test` → 446 tests, 0 failures, 0 errors (log:
  `/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3/backend-regression-1.log`). Planning and skill
  validators pass after regenerating the generator-owned story inventory.
- Regression 2: `./mvnw -f backend test` → 446 tests, 0 failures, 0 errors (log:
  `/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3/backend-regression-2.log`).
- EPIC-52 focused GREEN: `RoutingEligibilityAndReachabilityMigrationTest` +
  `RoutingEligibilityAndReachabilityUpgradePathTest` → 3 tests, 0 failures, 0 errors. Fresh and
  V45→V47 Testcontainers migrations prove owner/read/foreign-writer/dispatcher boundaries. A
  temporary removal of routing's catalog-read grant failed with PostgreSQL `42501`; it was reverted.
- Independent database review: **PASS**. V46 reproduces §4.10's exact two catalog shapes; V47
  reproduces the reachability shape and supplies the required ADR-N5 per-schema messaging tables.
  The migrations are additive and empty, have no tenant rows/RLS trigger, backfill, money fields,
  cross-schema write, or security-definer function. `PUBLIC` is revoked; real-role fresh and
  upgrade evidence proves least privilege, including dispatcher publication-only access.
- EPIC-53 RED: before V48, `RouteDecisionPersistenceMigrationTest` failed 2 errors with
  PostgreSQL relation `routing.route_decisions` absent (log:
  `/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3/route-53-red.log`).
- EPIC-53 GREEN: fresh and V47→V48 `RouteDecisionPersistenceMigrationTest` +
  `RouteDecisionPersistenceUpgradePathTest` → 3 tests, 0 failures, 0 errors; the combined routing
  51–53 suite → 12 tests, 0 failures, 0 errors (log:
  `/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3/routing-51-53-green.log`). The proof covers empty/same/
  cross-tenant GUC paths, parent/child RLS, grants, immutability, and representative upgrade.
- Mutation proof: temporarily changing the candidate-results `USING` policy to `true` made the
  cross-tenant assertion observe one row (1/0/1 expected failure; log:
  `/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3/route-53-rls-mutation.log`); the exact policy was
  immediately restored.
- Independent database review: **PASS**. V48 is additive and source-shaped; parent RLS plus
  FK-derived child policies cover all tenant-visible rows, `PUBLIC` is revoked, and `routing_role`
  has no update/delete grant. There is no backfill, source-table write, money/finality field,
  security-definer function, or Kafka/ISO contract change.
- Final regression 1: `./mvnw -f backend test` → 457 tests, 0 failures, 0 errors (log:
  `/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3/backend-regression-4-retry.log`).
- Final regression 2: `./mvnw -f backend test` → 457 tests, 0 failures, 0 errors (log:
  `/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3/backend-regression-5.log`). The first attempt was
  deliberately excluded after a corrected dynamic test-harness mapping; the focused corrected
  `OutboxDispatcherNoDomainWriteSweepTest` + route decision proof passed 30/0/0 before both
  counted full runs.

## Decisions, commits, and next work

- Class A: candidate ordering is priority ascending; UUID is only a deterministic tie presentation,
  not selection/fallback behavior. No Class B ADR was needed.
- Commits: `4722014 feat(routing): resolve active route candidates`; `c3f542f feat(routing): add
  eligibility and reachability catalogs`; final EPIC-53 routing decision slice is committed with
  this Wave 3 evidence.
- Class A technical correction: the dynamic dispatcher outbox sweep recognizes routing's
  source-defined `topic,type` outbox shape alongside egress, preserving its all-outbox
  least-privilege invariant without normalizing production schemas.
- Planning inventory, capability graph, skill validation, `git diff --check`, and generated-output
  restoration pass. Next: re-audit the highest-value independent candidate; do not revisit the
  one-pass known blockers without new source evidence.
