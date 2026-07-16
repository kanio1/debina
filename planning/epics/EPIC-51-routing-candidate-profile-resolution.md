---
status: not-started
depends_on: [EPIC-12-reference-data-ownership/Story 12.1]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-1, line 1285), [MVP]; ADR-N2 (routing in-process)"
---

# EPIC-51 — Routing: rozwiązywanie profili kandydatów (EPIC-ROUTE-1)

`[NARROWED 2026-07-16 — dependency-inventory deep-dive session]`: `depends_on` narrowed from the whole `EPIC-12-reference-data-ownership` epic to `Story 12.1` specifically. Every table this epic's stories (51.1–51.4) actually read — `reference_data.scheme_profiles` and its active-window fields — is delivered by Story 12.1 (`done`, migration `V9__reference_data_calendars_scheme_profiles.sql`). `Story 12.2`'s scope (validation/mapping/render profile catalogs, `[NO-CODE]`-gated to Iteration 5) is untouched by any story in this epic; `EPIC-52` (which does need eligibility/participant-capability catalogs) depends *on* `EPIC-51`, not the reverse, further confirming Story 12.2 isn't needed here.

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
