---
status: not-started
depends_on: [EPIC-05-nextjs-bff, EPIC-06-react-shadcn-frontend-skeleton]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md §14 line 385 (EPIC-FE-0); ADR-N3 line 21; sepa-nexus-react-component-foundation-blueprint.md §9 kroki 1-7"
---

# EPIC-23 — Frontend Foundation (EPIC-FE-0)

Decyzje fundamentu do zamrożenia przed pierwszym prawdziwym ekranem: BFF (ADR-N3, już w EPIC-05), SSE (ADR-N4), codegen OpenAPI+GraphQL w CI, konwencja `data-testid`, schemat deep-linków, mapa rola→ekran, obowiązkowe stany loading/error/empty.

## Story 23.1 — Codegen OpenAPI+GraphQL w CI

status: not-started
depends_on: [EPIC-07-ci-cd-foundation]

Taski:
- [ ] **Wpięcie generowania typów TS z OpenAPI (backend REST) i GraphQL SDL do pipeline'u CI frontendu.**
      `verify: npm run codegen && git diff --exit-code frontend/generated/` (brak niezacommitowanego dryfu po regeneracji w CI).

## Story 23.2 — Konwencja `data-testid` i deep-linki jako standard projektu

status: not-started
depends_on: []

Opis: `data-testid="{screen}.{component}.{element}"` — formalizacja wzorca użytego już w EPIC-06 Story 6.4.

Taski:
- [ ] **Udokumentuj i wymuś (lint rule lub code review checklist) konwencję `data-testid` oraz schemat deep-linków dla wszystkich przyszłych ekranów.**
      `verify: test -f frontend/CONVENTIONS.md && grep -q "data-testid" frontend/CONVENTIONS.md`

## Story 23.3 — Mapa rola→ekran + stany loading/error/empty

status: not-started
depends_on: []

Opis: §9 Role-to-Workspace Matrix z frontend blueprintu jako źródło; empty≠unauthorized (finding z component-foundation §6).

Taski:
- [ ] **Zaimplementuj współdzielony komponent stanu (loading/error/empty) używany przez każdy ekran; empty state nigdy nie wygląda jak unauthorized.**
      `verify: npm run build` + ręczna kontrola wg checklisty component-foundation §6.

## Story 23.4 — Krok 6-7 adoption planu: checklist a11y + podgląd komponentów

status: not-started
depends_on: [EPIC-06-react-shadcn-frontend-skeleton]

Opis: kroki 6-7 z `sepa-nexus-react-component-foundation-blueprint.md` §9 — checklist dostępności (tymczasowa, do czasu bramki axe-core w kroku 9/EPIC-24) i lekki podgląd komponentów.

Taski:
- [ ] **Checklist dostępności per ekran, używana w code review do czasu wpięcia bramki axe-core (EPIC-24).**
      `verify: test -f frontend/A11Y-CHECKLIST.md`
