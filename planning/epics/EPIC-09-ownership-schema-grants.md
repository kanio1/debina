---
status: not-started
depends_on: [EPIC-03-spring-modulith-backend-skeleton, EPIC-07-ci-cd-foundation]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-1, line 1257); sepa-nexus-blueprint-ownership-integration.md §9 (line 345); §3.6.4a/§3.6.5 layering+arch-test rules"
---

# EPIC-09 — Ownership: schema/grant enforcement mechanism (EPIC-OWN-1)

Ogólny mechanizm egzekwowania one-writer-per-schema — generyczne narzędzie, testowane od razu wobec modułów już istniejących (`payment` z Iteracji 0), rozszerzane w miarę powstawania kolejnych schematów. `[FREEZE]` reguła źródłowa: moduł pisze wyłącznie do własnego schematu.

## Story 9.1 — Jedna rola DB per moduł + granty/odbieranie uprawnień

status: not-started
depends_on: []

Opis: każdy schemat dostaje własną rolę-writer z jawnym grantem, bez domyślnego dostępu między schematami.

Kryterium ukończenia: rola modułu A nie może pisać do schematu modułu B.

Taski:
- [ ] **Zbuduj generyczny wzorzec roli-writer per schemat** (na bazie `sepa_app`/`sepa_migration` z EPIC-01) i macierz grantów/odwołań uprawnień dla wszystkich obecnie istniejących schematów.
      `verify: ./mvnw -f backend test -Dtest=*SchemaGrantMatrixTest*` → test SQL/Testcontainers potwierdza brak zapisu poza własnym schematem.

## Story 9.2 — Flyway folder-per-module + granice pakietów repository

status: not-started
depends_on: [Story 9.1]

Opis: migracje Flyway w katalogu per moduł, repozytoria ograniczone do własnego pakietu.

Kryterium ukończenia: struktura katalogów Flyway odzwierciedla granice modułów.

Taski:
- [ ] **Wymuś układ `backend/src/main/resources/db/migration/<module>/`** dla każdego nowego schematu i zasadę, że `Repository` żyje wyłącznie w pakiecie właściciela.
      `verify: find backend/src/main/resources/db/migration -mindepth 1 -maxdepth 1 -type d` → jeden katalog na moduł, zgodny z §3.6.2.

## Story 9.3 — Bramka Modulith `allowedDependencies`+`verify()`

status: not-started
depends_on: [EPIC-03-spring-modulith-backend-skeleton/Story 3.1]

Opis: rozszerzenie `ModularityTest` z EPIC-03 o pełną macierz dozwolonych/zabronionych zależności z §3.6.4.

Kryterium ukończenia: test pada na każdym zabronionym imporcie z macierzy §3.6.4.

Taski:
- [ ] **Skonfiguruj `allowedDependencies`** dla wszystkich modułów wg macierzy §3.6.4 (np. `ingress`→`iso-adapter`/`signature`/`security`/`reference-data`/`evidence-audit` dozwolone; `ingress`→`payment`/`settlement`/`ledger`/`egress` bezpośrednio zabronione).
      `verify: ./mvnw -f backend test -Dtest=ModularityTest` → przechodzi z pełną macierzą; celowe naruszenie z macierzy zabronionych pada.

## Story 9.4 — Reguły ArchUnit dot. ownership

status: not-started
depends_on: [Story 9.3]

Opis: reguły ArchUnit z §3.6.4a/§3.6.5 — brak repository poza pakietem właściciela, brak `settlement`→`ledger` repository, brak `Instant.now()` poza `ClockPort`, pakiet GraphQL nie zależy od command services, brak `@TenantId`/filtru tenant na poziomie Hibernate, brak `Repository` w klasie Controller.

Kryterium ukończenia: wszystkie reguły ArchUnit z §3.6.5 wdrożone i przechodzą.

Taski:
- [ ] **Zaimplementuj pakiet testów ArchUnit** dokładnie wg listy w §3.6.5 (ownership blueprint) — jedna reguła = jeden test, z nazwą odzwierciedlającą regułę źródłową.
      `verify: ./mvnw -f backend test -Dtest=*ArchRules*` → wszystkie reguły z §3.6.5 mają odpowiadający, przechodzący test.
