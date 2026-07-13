---
status: not-started
depends_on: [EPIC-12-reference-data-ownership]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-1, line 1285), [MVP]; ADR-N2 (routing in-process)"
---

# EPIC-51 — Routing: rozwiązywanie profili kandydatów (EPIC-ROUTE-1)

`[FREEZE]` ADR-N2: `routing` to moduł in-process Modulith konsumujący `payment.validated`, bez gRPC w MVP.

## Story 51.1 — Kandydaci z scheme/service/currency

status: not-started
depends_on: []

Taski:
- [ ] **Rozwiązywanie kandydatów tras z scheme/service/currency.**
      `verify: ./mvnw -f backend test -Dtest=*RouteCandidateResolutionTest*`

## Story 51.2 — Filtr wg okna aktywności profilu

status: not-started
depends_on: [Story 51.1]

Taski:
- [ ] **Filtr kandydatów wg okna aktywności profilu.**
      `verify: ./mvnw -f backend test -Dtest=*ProfileActiveWindowFilterTest*`

## Story 51.3 — Filtr kompatybilności zestawu wiadomości

status: not-started
depends_on: [Story 51.1]

Taski:
- [ ] **Filtr kandydatów wg kompatybilności zestawu wiadomości.**
      `verify: ./mvnw -f backend test -Dtest=*MessageSetCompatibilityFilterTest*`

## Story 51.4 — Kandydaci w read modelu

status: not-started
depends_on: [Story 51.1]

Taski:
- [ ] **Read model wystawiający listę kandydatów.**
      `verify: ./mvnw -f backend test -Dtest=*RouteCandidatesReadModelTest*`
