# Playwright-Learning-Development — Wizja Produktu, Kryteria Sukcesu i Analiza Pokrycia Edukacyjnego Playwright

**Natura dokumentu.** Artefakt nr 1 — przed jakąkolwiek dalszą architekturą. Cztery role jednocześnie: analityk biznesowy, Product Manager, System Architekt, Scrum Master, senior mentor Playwright/TypeScript. Pytanie kontrolne dla każdego akapitu poniżej: *czy ta funkcja istnieje, bo uczy konkretnej techniki Playwright albo konkretnego problemu testowania enterprise — czy dlatego, że "tak by zrobił prawdziwy bank"?*

---

## 0. Weryfikacja źródeł i aktualności

`[WERYFIKACJA]` Stan na 9 lipca 2026.

| Technologia | Status | Źródło pewności |
|---|---|---|
| Playwright 1.61 | current stable (15 czerwca 2026) | już zweryfikowane w tym wątku; `page.localStorage()`/`page.sessionStorage()` jako natywne API (koniec `evaluate()`-hacków do testu "brak tokenu w przeglądarce"), WebAuthn virtual authenticator (passkeys testowalne w CI), `expect.soft.poll()`, WebSocket widoczny w trace |
| TypeScript 7 | **GA 8 lipca 2026** (wczoraj), RC 18 czerwca 2026 | oficjalny blog Microsoftu (Daniel Rosenwasser) + 5 niezależnych źródeł zgodnych co do daty; kompilator natywny w Go ("Project Corsa"), ~10x szybszy type-check, strict mode domyślnie, `module: esnext` domyślnie, ES2015 jako podłoga (usunięty target es5) |
| `[WYMAGA POTWIERDZENIA]` | jeden artykuł SEO (picode.bunnode.com) podał sprzeczną datę GA (15 stycznia) — odrzucony jako niewiarygodny wobec oficjalnego źródła | — |
| `[RYZYKO, DATOWANE]` | TypeScript 7.1 (programmatic API) jeszcze nie wydane — narzędzia zależne od API kompilatora (`typescript-eslint`, `ts-morph`, custom transformery) mają czekać na 7.1, nie 7.0 | oficjalny RC post Microsoftu |

**Autorzy — realnie zweryfikowane, cytowalne źródła:**

| Autor | Data | Główna teza | Źródło | Wpływ na wizję/architekturę testów |
|---|---|---|---|---|
| Michał Drajna | 24 stycznia 2023 | Wyodrębnienie tokenu autoryzacji z cookies kontekstu, przechwycenie dynamicznego `transactionId` z `postDataJSON` żądania sieciowego, złożenie kolejnego żądania GraphQL i odpytywanie (`poll`) do momentu aż pole `status` przestanie być `InProgress` | Medium, "Sending a GraphQL POST Request with Playwright" | Bezpośrednio uzasadnia wzorzec "przechwyć realne żądanie sieciowe → wyciągnij z niego dane → zbuduj kolejne żądanie" jako legalny, nietrywialny sposób testowania GraphQL w Playwright — dokładnie ta technika trafia do naszej macierzy pokrycia (network capture + GraphQL) |
| Ivan Davidov | ~26 sierpnia 2025 | Playwright jako jedno narzędzie do UI **i** API; niestandardowy fixture `apiRequest` opakowujący `request` z metodą/URL; test E2E łączący: POST tworzący użytkownika (API) → logowanie jako ten użytkownik przez UI → weryfikacja dashboardu w jednym scenariuszu | idavidov.eu, "My First Live Session — Developing Playwright Framework for REST API Testing" | Bezpośrednio uzasadnia rdzeń naszej definicji sukcesu "mixed UI+API": nie jako dwa osobne zestawy testów, tylko jeden spójny przepływ, gdzie API robi tanie setupy, a UI weryfikuje efekt |
| Ivan Davidov | ~kwiecień 2026 (roadmap page) | "AI-Native Scaffold" — struktura projektu Playwright+TypeScript zaprojektowana tak, aby agent AI (Claude Code/Cursor/Copilot) mógł bezpiecznie rozszerzać zestaw testów bez halucynowania locatorów czy psucia konwencji | idavidov.eu/roadmap | Uzasadnia wymóg (Kryteria sukcesu edukacyjnego, pkt 4): fixtures/POM muszą być czytelne i przewidywalne na tyle, by nie tylko człowiek, ale i narzędzie AI mogło je bezpiecznie rozszerzać — to jest właśnie test na "płytki POM" |
| Viktor Konovalov | ~czerwiec 2026 (post "2 tyg. temu") | `storageState` zamiast logowania przez UI w każdym teście: uwierzytelnij raz, zapisz sesję, użyj wielokrotnie; logowanie przez UI zarezerwowane wyłącznie dla dedykowanych testów auth | LinkedIn, "Playwright tip: use storageState instead of logging in every test" | Bezpośrednio uzasadnia architekturę auth w naszym systemie: 11 realnych ról Keycloak → 11 zapisanych `storageState`, logowanie przez UI tylko w jednym, świadomie wyodrębnionym zestawie testów auth-flow |
| Stefan Minchev | — | `[BRAK ŹRÓDŁA]` — zidentyfikowałem prawdopodobny profil (QA, prawdopodobnie Wiser Technology, ten sam pracodawca co Ivan Davidov), ale bez żadnej publicznie dostępnej, możliwej do zacytowania treści | — | Nie cytuję, nie zgaduję tezy — zgodnie z zasadą źródłową |

---

## 1. Wizja produktu

**Jednozdaniowa wizja:** Playwright-Learning-Development to syntetyczna platforma płatnicza SEPA/ISO 20022, w której każdy ekran, rola, przepływ i integracja istnieje wyłącznie po to, by dać osobie uczącej się autentyczny, enterprise-realny poligon do opanowania Playwright 1.61, TypeScript 7 i architektury testów na poziomie Senior QA/SDET.

**Rozszerzona wizja.** Większość projektów edukacyjnych do nauki Playwright to todo-listy, sklepy albo proste CRUD-y — płaskie dane, jedna rola, jeden happy path. Uczą lokatorów i asercji, ale nie uczą tego, co faktycznie odróżnia Senior SDET-a: testowania systemu z prawdziwą asynchronicznością (SSE, kolejki, opóźnione rozliczenia), prawdziwą wielorolowością (11 ról Keycloak z realną segregacją uprawnień, nie dekoracją), prawdziwym rozdziałem UI/API/danych (BFF + GraphQL-read-only + REST-commands + bezpośrednie asercje SQL) i prawdziwymi warunkami wyścigu (dwóch operatorów rywalizujących o przypisanie tego samego wyjątku). SEPA/ISO 20022 nie jest tu celem — jest źródłem *naturalnie złożonych, niewymyślonych* stanów systemu: rozliczenie ma realne stany pośrednie, dostawa ma realne opóźnienia i retry, uzgadnianie ma realne przypadki brzegowe. Ta złożoność nie jest dodana sztucznie pod testy — istnieje, bo tak działają systemy płatnicze, i to właśnie czyni naukę na niej wartościowszą niż na wymyślonym demo.

