# HANDOFF

## Zadanie

SEPA Nexus jest w Iteracji 0. Ten batch ukończył EPIC-01 — PostgreSQL 18 Foundation; kolejnym technicznie odblokowanym epikiem jest EPIC-02 — Keycloak 26.6.4 Realm.

## Zrobione

- EPIC-01 oraz Stories 1.1, 1.2 i 1.3 mają `status: done`; `planning/README.md` wskazuje EPIC-01 jako done.
- Utworzono minimalny Maven/Flyway prerequisite przed formalnym EPIC-03: rootowy i backendowy Maven Wrapper 3.9.11, `backend/pom.xml` z Flyway 12.11.0, PostgreSQL JDBC 42.7.7, JUnit Jupiter 5.11.0 i Testcontainers 2.0.5. To zapisany `[PLANNING-DEFECT]`; nie dodano jeszcze Spring Boot/Modulith.
- Migracje `backend/src/main/resources/db/migration/V1__roles.sql`–`V6__payment_inbox_events.sql` tworzą role, `payment` schema, `payments` z RLS/force RLS oraz per-schema `outbox_events`/`inbox_events` z unikalnym `source_event_id`.
- Testy `PaymentsRlsTest` i `OutboxOwnershipTest` na PostgreSQL 18 Testcontainers sprawdzają empty-GUC-zero-rows, izolację cross-tenant oraz SQLSTATE `42501` dla drugiej roli zapisującej do payment outbox.
- Wykonane i pozytywne: `./mvnw -f backend flyway:info`, wszystkie `flyway:migrate`, kontenerowe `psql` verify przez `podman compose ... exec -T postgres`, `export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test` (3 testy, 0 błędów), `git diff --check`.

## Utknęliśmy na

Nic nie blokuje. EPIC-01 został ukończony i zweryfikowany; EPIC-02 nie został rozpoczęty. Lokalny PostgreSQL 18 pozostaje uruchomiony przez rootless Podman, bez usuwania named volume.

## Plan na następny krok

Otwórz `planning/epics/EPIC-02-keycloak-realm-iteration-0.md`, przeczytaj wskazany source i skill `keycloak-realm-config`, a następnie wykonaj pierwszy nieodhaczony task Story 2.1.

## Pułapki, których nie wolno powtórzyć

- Fedora 44 KDE używa rootless Podmana, nie Dockera. W sandboxie Podman i Maven cache są read-only; testy/infrastruktura wymagają zatwierdzonego wykonania poza nim.
- Testcontainers wymaga `DOCKER_HOST=unix://${XDG_RUNTIME_DIR}/podman/podman.sock`; nie wyłączaj Ryuk.
- Lokalny `psql` nie istnieje; używaj kontenerowego `podman compose -f infra/docker-compose.yml exec -T postgres psql ...` i raportuj faktyczną komendę.
- `podman-compose 1.6.0` nie obsługuje `podman compose ... ps <service>`; użyj `podman compose ... ps` bez nazwy usługi.
- Nie obchodź PostgreSQL RLS, nie przyznawaj `DELETE` roli aplikacyjnej, nie usuwaj named volumes i nie wpisuj PASS bez uruchomionej komendy.
- Node.js pozostaje `24.18.0`, TypeScript ma być exact 6.x po rozpoczęciu frontendu, a Playwright jest zakazany w Iteracji 0.
