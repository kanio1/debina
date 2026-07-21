---
status: blocked
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-4, line 1296), [MVP]"
---

# EPIC-38 — Settlement: book wewnętrzny i wsad plikowy (EPIC-SETTLE-4)

## Story 38.1 — `InternalBookStrategy`

status: blocked
depends_on: []

`[DECISION-BLOCKED 2026-07-21]`: source binds `INTERNAL_BOOK` +
`NONE_INTERNAL` to an internal post and `ON_INTERNAL_BOOK_POST`, but does not
define account mapping, eligibility, failure result or payment projection.
ADR-N11 is gross-instant-only. See Wave 6 packet D6-03.

Taski:
- [ ] **`InternalBookStrategy`, event `ON_INTERNAL_BOOK_POST`.**
      `verify: ./mvnw -f backend test -Dtest=*InternalBookStrategyTest*`

## Story 38.2 — `FileBatchStrategy` podstawowa

status: blocked
depends_on: []

`[DECISION-BLOCKED 2026-07-21]`: source authorises only the typed pair,
file/cycle assignment and later `ON_CYCLE_SETTLED`; batch identity/creator,
membership, cycle/date/session selection, close and replay behavior are absent.
Do not infer them from EPIC-73. See Wave 6 packet D6-04.

Taski:
- [ ] **Podstawowa `FileBatchStrategy`.**
      `verify: ./mvnw -f backend test -Dtest=*FileBatchStrategyTest*`
