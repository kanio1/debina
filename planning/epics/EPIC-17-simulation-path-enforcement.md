---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-9, line 1265); sepa-nexus-blueprint-ownership-integration.md §9 (line 353); README.md (simulation przez normalne ścieżki, [FREEZE])"
---

# EPIC-17 — Ownership: wymuszenie ścieżki symulacji (EPIC-OWN-9)

`[FREEZE]` symulacja wchodzi w ruch wyłącznie przez publiczne komendy `ingress` i topic `csm.response.received` — nigdy przez bezpośredni insert domenowy.

## Story 17.1 — Symulacja tylko przez publiczne komendy + `csm.response.received`

status: not-started
depends_on: []

Taski:
- [ ] **Test: moduł `simulation` woła wyłącznie publiczne porty komend `ingress` oraz konsumuje `csm.response.received` — brak innej ścieżki wejścia.**
      `verify: ./mvnw -f backend test -Dtest=*SimulationPublicPathOnlyTest*`

## Story 17.2 — Grant-test: brak insertu domenowego z symulacji

status: not-started
depends_on: [Story 17.1]

Taski:
- [ ] **SQL grant-test: rola `simulation` nie ma zapisu na `payment`/`settlement`/`ledger`/`egress`.**
      `verify: ./mvnw -f backend test -Dtest=*SimulationNoDomainWriteTest*`

## Story 17.3 — Deterministyczny replay z seeda

status: not-started
depends_on: [Story 17.1]

Taski:
- [ ] **Widok/read-model potwierdzający, że ten sam seed daje identyczny przebieg (podstawa pod EPIC modułu `simulation`, patrz otwarte pytanie w README).**
      `verify: ./mvnw -f backend test -Dtest=*DeterministicSeedReplayTest*`

## Otwarte pytania

- `[OPEN-QUESTION]` Żaden z przebadanych dokumentów backlogu (MFB §8, OWN §9, BPR §12) nie definiuje odrębnej rodziny epików dla samego modułu `simulation` (analogicznej do EPIC-CASE/EPIC-ROUTE/EPIC-SETTLE) — mimo że `simulation` jest `[MVP]` Iteracja 3 i decision gate nazywa ją "demo, które sprzedaje projekt". EPIC-17 pokrywa wyłącznie egzekwowanie granic (OWN-9), nie samą budowę silnika symulacji (scenariusze, failure profiles, `simulation_runs`). Nie tworzę tego epika samodzielnie — brak źródła. Do rozstrzygnięcia w kolejnej sesji: albo doprecyzować z użytkownikiem, albo poczekać na przyszły dokument analogiczny do backlog seed dla pozostałych modułów.
