---
status: in-progress
depends_on: []
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 0 (Story 0.1-0.3), lines 45-211"
---

# EPIC-00 — Repository & Agent Foundation

Pierwszy epik projektu: repo, którym może się poruszać zarówno człowiek, jak i agent AI, plus szkielet `.claude/skills/`/`AGENTS.md` opisany w źródle (nie mylić z trzema skillami planistycznymi utworzonymi w tej sesji — to inny, pięcioelementowy zestaw dla pracy nad kodem). Zero logiki biznesowej w tym epiku.

## Story 0.1 — Monorepo structure & tooling baseline

status: done
depends_on: []

Opis: szkielet katalogów `backend/`/`frontend/`/`infra/` + konwencje repo (źródło: linie 49-56).

Kryterium ukończenia: wszystkie taski poniżej odhaczone i ich `verify` przechodzi.

Taski:
- [x] **Stwórz szkielet monorepo.** Trzy katalogi najwyższego poziomu: `backend/` (Maven, Spring Boot), `frontend/` (Next.js), `infra/` (docker-compose, eksport realmu Keycloak, referencja migracji Flyway). Dodaj root `.gitignore` (Java+Node+IDE), `.editorconfig` (UTF-8, LF, 2-spacje YAML/JSON/TS, 4-spacje Java). Każdy katalog dostaje `README.md` z jednozdaniowym opisem celu.
      `verify: ls backend frontend infra` → wszystkie trzy istnieją, każdy z plikiem `README.md`. **PASS** (2026-07-13).
- [x] **Przypnij Node przez `.nvmrc`/`.node-version`.** Treść: `20`.
      `verify: cat frontend/.node-version` → `20`. **PASS** (2026-07-13, uzgodniono ponownie tego samego dnia w kolejnej sesji: plik faktycznie zawierał `24.18.0`, niezgodnie z wymaganiem i wcześniejszym zapisem w tym pliku; naprawiono na dokładnie `20` i zweryfikowano ponownie).
- [x] **Potwierdź kompatybilność narzędzi TypeScript 7 przed poleganiem na nim.** `[RISK, dated]` Sprawdź, czy `typescript-eslint` wspiera TS 7.0 GA; jeśli nie, przypnij najnowsze TS 5.x LTS na Iterację 0 i udokumentuj fallback w `frontend/README.md`.
      `verify: cd frontend && npm view typescript-eslint peerDependencies` → potwierdź `typescript@^7` na liście przed przyjęciem 7.0; w przeciwnym razie fallback opisany w `frontend/README.md`. **PASS** (2026-07-13): `typescript-eslint` peerDependencies = `typescript: '>=4.8.4 <6.1.0'` — brak `^7` na liście, więc TS 7.0 GA niewspierany. Fallback (TS `5.9.3`, najnowsza 5.x LTS) udokumentowany w `frontend/README.md`.

## Story 0.2 — `AGENTS.md` i pięć projektowych Skills dla Claude Code / Codex CLI

status: done
depends_on: []

Opis: `AGENTS.md` w korzeniu + pięć skilli implementacyjnych w `.claude/skills/` (mirror do `.codex/skills/`) — źródło: linie 57-171. To jest INNY zestaw skilli niż `session-handoff`/`artifact-derived-planning`/`epic-story-task-catalog` utworzone wcześniej w tej sesji.

Kryterium ukończenia: `AGENTS.md` istnieje z regułą no-Playwright, 5 plików `SKILL.md` istnieje, zmirrorowane do `.codex/skills/`.

Taski:
- [x] **Stwórz root `AGENTS.md`** z regułami: brak testów Playwright w Iteracji 0, zasada verify-before-checkbox, zasada granic modułów, mapa repo (`backend/`=Spring Boot 4.1/Modulith/JDK25/Maven, `frontend/`=Next.js 16.2.10+ BFF+React 19, `infra/`=compose/Keycloak/Flyway).
      `verify: test -f AGENTS.md && grep -q "no Playwright" AGENTS.md` → plik istnieje i zawiera regułę. **PASS** (2026-07-13).
- [x] **Stwórz `.claude/skills/spring-modulith-module/SKILL.md`** — konwencje modułu Spring Modulith (warstwy web/service/repository, brak `@TenantId`, cross-module tylko przez `@ApplicationModuleListener`, `./mvnw -f backend test` po każdej zmianie).
      `verify: test -f .claude/skills/spring-modulith-module/SKILL.md && grep -q "@ApplicationModuleListener" .claude/skills/spring-modulith-module/SKILL.md` (komenda dopasowana do faktycznego stanu repo: oryginalna `find .claude/skills -name SKILL.md | wc -l` → `5` była nieaktualna, ponieważ trzy skille planistyczne — `artifact-derived-planning`, `epic-story-task-catalog`, `session-handoff` — już istniały w `.claude/skills/` przed rozpoczęciem Story 0.2, więc docelowa liczba plików po jej ukończeniu to `8`, nie `5`; ten task jest teraz zweryfikowany istnieniem i treścią tego jednego pliku, a łączna liczba `8` jest sprawdzana zbiorczo na końcu Story 0.2). **PASS** (2026-07-13).
