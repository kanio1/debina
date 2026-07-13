---
status: not-started
depends_on: [EPIC-02-keycloak-realm-iteration-0, EPIC-03-spring-modulith-backend-skeleton]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 5 (Story 5.1-5.4), lines 472-505"
---

# EPIC-05 — Next.js BFF

Jedyne miejsce, z którym rozmawia przeglądarka — sesja po stronie serwera, CSRF, nagłówki bezpieczeństwa, proxy do `sepa-api` wyłącznie server-to-server (ADR-N3).

## Story 5.1 — Szkielet projektu

status: not-started
depends_on: []

Opis: `create-next-app` przypięty na `16.2.10`+ — źródło: linie 478-484.

Kryterium ukończenia: build przechodzi, wersja Next.js potwierdzona ≥16.2.10.

Taski:
- [ ] **Zaszkieletuj `create-next-app`, przypięty na `16.2.10` lub nowszy** `[SECURITY-PIN]` (majowe 2026 wydanie bezpieczeństwa naprawiło obejście autoryzacji middleware/proxy — nie szkieletuj na starszym 16.2.x). Wygenerowany `AGENTS.md` scal z root `AGENTS.md` z EPIC-00 Story 0.2, zamiast trzymać dwa.
      `verify: cd frontend && npx create-next-app@16.2.10 . --typescript --app --tailwind && npm run build` → build się udaje.
- [ ] **Potwierdź, że wersja z poprawką wylądowała.**
      `verify: npm list next | grep 16.2.1` → wersja ≥16.2.10.

## Story 5.2 — OIDC Authorization Code + PKCE, sesja HttpOnly

status: not-started
depends_on: [Story 5.1, EPIC-02-keycloak-realm-iteration-0]

Opis: pełny flow logowania bez tokenu w przeglądarce — źródło: linie 486-492.

Kryterium ukończenia: logowanie/wylogowanie działa end-to-end, token nigdy nie trafia do przeglądarki.

Taski:
- [ ] **Zaimplementuj flow Auth Code + PKCE** wobec klienta `sepa-web`: `/api/auth/login` przekierowuje do Keycloak, `/api/auth/callback` wymienia kod, przechowuje tokeny wyłącznie po stronie serwera, ustawia opaque `HttpOnly`/`Secure`/`SameSite=Lax` cookie sesji.
      `verify: curl -c cookies.txt -sI http://localhost:3000/api/auth/login | grep -i location` → przekierowanie do Keycloak z parametrem `code_challenge`.
- [ ] **Middleware odczytu sesji**: każdy request rozwiązuje opaque cookie na token po stronie serwera (in-memory Map wystarcza w Iteracji 0; Redis to ulepszenie późniejszej iteracji).
      `verify: po realnym logowaniu w przeglądarce, curl -b cookies.txt http://localhost:3000/api/session` → zwraca claimy zalogowanego użytkownika, nigdy surowy JWT.
- [ ] **Wylogowanie czyści sesję** po stronie serwera i przekierowuje do end-session endpointu Keycloak.
      `verify: curl -b cookies.txt -c cookies.txt http://localhost:3000/api/auth/logout && curl -b cookies.txt http://localhost:3000/api/session` → drugie wywołanie zwraca 401.

## Story 5.3 — Nagłówki bezpieczeństwa i CSRF

status: not-started
depends_on: [Story 5.2]

Opis: `middleware.ts` z CSP/HSTS/itd. + CSRF na trasach zmieniających stan — źródło: linie 494-499.

Kryterium ukończenia: nagłówki obecne na każdej odpowiedzi, brak tokenu CSRF → 403.

Taski:
- [ ] **`middleware.ts`** ustawia CSP, HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy na każdej odpowiedzi.
      `verify: curl -sI http://localhost:3000/ | grep -iE "content-security-policy|strict-transport-security|x-frame-options"` → wszystkie trzy obecne.
- [ ] **Token CSRF** wydawany przy tworzeniu sesji, walidowany na każdej trasie zmieniającej stan (`POST`/`PUT`/`DELETE`).
      `verify: curl -b cookies.txt -X POST http://localhost:3000/api/payments -d '{}' -H "Content-Type: application/json"` (bez nagłówka CSRF) → 403; z poprawnym tokenem → przechodzi do backendu.

## Story 5.4 — Proxy server-side do `sepa-api`

status: not-started
depends_on: [Story 5.3, EPIC-03-spring-modulith-backend-skeleton/Story 3.2]

Opis: jedna trasa serwerowa łącząca BFF z backendem — źródło: linie 501-504.

Kryterium ukończenia: przesłana płatność ląduje w `payment.payments`.

Taski:
- [ ] **Jedna trasa serwerowa**, `POST /api/payments`, walidująca sesję, doczepiająca token pobrany po stronie serwera, przekazująca do `sepa-api` `POST /api/v1/payments` — przeglądarka nigdy nie widzi URL ani tokenu `sepa-api`.
      `verify: po realnym logowaniu w przeglądarce, prześlij płatność przez tę trasę, potwierdź przez psql, że wiersz wylądował w payment.payments.`
