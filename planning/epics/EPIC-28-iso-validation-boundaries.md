---
status: not-started
depends_on: [EPIC-19-ingress-staging-pipeline/Story 19.3]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-3, line 1270), [MVP]"
---

# EPIC-28 — ISO: granice walidacji (EPIC-ISO-3)

## Story 28.1 — Wynik hartowania XML → `iso_message_parse_errors`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `iso.iso_message_parse_errors`, zapis wyniku hartowania XML z EPIC-19 Story 19.3.**
      `verify: psql -c "\d iso.iso_message_parse_errors"`

## Story 28.2 — Wyniki walidacji schema/structural/profile/mapping

status: not-started
depends_on: [Story 28.1, EPIC-12-reference-data-ownership/Story 12.2]

Taski:
- [ ] **Zaimplementuj cztery poziomy walidacji przez katalogi profili z EPIC-12.**
      `verify: ./mvnw -f backend test -Dtest=*ValidationLevelsTest*`

## Story 28.3 — Rozdział ISO-reject vs business-reject

status: not-started
depends_on: [Story 28.2]

Taski:
- [ ] **Test: odrzucenie na poziomie ISO nie jest tym samym statusem co odrzucenie biznesowe (pięcioosiowy rozdział statusów).**
      `verify: ./mvnw -f backend test -Dtest=*IsoRejectVsBusinessRejectTest*`

## Story 28.4 — Integracja katalogu reason/status

status: not-started
depends_on: [Story 28.2]

Taski:
- [ ] **Integracja FK z katalogiem reason/status z EPIC-12.**
      `verify: ./mvnw -f backend test -Dtest=*ReasonStatusCatalogIntegrationTest*`
