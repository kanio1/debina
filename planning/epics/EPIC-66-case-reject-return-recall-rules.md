---
status: not-started
depends_on: [EPIC-65-case-r-message-catalog, EPIC-42-settlement-return-vs-reversal]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-2, line 1276), [MVP]"
---

# EPIC-66 — Case: reguły reject/return/recall (EPIC-CASE-2)

Zawiera flagowy test projektu: finality-correct return-as-new-payment.

## Story 66.1 — Cztery koncepty reguł (reject/return/recall/internal-reversal)

status: not-started
depends_on: []

Taski:
- [ ] **Zaimplementuj rozróżnienie czterech konceptów: reject, return, recall, internal-reversal.**
      `verify: ./mvnw -f backend test -Dtest=*FourRMessageConceptsTest*`

## Story 66.2 — Macierz timingu + `PaymentStateValidator(as_of)`

status: not-started
depends_on: [Story 66.1]

Opis: pięć pasm timingu: before-route / after-route-pre-settlement / post-acceptance-pre-finality / post-finality / post-egress.

Taski:
- [ ] **`PaymentStateValidator` bramkujący legalność recall/reject/return wg macierzy timingu i `as_of`.**
      `verify: ./mvnw -f backend test -Dtest=*CaseTimingMatrixTest*` → pokrywa wszystkie pięć pasm.

## Story 66.3 — `ResolveRecall` bramkowany rolą

status: not-started
depends_on: [Story 66.1]

Taski:
- [ ] **Komenda `ResolveRecall` z bramkowaniem roli.**
      `verify: ./mvnw -f backend test -Dtest=*ResolveRecallAuthorizationTest*`

## Story 66.4 — Flagowy test: return-as-new-payment po finalności

status: not-started
depends_on: [Story 66.2, EPIC-42-settlement-return-vs-reversal/Story 42.1]

Taski:
- [ ] **Test: zwrot po finalności tworzy NOWĄ płatność, oryginalne linie dziennika bit-identyczne, brak ścieżki `reverse`.**
      `verify: ./mvnw -f backend test -Dtest=*ReturnAfterFinalityIsNewPaymentTest*` (współdzielony z EPIC-42 Story 42.1).

## Story 66.5 — pacs.004 inbound → oryginał `RETURNED`

status: not-started
depends_on: [Story 66.4]

Taski:
- [ ] **Przychodzący pacs.004 ustawia oryginalną płatność na `RETURNED`.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs004InboundReturnedTest*`
