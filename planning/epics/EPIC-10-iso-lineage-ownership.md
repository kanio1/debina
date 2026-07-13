---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-26-iso-message-lineage-core, EPIC-21-iso-identifier-refactor]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-2, line 1258); sepa-nexus-blueprint-ownership-integration.md §9 (line 346, 'ISO lineage split')"
---

# EPIC-10 — Ownership: ISO lineage split (EPIC-OWN-2)

Wymuszenie, że `iso-adapter` jest jedynym właścicielem tabel lineage/identyfikatorów, i że nie podejmuje decyzji biznesowych.

## Story 10.1 — `iso.payment_iso_identifiers` + `message_lineage` jako własność `iso-adapter`

status: not-started
depends_on: []

Opis: potwierdzenie ownership schematu `iso` po migracji z EPIC-21.

Kryterium ukończenia: tylko rola `iso-adapter` pisze do `iso.*`.

Taski:
- [ ] **Grant-test potwierdzający wyłączność zapisu `iso-adapter` do schematu `iso`.**
      `verify: ./mvnw -f backend test -Dtest=*IsoSchemaOwnershipTest*`

## Story 10.2 — Korelacja pacs.002 przez lineage

status: not-started
depends_on: [Story 10.1]

Opis: zapytania korelacyjne przechodzą wyłącznie przez `iso.payment_iso_identifiers`.

Kryterium ukończenia: brak alternatywnej ścieżki korelacji poza lineage.

Taski:
- [ ] **Test integracyjny: korelacja pacs.002 wyłącznie przez `iso.payment_iso_identifiers`, nie przez pole na `payment.payments`.**
      `verify: ./mvnw -f backend test -Dtest=*IsoCorrelationOwnershipTest*`

## Story 10.3 — `iso-adapter` nie podejmuje decyzji biznesowej (arch test)

status: not-started
depends_on: [EPIC-09-ownership-schema-grants/Story 9.4]

Opis: `iso-adapter` nigdy nie zmienia `payment.status`, nie routuje, nie rozlicza.

Kryterium ukończenia: reguła ArchUnit wymuszona.

Taski:
- [ ] **Reguła ArchUnit: pakiet `iso-adapter` nie wywołuje niczego, co zmienia `payment.status`/routing/settlement.**
      `verify: ./mvnw -f backend test -Dtest=*IsoAdapterNoBusinessDecisionTest*`
