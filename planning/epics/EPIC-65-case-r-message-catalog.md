---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-27-iso-correlation-engine/Story 27.2]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-1, line 1275), [MVP]"
---

# EPIC-65 — Case: katalog R-message i rozwiązywanie typu case (EPIC-CASE-1)

`[NARROWED 2026-07-16 — dependency-inventory deep-dive session]`: `depends_on` narrowed from the whole `EPIC-27-iso-correlation-engine` epic to `Story 27.2` specifically (the 9-step correlation policy that produces the `iso.message.correlated` MATCHED event this epic's Story 65.4 actually consumes — `sepa-nexus-message-flow-and-data-blueprint.md` line 187: "exactly 1 → status=MATCHED ... [Event] `iso.message.correlated`"). Nothing in this epic touches `EPIC-27` Story 27.4 (DLQ) or the `[P1]` Story 27.5 (manual-correlation read model). Precedent: `EPIC-20` Story 20.3 already made the identical narrowing for the identical dependency, for the identical reason. No cycle: nothing in `EPIC-27-iso-correlation-engine.md` references `EPIC-65`.

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
depends_on: [Story 65.1, EPIC-27-iso-correlation-engine/Story 27.2]

Taski:
- [ ] **Konsument Kafka: R-message z `iso.message.correlated` tworzy/łączy case.**
      `verify: ./mvnw -f backend test -Dtest=*RMessageCaseConsumerTest*`
