---
status: not-started
depends_on: [EPIC-26-iso-message-lineage-core]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-2, line 1269), [MVP]"
---

# EPIC-27 — ISO: silnik korelacji (EPIC-ISO-2)

Wiążąca zasada: adapter koreluje, payment-lifecycle przechodzi FSM. 9-krokowa polityka korelacji pacs.002.

## Story 27.1 — Ekstrakcja identyfikatorów pacs.002

status: not-started
depends_on: []

Taski:
- [ ] **Ekstrakcja `OrgnlMsgId`/`OrgnlEndToEndId` z pacs.002.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs002IdentifierExtractionTest*`

## Story 27.2 — 9-krokowa korelacja → `iso.iso_message_correlation`

status: not-started
depends_on: [Story 27.1]

Opis: wyniki MATCHED/AMBIGUOUS/ORPHANED.

Taski:
- [ ] **Zaimplementuj 9-krokową politykę korelacji dokładnie wg opisu w main blueprincie §2.4, zapis do `iso.iso_message_correlation`.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs002CorrelationPolicyTest*` → pokrywa MATCHED/AMBIGUOUS/ORPHANED.

## Story 27.3 — Duplikat i out-of-order

status: not-started
depends_on: [Story 27.2]

Taski:
- [ ] **Test: duplikat → `IGNORED_DUPLICATE`; wiadomość out-of-order → polityka FSM, nie błąd.**
      `verify: ./mvnw -f backend test -Dtest=*DuplicateAndOutOfOrderTest*`

## Story 27.4 — Orphan → DLQ + read model operatora

status: not-started
depends_on: [Story 27.2]

Taski:
- [ ] **Nierozpoznany status → DLQ + read model operatora do ręcznej korelacji (P1 dla samej akcji ręcznej, MVP dla DLQ).**
      `verify: ./mvnw -f backend test -Dtest=*OrphanDlqTest*`
