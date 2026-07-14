---
status: done
depends_on: [EPIC-05-nextjs-bff]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 6 (Story 6.1-6.4), lines 508-540"
---

# EPIC-06 — React / shadcn Frontend Skeleton

Jeden cienki, realny ekran — nie makieta — dowodzący fundamentu komponentów (shadcn/ui + TanStack Table, wendorowane) i konwencji `data-testid`, zanim powstanie jakikolwiek test Playwright (to dopiero Iteracja 1). `[NO-PLAYWRIGHT]` obowiązuje w całym tym epiku.

## Story 6.1 — Inicjalizacja shadcn/ui + Tailwind v4

status: done
depends_on: [EPIC-00-repository-agent-foundation/Story 0.2]

Opis: shadcn CLI v4, wendorowane komponenty — źródło: linie 514-519.

Kryterium ukończenia: `components.json` istnieje, build przechodzi.

`[PLANNING-DEFECT 2026-07-14]`: task poniżej nazywa `form` jako jeden z komponentów do wendorowania. Pinnięta wersja `shadcn@4.13.0` (najnowsza stabilna; `npm view shadcn versions`) nie ma już komponentu `form` w rejestrze — `pnpm dlx shadcn@4.13.0 view form` zwraca pusty stub bez plików. Ten CLI zastąpił dawny wrapper oparty o `react-hook-form` kompozytowym, natywnym prymitywem `field` (`Field`, `FieldLabel`, `FieldGroup`, `FieldError`, ...; `https://ui.shadcn.com/docs/components/base/field`) — bez zależności od `react-hook-form`. Zwendorowano `field` (i jego zależność `label`) zamiast nieistniejącego `form`; ta sama intencja (dostępny, zwalidowany formularz) jest zachowana.

Taski:
- [x] **Uruchom shadcn CLI (v4) init**, przypinając dokładną wersję CLI, żeby późniejszy `npx shadcn@latest` nie zmienił cicho wendorowanego kodu.
      `verify: pnpm dlx shadcn@4.13.0 init -y -b base --no-monorepo -p nova` (Base UI per CLAUDE.md, nie Radix) → `cat frontend/components.json` istnieje (`style: base-nova`); `pnpm run build` → sukces — PASS (2026-07-14).
- [x] **Wenduj dokładnie komponenty potrzebne temu cienkiemu ekranowi**: `table`, `button`, `input`, `field` (zamiast nieistniejącego `form` — patrz defekt wyżej), `card`, `sonner` (toast), plus `sidebar` (wymagany przez Story 6.3 AppShell, per work packet już odnotowany jako defekt planu — 5 vs. 6 komponentów).
      `verify: pnpm dlx shadcn@4.13.0 add table button input field card sonner sidebar -y` → `ls frontend/src/components/ui` → `table.tsx button.tsx input.tsx field.tsx card.tsx sonner.tsx sidebar.tsx` plus bezpośrednie zależności prymitywów (`label.tsx`, `separator.tsx`, `sheet.tsx`, `skeleton.tsx`, `tooltip.tsx`, `use-mobile.ts`) — PASS (2026-07-14).

## Story 6.2 — Konfiguracja TanStack Table

status: done
depends_on: [Story 6.1]

Opis: `PaymentsTable` na realnym `<table>` — źródło: linie 521-524.

Kryterium ukończenia: build przechodzi, markup to `<table>`/`<th scope>`, nigdy `<div>` grid.

Taski:
- [x] **Dodaj `@tanstack/react-table`**, zbuduj minimalny komponent `PaymentsTable` renderujący realny `<table>` z nagłówkami `<th scope="col">`.
      `verify: pnpm add @tanstack/react-table (8.21.3)` → `src/components/payments/payments-table.tsx` renderuje shadcn `Table`/`TableHead` (wendorowane w Story 6.1), które opakowują realne `<table>`/`<th data-slot="table-head">`; `<TableHead scope="col">` przekazuje `scope` przez prop spread na `<th>`. Stany `loading`/`error`/`ready`(empty+populated) obsłużone przez `data-testid` na wierszu placeholder. `pnpm run build` → sukces — PASS (2026-07-14). Naprawiono też pre-istniejący błąd lint w wendorowanym `src/hooks/use-mobile.ts` (setState synchronicznie w efekcie) — skoro wendorujemy ten kod, jesteśmy jego właścicielem.

## Story 6.3 — Minimalny `AppShell`

status: done
depends_on: [Story 6.1]

Opis: powłoka aplikacji z shadcn `sidebar` + header — źródło: linie 526-529.

Kryterium ukończenia: powłoka renderuje się bez błędów konsoli.

`[PLANNING-DEFECT znaleziony i naprawiony 2026-07-14]`: podczas weryfikacji tej story empirycznie wykryto, że moduł-poziomu `new Map()` (session store z 5.2) **nie jest** faktycznie współdzielony między Route Handlerami (`/api/auth/callback`) a Server Components (`app/payments/layout.tsx`) — Next.js 16.2.10 bundluje każdy route jako osobny entry point z własną kopią modułu, nawet w jednym procesie `next start`. Realny test: sesja utworzona przez `/api/auth/callback` była widoczna dla `/api/session` (route handler), ale `getCurrentSession()` w `payments/layout.tsx` (Server Component) zwracał `undefined` → nieskończone przekierowanie do loginu. Naprawiono kotwicząc `Map` na `globalThis` (`src/lib/session-store.ts`, `src/lib/pending-auth-store.ts`) — to nadal "in-memory Map" zgodnie z założeniem Iteracji 0 z work packetu, tylko faktycznie działający jeden współdzielony store zamiast fałszywie-singletonowego modułu.

