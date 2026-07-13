---
status: not-started
depends_on: [EPIC-49-egress-case-r-message-outbound]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-5, line 1279), [P1]"
---

# EPIC-69 — Case: odpowiedzi wychodzące egress/ISO (EPIC-CASE-5, `[P1]`)

Ten epik jest perspektywą "od strony case" tej samej granicy co EPIC-49 (perspektywa "od strony egress") — oba czytają/piszą te same tabele, nie duplikują zakresu.

## Story 69.1 — Żądanie renderowania+dostawy camt.029/pacs.004/pacs.028

status: not-started
depends_on: []

Taski:
- [ ] **`case` woła publiczny port `egress` z żądaniem renderowania+dostawy — nigdy nie renderuje/wysyła samodzielnie.**
      `verify: ./mvnw -f backend test -Dtest=*CaseRequestsEgressRenderTest*` (współdzielony z EPIC-49 Story 49.1).

## Story 69.2 — Lineage artefaktu wychodzącego

status: not-started
depends_on: [Story 69.1]

Taski:
- [ ] **Lineage artefaktu wychodzącego dla case.**
      `verify: ./mvnw -f backend test -Dtest=*CaseOutboundLineageTest*` (współdzielony z EPIC-49 Story 49.2).

## Story 69.3 — Test granicy: case nigdy nie renderuje/wysyła

status: not-started
depends_on: [Story 69.1]

Taski:
- [ ] **Reguła ArchUnit: `case` nie ma zależności do klas renderujących/wysyłających egress.**
      `verify: ./mvnw -f backend test -Dtest=*CaseNeverRendersOrSendsTest*`
