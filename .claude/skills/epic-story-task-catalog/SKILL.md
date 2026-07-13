---
name: epic-story-task-catalog
description: Użyj przy tworzeniu lub aktualizacji jakiegokolwiek pliku w /planning/ — wymagana struktura i format epików, stories, tasków oraz indeksu statusu.
---

# epic-story-task-catalog

## Struktura katalogu

```
/planning/README.md         — indeks: tabela (epik, status, zależności, link do pliku)
/planning/epics/EPIC-NN-nazwa.md   — jeden plik markdown na epik
```

`NN` to dwucyfrowy numer porządkowy odzwierciedlający kolejność techniczną (patrz skill `artifact-derived-planning`), nie kolejność tematyczną. `nazwa` to krótki kebab-case slug.

Nigdy nie usuwaj pliku ukończonego epika — `done` to poprawny, trwały stan w tym samym pliku, nie powód do skasowania.

## Format pliku epika

Zaczyna się od YAML frontmatter:

```yaml
---
status: not-started   # jedna z: not-started, in-progress, blocked, done
depends_on: []         # lista innych epików (np. [EPIC-01-platform-skeleton]) albo pusta lista
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 0-8"   # z którego artefaktu/sekcji wynika zakres tego epika
---
```

Po frontmatterze: tytuł (`# EPIC-NN — Nazwa`), krótkie streszczenie zakresu (1-3 zdania) z odniesieniem do źródła.

### Stories jako podsekcje

```markdown
## Story NN.M — Nazwa story

status: not-started | in-progress | blocked | done
depends_on: [inne story/epiki, jeśli dotyczy]

Opis: co ta story dostarcza i dlaczego (1-3 zdania, z odniesieniem do artefaktu źródłowego).

Kryterium ukończenia story: [konkretny, sprawdzalny warunek — zwykle "wszystkie taski poniżej odhaczone i ich verify przechodzi"]

Taski:
- [ ] **Nazwa zadania.** Opis co dokładnie zrobić i gdzie (plik/moduł/schemat).
      `verify: <dokładna komenda>` → oczekiwany rezultat.
- [ ] **Kolejne zadanie.** ...
      `verify: <dokładna komenda>` → oczekiwany rezultat.
```

## Źródło prawdy dla statusów

- **Stan checkboxów** (`- [ ]` / `- [x]`) jest źródłem prawdy dla ukończenia pojedynczego **tasku**.
- **Pole `status`** w nagłówku story i we frontmatterze epika jest źródłem prawdy dla ukończenia **story/epika jako całości**.
- Te dwa poziomy muszą być spójne: story nie może mieć `status: done` przy nieodhaczonych taskach; epik nie może mieć `status: done` przy story w stanie innym niż `done`. Aktualizuj oba przy każdej zmianie — nie tylko checkbox, nie tylko frontmatter.
- `status: blocked` wymaga krótkiej notatki dlaczego (i najlepiej odniesienia do otwartego pytania w `HANDOFF.md`, jeśli stamtąd wynika blokada).

## /planning/README.md — format indeksu

```markdown
# Planning Index

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-00-... | not-started | — | [epics/EPIC-00-....md](epics/EPIC-00-....md) |
| EPIC-01-... | not-started | EPIC-00-... | [epics/EPIC-01-....md](epics/EPIC-01-....md) |
```

Indeks jest generowany/aktualizowany z plików epików (frontmatter), nie odwrotnie — jeśli są niespójne, pliki epików wygrywają, a indeks trzeba poprawić.

## Dyscyplina

Nie twórz epika bez wypełnionego `source` wskazującego realny plik/sekcję w repo (zgodnie ze skillem `artifact-derived-planning`). Epik bez źródła to sygnał, że zakres został wymyślony, a nie wyprowadzony z dokumentacji.