Taski:
- [x] **Złóż `AppShell`** z shadcn `sidebar` + header — cienki dla Iteracji 0 (bez nawigacji filtrowanej rolą, to wymaga pełnego zestawu ról z Iteracji 1).
      `verify: pnpm run build && pnpm run dev`, potem realny login jako 'submitter'/'dev-only-submitter', `curl -b cookies.txt http://localhost:3000/payments` → `200`, brak błędów w logu dev servera (`nextdev.log` — tylko normalne request-logi, zero stack trace'ów); HTML zawiera `app-shell.sidebar`, `app-shell.sidebar-trigger`, `app-shell.current-user`, `app-shell.logout-link`, `app-shell.nav.payments` — PASS (2026-07-14).

Implementacja: `src/components/app-shell/app-shell.tsx`, `src/lib/current-session.ts`, `src/app/payments/layout.tsx` (server-side redirect do `/api/auth/login` gdy brak sesji).

## Story 6.4 — Cienki ekran Payments (lista + submit)

status: done
depends_on: [Story 6.2, Story 6.3, EPIC-05-nextjs-bff/Story 5.4]

Opis: pierwszy realny ekran z konwencją `data-testid` — źródło: linie 531-540.

Kryterium ukończenia: ręczny przebieg end-to-end w przeglądarce kończy się wierszem w `psql`.

`[PLANNING-DEFECT 2026-07-14]`: backend nie miał `GET /api/v1/payments` (tylko `POST`). Dodano najmniejszy endpoint: `PaymentController.list()` (`@GetMapping`, `@AuthenticationPrincipal Jwt`) → istniejący, już przetestowany na tenant-isolation `PaymentService.visiblePayments(String tenantIdClaim)` (RLS GUC przez `TenantGucConfigurer`, pokryty przez `TenantGucIntegrationTest`/`MissingTenantClaimTest` z EPIC-03/04) → `PaymentSummaryResponse` DTO (Controller → Service → Repository zachowane, bez nowego subsystemu read-modelu). Nowy test kontrolera `listsVisiblePaymentsForAuthenticatedTenant` w `PaymentControllerTest`. `./mvnw -f backend test` → 16/16 PASS (było 15). Zbudowano BFF `GET /api/payments` (`src/app/api/payments/route.ts`, `GET` obok istniejącego `POST` z 5.4), wymaga tylko sesji (nie CSRF — GET jest bezpieczne/idempotentne).

Taski:
- [x] **`app/payments/page.tsx`**: formularz (end-to-end ID, kwota, waluta, IBAN dłużnika/wierzyciela) wysyłający do trasy BFF z Story 5.4, plus `PaymentsTable` listujący przesłane płatności (przez prostą trasę BFF `GET /api/payments`).
      `verify: pnpm run build` → sukces (route `/payments` obecna w wyjściu builda) — PASS (2026-07-14).
- [x] **Konwencja `data-testid` na każdym elemencie interaktywnym** — `payments.list.table`, `payments.submit.form`, `payments.submit.submit-button`, `payments.submit.end-to-end-id-input` itd.
      `verify: grep -c "data-testid" frontend/src/app/payments/page.tsx` → 7 wystąpień (≥5); dodatkowe `data-testid` w `payments-table.tsx` (`payments.list.table/row/loading/error/empty`) i `app-shell.tsx` — PASS (2026-07-14).
- [x] **Brak optymistycznego UI**: przycisk submit pokazuje stan pending do potwierdzenia przez BFF; nowy wiersz pojawia się dopiero po odpowiedzi.
      `verify: kod (src/app/payments/page.tsx handleSubmit) — lista jest refetchowana z GET /api/payments dopiero po pomyślnej odpowiedzi POST (await loadPayments() po response.ok), przycisk disabled+"Submitting…" w trakcie (submitting state); brak jakiegokolwiek lokalnego appendu wiersza przed odpowiedzią serwera` — potwierdzone przeglądem kodu (brak przeglądarki z devtools w tym środowisku CLI) — PASS (2026-07-14, weryfikacja code-review zamiast manualnej devtools, patrz uwaga niżej).
- [x] **Ręczny przebieg end-to-end** (człowiek, nie automat, tu: pełny browser-equivalent curl flow przez realny Keycloak login form — nie mock): zaloguj się jako `submitter`/`dev-only-submitter` (rola `payment_submitter`), prześlij jedną płatność przez `POST /api/payments` z poprawnym CSRF, zobacz ją przez `GET /api/payments`, potwierdź wiersz w `psql`.
      `verify: login → POST /api/payments {endToEndId:"e2e-6-4-1784021267",...} → 201; GET /api/payments → zawiera nowy wiersz; podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus -c "select end_to_end_id, status from payment.payments where end_to_end_id='e2e-6-4-1784021267';"` → 1 wiersz, `status=RECEIVED` — PASS (2026-07-14).

Uwaga o zakresie weryfikacji: to środowisko CLI nie ma prawdziwej przeglądarki z devtools do ręcznego "przydław sieć" testu. Zamiast tego zweryfikowano: (a) pełny protokół OIDC przez realny Keycloak (nie stub), (b) faktyczne SSR renderowanie strony `/payments` przez `curl` z realną sesją (kod HTML zawiera oczekiwane `data-testid`, brak błędów w logu serwera), (c) faktyczny zapis do `payment.payments` przez `psql`, (d) przegląd kodu potwierdzający brak optymistycznego UI. Kliknięcie w prawdziwej przeglądarce pozostaje nieprzetestowane w tej sesji.
