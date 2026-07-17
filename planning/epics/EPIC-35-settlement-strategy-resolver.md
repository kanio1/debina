---
status: in-progress
depends_on: [EPIC-12-reference-data-ownership/Story 12.1]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-1, line 1293), [MVP]"
---

# EPIC-35 — Settlement: resolver strategii i basis/mode (EPIC-SETTLE-1)

`[FREEZE]` strategia zawsze przez `(settlement_basis, liquidity_mode)`, nigdy po nazwie profilu/CSM.

`[NARROWED 2026-07-16 — dual-agent governance/backlog-redesign session, H2]`: epic-level `depends_on` narrowed from the whole `EPIC-12-reference-data-ownership` epic to `Story 12.1` specifically (schema + grants for the reference-data catalogs, `done`) — the actual capability this resolver reads (`scheme_profiles`/settlement catalogs). No story in this file ever named which part of EPIC-12 was needed; `Story 12.2` (validation/mapping/rendering catalogs, `[NO-CODE]` until Iteration 5) is unrelated to strategy-resolution logic. `EPIC-51`/`EPIC-57` show the identical un-narrowed pattern against the same target epic — flagged in `planning/capabilities.yaml` as candidates, not changed in this pass (see `planning/BACKLOG-REDESIGN.md`).

## Story 35.1 — `SettlementStrategyResolver`

status: done

depends_on: [EPIC-12-reference-data-ownership/Story 12.1]

`[NARROWED 2026-07-16]`: was `depends_on: []` at story level while the epic-level frontmatter (now narrowed above) carried the real dependency — made explicit here since this is the specific story that reads reference-data catalogs.

`[DONE 2026-07-17]`: readiness audit full PASS (EPIC-12 Story 12.1 done; resolver needs no DB/Kafka/profile lookup — it never reads `reference_data.scheme_profiles`, which is a separate, later concern for Story 35.3). Built `com.sepanexus.settlement` (new top-level Modulith module, `allowedDependencies = {}`, no schema owned yet): `SettlementBasis` (6 values), `LiquidityMode` (8 values), `SettlementStrategyKind` (6 values), `SettlementStrategyResolver` (pure, stateless, immutable `Map`-backed lookup — no Spring, no DB, no Kafka), `UnsupportedSettlementStrategyCombinationException` (fail-closed, names the exact pair, never a default/first-match/basis-only/mode-only fallback).

Mapping matrix implemented — exactly the 4 pairs `sepa-nexus-message-flow-and-data-blueprint.md` §4.11 explicitly binds via its liquidity-mode-taxonomy annotations ("DCA_POOL (gross)", "ISOLATED_SUBACCOUNT (deferred/file)", "NONE_INTERNAL (internal book)"):

| settlement_basis | liquidity_mode | strategy kind |
|---|---|---|
| GROSS_INSTANT | DCA_POOL | GROSS_INSTANT |
| NET_DEFERRED | ISOLATED_SUBACCOUNT | NET_DEFERRED |
| ACH_FILE_BATCH | ISOLATED_SUBACCOUNT | FILE_BATCH |
| INTERNAL_BOOK | NONE_INTERNAL | INTERNAL_BOOK |

Deliberately unsupported (no unambiguous source binding — fail-closed, not guessed): `BULK_CGS_LIKE`+`TECHNICAL_ACCOUNT_LIKE`, `SIMULATED_PREFUNDED_INSTANT`+`PREFUNDED_RESERVE` (both `[P1]`), any pair involving `CENTRAL_BANK_MONEY_LIKE`/`COMMERCIAL_BANK_MONEY_LIKE`/`LAB_SYNTHETIC` (source calls these "risk labels" only, never binds them to a strategy), every other basis×mode pair, and null basis/mode/both.

