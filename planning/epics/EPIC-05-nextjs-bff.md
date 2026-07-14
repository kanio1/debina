---
status: done
depends_on: [EPIC-02-keycloak-realm-iteration-0, EPIC-03-spring-modulith-backend-skeleton]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 5 (Story 5.1-5.4), lines 472-505"
---

# EPIC-05 — Next.js BFF

Jedyne miejsce, z którym rozmawia przeglądarka — sesja po stronie serwera, CSRF, nagłówki bezpieczeństwa, proxy do `sepa-api` wyłącznie server-to-server (ADR-N3).

## Story 5.1 — Szkielet projektu

status: done
depends_on: []

Opis: `create-next-app` przypięty na `16.2.10`+ — źródło: linie 478-484.

Kryterium ukończenia: build przechodzi, wersja Next.js potwierdzona ≥16.2.10.

Taski:
- [x] **Zaszkieletuj `create-next-app`, przypięty na `16.2.10` lub nowszy** `[SECURITY-PIN]` (majowe 2026 wydanie bezpieczeństwa naprawiło obejście autoryzacji middleware/proxy — nie szkieletuj na starszym 16.2.x). Wygenerowany `AGENTS.md` scal z root `AGENTS.md` z EPIC-00 Story 0.2, zamiast trzymać dwa.
      `verify (adapted, npm→pnpm per [PLANNING-DEFECT], AGENTS.md conflict rules): scaffolded via 'pnpm dlx create-next-app@16.2.10 . --typescript --app --tailwind --eslint --src-dir --use-pnpm' into a temp dir, merged into frontend/ preserving .node-version and README.md, merged generated AGENTS.md content into root AGENTS.md; 'CI=true pnpm install --frozen-lockfile && pnpm run build' → PASS (2026-07-14).`
- [x] **Potwierdź, że wersja z poprawką wylądowała.**
      `verify (adapted npm→pnpm): pnpm list next | grep 16.2.1` → `next@16.2.10` — PASS (2026-07-14).

`[PLANNING-DEFECT 2026-07-14]`: session work packet asked for a "TypeScript 6.x exact pin"; no stable TypeScript 6.x exists (npm registry jumps 5.9.3 → 6.0.0-beta/dev → 7.x dev, no 6.0.0 GA). Kept the pre-existing, correctly-derived `5.9.3` pin from `frontend/README.md`'s `[USER-DECISION 2026-07-13]` fallback rule instead.

## Story 5.2 — OIDC Authorization Code + PKCE, sesja HttpOnly

status: done
depends_on: [Story 5.1, EPIC-02-keycloak-realm-iteration-0]

Opis: pełny flow logowania bez tokenu w przeglądarce — źródło: linie 486-492.

Kryterium ukończenia: logowanie/wylogowanie działa end-to-end, token nigdy nie trafia do przeglądarki.

Taski:
- [x] **Zaimplementuj flow Auth Code + PKCE** wobec klienta `sepa-web`: `/api/auth/login` przekierowuje do Keycloak, `/api/auth/callback` wymienia kod, przechowuje tokeny wyłącznie po stronie serwera, ustawia opaque `HttpOnly`/`Secure`/`SameSite=Lax` cookie sesji.
      `verify: curl -c cookies.txt -sI http://localhost:3000/api/auth/login | grep -i location` → redirect zawiera `code_challenge=...&code_challenge_method=S256` — PASS (2026-07-14).
- [x] **Middleware odczytu sesji**: każdy request rozwiązuje opaque cookie na token po stronie serwera (in-memory Map wystarcza w Iteracji 0; Redis to ulepszenie późniejszej iteracji).
      `verify: pełny browser-equivalent login (curl z jarem cookie przez Keycloak login form dla usera 'operator'/'dev-only-operator', realny code+state+PKCE roundtrip) → curl -b cookies.txt http://localhost:3000/api/session` → `{"claims":{"sub":...,"preferredUsername":"operator","tenantId":"00000000-0000-0000-0000-000000000001","branchId":"...-0101","roles":[]}}`, brak surowego JWT w odpowiedzi — PASS (2026-07-14).
- [x] **Wylogowanie czyści sesję** po stronie serwera i przekierowuje do end-session endpointu Keycloak.
      `verify: curl -b cookies.txt -c cookies.txt http://localhost:3000/api/auth/logout && curl -b cookies.txt http://localhost:3000/api/session` → logout 307 do Keycloak end-session z `id_token_hint`, `Set-Cookie` czyści `sepa_session`/`sepa_csrf` (`Max-Age=0`); drugie wywołanie `/api/session` → `401` — PASS (2026-07-14).