**Odbiorca:** Senior QA/SDET (lub awansujący Mid→Senior) uczący się Playwright 1.61, TypeScript 7, architektury testów (fixtures, POM i jego alternatyw, test data strategy) oraz testowania systemów enterprise (auth, autoryzacja, wielorolowość, asynchroniczność, dane, obserwowalność) — buduje portfolio i przygotowuje się do rozmów technicznych na poziomie senior.

**Propozycja wartości:**
- **Czego uczy:** pełnego spektrum Playwright — od `getByRole` po `APIRequestContext`, od `storageState` po `route.fulfill`, od `test.step` po `toMatchAriaSnapshot` — w kontekście, gdzie każda technika ma realny powód użycia, nie ćwiczenie wyrwane z kontekstu.
- **Czego dowodzi:** że osoba potrafi zaprojektować i utrzymać architekturę testów dla systemu enterprise — nie tylko napisać pojedynczy test.
- **Dlaczego SEPA jest dobrym poligonem:** bo dostarcza naturalną złożoność (wielorolowość, asynchroniczność, dane finansowe z realnymi niezmiennikami, integrację z Keycloak) bez konieczności jej wymyślania — złożoność, którą trzeba by i tak sztucznie zbudować w projekcie typu todo-app, tu przychodzi za darmo z domeny.
- **Dlaczego to więcej niż todo-app/sklep/CRM:** te projekty mają jedną rolę, płaski model danych, brak realnej asynchroniczności poza "poczekaj, aż się doda produkt do koszyka". SEPA Nexus ma 11 ról z realną segregacją danych (RLS/GUC), SSE, kolejkę współdzieloną z realnym wyścigiem, rozdział czytania (GraphQL) od pisania (REST), i integrację z prawdziwym IdP (Keycloak) — każdy z tych elementów to osobny, nietrywialny problem testowy.

**Granice produktu:**
- **Czym jest:** syntetyczny, deterministyczny poligon testowy zbudowany na realistycznych (nie fikcyjnych) wzorcach SEPA/ISO 20022.
- **Czym świadomie nie jest:** prawdziwym systemem bankowym, kopią żadnego istniejącego CSM/ACH/RTGS, systemem z realną zgodnością regulacyjną.
- **Dlaczego nie budujemy prawdziwego banku:** bo cel nie jest bankowy — jest testowy. Każda godzina spędzona na realizmie bankowym, który nie tworzy nowego problemu testowego, jest godziną odjętą od nauki Playwright.
- **Dlaczego zgodność domenowa ma wspierać naukę, nie dominować:** domena jest środkiem do celu. Jeśli jakikolwiek fragment realizmu SEPA nie generuje nowej, nazwanej lekcji Playwright/TypeScript/architektury testów — jest kandydatem do uproszczenia, nie do pogłębienia.

---

## 2. Główna definicja sukcesu (przed startem Iteracji 0)

> Po czym poznamy, że ten projekt realnie uczy Playwright, TypeScript i architektury testów na poziomie Senior QA/SDET, a nie tylko generuje dużo kodu i dokumentacji?

**Definicja sukcesu:** Projekt jest sukcesem, jeśli po zakończeniu MVP jedna osoba potrafi — bez notatek, na żywo, na rozmowie technicznej — otworzyć dowolny plik testowy z repozytorium i przez pięć minut tłumaczyć: (a) dlaczego użyła tego, a nie innego locatora, (b) dlaczego dany fragment jest fixture'em, a nie funkcją pomocniczą, (c) co konkretnie w systemie ten test chroni przed regresją, i (d) jak dokładnie ten test radzi sobie z asynchronicznością, izolacją i równoległością — a rozmówca (inny Senior SDET) nie znajdzie w tej odpowiedzi ani jednej "bo tak się zwykle robi" bez uzasadnienia.

Warunki brzegowe tej definicji:
- **Mierzalna:** liczba scenariuszy pokrywających każdy z klastrów tematycznych z §4 (nie "dużo testów", tylko konkretna, wyliczona lista).
- **Sprawdzalna:** każdy test ma w treści (nazwa, `test.step`, komentarz) jawne uzasadnienie "czego uczy" — sprawdzalne przez code review, nie przez zaufanie.
- **Powiązana z Playwright:** metryka pokrycia z §4 musi pokazać zero pustych klastrów w kolumnie "must" bez odpowiadającej funkcji systemu.
- **Powiązana z TypeScript:** strict mode (domyślny w TS7) bez `any` w warstwie testowej; typy generowane z kontraktów (OpenAPI/GraphQL), nie ręcznie duplikowane.
- **Powiązana z testowaniem enterprise:** obecność testów wielorolowych, izolacji dzierżawców/danych i walidacji SQL — nie tylko testów UI.
- **Realistyczna dla jednej osoby:** zakres MVP (§9) mieści się w budżecie jednej osoby bez porzucania P1/P2.
- **Odporna na overengineering:** każdy fixture/POM/abstrakcja ma nazwaną alternatywę odrzuconą i powód odrzucenia (§4, kolumna ryzyka).

---

## 3. Playwright Feature Coverage & Gap Analysis

`[METODA]` System oceniany jest jako **poligon edukacyjny**, nie jako produkt biznesowy — pytanie przy każdym wierszu brzmi "czy istniejący/planowany element systemu daje naturalne, nietrywialne miejsce do ćwiczenia tej techniki", nie "czy system ma tę funkcję biznesową".

