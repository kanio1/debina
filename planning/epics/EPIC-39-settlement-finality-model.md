---
status: not-started
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-5, line 1297), [MVP]"
---

# EPIC-39 — Settlement: model finalności (EPIC-SETTLE-5)

`[FREEZE]` accepted/posted/delivered ≠ final. Finalność to jawna, konfigurowana profilem reguła.

`[SAFETY-CORRECTION 2026-07-20]`: `payment` history no longer falsely derives finality from a
terminal business-FSM state; V30 corrects known `REJECTED`/`DISPATCHED` false positives and targeted
fresh/upgrade tests prove it. This negative repair does not create `finality_rule`, `FinalityPolicy`,
`settlement_finality_records`, a snapshot, or a `finality_at` authority. Stories 39.1–39.4 therefore
remain `DECISION-BLOCKED`/`CAPABILITY-BLOCKED`, not done.

## Story 39.1 — Katalog `finality_rule`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja katalogu `finality_rule`.**
      `verify: psql -c "\d reference_data.finality_rule"` (lub odpowiedni schemat wg §4.11).

## Story 39.2 — `FinalityPolicy` + `settlement_finality_records`

status: not-started
depends_on: [Story 39.1]

Taski:
- [ ] **`FinalityPolicy` + tabela `settlement_finality_records`.**
      `verify: ./mvnw -f backend test -Dtest=*FinalityPolicyTest*`

## Story 39.3 — Test: accepted/posted ≠ final

status: not-started
depends_on: [Story 39.2]

Taski:
- [ ] **Test: status `accepted`/`posted` nie ustawia finalności bez jawnej reguły profilu.**
      `verify: ./mvnw -f backend test -Dtest=*AcceptedPostedNotFinalTest*`

## Story 39.4 — Test: dostawa ≠ finalność

status: not-started
depends_on: [Story 39.2]

`[CYCLE-DETECTED, naprawione 2026-07-16 — dual-agent governance/backlog-redesign session]`: `depends_on` no longer references `EPIC-14-egress-boundary-ownership/Story 14.3`. That back-reference, combined with `EPIC-14` Story 14.3's own (unqualified, whole-epic) dependency on `EPIC-39`, formed a real cycle (39.4 → 14.3 → all of EPIC-39 → 39.4). This story and `EPIC-14` Story 14.3 assert the exact same thing (`DeliveredNotFinalTest`) — a shared-test pair, not a blocking dependency in either direction; whichever side is built first implements the shared class. See `planning/BACKLOG-REDESIGN.md` for the full writeup.

Taski:
- [ ] **Test: dostarczenie (`DELIVERED`) nie ustawia finalności.**
      `verify: ./mvnw -f backend test -Dtest=*DeliveredNotFinalTest*` (współdzielony z `EPIC-14` Story 14.3 — jedna implementacja, dowolna strona może zbudować pierwsza, patrz nota tam).
