---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-4, line 1260); sepa-nexus-blueprint-ownership-integration.md §9 (line 348) + §10 (line 368)"
---

# EPIC-12 — Ownership: katalogi reference-data (EPIC-OWN-4)

## Story 12.1 — Katalogi jako własność `reference-data`

status: not-started
depends_on: []

Kryterium ukończenia: `business_calendars`/`service_levels`/`scheme_profiles`/`settlement_cutoff_calendar` własnością `reference_data`.

Taski:
- [ ] **Grant-test: `reference_data` jedynym writerem `business_calendars`, `service_levels`, `scheme_profiles`, `settlement_cutoff_calendar`.** `[UWAGA]` Główny seed (MFB §8) i patch ownership (OWN §9) różnią się — OWN §9 pomija `settlement_cutoff_calendar` w liście S3, ale OWN §10 osobno potwierdza że ta tabela należy do `reference-data`. Traktuję to jako niekompletne powtórzenie w OWN §9, nie jako zmianę zakresu — zob. `[OPEN-QUESTION]` w README.
      `verify: ./mvnw -f backend test -Dtest=*ReferenceDataOwnershipTest*`

## Story 12.2 — Katalogi walidacji/mapowania/renderowania (R-09)

status: not-started
depends_on: [Story 12.1]

Opis: trzy wersjonowane katalogi z R-09 (decision gate §3), w tym seed `JSON_DIRECT` (ADR-N7).

Kryterium ukończenia: trzy katalogi istnieją i są wersjonowane.

Taski:
- [ ] **Migracja: `validation_profiles`, `mapping_profiles`, `render_profiles` w schemacie `reference_data`, wersjonowane.**
      `verify: psql -c "\dt reference_data.*profiles*"` → trzy tabele istnieją.