| Obszar Playwright | API / metody | Element systemu | Pokrycie | Potrzebna funkcja | Poziom | MVP | Ryzyko overengineeringu |
|---|---|---|---|---|---|---|---|
| Test/describe/hooks | `test`, `test.describe`, `beforeEach/afterEach/beforeAll/afterAll` | każdy z 9 ekranów | pełne | — | podstawowy | must | brak |
| Web-first assertions | `expect(locator).toHaveText/toBeVisible/toBeEnabled` | cztery osobne statusy na Payment Detail, disabled `Close cycle` do momentu spełnienia warunku | pełne | — | podstawowy→średni | must | brak |
| Soft assertions | `expect.soft` | walidacja wielu pól formularza Reference Data jednocześnie (diff preview) | częściowe | jawne wskazanie w spec, które asercje formularza mają być soft (żeby jeden błąd walidacji nie przerywał sprawdzenia reszty pól) | średni | should | niskie |
| Negative assertions | `expect(locator).not.toBeVisible` | brak przycisku "Fix"/"Repair" w Reconciliation (asercja nieobecności) | pełne | — | średni | must | brak |
| Custom matchers | `expect.extend` | domenowy matcher `toShowFourStatusAxes()` sprawdzający, że 4 statusy są oddzielnymi elementami, nie jednym scalonym | brak | nazwać i udokumentować 2–3 domenowe matchery jako część fundamentu testowego | zaawansowany | should | średnie (łatwo przesadzić z liczbą customowych matcherów) |
| Locator strategy | `getByRole/getByText/getByLabel/getByPlaceholder/getByTestId` | formularze (label), przyciski/zakładki (role), tabele (testid dla wierszy) — świadomy dobór wg problemu, nie jeden wzorzec wszędzie | pełne | — | podstawowy→średni | must | brak |
| Locator filters/chaining/strict mode | `.filter()`, `.locator().locator()`, strict-mode violation | wiersz tabeli po konkretnym `paymentId`, kolumna statusu w tym wierszu | częściowe | jedna świadoma "pułapka edukacyjna": dwa przyciski o identycznym widocznym tekście, różne role — uczy strict-mode violation i `getByRole(...,{name})` | średni→zaawansowany | must | niskie (to jest kontrolowana pułapka, nie realna funkcja) |
| Browser/BrowserContext | wiele kontekstów, izolacja ciasteczek/storage | test wyścigu przypisania wyjątku (dwóch operatorów, dwa konteksty) | pełne | już zaprojektowane (assignment-race) | zaawansowany | must | brak |
| APIRequestContext | `request.post/get`, niezależne od UI | REST commands (submit payment, close cycle) jako bezpośrednie testy kontraktu | pełne | — | średni | must | brak |
| Wbudowane fixture'y | `page`, `context`, `browser`, `request` | każdy test | pełne | — | podstawowy | must | brak |
| Worker fixtures | `scope: 'worker'` | jeden zasiany tenant/seed na workera (izolacja równoległości) | częściowe | jawna strategia per-worker (już zidentyfikowana jako input do Iteracji 0) | zaawansowany | must | brak |
| Custom fixtures | `test.extend` | `asRole(role)` → storageState, `apiClient`, `seededScenario(profile)` | pełne | już zaprojektowane (rekomendacja Screen+Component+API-fixture) | średni→zaawansowany | must | brak |
| Dependency fixtures | fixture zależny od innego fixture'a | `apiClient` zależny od `authenticatedContext` | częściowe | jawnie nazwać 1–2 przykłady w architekturze testów | zaawansowany | should | średnie |
| Projects | multi-project config | role jako osobne projekty (`operator`, `auditor`, ...) albo przeglądarki (Chromium/Firefox/WebKit) | częściowe | zdecydować: projects po roli czy po przeglądarce (albo oba) | średni | should | niskie |
| Tags/grep | `@smoke`, `@api`, `--grep` | `@smoke`/`@role`/`@sse`/`@fault-injection` per klaster z tej tabeli | brak | nazwać konwencję tagów w fundamencie testowym | podstawowy | must | brak |
| Annotations | `test.skip/fixme/slow` | testy P1 (np. case-decyzje) oznaczone `fixme` do czasu P1 | częściowe | konwencja: co dokładnie oznacza `slow` w kontekście SSE-testów | podstawowy | should | brak |
| Test steps | `test.step` | złożony scenariusz: submit→routing→settlement→egress w jednym teście z krokami | pełne | — | średni | must | brak |
| Page Object Model | klasyczny POM per ekran | 9 ekranów | pełne | — | podstawowy | must | brak |
| Component objects | jeden obiekt per współdzielony komponent (StatusChip, EvidenceDrawer, EntityTable) | już zaprojektowane w fundamencie komponentowym | pełne | — | średni | must | brak |
| App/Screen objects | cienka kompozycja component objects per workspace | 9 workspace'ów | pełne | — | średni | must | brak |
| Service objects | klient API per moduł backendowy (PaymentsApi, SettlementApi) | REST commands + GraphQL reads | pełne | — | średni | must | brak |
| Test data builders | budowniczy danych testowych (np. `aPayment().withStatus(...).build()`) | dane symulacji jako parametry scenariusza | częściowe | odłożone do P1 — Simulation Lab już jest "budowniczym" na poziomie systemu; osobna warstwa TS-builderów tylko gdy sceny symulacji przestaną wystarczać | zaawansowany | later | średnie jeśli dodane przedwcześnie |
| UI testing | pełny E2E przez interfejs | wszystkie 9 ekranów | pełne | — | podstawowy | must | brak |
| API testing | REST + GraphQL bez UI | REST commands, GraphQL reads | pełne | — | średni | must | brak |
| Mixed UI+API | setup przez API, weryfikacja przez UI (i odwrotnie) | submit płatności przez API → weryfikacja w Payment Detail UI | pełne | — | średni→zaawansowany | must | brak |
| API setup/cleanup | tworzenie/czyszczenie danych przez API poza testowanym flow | Simulation Lab jako fabryka fixture'ów | pełne | — | średni | must | brak |
| Authentication/storageState | zapisana sesja per rola | 11 ról Keycloak, BFF | pełne | — | podstawowy→średni | must | brak |
| Multi-role sessions | wiele kontekstów, różne role równolegle | test: `reconciliation_operator` przypisuje, `case_operator` widzi tylko odczyt | pełne | — | zaawansowany | must | brak |
| Keycloak login/logout | realny redirect OIDC, nie mock | BFF login flow | pełne | — | średni→zaawansowany | must | brak |
| Token refresh/token expiry | wygasła sesja w trakcie zadania | **brak zaprojektowanego zachowania** | brak | `[MUST-ADD]` — sesja wygasająca w trakcie pracy operatora: redirect do Keycloak, powrót na dokładnie tę samą podstronę (deep-link zachowany) | zaawansowany | must | niskie (realna, potrzebna funkcja BFF) |
| Role-based access / permission testing | test negatywny: rola bez uprawnienia dostaje 403 | już zaprojektowane (Gap-R1 z poprzedniej rundy: forbidden-command-403 niezależnie od UI) | pełne | — | zaawansowany | must | brak |
| Session isolation | brak przecieku stanu między testami/workerami | per-worker tenant/seed | częściowe | jak wyżej (worker fixtures) | zaawansowany | must | brak |
| Parallelism/workers/sharding | równoległe workery, sharding w CI | cały zestaw testów | częściowe | strategia per-worker (Iteracja 0) | zaawansowany | must | brak |
| Retries | `retries` w konfiguracji, i **dlaczego to nie jest lekarstwo na flaky** | polityka: 0 retries lokalnie, ograniczone w CI z jawnym alarmem przy powtórce | brak | nazwać politykę retries wprost jako część fundamentu (uczy różnicy między "ukryciem" a "naprawą" flaky) | zaawansowany | must | niskie |
| Test isolation | świeży stan per test | RLS/GUC + seed per test | pełne | — | średni | must | brak |
| Network interception (route/request/response) | `page.route`, inspekcja żądań | GraphQL request inspection (Drajna-style: przechwyć, wyciągnij dane, zbuduj kolejne żądanie) | pełne | — | zaawansowany | must | brak |
| Mocking (abort/continue/fulfill) | świadome, wąskie użycie | **tylko** na granicy symulowanego kontrahenta zewnętrznego (CSM) — już ustalona zasada w tym wątku | pełne (jako polityka) | — | zaawansowany | must | brak — polityka już chroni przed nadużyciem |
| HAR (record/replay) | `page.routeFromHAR` | nagranie jednej interakcji CSM raz, odtwarzanie deterministyczne w CI bez żywego silnika symulacji | brak | `[SHOULD-ADD]` — mała funkcja testowa (nie biznesowa): jeden nagrany HAR jako alternatywa dla Simulation Lab przy testach niewymagających pełnego determinizmu na żywo | zaawansowany | should | niskie |
| Fault injection | deterministyczne wstrzyknięcie awarii | `failure_profiles` w Simulation Lab (CSM timeout, egress failure, mismatch) | pełne | — | zaawansowany→senior | must | brak |
| Batch-vs-instant grouping `[ADD, domain-grounding]` | multi-item submission vs. single-item message | file rail (§2.3) vs. instant path — rail-dependent grouping rule (§2.2d) | pełne | — | średni→zaawansowany | must | brak — utrwala istniejący podział, nie dodaje domeny |
| Upload plików | `setInputFiles`, drag-drop | Payments & Files (już wzbogacone: drag-drop, progress, walidacja) | pełne | — | średni | must | brak |
| Download plików | `page.waitForEvent('download')` | delivery receipt, evidence bundle (już zaprojektowane) | pełne | — | średni | must | brak |
| Eksport XML/CSV/JSON | walidacja zawartości pobranego pliku | wynik pliku batch (pain.002-style result file), evidence bundle jako JSON | częściowe | jawnie nazwać format eksportu evidence bundle (obecnie "P1, format do ustalenia") | średni→zaawansowany | should | niskie |
| Dialogi | `page.on('dialog')` | natywny dialog przeglądarki przy anulowaniu wyboru pliku (rzadkie w nowoczesnym UI) | brak | nie dodawać sztucznie — nowoczesne UI (shadcn `alert-dialog`) to nie natywne dialogi `window.confirm`; ten obszar jest z natury rzadki we frameworkach React i nie wymaga sztucznego dodawania | podstawowy | later | wysokie jeśli wymuszone |
| Popupy/nowe zakładki | `context.on('page')`, multi-page | **brak zaprojektowanego miejsca** | brak | `[MUST-ADD]` — "otwórz w nowej karcie" dla deep-linków z Evidence drawer (realna, uzasadniona funkcja UX: nie tracisz kontekstu bieżącego dochodzenia) | średni→zaawansowany | must | niskie (mała, prawdziwa funkcja UX) |
| iframes | `frameLocator` | brak naturalnego miejsca — SEPA Nexus nie ma osadzonych widgetów stron trzecich | brak | **świadomie nie dodawać** — sztuczny iframe tylko po to, by przetestować `frameLocator`, jest dokładnie tym, czego anty-cele zabraniają | średni | later (odrzucone) | wysokie jeśli wymuszone |
| Permissions API | `context.grantPermissions(['clipboard-read'])` | przyciski "Copy correlation ID"/"Copy trace ID" w Evidence | pełne | — | średni | must | brak |
| Viewport | `page.setViewportSize` | desktop-first jako świadoma decyzja produktowa | częściowe | jedna świadoma asercja: konsola musi zachować użyteczność przy min-width (kolaps sidebar) | podstawowy | should | niskie |
| Device emulation | `devices['...']` | **brak naturalnego miejsca** — konsola operatorska jest świadomie desktop-only | brak | **nie wymuszać ekranów mobilnych.** Jedyne uzasadnione użycie: multi-project pokrycie 3 przeglądarek desktopowych (Chromium/Firefox/WebKit), nie urządzeń mobilnych | podstawowy | should (jako browser coverage, nie mobile) | wysokie jeśli wymuszone jako mobile |
| Locale/timezone | `locale`, `timezoneId` | kwoty PLN vs EUR (separator dziesiętny), cut-off wyświetlany w strefie operatora vs UTC | częściowe | `[SHOULD-ADD]` — jawna reguła wyświetlania: kwoty formatowane wg locale, cut-off pokazany w lokalnej strefie z UTC w `title` | średni→zaawansowany | should | niskie (naturalnie wynika z PLN+EUR w domenie) |
| Tracing | `--trace on-first-retry` | polityka CI już częściowo ustalona (retain-on-first-failure) | pełne | — | zaawansowany | must | brak |
| Screenshots | `screenshot: 'only-on-failure'` | każdy test | pełne | — | podstawowy | must | brak |
| Video | `video: 'retain-on-failure'` | każdy test | pełne | — | podstawowy | must | brak |
| Console logs | `page.on('console')` | asercja "zero błędów konsoli" jako higiena cross-cutting | częściowe | nazwać to jawnie jako regułę CI, nie zostawiać domyślnie | podstawowy | should | brak |
| Reporters | HTML/JSON/JUnit, custom | raport per rola/klaster | częściowe | zdecydować format raportowania w Iteracji 0 | podstawowy | must | brak |
| Debug mode | `--debug`, UI mode, Inspector | podczas budowy każdego testu | pełne | — | podstawowy | must | brak |
| Visual testing | `toHaveScreenshot` | **świadomie ograniczone** — pełnostronicowy regres pikselowy odrzucony wcześniej w tym wątku jako niska wartość/wysoki koszt utrzymania dla konsoli danych | częściowe (celowo) | ewentualne snapshoty na poziomie komponentu/tokenu (P2), nigdy całej strony | zaawansowany | later | wysokie jeśli pełnostronicowe |
| Accessibility checks | axe-core, `toMatchAriaSnapshot` | już zaprojektowana brama axe-core w CI (poprzednie rundy tego wątku) | pełne | — | zaawansowany | must | brak |
| SQL/data validation | bezpośrednie zapytanie SQL obok asercji UI | zamknięcie cyklu rozliczeniowego przez UI + asercja salda w bazie w tym samym teście | pełne | — | zaawansowany→senior | must | brak |
| GraphQL testing | odczyt read-modeli, "inspect don't mock" | każdy ekran czyta przez GraphQL | pełne | — | średni→zaawansowany | must | brak |
| Protobuf/gRPC-adjacent | kontrakt + backward-compat | **świadomie odłożone** — routing pozostaje in-process w MVP (ADR), ekstrakcja gRPC to nazwane ćwiczenie P2 | brak (celowo) | ćwiczenie kontraktowe staje się uczciwie dostępne dopiero przy ekstrakcji P2 — nie wymuszać wcześniej | senior | later | wysokie jeśli wymuszone przed ekstrakcją |
| Flaky test diagnosis | systematyczna, nie przypadkowa | kontrolowane "pułapki edukacyjne" (patrz niżej) + realna asynchroniczność (SSE, kolejki) | częściowe | patrz lista pułapek poniżej | senior | must | — |
| CI execution | lokalne + CI, identyczne zachowanie | cały zestaw | częściowe | ustalić w Iteracji 0 | podstawowy→średni | must | brak |
| Artifact analysis | odczyt trace/screenshot/video po awarii | każda awaria testu | pełne | — | zaawansowany | must | brak |
| Test observability | korelacja `correlationId`/`traceId` między testem a logami systemu | Evidence/Audit już eksponuje te pola w UI | pełne | — | senior | must | brak |

