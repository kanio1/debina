---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-27-iso-correlation-engine]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-1, line 1275), [MVP]"
---

# EPIC-65 — Case: katalog R-message i rozwiązywanie typu case (EPIC-CASE-1)

`[FREEZE]` case jest decision-and-coordination only, nigdy silnikiem workflow.

## Story 65.1 — Schemat `case` + ownership

status: not-started
depends_on: []

Taski:
- [ ] **Migracja schematu `case`, tabel `cases`, `case_decisions`, `case_r_message_links`, `case_evidence_bundles`, `case.outbox_events`/`inbox_events`.**
      `verify: psql -c "\dt case.*"`

## Story 65.2 — Katalogi case-type i reguł R-message w reference-data

status: not-started
depends_on: [Story 65.1]

Taski:
- [ ] **Katalog case-type + reguły R-message w `reference_data`.**
      `verify: psql -c "\d reference_data.case_type_catalog"` (lub równoważny).

## Story 65.3 — `CaseTypeResolver`

status: not-started
depends_on: [Story 65.2]

Taski:
- [ ] **`CaseTypeResolver` rozstrzygający typ case z R-message.**
      `verify: ./mvnw -f backend test -Dtest=*CaseTypeResolverTest*`

## Story 65.4 — Konsumpcja `iso.message.correlated` dla R-message

status: not-started
depends_on: [Story 65.1, EPIC-27-iso-correlation-engine]

Taski:
- [ ] **Konsument Kafka: R-message z `iso.message.correlated` tworzy/łączy case.**
      `verify: ./mvnw -f backend test -Dtest=*RMessageCaseConsumerTest*`