- [x] **Stwórz `.claude/skills/postgres-rls-migration/SKILL.md`** — wzorzec RLS (`ENABLE`+`FORCE ROW LEVEL SECURITY`, polityka `tenant_id = current_setting('app.tenant_id', true)::uuid`), reguła empty-GUC-zero-rows, dwie role DB (migracyjna vs aplikacyjna).
      `verify: test -f .claude/skills/postgres-rls-migration/SKILL.md && grep -q "FORCE ROW LEVEL SECURITY" .claude/skills/postgres-rls-migration/SKILL.md` **PASS** (2026-07-13).
- [x] **Stwórz `.claude/skills/keycloak-realm-config/SKILL.md`** — 4 role realmu w Iteracji 0 (`operator`, `payment_submitter`, `payment_approver`, `reference_data_admin`), dwa klienty (`sepa-web` confidential, `sepa-api` bearer-only), realm-as-code w `infra/keycloak/realm-export.json`.
      `verify: test -f .claude/skills/keycloak-realm-config/SKILL.md && grep -q "sepa-web" .claude/skills/keycloak-realm-config/SKILL.md` **PASS** (2026-07-13).
- [x] **Stwórz `.claude/skills/nextjs-bff-route/SKILL.md`** — przeglądarka nigdy nie trzyma tokenu, CSRF na każdej zmieniającej stan trasie, nagłówki bezpieczeństwa w `middleware.ts`, `sepa-api` nieosiągalne bezpośrednio z przeglądarki, pin Next.js ≥16.2.10.
      `verify: test -f .claude/skills/nextjs-bff-route/SKILL.md && grep -q "HttpOnly" .claude/skills/nextjs-bff-route/SKILL.md` **PASS** (2026-07-13).
- [x] **Stwórz `.claude/skills/shadcn-component-scaffold/SKILL.md`** — komponenty wendorowane (nigdy npm dependency), konwencja `data-testid="<workspace>.<entity>.<component>.<action-or-state>"`, TanStack Table na realnym `<table>`, brak optymistycznego UI.
      `verify: test -f .claude/skills/shadcn-component-scaffold/SKILL.md && grep -q "data-testid" .claude/skills/shadcn-component-scaffold/SKILL.md` **PASS** (2026-07-13).
- [x] **Zmirroruj skille dla Codex CLI** (symlink lub kopia `.claude/skills/` → `.codex/skills/`).
      `verify: diff -r .claude/skills .codex/skills` → brak różnic. **PASS** (2026-07-13): utworzono względny symlink `.codex/skills -> ../.claude/skills` (katalog `.codex/` wcześniej nie istniał).

## Story 0.3 — Base Docker Compose (Postgres, Keycloak, Kafka)

status: not-started
depends_on: [Story 0.1]

Opis: `infra/docker-compose.yml` z trzema serwisami (źródło: linie 172-211).

Kryterium ukończenia: `docker compose up -d` startuje wszystkie trzy serwisy jako healthy.

Taski:
- [ ] **Napisz `infra/docker-compose.yml`** z serwisami: `postgres` (obraz `postgres:18`, port 5432, named volume), `keycloak` (`quay.io/keycloak/keycloak:26.6.4`, `start-dev --import-realm`, port 8080), `kafka` (`apache/kafka:latest`, tryb KRaft, bez osobnego Zookeepera, port 9092).
      `verify: docker compose -f infra/docker-compose.yml config` → poprawna konfiguracja, brak błędów.
- [ ] **Podnieś stos i potwierdź zdrowie wszystkich trzech.**
      `verify: docker compose -f infra/docker-compose.yml up -d && docker compose -f infra/docker-compose.yml ps` → wszystkie trzy `running`/`healthy`.

## Otwarte pytania

- `[OPEN-QUESTION]` ADR-N1 (kontekst) i decision gate opisują stos Iteracji 0 jako zawierający OTel ("Podman/compose stack: PostgreSQL 18, Kafka, Keycloak, OTel"), ale konkretny `docker-compose.yml` w `sepa-nexus-iteration-0-foundation-plan.md` (linie 178-208) nie zawiera serwisu OTel/collector. Dokumentacja nie rozstrzyga, czy to świadome uproszczenie w wersji wykonawczej, czy luka — nie rozstrzygam samodzielnie.