### Podsumowanie analizy

**Top 10 brakujących funkcji systemu o największej wartości edukacyjnej (uszeregowane):**
1. Wygasająca sesja w trakcie zadania → redirect Keycloak → powrót na tę samą podstronę (token refresh/expiry — obecnie zupełny brak).
2. "Otwórz w nowej karcie" dla deep-linków z Evidence drawer (multi-page/popup — obecnie zupełny brak, mała i uzasadniona funkcja).
3. Jawna polityka retries jako część fundamentu testowego (uczy różnicy: ukrycie flaky vs. jego naprawa).
4. Jawna konwencja tagów `@smoke`/`@role`/`@sse`/`@fault-injection` (grep-driven wykonanie w CI).
5. HAR jako alternatywna ścieżka determinizmu obok Simulation Lab (mała funkcja testowa, nie biznesowa).
6. Jawna reguła locale/timezone dla kwot PLN/EUR i cut-off (naturalnie wynika z domeny, nikt jeszcze tego nie nazwał).
7. Nazwane 2–3 domenowe custom matchery (`toShowFourStatusAxes` i podobne).
8. Jawnie nazwany format eksportu evidence bundle (obecnie "P1, do ustalenia").
9. Jawna strategia projects (rola vs. przeglądarka vs. oba).
10. Kontrolowane pułapki edukacyjne (lista niżej) jako świadomy, nazwany element fundamentu — obecnie istnieją przypadkowo (assignment-race), nie jako systematyczna kategoria.

