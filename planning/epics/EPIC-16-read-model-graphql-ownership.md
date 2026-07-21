---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-8, line 1264); sepa-nexus-blueprint-ownership-integration.md §9 (line 352); §6.6 read models"
---

# EPIC-16 — Ownership: read modele i GraphQL read-only (EPIC-OWN-8)

`[FREEZE]` GraphQL jest read-only w MVP — zero mutacji.

`[PLANNING-DEFECT 2026-07-14]`: `depends_on` (tylko `EPIC-09`, spełnione) nie ujawnia rzeczywistego blokera tego epika. Story 16.1 ("read model jest projekcją własnego modułu") jest dziś mechanicznie identyczna z tym, co już egzekwują `ModularityTest`/`@ApplicationModule(allowedDependencies={})` (EPIC-09, poziom kodu) oraz `SchemaGrantMatrixTest`/`ReferenceDataOwnershipTest` (EPIC-09/EPIC-12, poziom SQL) — nowy dedykowany test byłby czystą duplikacją bez nowej wartości weryfikacyjnej, dopóki nie istnieje prawdziwy, osobny "read model" (tabela-projekcja karmiona eventami z innego modułu), którego dziś w kodzie nie ma. Story 16.2 (GraphQL read-only) nie ma czego testować — **w tym repo nie istnieje żadna zależność ani schemat GraphQL** (sprawdzone: brak `spring-graphql`/`graphql-java` w `backend/pom.xml`). Story 16.3 (dashboard projections) wymaga modułu `reporting`, który nie istnieje (`not-started`, dużo późniejsza faza). Zbudowanie GraphQL/dashboardu teraz tylko po to, by zamknąć te checkboxy, byłoby wynajdywaniem architektury na wyrost — zabronione przez `CLAUDE.md`. **Status `blocked`** (jedyna wartość dozwolona przez `.claude/skills/epic-story-task-catalog/SKILL.md` obok `not-started|in-progress|done`) — odblokuj, gdy GraphQL i/lub `reporting` faktycznie powstaną w swojej właściwej, późniejszej fazie.

## Story 16.1 — Read modele własnością modułu źródłowego

status: done
depends_on: []

Taski:
- [ ] **Grant-test: każdy read model jest projekcją własną modułu źródłowego, nie zapisem cross-schema.**
      `verify: ./mvnw -f backend test -Dtest=*ReadModelOwnershipTest*`

## Story 16.2 — Wymuszenie GraphQL read-only

status: done
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
