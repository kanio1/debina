---
status: not-started
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-4, line 1296), [MVP]"
---

# EPIC-38 — Settlement: book wewnętrzny i wsad plikowy (EPIC-SETTLE-4)

## Story 38.1 — `InternalBookStrategy`

status: not-started
depends_on: []

Taski:
- [ ] **`InternalBookStrategy`, event `ON_INTERNAL_BOOK_POST`.**
      `verify: ./mvnw -f backend test -Dtest=*InternalBookStrategyTest*`

## Story 38.2 — `FileBatchStrategy` podstawowa

status: not-started
depends_on: []

Taski:
- [ ] **Podstawowa `FileBatchStrategy`.**
      `verify: ./mvnw -f backend test -Dtest=*FileBatchStrategyTest*`