**Top 10 funkcji Playwright, których nie da się jeszcze dobrze nauczyć:**
1. `frameLocator`/iframes — brak naturalnego miejsca, i **nie powinno się go sztucznie tworzyć**.
2. Device emulation w sensie mobilnym — konsola jest świadomie desktop-only.
3. Natywne dialogi przeglądarki (`page.on('dialog')`) — rzadkie w nowoczesnym React UI.
4. Protobuf/gRPC kontrakt-testing — uczciwie dostępne dopiero po ekstrakcji P2 routingu.
5. Pełnostronicowe visual regression — świadomie odrzucone jako niska wartość/wysoki koszt.
6. Sharding na wielu maszynach — wymaga realnego CI, nie tylko lokalnego runu (odblokowane dopiero po Iteracji 0).
7. Dependency fixtures w pełnej głębi (łańcuchy 3+ poziomów) — obecny model jest płytszy.
8. Test data builders jako osobna warstwa TS — obecnie ta rola pełni Simulation Lab.
9. `--checkers`/równoległość TS7-natywnego kompilatora w praktyce — zbyt świeże (GA wczoraj), do zweryfikowania w Iteracji 0.
10. Realne sharding+multi-region CI — wymaga infrastruktury, nie tylko konfiguracji Playwright.

**Minimalny zakres MVP o największym pokryciu przy najmniejszym overengineeringu:** trzy pierwsze ekrany (Control Room, Payments & Files, Payment Detail) + Simulation Lab jako fabryka danych + Keycloak z 3–4 rolami (nie od razu 11) — to samo w sobie pokrywa ~70% wierszy oznaczonych `must` w tabeli powyżej.

**Funkcje, których nie warto dodawać (drogie, uczą niewiele):** pełnostronicowe visual regression; natywne dialogi przeglądarki wymuszone sztucznie; iframe wymuszony sztucznie; mobilna emulacja urządzeń; test data builder jako osobna warstwa, dopóki Simulation Lab wystarcza; customowe matchery w nadmiarze (więcej niż 3–4 na start).

**Kontrolowane "pułapki edukacyjne" (świadomie małe, izolowane):**
- Strict-mode violation: dwa przyciski o identycznym tekście, różne role → uczy `getByRole(...,{name})`.
- Wyścig przypisania w Reconciliation (już zaprojektowany) → uczy multi-context concurrency.
- Konflikt idempotency 409 (już zaprojektowany) → uczy negative-path assertion.
- Konflikt wersji w Reference Data (już zaprojektowany) → uczy optimistic locking.
- **Nowa, do dodania:** jeden świadomie "źle nazwany" `data-testid` w dokumentacji przykładowej (nie w realnym kodzie) jako materiał do dyskusji "dlaczego to jest zły locator" — czysto dydaktyczny artefakt, nie funkcja systemu.

### 3a. Persona-Driven Scenario Set `[ADD, persona-driven]`

