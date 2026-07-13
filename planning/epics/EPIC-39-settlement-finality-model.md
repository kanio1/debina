---
status: not-started
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-5, line 1297), [MVP]"
---

# EPIC-39 — Settlement: model finalności (EPIC-SETTLE-5)

`[FREEZE]` accepted/posted/delivered ≠ final. Finalność to jawna, konfigurowana profilem reguła.

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
depends_on: [Story 39.2, EPIC-14-egress-boundary-ownership/Story 14.3]

Taski:
- [ ] **Test: dostarczenie (`DELIVERED`) nie ustawia finalności.**
      `verify: ./mvnw -f backend test -Dtest=*DeliveredNotFinalTest*` (współdzielony z EPIC-14).
