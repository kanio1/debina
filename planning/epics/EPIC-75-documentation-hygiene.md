---
status: done
depends_on: []
source: "sepa-nexus-full-blueprint-review-and-task-plan.md line 355 (EPIC-DOC-1)"
---

# EPIC-75 — Higiena dokumentacji (EPIC-DOC-1)

Zastosuj adnotacje supersession R-04/R-06/R-10/R-16/R-22/R-23 i tabelę topiców §3.7 v2 do blueprintu głównego. Źródło opisuje to jako "jeden przebieg `str_replace`, ≤1 dzień".

## Story 75.1 — Adnotacje supersession + §3.7 v2

status: done
depends_on: []

Opis: sprawdzone bezpośrednio w tym repo (sesja inicjalizacji CLAUDE.md) — `sepa-nexus-message-flow-and-data-blueprint.md` już zawiera nagłówek "§3.7 Kafka Topic Catalog v2 / AsyncAPI Source of Truth" (linia 311 wg wcześniejszego przeglądu), a repozytorium nie zawiera żadnych zdublowanych/przestarzałych wersji plików patchy (`uploads/`/`outputs/` split opisywany w `sepa-nexus-epics-stories-tasks-file-readiness-gate.md` nie istnieje w tym repo — tylko płaski, pojedynczy zestaw plików kanonicznych). Traktuję to jako mocną poszlakę ukończenia, nie dowód linia-po-linii każdej z sześciu adnotacji R-NN.

Kryterium ukończenia: `§3.7 v2` istnieje w głównym blueprincie (potwierdzone).

Taski:
- [x] **Potwierdź istnienie nagłówka `§3.7 ... v2` w `sepa-nexus-message-flow-and-data-blueprint.md`.**
      `verify: grep -n "3.7.*v2\|Topic Catalog v2" sepa-nexus-message-flow-and-data-blueprint.md` → dopasowanie znalezione.

## Otwarte pytania

- `[OPEN-QUESTION]` Nie zweryfikowano linia-po-linii, czy wszystkie sześć konkretnych adnotacji supersession (R-04/R-06/R-10/R-16/R-22/R-23) faktycznie znajduje się w tekście — tylko że dokument jest w wersji v2 i repo nie zawiera przestarzałych duplikatów. Jeśli to kiedyś będzie miało znaczenie (np. audyt), wymaga dodatkowego sprawdzenia grep per R-numer.
