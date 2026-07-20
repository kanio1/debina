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

- Completed primary: EPIC-51 Stories 51.1 and 51.2.
- Rejected/source-blocked: EPIC-51 Story 51.3 (missing allowed-message-set representation),
  EPIC-28.2–28.4 (explicit Iteration-5 validation-catalog gate), EPIC-32.4 (no reversal command
  contract), EPIC-33.3, EPIC-42.1, EPIC-29.1/44, and egress continuation dependent on those gaps.
- Reserve audit: EPIC-57 profile story has source ownership but no DDL contract; EPIC-65 case
  schema names tables but does not provide a DDL contract. Both require source-backed table shapes
  rather than guessed schemas.

## Implemented capability

- `reference_data.profile_route_priorities` implements source §4.10's exact candidate ordering
  key. `reference_data_role` is sole writer; `routing_role` can read only.
- `routing.RouteCandidateResolver` uses a narrow read port, filters only active profile windows,
  and orders candidate output by source priority. It makes no decision, payment/FSM, settlement,
  money, Kafka, or finality mutation.
- Migration impact: additive V45, new empty catalog, no backfill or existing-table lock, no RLS
  because static reference-data uses ownership grants. The only new index-like structure is the
  source-specified primary key used by exact candidate lookups.

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

## Decisions, commits, and next work

- Class A: candidate ordering is priority ascending; UUID is only a deterministic tie presentation,
  not selection/fallback behavior. No Class B ADR was needed.
- Commits: pending final diff inspection.
- Next: commit this verified routing slice, then continue the reserve audit without revisiting the
  unchanged blockers.
