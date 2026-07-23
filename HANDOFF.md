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
- Wave 2 oznaczyła każdą z 138 klas Surefire dokładnie jednym trwałym tagiem:
  39 klas `fast` i 99 klas `testcontainers`. Audyt zależności obejmuje
  bezpośrednie importy oraz użycie/dziedziczenie wspólnych supportów
  PostgreSQL/Kafka/Keycloak.
- `TestClassificationCompletenessTest` fail-closed wykrywa brak, konflikt i
  niezgodność tagu z zależnością runtime. Usunięto obie ręczne listy `-Dtest=`
  z grafu Dagger.
- Dodano publiczne `BackendTestcontainers` (wyłącznie tag `testcontainers`) i
  `BackendRegressionAll` (unfiltered equivalence oracle).
  `TestcontainersRegression` jest deprecated aliasem pełnego oracle;
  `FullLocal` używa tylko `BackendTestcontainers`.
- Maven proof: `fast` = 146 testów, `testcontainers` = 397, unfiltered = 543;
  39+99 klas daje dokładnie 138, przecięcie klas jest puste, unia identyczna
  ze zbiorem unfiltered.
- Dagger runtime proof: `backend-testcontainers` = 397/0/0/0 w 4m32s;
  `backend-regression-all` = 543/0/0/0 w 4m36s. Oba użyły jawnego typed socket
  `/run/podman/podman.sock` i frozen lock.
- Chroniony `build/generated-spring-modulith/javadoc.json` został po Maven
  odtworzony do HEAD i ma oczekiwany SHA-256
  `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`.

## Utknęliśmy na

Nic nie blokuje. Wave 2 jest gotowa do lokalnego commita. Zachowane wcześniejsze
zmiany dokumentacyjne/planning nadal pozostają poza tym selektywnym commitem.

## Plan na następny krok

Zacommitować Wave 2, następnie przejść do Wave 3. Zastąpić obecne siedem leaves
integration dokładnie pięcioma: `backend-integration`,
`frontend-production-build`, `database-contract`, `database-upgrade`,
`kafka-contract`. Fresh PostgreSQL readiness/migrate/validate/credential/RLS/
grants ma wykonać się na jednej instancji; upgrade pozostaje oddzielny.

## Pułapki, których nie wolno powtórzyć

- Nie wracać do 62 klas z samym `@Testcontainers`: 37 dalszych zależności
  runtime wynika z bezpośrednich kontenerów bez tej adnotacji lub supportów.
- Nie uruchamiać unfiltered `BackendRegressionAll` jako dziecka `FullLocal`;
  duplikowałoby 146 testów `fast`.
- Nie traktować dwóch świeżych baz jako konsolidacji database-contract.
- Nie dodawać ambient host socketu, nested Dagger CLI, remote CI ani `git push`.
- Nie zmieniać chronionego `build/generated-spring-modulith/javadoc.json`.
