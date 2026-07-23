# HANDOFF

## Zadanie

Realizacja programu `docs/ci/DAGGER-FINAL-ARCHITECTURE-CONTRACT.md` w ośmiu
lokalnych falach. Celem jest jeden automatyczny `acceptance`, trwała
klasyfikacja testów, finalne grafy integration/full-local, pełny runtime proof
i czysty worktree bez `git push`.

## Zrobione

- Wave 0 odtworzyła stan, zapisała finalny kontrakt i zakończyła się lokalnym
  commitem `18818e1 docs(ci): record final dagger architecture contract`.
- Wave 1 ustanowiła finalne nazwy/topologię i zakończyła się commitem
  `159a736 refactor(ci): establish final acceptance topology`.
- Wave 2 wprowadziła rozłączne tagi 39 klas `fast` / 99 klas
  `testcontainers`, equivalence 146+397=543 i zakończyła się commitem
  `b654a10 test(ci): classify backend suites by durable tags`.
- Wave 3 skonsolidowała pięć finalnych integration leaves i zakończyła się
  commitem `233a862 refactor(ci): consolidate integration contracts`.
- Wave 4 potwierdziła, że frontend nie ma żadnego `NEXT_PUBLIC_*` ani
  projektowego build-time env. Pięć wartości OIDC/BFF/backend jest
  server-only i odczytywane leniwie: `KEYCLOAK_ISSUER`,
  `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `BFF_BASE_URL`,
  `BACKEND_API_BASE_URL`.
- Wyodrębniono jeden `frontendProductionBuild`. Integration i wszystkie runtime
  smoke services używają tego samego `.next`; bindings i secret są dokładane
  dopiero po vertex `pnpm run build`. `frontend_env_test.go` blokuje
  przypadkowe wprowadzenie build-time public env albo przesunięcie bindings
  przed build.
- Focused integration po refaktorze przeszedł w 8.5s; `pnpm run build` miał
  jawny status `CACHED`, a database/Kafka contracts pozostały zielone.
- Pełny `smoke-suite` przeszedł w 3m01s z env ustawionymi wyłącznie runtime:
  D3A oraz wszystkie D3B evidence zakończyły się kodem 0. Trace zawiera jeden
  współdzielony production build vertex dla wszystkich czterech runtime graphs
  i ten vertex jest `CACHED`.
- Chroniony `build/generated-spring-modulith/javadoc.json` został po Maven
  odtworzony do HEAD i ma oczekiwany SHA-256
  `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`.

## Utknęliśmy na

Nic nie blokuje. Wave 4 jest gotowa do lokalnego commita. Zachowane wcześniejsze
zmiany dokumentacyjne/planning nadal pozostają poza tym selektywnym commitem.

## Plan na następny krok

Zacommitować Wave 4, następnie przejść do Wave 5. Ustawić acceptance jako
równoległe `fast` + `integration`, a po ich sukcesie sekwencyjny `smoke-suite`.
Zachować oddzielne `pipeline-assurance` oraz `full-local = acceptance +
backend-testcontainers`. Rozbudować kompletny zewnętrzny architecture runner.

## Pułapki, których nie wolno powtórzyć

- Nie dodawać backend `fast` z powrotem do top-level `Fast`; jego właścicielem
  w acceptance jest `backend-integration`.
- Nie uruchamiać unfiltered `BackendRegressionAll` jako dziecka `FullLocal`;
  duplikowałoby 146 testów `fast`.
- Nie rozbijać `database-contract` na niezależne fresh services; upgrade ma
  pozostać jedyną drugą instancją.
- Nie dodawać OIDC/BFF endpointów ani secretu przed
  `frontendProductionBuild`; zmieniłoby to cache key i mogłoby bake'ować runtime
  configuration.
- Nie dodawać ambient host socketu, nested Dagger CLI, remote CI ani `git push`.
- Nie zmieniać chronionego `build/generated-spring-modulith/javadoc.json`.
