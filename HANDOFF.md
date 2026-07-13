# HANDOFF

## Zadanie

To jest repozytorium **SEPA Nexus** — w pełni udokumentowany, ale wcześniej niezaimplementowany, syntetyczny projekt platformy płatniczej SEPA/ISO 20022, którego celem jest bycie realistycznym poligonem do nauki Playwright/testowania systemów enterprise dla Senior QA/SDET. Poprzednia sesja przekształciła dokumentację architektoniczną w wykonalny katalog pracy `/planning/`. Ta sesja rozpoczęła faktyczną implementację od pierwszego zadania w `/planning/epics/EPIC-00-repository-agent-foundation.md`.

## Zrobione

Ukończono **EPIC-00, Story 0.1 — Monorepo structure & tooling baseline** (status story: `done`, wszystkie 3 taski odhaczone, wszystkie `verify:` przeszły):

1. **Szkielet monorepo**: utworzono `backend/`, `frontend/`, `infra/`, każdy z `README.md` (jednozdaniowy opis celu). Dodano root `.gitignore` (Java+Node+IDE) i `.editorconfig` (UTF-8, LF, 2-spacje YAML/JSON/TS/JS, 4-spacje Java). `verify: ls backend frontend infra` → PASS.
2. **Pin Node**: `frontend/.node-version` z zawartością `20`. `verify: cat frontend/.node-version` → `20`, PASS.
3. **Weryfikacja kompatybilności TypeScript 7**: sprawdzono `npm view typescript-eslint peerDependencies` → `typescript: '>=4.8.4 <6.1.0'`, brak `^7` na liście — TS 7.0 GA niewspierany przez `typescript-eslint` (sprawdzone 2026-07-13, npm rejestr pokazuje TypeScript stabilnie już na 7.0.2, ale `typescript-eslint` nie nadążył). Zgodnie z regułą fallbacku, przypięto TypeScript `5.9.3` (najnowsza 5.x LTS) na Iterację 0 i udokumentowano to w `frontend/README.md` sekcją "TypeScript version pin (Iteration 0)".

Zaktualizowano status w `/planning/epics/EPIC-00-repository-agent-foundation.md` (Story 0.1: `not-started` → `done`, epik: `not-started` → `in-progress`, bo Story 0.2 i 0.3 wciąż `not-started`) oraz w `/planning/README.md` (wiersz EPIC-00: `not-started` → `in-progress`).

## Utknęliśmy na

Nic nie blokuje. Story 0.1 zakończona czysto i w całości zweryfikowana. Zgodnie z instrukcją użytkownika ("pracuj sekwencyjnie przez Story 0.1, zatrzymaj się gdy się skończy") — sesja świadomie zatrzymana tutaj, **nie rozpoczęto Story 0.2** mimo że jest technicznie kolejna w kolejności.

## Plan na następny krok

Otwórz `/planning/epics/EPIC-00-repository-agent-foundation.md`, Story 0.2 — "`AGENTS.md` i pięć projektowych Skills dla Claude Code / Codex CLI". Pierwszy nieodhaczony task: "Stwórz root `AGENTS.md`" z regułami no-Playwright, verify-before-checkbox, module-boundary (dokładna treść minimalna podana w źródle `sepa-nexus-iteration-0-foundation-plan.md`, linie 64-81). `verify: test -f AGENTS.md && grep -q "no Playwright" AGENTS.md`. Uwaga: Story 0.2 tworzy INNY zestaw skilli niż `session-handoff`/`artifact-derived-planning`/`epic-story-task-catalog` już istniejące w `.claude/skills/` — te trzy zostają nietknięte, pięć nowych (`spring-modulith-module`, `postgres-rls-migration`, `keycloak-realm-config`, `nextjs-bff-route`, `shadcn-component-scaffold`) dochodzi obok nich. Story 0.2 zależy tylko od pustej listy (`depends_on: []`), więc nie jest blokowana przez nic z tej sesji.

## Pułapki, których nie wolno powtórzyć

- `npm view typescript-eslint peerDependencies` działa bez `node_modules`/`package.json` w `frontend/` (odpytuje rejestr npm bezpośrednio) — nie trzeba wcześniej inicjalizować projektu Node, żeby wykonać ten konkretny `verify`.
- Rejestr npm w tym środowisku pokazuje TypeScript już na wersji 7.0.2/7.1.0-dev (data systemowa 2026-07-13) — to spójne z tym, że CLAUDE.md każe "re-verify versions if starting significantly later than this plan's date"; sama wersja TS nie jest problemem, problemem jest opóźnienie `typescript-eslint` we wspieraniu jej, co zostało poprawnie zidentyfikowane i udokumentowane jako fallback, a nie zignorowane.
- Frontmatter epika (`status` na poziomie całego pliku) i frontmatter pojedynczej story to DWA osobne pola — ukończenie jednej story nie oznacza `status: done` dla epika, dopóki wszystkie jego story nie są `done` (tu: epik EPIC-00 ma `in-progress`, bo Story 0.2/0.3 czekają).
