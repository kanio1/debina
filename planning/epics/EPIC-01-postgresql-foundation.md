---
status: done
depends_on: [EPIC-00-repository-agent-foundation]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 1 (Story 1.1-1.3), lines 214-278"
---

# EPIC-01 — PostgreSQL 18 Foundation

Prawda bazodanowa dla cienkiego pionowego wycinka: jeden schemat, jedna tabela, RLS udowodnione realnym testem negatywnym, oraz wzorzec outbox per-schema (ADR-N5) zweryfikowany wcześnie, bo to najbardziej ryzykowne założenie architektoniczne do zostawienia bez weryfikacji.

> [PLANNING-DEFECT] EPIC-01 zaczyna się przed formalnym scaffoldem Maven w EPIC-03, choć jego pierwsze `verify:` wymaga `./mvnw -f backend`. Utworzono wyłącznie minimalny prerequisite: rootowy Maven Wrapper dla tej komendy, wrapper w `backend/` oraz `backend/pom.xml` z Flyway i sterownikiem PostgreSQL. Spring Boot/Modulith pozostaje zakresem EPIC-03.

## Story 1.1 — Flyway setup & dwie role DB

status: done
depends_on: []

Opis: Flyway w Maven + dwie role DB (`sepa_migration`, `sepa_app`) — źródło: linie 220-229.

Kryterium ukończenia: obie role istnieją, `sepa_app` bez `Superuser`/`Bypass RLS`.

Taski:
- [x] **Dodaj Flyway do backendu** (`flyway-core` + `flyway-database-postgresql`), migracje z `backend/src/main/resources/db/migration`.
      `verify: ./mvnw -f backend flyway:info` → PASS (2026-07-14): połączono z PostgreSQL 18.4; `Schema version: << Empty Schema >>`, `No migrations found`.
- [x] **Stwórz dwie role DB w migracji bootstrapowej** (`V1__roles.sql`): `sepa_migration` (właściciel DDL, tylko Flyway) i `sepa_app` (RLS-bound, aplikacja — NIE `BYPASSRLS`, NIE superuser).
      `verify: podman compose -f infra/docker-compose.yml exec -T postgres psql -U sepa_migration -d sepa_nexus -c "\du" | grep -E "sepa_migration|sepa_app"` → PASS (2026-07-14): kontenerowy odpowiednik z powodu braku lokalnego `psql`; obie role wylistowane, `sepa_app` bez atrybutów `Superuser`/`Bypass RLS`.

## Story 1.2 — schemat `payment`, tabela `payments`, RLS

status: done
depends_on: [Story 1.1]

Opis: pierwsza domenowa tabela + RLS z testem negatywnym empty-GUC — źródło: linie 231-256.

Kryterium ukończenia: RLS wymuszone, test `PaymentsRlsTest` przechodzi.

Taski:
- [x] **Stwórz schemat `payment`**, właściciel `sepa_migration`, `sepa_app` z `USAGE`+`SELECT/INSERT/UPDATE` (nigdy `DELETE`).
      `verify: podman compose -f infra/docker-compose.yml exec -T postgres psql -U sepa_migration -d sepa_nexus -c "\dn payment"` → PASS (2026-07-14): kontenerowy odpowiednik z powodu braku lokalnego `psql`; schemat istnieje, właściciel `sepa_migration`.
- [x] **Stwórz cienką tabelę `payments`** (id, tenant_id, end_to_end_id, amount, currency, debtor_iban, creditor_iban, status CHECK IN RECEIVED/VALIDATED/REJECTED/DISPATCHED, created_at, version) + unikalny indeks `(tenant_id, end_to_end_id)`. Bez sygnatury/lineage ISO — to Iteracja 1+.
      `verify: podman compose -f infra/docker-compose.yml exec -T postgres psql -U sepa_migration -d sepa_nexus -c "\d payment.payments"` → PASS (2026-07-14): kontenerowy odpowiednik z powodu braku lokalnego `psql`; tabela ma wymagane kolumny, constraint statusu i indeks `(tenant_id, end_to_end_id)`.
- [x] **Włącz i wymuś RLS, dodaj politykę tenant** (dokładny wzorzec z Story 0.2 skilla `postgres-rls-migration`).
      `verify: podman compose -f infra/docker-compose.yml exec -T postgres psql -U sepa_migration -d sepa_nexus -c "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname='payments'"` → PASS (2026-07-14): kontenerowy odpowiednik z powodu braku lokalnego `psql`; oba pola mają wartość `t`.
- [x] **Napisz test negatywny empty-GUC-zero-rows** (JUnit+Testcontainers): połączenie jako `sepa_app` bez ustawionego `app.tenant_id` → zero wierszy, nawet gdy istnieją wiersze innych tenantów.
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=PaymentsRlsTest` → PASS (2026-07-14): 2 testy, 0 błędów; obejmuje empty-GUC i cross-tenant.

## Story 1.3 — tabele outbox/inbox per schemat (ADR-N5)

status: done
depends_on: [Story 1.2]

Opis: pierwsza realizacja wzorca per-schema outbox/inbox z ADR-N5, dla schematu `payment` — źródło: linie 258-277.

Kryterium ukończenia: tabele istnieją, test `OutboxOwnershipTest` przechodzi.

Taski:
- [x] **Stwórz `payment.outbox_events`** (id, aggregate_id, event_type, payload jsonb, correlation_id, created_at, published_at) + indeks częściowy na nieopublikowanych.
      `verify: podman compose -f infra/docker-compose.yml exec -T postgres psql -U sepa_migration -d sepa_nexus -c "\d payment.outbox_events"` → PASS (2026-07-14): kontenerowy odpowiednik z powodu braku lokalnego `psql`; tabela i indeks częściowy istnieją.
- [x] **Stwórz `payment.inbox_events`** z unikalnym ograniczeniem na `source_event_id` (redelivered Kafka message = bezpieczny no-op).
      `verify: podman compose -f infra/docker-compose.yml exec -T postgres psql -U sepa_migration -d sepa_nexus -c "\d payment.inbox_events"` → PASS (2026-07-14): kontenerowy odpowiednik z powodu braku lokalnego `psql`; tabela ma unique constraint na `source_event_id`.
- [x] **Test negatywny: druga rola-writer nie może pisać do `payment.outbox_events`.** Utwórz tymczasową `other_module_role`, spróbuj `INSERT`, oczekuj permission-denied — dowód, że one-writer-per-schema trzyma się nawet dla tabeli outbox.
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=OutboxOwnershipTest` → PASS (2026-07-14): 1 test, 0 błędów; oczekiwany SQLSTATE `42501` dla INSERT roli innego modułu.
