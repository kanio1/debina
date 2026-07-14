---
status: blocked
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-4, line 1260); sepa-nexus-blueprint-ownership-integration.md §9 (line 348) + §10 (line 368)"
---

# EPIC-12 — Ownership: katalogi reference-data (EPIC-OWN-4)

## Story 12.1 — Katalogi jako własność `reference-data`

status: done
depends_on: []

Kryterium ukończenia: `business_calendars`/`service_levels`/`scheme_profiles`/`settlement_cutoff_calendar` własnością `reference_data`.

`[PLANNING-DEFECT 2026-07-14]`: `service_levels` jest wymieniane w tabelach własności (MFB §3.6.2 odpowiednik, OWN §9/§10) ale **nie ma DDL nigdzie** w żadnym z dwóch źródłowych dokumentów — sam `grep` po obu plikach znajduje wyłącznie wzmianki nazwy w tabelach ownership, zero `CREATE TABLE reference_data.service_levels`. Za zgodą użytkownika (pytanie zadane wprost w tej sesji): **pominięto `service_levels`** zamiast wymyślać kolumny — pozostaje nieodhaczone poniżej, do uzupełnienia gdy źródło dostanie realną definicję.

Taski:
- [x] **Grant-test: `reference_data` jedynym writerem `business_calendars`, `scheme_profiles`, `settlement_cutoff_calendar`.** (`service_levels` pominięty — patrz `[PLANNING-DEFECT]` wyżej; `[UWAGA]` z poprzedniej wersji tego pliku dot. `settlement_cutoff_calendar` rozstrzygnięte identycznie — OWN §10 potwierdza własność, budowana tu).
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=ReferenceDataOwnershipTest` → `Tests run: 3, Failures: 0` — PASS (2026-07-14). Nowe migracje `backend/src/main/resources/db/migration/reference_data/V8__reference_data_schema.sql` (schemat + rola `reference_data_role` + granty: `reference_data_role` R/W na `reference_data`, `sepa_app` tylko `SELECT`) i `V9__reference_data_calendars_scheme_profiles.sql` (DDL 1:1 z `sepa-nexus-message-flow-and-data-blueprint.md` §4.13, linie 897-908). Nowy test `backend/src/test/java/com/sepanexus/referencedata/ReferenceDataOwnershipTest.java`: (1) `reference_data_role` zapisuje do wszystkich trzech tabel bez błędu; (2) `sepa_app` (rola `payment-lifecycle`) może `SELECT`, ale `INSERT` → `SQLSTATE 42501`; (3) `reference_data_role` nie może pisać do `payment.payments` → `42501`. Migracja zastosowana też przeciw już-długo-działającej realnej bazie (`./mvnw -f backend flyway:migrate` + `psql \dt reference_data.*`) → trzy tabele, `Owner: sepa_migration` — PASS.

## Story 12.2 — Katalogi walidacji/mapowania/renderowania (R-09)

status: blocked
depends_on: [Story 12.1]

Opis: trzy wersjonowane katalogi z R-09 (decision gate §3), w tym seed `JSON_DIRECT` (ADR-N7).

Kryterium ukończenia: trzy katalogi istnieją i są wersjonowane.

`[PLANNING-DEFECT 2026-07-14, rozstrzygnięte]`: ten task koliduje z jawnym znacznikiem źródła. `sepa-nexus-message-flow-and-data-blueprint.md` §4.13a oznacza dokładnie tę sekcję jako `[NO-CODE]` i mówi wprost: "full DDL lands with the iteration that implements ISO validation (Iteration 5, per the routing/ISO epics), not here." `[NO-CODE]` w `CLAUDE.md` oznacza "dokument analityczny, nic do zaimplementowania wprost z niego" — budowanie migracji teraz byłoby ignorowaniem jawnej instrukcji sekwencjonowania źródła, nie tylko luką w planningu. **Nie zaimplementowano.**

**Status `blocked`** (jedyna wartość dozwolona przez `.claude/skills/epic-story-task-catalog/SKILL.md` dla tego przypadku — `not-started|in-progress|blocked|done` to cały dozwolony zbiór, nie ma osobnej wartości "deferred"). Blokerem jest sekwencjonowanie źródła (Iteracja 5, moduł ISO validation), nie brak zależności planningowej — `depends_on` (Story 12.1) jest spełnione, ale realizacja wymaga `iso.iso_message_versions` (§4.3c), które nie istnieje. Docelowy epik/iteracja: Iteracja 5 (ISO validation, epiki EPIC-ISO/EPIC-RECON per `sepa-nexus-message-flow-and-data-blueprint.md` §8) — odblokuj, gdy `iso.iso_message_versions` faktycznie zacznie odwoływać się do tych kodów katalogowych.