`[METODA]` Tabela w §3 dowodzi, że dana *technika* Playwright ma miejsce w systemie. Poniższe scenariusze dowodzą czegoś innego: że dana *osoba* (frontend blueprint §3a) faktycznie przechodzi przez system tak, jak przechodzi w realnym dniu pracy — łącznie z przekazaniem zadania między personami, czego żaden pojedynczy wiersz tabeli technik nie pokazuje. Jeden kotwiczący scenariusz na personę, plus trzy scenariusze przekazania (§3b frontend blueprintu):

| Persona | Kotwiczący scenariusz | Co dowodzi |
|---|---|---|
| Operator | poranna triaż: otwiera Control Room, SSE aktualizuje kafelek na żywo, klika w oflagowany obiekt, trafia dokładnie tam gdzie oczekiwał | live-UI assertion + deep-link, nie tylko `toBeVisible` |
| Payment Approver `[ADD, persona-driven]` | otwiera kolejkę zatwierdzeń, widzi tylko płatności innego makera (nigdy własne), zatwierdza z komentarzem | BOLA-safe queue + zakaz samoakceptacji jako pierwsza asercja, nie afterthought |
| Supervisor | podejmuje ręczną korelację ISO niedostępną dla zwykłego `operator` (FGAP `ops_senior`) | permission testing na poziomie *tej samej* roli z podniesionym uprawnieniem, nie osobnej roli |
| Administrator | edytuje profil walidacji z datą przyszłej ważności, `diff preview` pokazuje dokładnie to co się zmieni | forms + walidacja + wersjonowanie, nie proste CRUD |
| Auditor | śledzi jedną płatność od `correlationId` w Payment Detail do pełnego wpisu w Evidence/Audit, cross-tenant | test obserwowalności: korelacja test↔system, nie tylko `expect` na UI |
| Exception Analyst | dwa konteksty przeglądarki, dwóch `reconciliation_operator`, wyścig o `Assign to me`, dokładnie jeden wygrywa | już zaprojektowany wyścig, teraz nazwany jawnie z persony, nie tylko z mechaniki |
| Fraud/Risk Analyst `[ADD, persona-driven]` | otwiera wstrzymaną płatność, czyta powód reguły, zwalnia — płatność wraca dokładnie tam gdzie stanęła w 4EV, nie na początek | test "detour, not dead end": stan po zwolnieniu musi być tym samym stanem co przed wstrzymaniem |
| Case & Recall Owner | otwiera eskalowany case, evidence trail jest już kompletny (assercja: nic nie brakuje), podejmuje decyzję resolve | asercja kompletności danych *odziedziczonych* z poprzedniego kroku, nie danych stworzonych w tym teście |
| Tenant/Security Configuration Owner | rotacja klucza podpisu (P1); stary klucz przestaje weryfikować, nowy zaczyna, moment przejścia jest deterministyczny | test cyklu życia klucza, nie tylko pojedynczej operacji |

**Trzy scenariusze przekazania (cross-persona), nowe względem §3:**
1. **Exception → Case handoff:** Exception Analyst eskaluje wyjątek → Case Owner otwiera dokładnie ten sam wyjątek jako case, z tym samym evidence trail. Test przechodzi przez dwa `storageState`, jedną transakcję biznesową, i asercję, że nic nie zgubiło się po drodze.
2. **Reference-data change → wszyscy pozostali:** Administrator zapisuje zmianę katalogu z datą ważności w przyszłości → test przesuwa `as_of` (przez Simulation Lab, nie przez modyfikację zegara systemowego) → asercja, że inna persona (np. `payment_submitter`) widzi nowe zachowanie dokładnie od tej daty, nie wcześniej.
3. **Dowolna decyzja → Auditor:** dowolny test z dowolnej persony powyżej kończy się jedną wspólną asercją: wpis audytu istnieje, jest w tej samej transakcji co komenda, i `auditor` może go odnaleźć przez `correlationId` — ta asercja jest częścią **fixture'a**, nie osobnym testem, żeby uniknąć duplikacji.

### 3c. 4EV / Business-Security Playwright Scenarios `[ADD, persona-driven]`

`[METODA]` Ten klaster istnieje, bo maker-checker i mechanizmy towarzyszące (main blueprint §2.2b/§2.2c, Keycloak-26 blueprint §6a/§10/§11) to jedyne miejsce w systemie wymagające **dwóch niezależnych, jednocześnie aktywnych sesji użytkowników w jednym teście** — to najbogatsza, najbardziej senior-level powierzchnia testowa w całym projekcie, i każdy pojedynczy punkt z wymaganej listy ma tu imienny, zaprojektowany dom.

