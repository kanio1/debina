---
status: not-started
depends_on: [EPIC-39-settlement-finality-model]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-8, line 1300), [MVP]"
---

# EPIC-42 — Settlement: zwrot vs reversal (EPIC-SETTLE-8)

`[FREEZE]` zwrot po finalności = nowa płatność w przeciwnym kierunku, nigdy reversal księgowy.

`[READINESS 2026-07-20]`: ADR-N10 now defines authoritative finality and the LedgerPort reservation
contract. This epic remains execution-blocked until EPIC-39 and EPIC-32 implement those capabilities;
it must not bypass their public ports or invent a return flow.

## Story 42.1 — Reafirmacja: zwrot = nowa płatność

status: blocked
depends_on: []

`[CAPABILITY-BLOCKED 2026-07-20]`: the frozen source is explicit that a post-finality return is
requested by `case` through `ReturnPaymentRequestPort` and enters the normal payment intake path;
`case` schema/module, the request port, and the intake contract do not yet exist. The current
payment status vocabulary has no source-wired return transition. Adding a direct return command,
`RETURNED` transition, or any `LedgerPort.reverse` route here would bypass the required owners and
invent the missing request semantics. Needed capability packet: EPIC-65 case ownership plus a
source-backed payment intake request contract; no business return is implemented while blocked.

Taski:
- [ ] **Test: zwrot po finalności tworzy nową płatność (`payment.payments`), oryginalne linie dziennika bit-identyczne, brak ścieżki `reverse`.** `[CAPABILITY-BLOCKED]`
      `verify: ./mvnw -f backend test -Dtest=*ReturnAfterFinalityIsNewPaymentTest*` — NOT RUN; case/request capability absent.

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
