---
status: not-started
depends_on: [EPIC-05-nextjs-bff]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 6 (Story 6.1-6.4), lines 508-540"
---

# EPIC-06 — React / shadcn Frontend Skeleton

Jeden cienki, realny ekran — nie makieta — dowodzący fundamentu komponentów (shadcn/ui + TanStack Table, wendorowane) i konwencji `data-testid`, zanim powstanie jakikolwiek test Playwright (to dopiero Iteracja 1). `[NO-PLAYWRIGHT]` obowiązuje w całym tym epiku.

## Story 6.1 — Inicjalizacja shadcn/ui + Tailwind v4

status: not-started
depends_on: [EPIC-00-repository-agent-foundation/Story 0.2]

Opis: shadcn CLI v4, wendorowane komponenty — źródło: linie 514-519.

Kryterium ukończenia: `components.json` istnieje, build przechodzi.

Taski:
- [ ] **Uruchom shadcn CLI (v4) init**, przypinając dokładną wersję CLI, żeby późniejszy `npx shadcn@latest` nie zmienił cicho wendorowanego kodu.
      `verify: cat frontend/components.json` → istnieje, zapisuje konfigurację shadcn; `npm run build` → sukces.
- [ ] **Wenduj dokładnie komponenty potrzebne temu cienkiemu ekranowi**: `table`, `button`, `input`, `form`, `card`, `sonner` (toast) — nie cały zestaw.
      `verify: ls frontend/components/ui` → zawiera dokładnie te pięć (plus bezpośrednie zależności prymitywów).

## Story 6.2 — Konfiguracja TanStack Table

status: not-started
depends_on: [Story 6.1]

Opis: `PaymentsTable` na realnym `<table>` — źródło: linie 521-524.

Kryterium ukończenia: build przechodzi, markup to `<table>`/`<th scope>`, nigdy `<div>` grid.

Taski:
- [ ] **Dodaj `@tanstack/react-table`**, zbuduj minimalny komponent `PaymentsTable` renderujący realny `<table>` z nagłówkami `<th scope="col">`.
      `verify: npm run build` → sukces; ręczna kontrola w devtools potwierdza markup `<table>`/`<th scope>`.

## Story 6.3 — Minimalny `AppShell`

status: not-started
depends_on: [Story 6.1]

Opis: powłoka aplikacji z shadcn `sidebar` + header — źródło: linie 526-529.

Kryterium ukończenia: powłoka renderuje się bez błędów konsoli.

Taski:
- [ ] **Złóż `AppShell`** z shadcn `sidebar` + header — cienki dla Iteracji 0 (bez nawigacji filtrowanej rolą, to wymaga pełnego zestawu ról z Iteracji 1).
      `verify: npm run build && npm run dev` potem ręcznie załaduj `http://localhost:3000` → powłoka renderuje się bez błędów konsoli.

## Story 6.4 — Cienki ekran Payments (lista + submit)

status: not-started
depends_on: [Story 6.2, Story 6.3, EPIC-05-nextjs-bff/Story 5.4]

Opis: pierwszy realny ekran z konwencją `data-testid` — źródło: linie 531-540.

Kryterium ukończenia: ręczny przebieg end-to-end w przeglądarce kończy się wierszem w `psql`.

Taski:
- [ ] **`app/payments/page.tsx`**: formularz (end-to-end ID, kwota, waluta, IBAN dłużnika/wierzyciela) wysyłający do trasy BFF z Story 5.4, plus `PaymentsTable` listujący przesłane płatności (przez prostą trasę BFF `GET /api/payments`).
      `verify: npm run build` → sukces.
- [ ] **Konwencja `data-testid` na każdym elemencie interaktywnym** — `payments.list.table`, `payments.submit.form`, `payments.submit.submit-button`, `payments.submit.end-to-end-id-input` itd. Żaden test Playwright nie jest tu pisany — ID istnieją, żeby Iteracja 1 mogła napisać test bez dotykania tego komponentu ponownie.
      `verify: grep -c "data-testid" frontend/app/payments/page.tsx` → co najmniej 5 wystąpień.
- [ ] **Brak optymistycznego UI**: przycisk submit pokazuje stan pending do potwierdzenia przez BFF; nowy wiersz pojawia się dopiero po odpowiedzi.
      `verify: ręczna kontrola — przydław sieć w devtools, potwierdź że wiersz nie pojawia się przed odpowiedzią.`
- [ ] **Ręczny przebieg end-to-end** (człowiek, nie automat): zaloguj się jako `payment_submitter`, prześlij jedną płatność, zobacz ją w tabeli, potwierdź wiersz w `psql`.
      `verify: psql -c "SELECT end_to_end_id, status FROM payment.payments"` → przesłana płatność obecna ze statusem `RECEIVED`.
