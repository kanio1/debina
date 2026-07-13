---
status: not-started
depends_on: [EPIC-00-repository-agent-foundation]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 1 (Story 1.1-1.3), lines 214-278"
---

# EPIC-01 — PostgreSQL 18 Foundation

Prawda bazodanowa dla cienkiego pionowego wycinka: jeden schemat, jedna tabela, RLS udowodnione realnym testem negatywnym, oraz wzorzec outbox per-schema (ADR-N5) zweryfikowany wcześnie, bo to najbardziej ryzykowne założenie architektoniczne do zostawienia bez weryfikacji.

## Story 1.1 — Flyway setup & dwie role DB

status: not-started
depends_on: []

Opis: Flyway w Maven + dwie role DB (`sepa_migration`, `sepa_app`) — źródło: linie 220-229.

Kryterium ukończenia: obie role istnieją, `sepa_app` bez `Superuser`/`Bypass RLS`.

Taski:
- [ ] **Dodaj Flyway do backendu** (`flyway-core` + `flyway-database-postgresql`), migracje z `backend/src/main/resources/db/migration`.
      `verify: ./mvnw -f backend flyway:info` → łączy się, pokazuje zero zastosowanych migracji.
- [ ] **Stwórz dwie role DB w migracji bootstrapowej** (`V1__roles.sql`): `sepa_migration` (właściciel DDL, tylko Flyway) i `sepa_app` (RLS-bound, aplikacja — NIE `BYPASSRLS`, NIE superuser).
      `verify: psql -c "\du" | grep -E "sepa_migration|sepa_app"` → obie role wylistowane, `sepa_app` bez atrybutów `Superuser`/`Bypass RLS`.

## Story 1.2 — schemat `payment`, tabela `payments`, RLS

status: not-started
depends_on: [Story 1.1]

Opis: pierwsza domenowa tabela + RLS z testem negatywnym empty-GUC — źródło: linie 231-256.

Kryterium ukończenia: RLS wymuszone, test `PaymentsRlsTest` przechodzi.

Taski:
- [ ] **Stwórz schemat `payment`**, właściciel `sepa_migration`, `sepa_app` z `USAGE`+`SELECT/INSERT/UPDATE` (nigdy `DELETE`).
      `verify: psql -c "\dn payment"` → schemat istnieje, właściciel `sepa_migration`.
- [ ] **Stwórz cienką tabelę `payments`** (id, tenant_id, end_to_end_id, amount, currency, debtor_iban, creditor_iban, status CHECK IN RECEIVED/VALIDATED/REJECTED/DISPATCHED, created_at, version) + unikalny indeks `(tenant_id, end_to_end_id)`. Bez sygnatury/lineage ISO — to Iteracja 1+.
      `verify: psql -c "\d payment.payments"` → tabela istnieje z dokładnie tymi kolumnami.
- [ ] **Włącz i wymuś RLS, dodaj politykę tenant** (dokładny wzorzec z Story 0.2 skilla `postgres-rls-migration`).
      `verify: psql -c "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname='payments'"` → oba `t`.
- [ ] **Napisz test negatywny empty-GUC-zero-rows** (JUnit+Testcontainers): połączenie jako `sepa_app` bez ustawionego `app.tenant_id` → zero wierszy, nawet gdy istnieją wiersze innych tenantów.
      `verify: ./mvnw -f backend test -Dtest=PaymentsRlsTest` → przechodzi, w tym przypadek empty-GUC i cross-tenant.

## Story 1.3 — tabele outbox/inbox per schemat (ADR-N5)

status: not-started
depends_on: [Story 1.2]

Opis: pierwsza realizacja wzorca per-schema outbox/inbox z ADR-N5, dla schematu `payment` — źródło: linie 258-277.

Kryterium ukończenia: tabele istnieją, test `OutboxOwnershipTest` przechodzi.

Taski:
- [ ] **Stwórz `payment.outbox_events`** (id, aggregate_id, event_type, payload jsonb, correlation_id, created_at, published_at) + indeks częściowy na nieopublikowanych.
      `verify: psql -c "\d payment.outbox_events"` → tabela istnieje.
- [ ] **Stwórz `payment.inbox_events`** z unikalnym ograniczeniem na `source_event_id` (redelivered Kafka message = bezpieczny no-op).
      `verify: psql -c "\d payment.inbox_events"` → tabela istnieje z unique constraint na `source_event_id`.
- [ ] **Test negatywny: druga rola-writer nie może pisać do `payment.outbox_events`.** Utwórz tymczasową `other_module_role`, spróbuj `INSERT`, oczekuj permission-denied — dowód, że one-writer-per-schema trzyma się nawet dla tabeli outbox.
      `verify: ./mvnw -f backend test -Dtest=OutboxOwnershipTest` → przechodzi.
