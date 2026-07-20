# Debina Autonomous Capability Wave 4

## Baseline and historical reconciliation

- Actual baseline: `e9f41d19d874f2239ee2e90332aa9a5259e7fa8a` on `main`.
- Previous durable record: `planning/programs/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3.md`.
- Verified ancestry includes Wave 3 commits `4722014`, `c3f542f`, `3ace08d` and
  `e9f41d1`.  EPIC-51 stories 51.1--51.2, EPIC-52 stories 52.1--52.3 and
  EPIC-53 stories 53.1--53.3 are historical completion evidence, not Wave 4
  delivery.
- The root handoff retains the earlier ISO/Kafka skill activation and Wave 3
  routing tranche.  It is supplemented, not replaced, by this record.

## Discovery and stable sources

- `.agents/skills` is the `../.claude/skills` symlink and resolves to the
  repository authoring tree.  Registry entries for
  `debina-iso20022-validation-lineage` and
  `debina-kafka-payment-contract` are `ACTIVE`.
- Explicit skill loading is used where applicable.  No implicit-routing claim
  is made in this wave.

| Source | Blob | Applicable evidence |
|---|---|---|
| `README.md` | `4ba853013a61b43c519beb9891fd5892fa9a7289` | frozen ADR index; routing remains in-process |
| `HANDOFF.md` | `6d84e428058da05cfc45c261ce2d1e75168d2b5c` | prior domain and Wave 3 evidence |
| `planning/README.md` | `3b79ed5a0e931eb3d6ba246f7f8dd432902273bb` | index is stale for completed EPIC-51--53 stories |
| `planning/capabilities.yaml` | `b65d13d692daaff7400b2a730199e9f32973909a` | dependency/readiness graph |
| `planning/capability-graph.json` | `c772c67243a712b6d9b26370c0b85b3dd617e4c1` | generated graph view |
| `planning/story-inventory.json` | `7861c3ad66b722e40fd6f4d067037ea358d1a91f` | generated inventory view |
| `sepa-nexus-message-flow-and-data-blueprint.md` | `d3c3a99ac0fedfe1d87dbe93a845aef4e4900073` | §3.9 routing ownership, §4.10 fallback and immutable decision DDL, §8 routing sequence |
| `sepa-nexus-blueprint-ownership-integration.md` | `cea2895a4c3f9b309f713daf8906a243f4561632` | one-writer-per-schema and module ownership |

## Candidate inventory and locked queues

| Story | Classification | Evidence and scope |
|---|---|---|
| 51.4 | READY | §3.9 authorizes a module-owned, read-only candidate projection; no external API or mutation is needed. |
| 54.1 | READY | §4.10 defines static `reference_data.profile_fallback_rules`; implementation needs the next Flyway migration and role/RLS proof. |
| 54.2 | READY after Class B ADR | §4.10 authorizes an explicit fallback outcome but its `fallback_rule_id uuid` conflicts with the fallback table's composite key. |
| 54.3 | READY | §4.10 explicitly rejects implicit instant-to-batch fallback; prove the selected behavior after 54.2. |
| 56.1 | READY after fallback policy | Source-backed deterministic fixtures can exercise the real resolver and fallback policy. |
| 56.2 | READY after fixtures | Pairwise test coverage can cover source-backed routing/fallback dimensions and a mutation detector. |
| 56.3 | SOURCE-BLOCKED | No authoritative amount-limit or amount/currency boundary vocabulary exists. Exact currency matching is insufficient to invent lower/exact/upper amount rules. |
| 54.4 | SOURCE-BLOCKED | No source-backed profile-to-CSM or multi-CSM fallback semantics. |
| 55.1 | CAPABILITY-BLOCKED | No settlement-owned cut-off/cycle read surface exists; an empty port would not deliver the story. |
| 53.4 | CAPABILITY-BLOCKED | GraphQL read-model capability is absent. |
| 52.4 | CAPABILITY-BLOCKED | Control Room/frontend capability is absent. |

Primary queue: 51.4, 54.1, 54.2, 54.3, 56.1 and 56.2.

Reserve queue: none.  The conditional reserve candidates are blocked as recorded
above.  Story 56.3 remains source-blocked rather than being reduced to an
unsupported partial amount rule.

## Required representation decision

