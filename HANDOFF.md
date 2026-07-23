# HANDOFF

## Zadanie

Debina to syntetyczna platforma badawcza SEPA/ISO 20022. Na gałęzi `rebase/enterprise-evolution` trwa Phase D; obecnie domknięto D3A-owy, lokalny i efemeryczny overlay realm Keycloak, bez uruchamiania Chromium ani testów płatności.

## Zrobione

- D2B jest potwierdzone: jawne `dagger call testcontainers-regression --runtime-socket=/run/podman/podman.sock` wykonało 540 testów bez błędów; `fast` i socket-free `integration` pozostają niezależne od socketa.
- D3A ma potwierdzone PostgreSQL/Flyway, Keycloak, backend i frontend production readiness. Keycloak działa przez `AsService.Args`, backend jest uporządkowany markerem po Flyway, a frontend używa `pnpm exec next start --hostname 0.0.0.0 --port 3000`.
- Korekta D3A credential-contract jest lokalnie przygotowana i potwierdzona pojedynczym leafem: marker Flyway jest zapisywany przez ten sam skończony proces co `migrate`+`validate` (60 migracji), następnie aliasowy klient loguje się jako `sepa_app` do `sepa_nexus` tym samym sekretem, który otrzymuje backend. `dagger call smoke-backend-credential-readiness --progress=plain` zwrócił `elapsed=0:09.57 exit=0`; `smoke-backend-readiness` po korekcie zwrócił `elapsed=0:47.57 exit=0`, z markerem i `/actuator/health`.
- Zatwierdzony overlay D3A nie zmienia `infra/keycloak/realm-export.json`. `dagger/pure/realm_overlay.go` deterministycznie dodaje wyłącznie `http://frontend:3000/api/auth/callback` i `http://frontend:3000`, a testy fail-closed kontrolują strukturę realm, klientów, kolejność i listy URI/origin.
- `dagger/cmd/realm-overlay` tworzy jeden lokalny katalog grafu z `realm-export.json` oraz `verified.marker`; marker powstaje wyłącznie po udanym zapisie zweryfikowanego realm. Keycloak importuje ten sam plik jako `1000:1000`, mode `0640`, a klient readiness odczytuje marker przed discovery.
- Końcowy proof `dagger call smoke-keycloak-readiness stdout --progress=plain` przeszedł w `elapsed=0:36.37 exit=0`: helper, marker, import `sepa-nexus`, TCP 8080 i alias-bound OpenID discovery. Dagger pokazał później znany nadrzędny `ERROR` teardown, ale `Container.stdout` było DONE, oba bezpieczne markery istnieją i shell zwrócił 0.
- Audyt publicznego API Daggera z `1503e21` potwierdził: `fast`, `integration`, trzy jawne funkcje Testcontainers oraz liście PostgreSQL/Flyway, Keycloak, backend i frontend są zaimplementowane; wszystkie mają wskazane proofy. Nie istnieje `all`. `smoke` istnieje jako publiczny check, ale jest tylko częściowym runnerem: po readiness uruchamia nieśledzony draft `frontend/e2e/d3a-vertical-smoke.spec.ts` przez `pnpm run test:smoke:d3a`.

## Utknęliśmy na

