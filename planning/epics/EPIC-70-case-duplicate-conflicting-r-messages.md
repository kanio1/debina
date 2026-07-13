---
status: not-started
depends_on: [EPIC-65-case-r-message-catalog]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-6, line 1280), [P1]"
---

# EPIC-70 — Case: duplikaty i sprzeczne R-message (EPIC-CASE-6, `[P1]`)

## Story 70.1 — Tłumienie duplikatów

status: not-started
depends_on: []

Taski:
- [ ] **`CASE_DUPLICATE_SUPPRESSED`, dedupe na tym samym evencie.**
      `verify: ./mvnw -f backend test -Dtest=*CaseDuplicateSuppressedTest*`

## Story 70.2 — Eskalacja konfliktu (nigdy auto-rozwiązanie)

status: not-started
depends_on: [Story 70.1]

Taski:
- [ ] **Test: sprzeczne R-message eskalują do operatora, nigdy auto-rozwiązywane.**
      `verify: ./mvnw -f backend test -Dtest=*ConflictingRMessageEscalatesTest*`

## Story 70.3 — `case_duplicate_links`

status: not-started
depends_on: [Story 70.1]

Taski:
- [ ] **Migracja `case.case_duplicate_links`.**
      `verify: psql -c "\d case.case_duplicate_links"`
