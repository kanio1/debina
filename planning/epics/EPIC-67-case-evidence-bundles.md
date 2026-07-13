---
status: not-started
depends_on: [EPIC-65-case-r-message-catalog]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-3, line 1277), [MVP]"
---

# EPIC-67 — Case: bundle evidence (EPIC-CASE-3)

## Story 67.1 — `case_evidence_bundles` + `case_evidence_pointers`

status: not-started
depends_on: []

Opis: wzorzec pointer, jak w EPIC-58.

Taski:
- [ ] **Migracja `case.case_evidence_bundles`, `case.case_evidence_pointers`.**
      `verify: psql -c "\dt case.case_evidence*"`

## Story 67.2 — `case_r_message_links`

status: not-started
depends_on: [Story 67.1]

Taski:
- [ ] **Migracja `case.case_r_message_links`.**
      `verify: psql -c "\d case.case_r_message_links"`

## Story 67.3 — FSM case + `case_events`

status: not-started
depends_on: [Story 67.1]

Taski:
- [ ] **FSM `case.cases` + tabela `case.case_events`.**
      `verify: ./mvnw -f backend test -Dtest=*CaseFsmTest*`