`dagger check smoke` nie jest jeszcze potwierdzonym proofem D3A. Pierwotny leaf zatrzymał się przed Chromium, bo oddzielny `WithNewFile` marker nie wymuszał wykonania Flyway; to zostało rozdzielnie naprawione i potwierdzone przez credential oraz backend readiness. Dwa ograniczone leafe pionowe uruchomiły Chromium oraz realną stronę Keycloak, ale nie doszły do przesłania poświadczeń: pierwszy (`/tmp/debina-d3a-login-session-health-fixed.log`, `elapsed=1:15.91 exit=1`) nie znalazł `getByLabel(/^password$/i)`, a jeden zmieniony proof (`/tmp/debina-d3a-login-session-health-locator-fixed.log`, `elapsed=1:13.12 exit=1`) potwierdził brak `input[name="password"][type="password"]` przez 10 s. Następnie bezsekretny leaf diagnostyczny przeszedł (`/tmp/debina-d3a-keycloak-login-form-evidence.log`, `elapsed=1:06.72 exit=0`): domyślny flow realm nie ma własnego `browserFlow` ani `authenticationFlows`, a rzeczywista strona jest `USERNAME-FIRST-FORM` z polem `username` (accessible name `Username or email`) i przyciskiem `Sign In`. Zredagowany ślad ujawnił, że callback ignorował istniejący publiczny `BFF_BASE_URL` i budował redirect z transportowego `request.url`, emitując `http://0.0.0.0:3000/`. Korekta używa wyłącznie tego istniejącego authority; bez nagłówków i bez Dagger aliases w produkcyjnym kodzie. Focused contract test, lint, typecheck, build oraz Go checks przeszły. Jeden proof po korekcie (`/tmp/debina-d3a-login-session-health-post-callback-fixed.log`, `elapsed=1:06.78 exit=1`) przeszedł callback i wrócił do aliasowego frontendu: `sepa_session` istnieje, a `/api/session` zwrócił 200 oraz oczekiwaną rolę. Pierwsza nowa granica to `SESSION-CONTRACT-BLOCKED`: bezpieczne claims mają `preferredUsername: null`, choć pionowy test oczekuje source-backed user identity. Shell nie został jeszcze osiągnięty. Nie wolno ponawiać niezmienionego grafu. D3B i D4 nie zostały uruchomione. Należy nadal zachować user-owned `build/generated-spring-modulith/javadoc.json` poza stagingiem; zewnętrzny drift jest poza zakresem Phase D.

## Plan na następny krok

Wykonaj wyłącznie source-backed diagnostykę kontraktu claims sesji: ustal, czy `preferredUsername` ma pochodzić z ID tokenu, access tokenu lub innego zatwierdzonego claimu obecnego realm i użytkownika; nie naprawiaj mapperów realm ani produkcyjnej semantyki bez osobnej decyzji. Nie ponawiaj niezmienionego grafu, nie zmieniaj produktu ani bezpieczeństwa, nie używaj sekretów, localhost/workaroundów ani nie przechodź do podróży płatności.

## Pułapki, których nie wolno powtórzyć

- Nie uruchamiaj długowiecznych Keycloak/backend/frontend procesów przez `WithExec`; korzystaj z `AsService.Args` i krótkotrwałego klienta.
- Nie odtwarzaj ani nie stage’uj `build/generated-spring-modulith/javadoc.json`; nie wykonuj/stage’uj `frontend/e2e/` w tym pakiecie.
- Overlay nie może dotknąć canonical realm, rozszerzać wildcardów, zmieniać klienta `sepa-web` poza dwoma wpisami ani ujawniać sekretów. Nie eksportuj generowanego realm/markera z grafu.
- Nie traktuj istnienia `dagger check smoke` ani nieśledzonego `frontend/e2e/` jako implementacji lub proofu browser journey. Nie używaj `localhost`, mapowania `/etc/hosts` ani `--host-resolver-rules` w grafie Dagger.
- Nie deklaruj uruchomienia Chromium, PKCE, callbacku ani session proofu, dopóki leaf nie przejdzie; zatrzymany leaf z błędem `sepa_app` nie doszedł do Playwright.
- Nie myl rozwiązanego Dagger credential-contract z obecnym browser callback blockerem. Dwa pierwotne locatory hasła nie odnalazły kontrolki, lecz bezsekretna diagnostyka potwierdziła username-first flow; poprawiony test przesłał poświadczenia i dopiero potem otrzymał `chrome-error://chromewebdata/`. Nie deklaruj callbacku ani session proofu.
- Nie traktuj poprawnego aliasowego callbacku jako sukcesu całej podróży: aktualny źródłowy redirect po callbacku używa transportowego `request.url` i w Dagger zwrócił `http://0.0.0.0:3000/`. Nie wprowadzaj host mappingów ani `localhost` jako obejścia.
- Callback po korekcie doszedł do frontendu i utworzył sesję, ale nie deklaruj pełnego session/shell proofu: `/api/session` zwraca 200 z rolą, lecz `preferredUsername` jest `null`. Najpierw ustal kontrakt claimów, bez zmian mapperów Keycloak lub osłabiania BFF.
- Jawny Testcontainers socket pozostaje tylko w dedykowanym poleceniu; nie ukrywaj go w `fast` lub `integration`.
