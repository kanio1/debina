---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-8, line 1264); sepa-nexus-blueprint-ownership-integration.md §9 (line 352); §6.6 read models"
---

# EPIC-16 — Ownership: read modele i GraphQL read-only (EPIC-OWN-8)

`[FREEZE]` GraphQL jest read-only w MVP — zero mutacji.

## Story 16.1 — Read modele własnością modułu źródłowego

status: not-started
depends_on: []

Taski:
- [ ] **Grant-test: każdy read model jest projekcją własną modułu źródłowego, nie zapisem cross-schema.**
      `verify: ./mvnw -f backend test -Dtest=*ReadModelOwnershipTest*`

## Story 16.2 — Wymuszenie GraphQL read-only

status: not-started
depends_on: [Story 16.1]

Taski:
- [ ] **Reguła ArchUnit/test schematu: schemat GraphQL ma zero pól `Mutation`.**
      `verify: ./mvnw -f backend test -Dtest=*GraphQLReadOnlyTest*`

## Story 16.3 — Odświeżanie projekcji dashboardu

status: not-started
depends_on: [Story 16.1]

Taski:
- [ ] **Test: projekcje dashboardu odświeżają się przez event/read-model, nigdy przez bezpośredni odczyt tabeli domenowej innego modułu.**
      `verify: ./mvnw -f backend test -Dtest=*DashboardProjectionRefreshTest*`
