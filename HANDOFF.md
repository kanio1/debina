# HANDOFF

## Zadanie

Debina to syntetyczna platforma badawcza SEPA/ISO 20022. Na gałęzi `rebase/enterprise-evolution` trwa Phase D; D3A-owy lokalny Chromium proof login → BFF session → shell jest potwierdzony, bez uruchamiania podróży płatności.

## Zrobione

- D2B jest potwierdzone: jawne `dagger call testcontainers-regression --runtime-socket=/run/podman/podman.sock` wykonało 540 testów bez błędów; `fast` i socket-free `integration` pozostają niezależne od socketa.
- D3A ma potwierdzone PostgreSQL/Flyway, Keycloak, backend i frontend production readiness. Keycloak działa przez `AsService.Args`, backend jest uporządkowany markerem po Flyway, a frontend używa `pnpm exec next start --hostname 0.0.0.0 --port 3000`.
- Korekta D3A credential-contract jest lokalnie przygotowana i potwierdzona pojedynczym leafem: marker Flyway jest zapisywany przez ten sam skończony proces co `migrate`+`validate` (60 migracji), następnie aliasowy klient loguje się jako `sepa_app` do `sepa_nexus` tym samym sekretem, który otrzymuje backend. `dagger call smoke-backend-credential-readiness --progress=plain` zwrócił `elapsed=0:09.57 exit=0`; `smoke-backend-readiness` po korekcie zwrócił `elapsed=0:47.57 exit=0`, z markerem i `/actuator/health`.
- Zatwierdzony overlay D3A nie zmienia `infra/keycloak/realm-export.json`. `dagger/pure/realm_overlay.go` deterministycznie dodaje wyłącznie `http://frontend:3000/api/auth/callback` i `http://frontend:3000`, a testy fail-closed kontrolują strukturę realm, klientów, kolejność i listy URI/origin.
- `dagger/cmd/realm-overlay` tworzy jeden lokalny katalog grafu z `realm-export.json` oraz `verified.marker`; marker powstaje wyłącznie po udanym zapisie zweryfikowanego realm. Keycloak importuje ten sam plik jako `1000:1000`, mode `0640`, a klient readiness odczytuje marker przed discovery.
- Końcowy proof `dagger call smoke-keycloak-readiness stdout --progress=plain` przeszedł w `elapsed=0:36.37 exit=0`: helper, marker, import `sepa-nexus`, TCP 8080 i alias-bound OpenID discovery. Dagger pokazał później znany nadrzędny `ERROR` teardown, ale `Container.stdout` było DONE, oba bezpieczne markery istnieją i shell zwrócił 0.
- Audyt publicznego API Daggera z `1503e21` potwierdził: `fast`, `integration`, trzy jawne funkcje Testcontainers oraz liście PostgreSQL/Flyway, Keycloak, backend i frontend są zaimplementowane; wszystkie mają wskazane proofy. Nie istnieje `all`.
- D3A vertical Chromium proof przeszedł: `dagger call smoke-login-session-health --progress=plain` uruchomił jeden test w jednym workerze i zwrócił `elapsed=1:05.17 exit=0`; realna ścieżka jest `http://frontend:3000` → Keycloak username-first/PKCE → callback → host-only HttpOnly `sepa_session` → `/api/session` → shell. `sub` i `payment_submitter` są wymaganym safe identity/authorization proofem; `preferredUsername` jest zgodnie z kontraktem `null` (client `sepa-web` ma wyłącznie `basic` i `sepa-guc`), a shell pokazuje istniejący fallback `unknown user`. Nie ma tokenów, kodu, sekretu ani wartości cookie w projection. `dagger check smoke` przeszedł przez ten sam finite leaf z cache Daggera (`elapsed=0:04.39 exit=0`).

## Utknęliśmy na

Pierwsze proofy D3A odsłoniły kolejno ordering Flyway, credential authority, username-first authenticator i callback redirect przez bind address `0.0.0.0`. Wszystkie zostały naprawione w ramach lokalnego grafu albo istniejącego publicznego `BFF_BASE_URL`; canonical realm, PKCE, state/nonce, cookie security i produkcyjne zachowanie autoryzacyjne nie zostały osłabione. Ostatnia rozbieżność `preferredUsername: null` była `TEST-EXPECTATION-OVERCONSTRAINED`: typ `SessionClaims` oraz scope’y `sepa-web` deklarują ten field jako nullable, stabilnym identity jest `sub`, a shell posiada jawny fallback. D3B i D4 nie zostały uruchomione. User-owned `build/generated-spring-modulith/javadoc.json` musi nadal pozostać poza stagingiem; zewnętrzny drift jest poza zakresem Phase D.

## Plan na następny krok

Kolejny krok wymaga nowego, jawnie zatwierdzonego pakietu Phase D. Nie rozpoczynaj D3B, D4 ani Phase E bez takiej decyzji. Zachowaj jawny socket contract Testcontainers oraz D3A `smoke` ograniczony wyłącznie do login/session/health.

## Pułapki, których nie wolno powtórzyć

- Nie uruchamiaj długowiecznych Keycloak/backend/frontend procesów przez `WithExec`; korzystaj z `AsService.Args` i krótkotrwałego klienta.
- Nie odtwarzaj ani nie stage’uj `build/generated-spring-modulith/javadoc.json`; nie wykonuj/stage’uj `frontend/e2e/` w tym pakiecie.
- Overlay nie może dotknąć canonical realm, rozszerzać wildcardów, zmieniać klienta `sepa-web` poza dwoma wpisami ani ujawniać sekretów. Nie eksportuj generowanego realm/markera z grafu.
- Nie rozszerzaj `dagger check smoke` poza jeden potwierdzony D3A browser journey. Nie używaj `localhost`, mapowania `/etc/hosts` ani `--host-resolver-rules` w grafie Dagger.
- Nie interpretuj nullable `preferredUsername` jako missing authentication: obowiązujący bezpieczny identity proof to non-empty `sub` plus oczekiwana rola. Nie zmieniaj mapperów Keycloak ani BFF claim source bez osobnej decyzji.
- Jawny Testcontainers socket pozostaje tylko w dedykowanym poleceniu; nie ukrywaj go w `fast` lub `integration`.