Test-first: `SettlementStrategyResolverTest` (17 cases: 4 legal, 6 illegal, 2 deliberately-unsupported-P1, 3 null-handling, 1 determinism, 1 exact-pair-in-message) — structural RED confirmed (`cannot find symbol` on all 4 domain types, physically written before any production class existed), semantic RED confirmed after adding compiling-but-`throw new UnsupportedOperationException` stub (`Tests run: 17, Failures: 12, Errors: 5`), GREEN after real implementation (`17/17 PASS`). Mutation-proof, all 3 caught then reverted: (1) resolver keyed on basis only (ignoring `liquidityMode` param) → `Tests run: 17, Failures: 3, Errors: 3`; (2) wrong strategy kind for `NET_DEFERRED`+`ISOLATED_SUBACCOUNT` → exactly `resolvesLegalCombination` FAIL (1); (3) default-fallback instead of throwing → exactly the 12 `rejects*` tests FAIL. `git diff --check` clean after each revert, no leftover mutation markers.

Audits: profile/CSM-name grep (`TIPS|RT1|STEP2|STET|KIR|ELIXIR|CSM|profileCode|profileName|schemeProfile`) → only Javadoc prose explaining the prohibition, zero executable logic; infrastructure grep (`JdbcTemplate|DataSource|EntityManager|Repository|SQL verbs|KafkaTemplate|@KafkaListener`) → clean; cross-module grep (`signature|paymentlifecycle|routing|egress|ledger`) → clean. `ModularityTest`/`OwnershipArchRulesTest`/`PaymentNoGodModuleTest` → `9/9 PASS` (both full-regression runs).

Full backend regression run twice (`251 tests, 0 failures, 15 errors` both times, byte-identical error set) — all 15 errors are pre-existing `ApplicationContext`/Kafka-connect-refused failures in `WalkingSkeletonIntegrationTest`/`JsonDirectIngestionTest`/`PaymentControllerTest`/`SecurityConfigTest`, none touching `com.sepanexus.settlement`, all four files last modified 2026-07-14/15 (confirmed via `git log`, untouched this session). This session's diff is exclusively new files under `com.sepanexus.settlement`, a module with `allowedDependencies = {}` (structurally cannot reach Kafka/Spring config in another module) — classified `INFRASTRUCTURE BLOCKED` for the full-regression gate only, not a Story 35.1 defect, not fixed (out of scope for a pure-domain resolver story). Targeted `verify:` and all architecture tests are independently green.

Taski:
- [x] **Zaimplementuj `SettlementStrategyResolver(settlement_basis, liquidity_mode)`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementStrategyResolverTest*` → `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` — PASS (2026-07-17, confirmed twice).

## Story 35.2 — Zakaz przełączania po nazwie profilu (ArchUnit)

status: not-started
depends_on: [Story 35.1]

`[READINESS NOTE 2026-07-17]`: analytically `READY` — `Story 35.1` is `done`, `com.sepanexus.settlement.SettlementStrategyResolver` exists as the concrete class an ArchUnit rule can target (no `switch`/`if` on profile/CSM name anywhere reachable from strategy selection), and the rule needs no DB/Kafka — same pure-ArchUnit shape as `com.sepanexus.OwnershipArchRulesTest`/`ModularityTest`.

Taski:
- [ ] **Reguła ArchUnit: zero `switch`/`if` po nazwie profilu/CSM w kodzie wyboru strategii.**
      `verify: ./mvnw -f backend test -Dtest=*NoProfileNameSwitchTest*`

## Story 35.3 — Zamrożony snapshot profilu na attempt

status: not-started
depends_on: [Story 35.1]

`[CAPABILITY-BLOCKED 2026-07-17]`: `settlement_profile_snapshots` must freeze `basis+mode+finality + reference-data version` per attempt (§4.11 "Config vs runtime"), but `reference_data.scheme_profiles` (migration `V9`) has no `settlement_basis`/`liquidity_mode`/`finality_rule` columns today — only a narrower `family text` (`GROSS_INSTANT | NET_DEFERRED` per the V9 comment). No source document defines how `family`/`service_level`/`netting_mode` map onto the 6-value `SettlementBasis`/8-value `LiquidityMode` taxonomy this story's own resolver (35.1) now implements. Not implementable without either (a) a source-backed DDL extension to `scheme_profiles` (or a linked table) adding these columns, or (b) an explicit user/team decision on the mapping — recorded here rather than guessed. `Story 35.1` itself never reads this table and is unaffected.

Taski:
- [ ] **`settlement_profile_snapshots` zamrożony per attempt.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementProfileSnapshotTest*`
