---
status: not-started
depends_on: [EPIC-39-settlement-finality-model]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-8, line 1300), [MVP]"
---

# EPIC-42 — Settlement: zwrot vs reversal (EPIC-SETTLE-8)

`[FREEZE]` zwrot po finalności = nowa płatność w przeciwnym kierunku, nigdy reversal księgowy.

`[READINESS 2026-07-20]`: removal of false terminality-derived history finality makes no claim that
an authoritative pre/post-finality state exists. The return/reversal runtime remains blocked on
EPIC-39 finality authority and EPIC-32 LedgerPort reservation/post/release contract.

## Story 42.1 — Reafirmacja: zwrot = nowa płatność

status: not-started
depends_on: []

Taski:
- [ ] **Test: zwrot po finalności tworzy nową płatność (`payment.payments`), oryginalne linie dziennika bit-identyczne, brak ścieżki `reverse`.**
      `verify: ./mvnw -f backend test -Dtest=*ReturnAfterFinalityIsNewPaymentTest*`

## Story 42.2 — Reversal tylko pre-finality

status: not-started
depends_on: [Story 42.1]

Taski:
- [ ] **Test: reversal księgowy dozwolony wyłącznie przed finalnością.**
      `verify: ./mvnw -f backend test -Dtest=*ReversalOnlyPreFinalityTest*`

## Story 42.3 — Read model wyjaśnienia + Playwright

status: not-started
depends_on: [Story 42.1]

Taski:
- [ ] **Read model wyjaśniający decyzję zwrot-vs-reversal + test Playwright na dashboardzie.**
      `verify: npm run test:e2e -- --grep "@smoke.*return-vs-reversal"`
