---
status: not-started
depends_on: [EPIC-65-case-r-message-catalog]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-5, line 1272), [P1]"
---

# EPIC-30 — ISO: lineage R-message (EPIC-ISO-5, `[P1]`)

Odblokowywany przez / konsumuje `case` z EPIC-65.

## Story 30.1 — Korelacja zwrotu pacs.004

status: not-started
depends_on: []

Taski:
- [ ] **Korelacja `pacs.004` (return) do oryginalnej płatności.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs004ReturnCorrelationTest*`

## Story 30.2 — Powiązanie recall camt.056 → case

status: not-started
depends_on: [Story 30.1, EPIC-65-case-r-message-catalog]

Taski:
- [ ] **Powiązanie `camt.056` (recall) z rekordem `case`.**
      `verify: ./mvnw -f backend test -Dtest=*Camt056CaseLinkTest*`

## Story 30.3 — Powiązanie rozstrzygnięcia camt.029

status: not-started
depends_on: [Story 30.2]

Taski:
- [ ] **Powiązanie `camt.029` (rozstrzygnięcie) z odpowiadającym `case`.**
      `verify: ./mvnw -f backend test -Dtest=*Camt029ResolutionLinkTest*`

## Story 30.4 — Dashboard śledztwa

status: not-started
depends_on: [Story 30.1, Story 30.2, Story 30.3]

Taski:
- [ ] **Dashboard śledztwa R-message (Playwright).**
      `verify: npm run test:e2e -- --grep "@smoke.*r-message-investigation"`
