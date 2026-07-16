---
status: not-started
depends_on: [EPIC-12-reference-data-ownership/Story 12.1]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-1, line 1293), [MVP]"
---

# EPIC-35 — Settlement: resolver strategii i basis/mode (EPIC-SETTLE-1)

`[FREEZE]` strategia zawsze przez `(settlement_basis, liquidity_mode)`, nigdy po nazwie profilu/CSM.

`[NARROWED 2026-07-16 — dual-agent governance/backlog-redesign session, H2]`: epic-level `depends_on` narrowed from the whole `EPIC-12-reference-data-ownership` epic to `Story 12.1` specifically (schema + grants for the reference-data catalogs, `done`) — the actual capability this resolver reads (`scheme_profiles`/settlement catalogs). No story in this file ever named which part of EPIC-12 was needed; `Story 12.2` (validation/mapping/rendering catalogs, `[NO-CODE]` until Iteration 5) is unrelated to strategy-resolution logic. `EPIC-51`/`EPIC-57` show the identical un-narrowed pattern against the same target epic — flagged in `planning/capabilities.yaml` as candidates, not changed in this pass (see `planning/BACKLOG-REDESIGN.md`).

## Story 35.1 — `SettlementStrategyResolver`

status: not-started
depends_on: [EPIC-12-reference-data-ownership/Story 12.1]

`[NARROWED 2026-07-16]`: was `depends_on: []` at story level while the epic-level frontmatter (now narrowed above) carried the real dependency — made explicit here since this is the specific story that reads reference-data catalogs.

Taski:
- [ ] **Zaimplementuj `SettlementStrategyResolver(settlement_basis, liquidity_mode)`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementStrategyResolverTest*`

## Story 35.2 — Zakaz przełączania po nazwie profilu (ArchUnit)

status: not-started
depends_on: [Story 35.1]

Taski:
- [ ] **Reguła ArchUnit: zero `switch`/`if` po nazwie profilu/CSM w kodzie wyboru strategii.**
      `verify: ./mvnw -f backend test -Dtest=*NoProfileNameSwitchTest*`

## Story 35.3 — Zamrożony snapshot profilu na attempt

status: not-started
depends_on: [Story 35.1]

Taski:
- [ ] **`settlement_profile_snapshots` zamrożony per attempt.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementProfileSnapshotTest*`