`§4.10` currently gives `profile_fallback_rules` a composite `(profile_id,
priority)` primary key while `routing.route_decisions.fallback_rule_id` is a
UUID.  Before implementing 54.2, an ADR must compare: a surrogate UUID with
the source uniqueness retained; composite components copied into immutable
decision evidence; and a versioned immutable fallback-rule identity.  The
decision must retain explicit fallback only, immutable route evidence,
`reference_data` sole write ownership and `routing` read-only access to static
configuration.  `condition text` is stored but no condition language exists:
non-null conditions fail closed and are never evaluated dynamically.

## Known unchanged blockers

The Wave 3 classifications are retained without a broad re-audit: EPIC-51.3
(allowed-message-set representation), EPIC-29.1/44 (outbound artifacts and
profile snapshot), EPIC-33.3 (SLA event/timing policy), EPIC-42.1
(return/requested-payment intake), EPIC-57 (reconciliation DDL), EPIC-65
(case DDL) and EPIC-73.1 (file-intake policy).

## Implementation and verification log

- Class B ADR: `ADR-N12-routing-fallback-rule-identity.md` selected a surrogate
  UUID while preserving source `(profile_id, priority)` uniqueness. It rejected
  an unbound UUID, composite decision replacement and premature rule-version
  catalog. Non-null conditions are fail-closed and unexecuted.
- V49 adds `reference_data.profile_fallback_rules`; V50 binds the existing
  `routing.route_decisions.fallback_rule_id` by FK and a flag/link consistency
  constraint. The write path remains `reference_data_role` → static catalog,
  `routing_role` → routing evidence only.
- 51.4: `RouteCandidatesReadModel` is a typed internal projection over the
  existing candidate resolver, with no GraphQL/REST API or mutation.
- 54.1--54.3: `FallbackPolicy` selects explicit ordered unconditional rules;
  `FallbackDecisionEvidenceRecorder` verifies the static rule and atomically
  writes decision, candidate and explanation evidence under transaction-local
  tenant context. No configuration gives no selection; unsupported condition
  gives no selection/evidence.
- 56.1--56.2: deterministic fixed fixtures replay the real resolver/policy;
  four pairwise scenarios cover all pairs across profile match, condition and
  input order. Reversing the production priority comparator made two named
  assertions fail, then was immediately reverted.
- Focused proof so far: initial structural RED `FallbackRuleCatalogMigrationTest`
  failed `2/0/2` against V48 (missing relation); after V49 it passed `2/0/0`.
  The combined routing/migration suite passed `17/0/0`; the later restored
  unit/pairwise suite passed `8/0/0`. PostgreSQL 18 fresh and V48→V50 upgrade
  paths passed, with RLS, grants, `PUBLIC`, FK, rollback, replay and
  cross-tenant assertions in the named migration tests.

### Database review

**Verdict: PASS.** V49/V50 are additive forward migrations. V49 creates a
small empty static catalog; V50's only existing-table change is a validated
flag/link check and FK whose V48 rows are null-compatible. The source ordering
uniqueness, ownership grants, `PUBLIC` revocation, RLS parent/child behavior,
append-only decision grants, cross-schema read-only reference and FK integrity
are covered by isolated PostgreSQL 18 fresh/upgrade tests. No index is added
without a query pattern, no dynamic condition evaluation or `SECURITY DEFINER`
function is introduced, and no schema other than `reference_data` or `routing`
is changed. A failed child insert rolls back all routing evidence; a duplicate
technical decision identity cannot create a second row. No blocking database
finding remains.

### Final gate before handoff commit

- Focused routing + PostgreSQL + Modulith suite: `25/0/0 PASS`.
- Fresh migration and V48→V50 upgrade: PASS in the named isolated PostgreSQL
  18 tests; no local Compose database was used as a fixture.
- Mutation: reversing `FallbackPolicy` priority caused
  `FallbackSelectedOutcomeTest` to fail two assertions; it was immediately
  reverted, then `8/0/0 PASS` reran.
- Planning/governance validators, capability validator and all active-skill
  validators: PASS. `git diff --check`: PASS.
- Backend regressions from clean build state: `475/0/0 PASS` in 3:31 and
  `475/0/0 PASS` in 3:30. Frontend/BFF: N/A; no frontend or BFF surface changed.
- Kafka and ISO contract proofs: N/A; no Kafka topic, producer/consumer or ISO
  parser/lineage surface changed. Existing active skills were sanity-checked;
  only explicit skill use is claimed.

## Next work

Re-audit the next highest-value backlog family outside completed routing. Do
not resume EPIC-51.3, 54.4 or 56.3 without their missing authoritative source;
do not revisit Wave 3 known blockers without changed source hashes.
