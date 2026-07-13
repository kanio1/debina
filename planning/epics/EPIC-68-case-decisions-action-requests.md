---
status: not-started
depends_on: [EPIC-66-case-reject-return-recall-rules]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-4, line 1278), [MVP]/[P1]"
---

# EPIC-68 — Case: decyzje i action requests (EPIC-CASE-4)

## Story 68.1 — Wyniki decyzji

status: not-started
depends_on: []

Taski:
- [ ] **Zaimplementuj wyniki decyzji case (`case_decisions`).**
      `verify: ./mvnw -f backend test -Dtest=*CaseDecisionOutcomeTest*`

## Story 68.2 — `case_action_requests` (request-only)

status: not-started
depends_on: [Story 68.1]

Taski:
- [ ] **Migracja `case.case_action_requests`, wzorzec request-only (nigdy bezpośrednia mutacja).**
      `verify: psql -c "\d case.case_action_requests"`

## Story 68.3 — Komendy administracyjne bramkowane rolą (REST, nie GraphQL)

status: not-started
depends_on: [Story 68.2]

Taski:
- [ ] **Komendy case jako REST admin-command, bramkowane rolą, nigdy mutacja GraphQL.**
      `verify: ./mvnw -f backend test -Dtest=*CaseCommandNotGraphQLMutationTest*`