| Wymagany element | Scenariusz | Mechanika Playwright |
|---|---|---|
| Dwie niezależne sesje / dwa `BrowserContext` / dwa `storageState` | maker i checker działają jednocześnie w jednym teście | `browser.newContext({storageState: makerState})` + `browser.newContext({storageState: checkerState})`, oba żywe w jednej funkcji testowej |
| Dwóch użytkowników z różnymi rolami | `payment_submitter` przygotowuje, `payment_approver` decyduje | dwa `storageState` odpowiadające dwóm rolom (§9, Keycloak blueprint) |
| Zakaz samoakceptacji | ten sam użytkownik nie może być checkerem własnej płatności | negatywny test REST: wywołanie `/approve` z tokenem makera → 403, niezależnie od tego czy token *ma* rolę `payment_approver` (podwójna asercja: DB constraint §2.2b **i** command-handler guard) |
| Approve/reject przez drugiego użytkownika | happy path 4EV, pojedyncza płatność | maker submit → checker widzi w kolejce → approve z komentarzem → `payment.received` publikuje się dopiero teraz (asercja: FSM nie ruszył przed decyzją) |
| Batch/bulk approval | jedna decyzja na grupę + override pojedynczego elementu | `ApproveBatch` na całej grupie + osobny test wyciągający jeden `payment_id` z batcha do osobnej decyzji |
| VoP match/no match/close match | trzy oddzielne, deterministyczne scenariusze | Simulation Lab seeduje trzy różne odbiorców dające trzy różne wyniki `vop_checks` — deterministyczne, nie losowe |
| Override VoP mismatch z audytem | `NO_MATCH` blokuje submit, override wymaga step-up i zostawia ślad | REST + SQL: po override, `audit_log` ma wiersz w tej samej transakcji co zmiana statusu — asercja przez zapytanie SQL, nie tylko przez UI |
| Fraud hold i release | wstrzymanie deterministyczne (via `failure_profiles`), potem decyzja | Fraud/Risk Analyst release → płatność wraca do dokładnie tego stanu 4EV, w którym była przed wstrzymaniem (nie do stanu początkowego) |
| Limity i przekroczenia limitów | dzienny/tenant/pojedynczy/batch, każdy osobno | cztery małe, deterministyczne testy limitów, każdy z jawnym progiem z `limit_policies`, każdy asertujący reakcję (block/require-approval/require-step-up) właściwą dla tego konkretnego limitu |
| Step-up auth dla akcji wysokiego ryzyka | świeża sesja przechodzi, stara sesja dostaje redirect | sesja z `auth_time` sprzed >5 min próbuje VoP-override → 401 → redirect z `acr_values=step-up` → ponowna autentykacja → retry tej samej komendy się udaje |
| Próby dostępu do obiektów innego tenanta | BOLA na kolejce zatwierdzeń | token tenanta A próbuje `GET /payments/{id należący do B}` i `/approve` na nim → RLS + jawny guard w handlerze, oba muszą odciąć |
| Manipulowanie ID w REST API | sekwencyjne/zgadywane ID nie działają | seed dwóch płatności w dwóch tenantach, inkrementacja ID między nimi w żądaniu → asercja 403/404, nigdy 200 z cudzymi danymi |
| Walidacja REST API | kontrakt każdej z nowych komend | `APIRequestContext` bezpośrednio na `/approve`, `/reject`, `/vop-override`, `/fraud-holds/*`, niezależnie od UI |
| Walidacja SQL | stan bazy potwierdza decyzję UI | po approve: `payment_approvals.status='APPROVED'`, `checker_user_id` wypełniony, `payments`/FSM ruszył dopiero po tym wierszu — jeden test, oba sprawdzenia |
| Walidacja audit trail | kompletność, nie tylko obecność | wpis audytu ma `correlation_id`, `decision_comment` (gdy wymagany), `before`/`after`, i jest odnajdywalny zarówno po `payment_id` jak i po aktorze |
| Walidacja statusów w UI | cztery istniejące osie statusu + piąta (approval) nigdy się nie mieszają | `Approval status` renderuje się jako osobny, nazwany chip, nigdy scalony z `Business status` — dokładnie ta sama dyscyplina co przy istniejących czterech osiach |
| Security headers `[ADD, security-review]` | CSP/HSTS/X-Frame-Options obecne na każdej odpowiedzi BFF | `page.on('response')` odczytuje nagłówki, nie treść — asercja obecności, nie zgadywanie wartości |
| Kształt błędu REST (RFC 7807) `[ADD, security-review]` | każda odpowiedź 4xx/5xx ma ten sam kształt (`type`/`title`/`status`/`detail`/`correlationId`) | `APIRequestContext` na kilku różnych błędach (422/403/404/409) → jedna wspólna asercja kształtu, nie cztery różne |
| Rate limit na wrażliwym business flow `[ADD, security-review]` | przekroczenie limitu decyzji na `payment_approver` w krótkim oknie | seria szybkich `/approve` z jednego `storageState` → w pewnym momencie 429, niezależnie od ogólnego limitu API |

---

## 4. Mierzalne kryteria sukcesu MVP

| # | Kryterium | Mierzalny próg |
|---|---|---|
| 1 | Pionowe flow zbudowane pod naukę różnych technik | ≥5 (submit→timeline, close-cycle, retry-delivery, assignment-race, launch-replay-simulation) |
| 2 | Scenariusze UI | ≥15 |
| 2 | Scenariusze API | ≥10 |
| 2 | Scenariusze UI+API mieszane | ≥5 |
| 2 | Scenariusze auth/multi-role | ≥8 (min. 4 realne role z 12 `[CHANGE]`) |
| 2 | Scenariusze SQL validation | ≥3 |
| 2 | Scenariusze negative path | ≥8 |
| 2 | Scenariusze visual (celowo ograniczone) | 0–2, komponentowe, nie pełnostronicowe |
| 2 | Scenariusze accessibility | ≥5 (axe-core per ekran MVP) |
| 2 | Scenariusze network mocking (na granicy CSM) | ≥2 |
| 2 | Scenariusze fault injection | ≥3 |
| 2 | Scenariusze upload/download | ≥3 |
| 2 | Scenariusze GraphQL | ≥5 |
| 2 | Scenariusze Protobuf/gRPC-adjacent | 0 w MVP (świadomie P2) |
| 2 | Scenariusze 4EV/business-security (§3c) `[ADD, persona-driven]` | ≥16 — jeden na każdy wiersz tabeli §3c, żaden pominięty |
| 3 | Świadomie użyte interfejsy/metody Playwright | ≥40 z tabeli §3 |
| 4 | Typy locatorów użyte świadomie | ≥5 (role/label/testid/placeholder/filter+chaining) |
| 5 | Testy wielorolowe z Keycloak | ≥1 test na parę ról z realnym konfliktem uprawnień |
| 6 | Testy równoległe z izolacją danych | pełny zestaw uruchamialny z `--workers>1` bez przecieku stanu |
| 7 | Demonstracja dobry/zły POM | ≥1 udokumentowany przykład każdego (dobry POM, zły POM jako antywzorzec w dokumentacji, nie w kodzie produkcyjnym) |
| 8 | Flakiness | <2% w 20 powtórzeniach (`--repeat-each=20`) na CI dla całego MVP zestawu |
| 9 | Portfolio-gotowość | repo publiczne, README wyjaśniające architekturę testów, ≥1 nagrany trace jako demo |
| 10 | Laboratorium do rozmów o architekturze | dokument (ten + kolejne) wystarczający do 30-minutowej rozmowy o decyzjach architektonicznych bez przygotowania na żywo |

---

## 5. Kryteria sukcesu edukacyjnego

Osoba ucząca się realnie opanowała materiał, jeśli potrafi: (1) uzasadnić wybór każdego API Playwright w konkretnym teście; (2) dobrać locator do problemu, nie tylko sięgać po `data-testid`; (3) wytłumaczyć auto-waiting, strict mode, flakiness i izolację testów własnymi słowami, na przykładzie z tego repo; (4) zaprojektować fixture/POM bez tworzenia płytkiej abstrakcji ukrywającej Playwright; (5) połączyć UI, API, SQL, Keycloak i dane testowe w jednym spójnym scenariuszu; (6) zdiagnozować awaria z trace, screenshotów, wideo, logów konsoli i sieci — bez ponownego uruchamiania testu; (7) uzasadnić, który test powinien być UI, który API, który SQL, a który w ogóle nie powinien istnieć; (8) pisać testy czytelne, stabilne, równoległe, gotowe do CI; (9) rozpoznać overengineering we własnym frameworku testowym; (10) opowiedzieć o projekcie na rozmowie technicznej jako Senior QA/SDET — z konkretami, nie ogólnikami.

