# HANDOFF

## Zadanie

Debina to syntetyczna platforma badawcza SEPA/ISO 20022. Na gałęzi `rebase/enterprise-evolution` trwa Phase D; obecnie domknięto D3A-owy, lokalny i efemeryczny overlay realm Keycloak, bez uruchamiania Chromium ani testów płatności.

## Zrobione

- D2B jest potwierdzone: jawne `dagger call testcontainers-regression --runtime-socket=/run/podman/podman.sock` wykonało 540 testów bez błędów; `fast` i socket-free `integration` pozostają niezależne od socketa.
- D3A ma potwierdzone PostgreSQL/Flyway, Keycloak, backend i frontend production readiness. Keycloak działa przez `AsService.Args`, backend jest uporządkowany markerem po Flyway, a frontend używa `pnpm exec next start --hostname 0.0.0.0 --port 3000`.
- Zatwierdzony overlay D3A nie zmienia `infra/keycloak/realm-export.json`. `dagger/pure/realm_overlay.go` deterministycznie dodaje wyłącznie `http://frontend:3000/api/auth/callback` i `http://frontend:3000`, a testy fail-closed kontrolują strukturę realm, klientów, kolejność i listy URI/origin.
- `dagger/cmd/realm-overlay` tworzy jeden lokalny katalog grafu z `realm-export.json` oraz `verified.marker`; marker powstaje wyłącznie po udanym zapisie zweryfikowanego realm. Keycloak importuje ten sam plik jako `1000:1000`, mode `0640`, a klient readiness odczytuje marker przed discovery.
- Końcowy proof `dagger call smoke-keycloak-readiness stdout --progress=plain` przeszedł w `elapsed=0:36.37 exit=0`: helper, marker, import `sepa-nexus`, TCP 8080 i alias-bound OpenID discovery. Dagger pokazał później znany nadrzędny `ERROR` teardown, ale `Container.stdout` było DONE, oba bezpieczne markery istnieją i shell zwrócił 0.
- Audyt publicznego API Daggera z `1503e21` potwierdził: `fast`, `integration`, trzy jawne funkcje Testcontainers oraz liście PostgreSQL/Flyway, Keycloak, backend i frontend są zaimplementowane; wszystkie mają wskazane proofy. Nie istnieje `all`. `smoke` istnieje jako publiczny check, ale jest tylko częściowym runnerem: po readiness uruchamia nieśledzony draft `frontend/e2e/d3a-vertical-smoke.spec.ts` przez `pnpm run test:smoke:d3a`.

## Utknęliśmy na

`dagger check smoke` nie jest uczciwym proofem D3A: jego publiczny opis deklaruje PKCE/BFF session, ale źródło zawiera tylko runtime readiness oraz nieśledzony draft Playwright. Draft używa `http://localhost:3000` i `--host-resolver-rules=MAP localhost frontend`, co jest sprzeczne z zatwierdzonym aliasowym originem `http://frontend:3000`; nie był wykonany ani staged. Chromium, logowanie, D3B i D4 nie zostały uruchomione. Należy nadal zachować user-owned `build/generated-spring-modulith/javadoc.json` poza stagingiem; zewnętrzny drift jest poza zakresem Phase D.

## Plan na następny krok

Wykonaj wyłącznie `D3A-VERTICAL-LOGIN-SESSION-HEALTH`: zastąp częściowy runner jednym śledzonym testem Chromium używającym `http://frontend:3000` i `http://keycloak:8080`, realnego authorization-code/PKCE, callbacku, `sepa_session`, `/api/session` i authenticated shell; nie dodawaj żadnej podróży płatności.

## Pułapki, których nie wolno powtórzyć

- Nie uruchamiaj długowiecznych Keycloak/backend/frontend procesów przez `WithExec`; korzystaj z `AsService.Args` i krótkotrwałego klienta.
- Nie odtwarzaj ani nie stage’uj `build/generated-spring-modulith/javadoc.json`; nie wykonuj/stage’uj `frontend/e2e/` w tym pakiecie.
- Overlay nie może dotknąć canonical realm, rozszerzać wildcardów, zmieniać klienta `sepa-web` poza dwoma wpisami ani ujawniać sekretów. Nie eksportuj generowanego realm/markera z grafu.
- Nie traktuj istnienia `dagger check smoke` ani nieśledzonego `frontend/e2e/` jako implementacji lub proofu browser journey. Nie używaj `localhost`, mapowania `/etc/hosts` ani `--host-resolver-rules` w grafie Dagger.
- Jawny Testcontainers socket pozostaje tylko w dedykowanym poleceniu; nie ukrywaj go w `fast` lub `integration`.