Implementacja: `src/lib/{oidc-config,pkce,pending-auth-store,session-store,session-cookies}.ts`, `src/app/api/auth/{login,callback,logout}/route.ts`, `src/app/api/session/route.ts`. id_token weryfikowany podpisem przez `jose` (`createRemoteJWKSet` + `jwtVerify`, sprawdza `iss`, `aud`, i `nonce` z pending-auth-store) — silniejsze niż wymagał sam task, bo to bezpośrednio broni przed podstawieniem tokenu.

## Story 5.3 — Nagłówki bezpieczeństwa i CSRF

status: done
depends_on: [Story 5.2]

Opis: `middleware.ts` z CSP/HSTS/itd. + CSRF na trasach zmieniających stan — źródło: linie 494-499.

Kryterium ukończenia: nagłówki obecne na każdej odpowiedzi, brak tokenu CSRF → 403.

`[PLANNING-DEFECT 2026-07-14]`: task poniżej nazywa plik `middleware.ts`, ale zainstalowany Next.js 16.2.10 przenosi tę konwencję na `proxy.ts` (`middleware` jest deprecated — `node_modules/next/dist/docs/.../file-conventions/proxy.md`: *"The middleware file convention is deprecated and has been renamed to proxy"*). Zaimplementowano jako `src/proxy.ts` (`export function proxy(...)`), zgodnie z regułą work packetu "stosuj aktualną konwencję zainstalowanej wersji Next.js".

Taski:
- [x] **`proxy.ts`** (nowa nazwa `middleware.ts` w 16.2.10) ustawia CSP, HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy na każdej odpowiedzi.
      `verify: curl -sI http://localhost:3000/ | grep -iE "content-security-policy|strict-transport-security|x-frame-options"` → wszystkie trzy obecne — PASS (2026-07-14).
- [x] **Token CSRF** wydawany przy tworzeniu sesji (`sepa_csrf`, readable cookie, double-submit vs. server-side `session.csrfToken` porównywane `timingSafeEqual`), walidowany na każdej trasie zmieniającej stan (`POST`/`PUT`/`DELETE`) przez `authorizeStateChangingRequest` (sesja → CSRF → Content-Type, w tej kolejności, per-route zgodnie z zasadą work packetu, nie w proxy).
      `verify: po realnym loginie, curl -b cookies.txt -X POST http://localhost:3000/api/payments -d '{}' -H "Content-Type: application/json"` (bez nagłówka `x-csrf-token`) → `403`; z poprawnym tokenem (`-H "x-csrf-token: $CSRF"`, backend uruchomiony) → `400 application/problem+json` z backendu (walidacja pustego body — pass-through potwierdzony, nie błąd proxy) — PASS (2026-07-14).

Implementacja: `src/lib/security-headers.ts`, `src/proxy.ts`, `src/lib/csrf.ts`, `src/lib/state-changing-request.ts`.

## Story 5.4 — Proxy server-side do `sepa-api`

status: done
depends_on: [Story 5.3, EPIC-03-spring-modulith-backend-skeleton/Story 3.2]

Opis: jedna trasa serwerowa łącząca BFF z backendem — źródło: linie 501-504.

Kryterium ukończenia: przesłana płatność ląduje w `payment.payments`.

Taski:
- [x] **Jedna trasa serwerowa**, `POST /api/payments`, walidująca sesję, doczepiająca token pobrany po stronie serwera, przekazująca do `sepa-api` `POST /api/v1/payments` — przeglądarka nigdy nie widzi URL ani tokenu `sepa-api`.
      `verify: pełny browser-equivalent login jako 'submitter'/'dev-only-submitter' (rola payment_submitter, wymagana przez @PreAuthorize w PaymentService), backend uruchomiony (./mvnw -f backend spring-boot:run), curl -b cookies.txt -X POST http://localhost:3000/api/payments -H "x-csrf-token: $CSRF" -d '{"endToEndId":"e2e-verify-1784017444","amount":123.45,"currency":"EUR","debtorIban":"DE89370400440532013000","creditorIban":"FR1420041010050500013M02606"}' -H "Content-Type: application/json"` → `201 Created`; `podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus -c "select id, end_to_end_id, amount, currency, status from payment.payments where end_to_end_id = 'e2e-verify-1784017444';"` → 1 wiersz, `status=RECEIVED` — PASS (2026-07-14).

Implementacja: `src/app/api/payments/route.ts` (przekazuje `X-Correlation-Id`, doczepia `Authorization: Bearer <server-side access token>`, zwraca RFC 7807 `application/problem+json` 1:1 z backendu na błąd walidacji).