## 6. Kryteria sukcesu technicznego (walking skeleton)

Szkielet nadaje się do pierwszych iteracji, gdy: (1) cały minimalny stack (Spring Modulith + PostgreSQL + Kafka + Keycloak 26.6.4 + Next.js BFF) uruchamia się razem jedną komendą; (2) istnieje jeden pionowy flow UI→backend→baza; (3) istnieje ≥1 test Playwright UI; (4) ≥1 test Playwright API; (5) ≥1 test mieszany UI+API; (6) ≥1 scenariusz Keycloak/auth; (7) ≥1 walidacja SQL; (8) testy działają identycznie lokalnie i w CI; (9) generowane są trace/screenshoty/raporty/artefakty; (10) struktura pozwala dodawać kolejne moduły edukacyjne bez przepisywania fundamentu.

## 7. Kryteria sukcesu produktowego

MVP jest wystarczające, ale nie przeprojektowane, gdy: (1) pokrywa szerokie spektrum Playwright (§3, kolumna "must" bez pustych pól); (2) każdy większy ekran/API/flow ma jawne uzasadnienie edukacyjne (kolumna "Potrzebna funkcja" w §3 nie rośnie bez końca); (3) nie symuluje pełnego banku (granice z §1 przestrzegane); (4) wykonalne przez jedną osobę w realistycznym czasie; (5) P1/P2 świadomie odłożone (patrz Top 10 "nie da się jeszcze nauczyć"); (6) nie powstaje kolejny Big Design Up Front — po tym artefakcie następuje kod, nie kolejny dokument wizji; (7) projekt przechodzi od dokumentacji do działającego kodu w mierzalnym czasie; (8) pierwsza iteracja daje realną możliwość uruchomienia i testowania; (9) system jest wystarczająco realistyczny, by uczyć testów enterprise; (10) system jest wystarczająco mały, by domena nie zablokowała nauki Playwright.

## 8. Anty-cele

Nie budujemy prawdziwego systemu bankowego. Nie optymalizujemy pod pełną zgodność produkcyjną. Nie projektujemy funkcji tylko dlatego, że istnieją w realnych systemach płatniczych. Nie tworzymy dokumentacji dla samej dokumentacji. Nie dodajemy ekranów/API/integracji bez nazwanej lekcji Playwright/TypeScript/architektury testów/systemów enterprise. Nie piszemy testów dla liczby testów. Nie tworzymy płytkiego POM ukrywającego Playwright zamiast go uczyć. Nie budujemy frameworka tak abstrakcyjnego, że znika spod niego `Page`, `Locator`, `expect`, fixtures i network API. **Nie przesuwamy startu kodowania przez kolejne audyty dokumentacji — ten artefakt jest ostatnim dokumentem czysto wizyjnym przed kodem.** Nie traktujemy SEPA jako celu nadrzędnego nad edukacją Playwright.

---

## 9. Rekomendowany wynik końcowy

1. **Wizja (1 zdanie):** patrz §1.
2. **Wizja rozszerzona:** patrz §1.
3. **Główna definicja sukcesu:** patrz §2.
4. **Mierzalne kryteria MVP:** patrz §4 (10 kategorii, konkretne progi liczbowe).
5. **Mapa edukacyjna Playwright:** klastry z §3 pogrupowane wg poziomu (podstawowy→senior) — pełne pokrycie od `test`/`expect` po SQL+GraphQL+fault-injection w jednym scenariuszu.
6. **Coverage & Gap Analysis:** tabela §3, ~55 wierszy, zero pustych uzasadnień.
7. **Brakujące funkcje potrzebne do nauki Playwright:** Top 10 w §3 (sesja wygasająca, nowa karta z Evidence, polityka retries, tagi, HAR, locale/timezone, custom matchery, format eksportu, strategia projects, systematyczne pułapki edukacyjne).
8. **Funkcje zbędne/zbyt drogie edukacyjnie:** pełnostronicowy visual regression, wymuszone iframe, wymuszona mobilna emulacja, wymuszone natywne dialogi, przedwczesny test-data-builder, nadmiar customowych matcherów.
9. **Minimalny zakres Iteracji 0:** szkielet techniczny (§6) + 3–4 role Keycloak (nie 11 od razu) + pierwsze 3 ekrany jako cel Iteracji 1, bez pełnej głębi P1/P2.
10. **Decyzja:**

```text
DECYZJA: PROJEKT MOŻE PRZEJŚĆ DO ITERACJI 0
WHY: Wizja jest jednoznaczna, definicja sukcesu mierzalna i sprawdzalna, a analiza pokrycia pokazuje, że istniejący projekt systemu (BFF, GraphQL-read-only, REST-commands, SSE, 11 ról Keycloak, Simulation Lab jako fabryka danych, TanStack tabele, evidence progressive disclosure) już dziś daje naturalne miejsce dla zdecydowanej większości technik Playwright wymaganych na poziomie Senior QA/SDET — bez wymuszania sztucznych funkcji. Zidentyfikowane braki (sesja wygasająca, nowa karta z deep-linka, jawna polityka retries/tagów/locale) są małe, tanie, i realnie uzasadnione biznesowo, nie wymyślone pod Playwright. Jedyne obszary bez pokrycia (iframe, mobilna emulacja, pełnostronicowy visual regression, gRPC przed ekstrakcją P2) są świadomie i poprawnie odrzucone, zgodnie z anty-celami.
MUST_ADD: (1) redirect-i-powrót przy wygasłej sesji, (2) "otwórz w nowej karcie" z Evidence drawer, (3) jawna polityka retries, (4) konwencja tagów @smoke/@role/@sse/@fault-injection, (5) jawna reguła locale/timezone dla kwot i cut-off.
NEXT: sepa-nexus-iteration-0-foundation-plan.md, z tymi pięcioma pozycjami jako jawne zadania fundamentu, oraz z zakresem 3–4 ról (nie 11) na start.
```

---

*Koniec artefaktu nr 1. `[NO-CODE]` — wizja, kryteria sukcesu, analiza pokrycia. Źródła zweryfikowane 9 lipca 2026, żadna teza nie została zmyślona ani przypisana bez potwierdzenia.*
