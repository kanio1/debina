---
status: in-progress
depends_on: [EPIC-05-nextjs-bff, EPIC-06-react-shadcn-frontend-skeleton]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md §14 line 385 (EPIC-FE-0); ADR-N3 line 21; sepa-nexus-react-component-foundation-blueprint.md §9 kroki 1-7"
---

# EPIC-23 — Frontend Foundation (EPIC-FE-0)

Decyzje fundamentu do zamrożenia przed pierwszym prawdziwym ekranem: BFF (ADR-N3, już w EPIC-05), SSE (ADR-N4), codegen OpenAPI+GraphQL w CI, konwencja `data-testid`, schemat deep-linków, mapa rola→ekran, obowiązkowe stany loading/error/empty.

`[AUDYT 2026-07-14]`: przed budową sprawdzono realny stan frontendu (`find frontend/src`, `grep -rn data-testid`) — EPIC-05/06 dostarczyły więcej niż "goły szkielet": pełny BFF (sesja server-side, CSRF double-submit, PKCE), ekran `/payments` (lista+submit) z konwencją `data-testid` już częściowo w użyciu. Story 23.2/23.3/23.4 formalizują/rozszerzają to, co już istnieje — nie zaczynają od zera.

## Story 23.1 — Codegen OpenAPI+GraphQL w CI

status: blocked
depends_on: [EPIC-07-ci-cd-foundation]

`[PLANNING-DEFECT 2026-07-14]`: `depends_on` (EPIC-07, done) jest spełnione, ale rzeczywista zdolność do wygenerowania czegokolwiek nie istnieje: (1) brak `springdoc-openapi`/`swagger` w `backend/pom.xml` — nie ma endpointu `/v3/api-docs`, więc nie ma źródła OpenAPI do wygenerowania; (2) brak `spring-graphql`/`graphql-java` w `backend/pom.xml` — GraphQL nie istnieje w ogóle (potwierdzone ponownie, ta sama luka co w EPIC-16). `npm run codegen`/`frontend/generated/` nie mają dziś żadnego wejścia. Zbudowanie samego `codegen` npm-script bez żadnego z tych dwóch źródeł byłoby generowaniem pustki — nie zrobiono. **Status `blocked`** — odblokuj, gdy backend faktycznie eksponuje OpenAPI (dowolny moduł REST) i/lub GraphQL schema istnieje.

Taski:
- [ ] **Wpięcie generowania typów TS z OpenAPI (backend REST) i GraphQL SDL do pipeline'u CI frontendu.**
      `verify: npm run codegen && git diff --exit-code frontend/generated/` — `NOT RUN`, `blocked` (patrz wyżej).

## Story 23.2 — Konwencja `data-testid` i deep-linki jako standard projektu

status: done
depends_on: []

Opis: `data-testid="{screen}.{component}.{element}"` — formalizacja wzorca użytego już w EPIC-06 Story 6.4.

Taski:
- [x] **Udokumentuj i wymuś (lint rule lub code review checklist) konwencję `data-testid` oraz schemat deep-linków dla wszystkich przyszłych ekranów.**
      `verify: test -f frontend/CONVENTIONS.md && grep -q "data-testid" frontend/CONVENTIONS.md` → PASS (2026-07-14). Nowy `frontend/CONVENTIONS.md` — dokumentuje wzorzec już użyty w `payments-table.tsx`/`payments/page.tsx`/`app-shell.tsx` (potwierdzone `grep -rn data-testid`), plus schemat deep-linków `/{workspace}`, `/{workspace}/[id]`, `/{workspace}/[id]/{tab}` (frontend blueprint §11).

## Story 23.3 — Mapa rola→ekran + stany loading/error/empty

status: done
depends_on: []

Opis: §9 Role-to-Workspace Matrix z frontend blueprintu jako źródło; empty≠unauthorized (finding z component-foundation §6).

Taski:
- [x] **Zaimplementuj współdzielony komponent stanu (loading/error/empty) używany przez każdy ekran; empty state nigdy nie wygląda jak unauthorized.**
      `verify: npm run build` + ręczna kontrola wg checklisty component-foundation §6 → PASS (2026-07-14). Nowy `frontend/src/components/shared/screen-state.tsx` (`ScreenStateContent`/`ScreenState`, kinds: `loading|error|empty|unauthorized|unauthenticated`) — `PaymentsTable` przepisany, by go używać (identyczne `data-testid` zachowane: `payments.list.loading/error/empty`, potwierdzone `grep`). Nowy `frontend/src/lib/role-workspace-map.ts` — tabela `WORKSPACES` skopiowana z §9 (7 workspace'ów, role dokładnie jak w blueprincie), `path` ustawiony tylko dla `payments` (jedyny zbudowany dziś). `AppShell`/`payments/layout.tsx` przepięte na `session.claims.roles` (już populowane z `realm_access.roles` w `api/auth/callback/route.ts` — nie nowa ścieżka danych). **Realny smoke-test przeciw żywemu Keycloak** (nie mock): token dla `submitter` (`payment_submitter`) → `visibleWorkspacesForRoles` zwraca `payments`; token dla `refdata` (`reference_data_admin`) → zwraca `[]` (workspace `reference-data` ma rolę ale nie ma jeszcze `path`) — dwa różne prawdziwe użytkownicy, dwa różne poprawne wyniki, nie próżny dowód.

## Story 23.4 — Krok 6-7 adoption planu: checklist a11y + podgląd komponentów

status: done
depends_on: [EPIC-06-react-shadcn-frontend-skeleton]

Opis: kroki 6-7 z `sepa-nexus-react-component-foundation-blueprint.md` §9 — checklist dostępności (tymczasowa, do czasu bramki axe-core w kroku 9/EPIC-24) i lekki podgląd komponentów.

`[SCOPE]`: krok 7 (podgląd komponentów / `/dev/components`) jest w źródle jawnie warunkowy — "skip if it doesn't earn its setup cost by Iteration 1" — pominięty; jedyny task tego story (`A11Y-CHECKLIST.md`) tego nie wymaga.

Taski:
- [x] **Checklist dostępności per ekran, używana w code review do czasu wpięcia bramki axe-core (EPIC-24).**
      `verify: test -f frontend/A11Y-CHECKLIST.md` → PASS (2026-07-14). Nowy `frontend/A11Y-CHECKLIST.md` — każda pozycja wyprowadzona z istniejącego bloku `[ACCESSIBILITY]` w `sepa-nexus-first-3-screens-ui-spec.md` lub z findingu component-foundation §6, żadna nie wymyślona.
