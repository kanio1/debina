---
status: not-started
depends_on: [EPIC-65-case-r-message-catalog, EPIC-62-reconciliation-exception-lifecycle]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-7, line 1281), [P1]"
---

# EPIC-71 — Case: przypadki powiązane z rekoncyliacją (EPIC-CASE-7, `[P1]`)

## Story 71.1 — `RECONCILIATION_LINKED_CASE`

status: not-started
depends_on: []

Opis: z wyjątku rekoncyliacji, request-only, powiązanie tylko-do-odczytu.

Taski:
- [ ] **Typ case `RECONCILIATION_LINKED_CASE` tworzony z eskalacji wyjątku rekoncyliacji (EPIC-62 Story 62.4), jako request-only, read-only link.**
      `verify: ./mvnw -f backend test -Dtest=*ReconciliationLinkedCaseTest*`
