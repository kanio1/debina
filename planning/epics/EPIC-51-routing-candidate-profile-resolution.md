---
status: in-progress
depends_on: [EPIC-12-reference-data-ownership/Story 12.1]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-1, line 1285), [MVP]; ADR-N2 (routing in-process)"
---

# EPIC-51 — Routing: rozwiązywanie profili kandydatów (EPIC-ROUTE-1)

`[NARROWED 2026-07-16 — dependency-inventory deep-dive session]`: `depends_on` narrowed from the whole `EPIC-12-reference-data-ownership` epic to `Story 12.1` specifically. Every table this epic's stories (51.1–51.4) actually read — `reference_data.scheme_profiles` and its active-window fields — is delivered by Story 12.1 (`done`, migration `V9__reference_data_calendars_scheme_profiles.sql`). `Story 12.2`'s scope (validation/mapping/render profile catalogs, `[NO-CODE]`-gated to Iteration 5) is untouched by any story in this epic; `EPIC-52` (which does need eligibility/participant-capability catalogs) depends *on* `EPIC-51`, not the reverse, further confirming Story 12.2 isn't needed here.

`[FREEZE]` ADR-N2: `routing` to moduł in-process Modulith konsumujący `payment.validated`, bez gRPC w MVP.

## Story 51.1 — Kandydaci z scheme/service/currency

status: done
depends_on: []

Taski:
- [x] **Rozwiązywanie kandydatów tras z scheme/service/currency.**
      `verify: ./mvnw -f backend test -Dtest=RouteCandidateResolutionTest,RouteCandidateCatalogMigrationTest,RouteCandidateCatalogMigrationUpgradePathTest` → `6/0/0 PASS` (2026-07-20). `reference_data.profile_route_priorities` is an additive source-shaped catalog with `reference_data_role` as sole writer and `routing_role` read-only; `RouteCandidateResolver` uses a narrow read port, exact static key, and priority ordering only. Fresh PostgreSQL 18, V44→V45 upgrade, writer/foreign-writer negative (`42501`), PK conflict (`23505`), and a validity-filter mutation RED proof are recorded in `planning/programs/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3.md`.

## Story 51.2 — Filtr wg okna aktywności profilu

status: done
depends_on: [Story 51.1]

Taski:
- [x] **Filtr kandydatów wg okna aktywności profilu.**
      `verify: ./mvnw -f backend test -Dtest=RouteCandidateResolutionTest` → `3/0/0 PASS` (2026-07-20). The filter includes both source `valid_from`/`valid_to` boundaries and excludes dates before/after the window; a temporary removed-filter mutation failed `2/3` tests, then was reverted and the suite passed again.

## Story 51.3 — Filtr kompatybilności zestawu wiadomości

status: not-started
depends_on: [Story 51.1]

`[SOURCE-BLOCKED 2026-07-20]`: blueprint §4.10 says that allowed message set is static
reference-data, but supplies no typed column/table or authoritative rule-code vocabulary for a
message-set compatibility representation. `participant_capabilities` has only
`participant_id/profile_id/access_mode`; `participant_eligibility_rules.rule_code/rule_value` is
generic and does not define a message-set encoding. Do not overload it with guessed ISO/CSM
semantics. Required evidence: an authoritative representation and matching policy for the allowed
message set; then this story can use the completed candidate catalog.

Taski:
- [ ] **Filtr kandydatów wg kompatybilności zestawu wiadomości.**
      `verify: ./mvnw -f backend test -Dtest=*MessageSetCompatibilityFilterTest*`

## Story 51.4 — Kandydaci w read modelu

status: not-started
depends_on: [Story 51.1]

Taski:
- [ ] **Read model wystawiający listę kandydatów.**
      `verify: ./mvnw -f backend test -Dtest=*RouteCandidatesReadModelTest*`
