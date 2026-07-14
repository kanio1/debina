---
status: done
depends_on: [EPIC-00-repository-agent-foundation, EPIC-01-postgresql-foundation]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 3 (Story 3.1-3.4), lines 303-433"
---

# EPIC-03 — Spring Boot / Modulith Backend Skeleton

Moduł `payment-lifecycle`, cienki, z dyscypliną Controller→Service→Repository i podpięciem RLS-GUC udowodnionym naprawdę — wzorzec, który kopiuje każdy późniejszy moduł.

## Story 3.1 — Szkielet projektu Maven

status: done
depends_on: []

Opis: projekt Maven (JDK 25, Spring Boot 4.1.x, Spring Modulith) + test architektury `ApplicationModules.verify()` — źródło: linie 309-340.

Kryterium ukończenia: `ModularityTest` przechodzi i jest to bramka CI.

Taski:
- [x] **Wygeneruj projekt Maven** (JDK 25, `spring-boot-starter-parent` 4.1.x, `spring-modulith-starter-core`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`, driver `postgresql`, `flyway-core`, Testcontainers postgresql/kafka/junit-jupiter).
      `verify: ./mvnw -f backend -q compile` → PASS (2026-07-14).
- [x] **Dodaj `application.yml`** z datasource na `sepa_app` (nigdy `sepa_migration`), Flyway na `sepa_migration`, issuer URI Keycloak dla resource servera.
      `verify: ./mvnw -f backend -q spring-boot:run` + `curl -fsS http://localhost:8081/actuator/health` → PASS (2026-07-14; `{"status":"UP"}`), proces zakończony graceful shutdown.
- [x] **Stwórz szkielet pakietów modułu**: `backend/src/main/java/.../modules/paymentlifecycle/{web,service,repository,domain}`.
      `verify: find backend/src/main/java -type d -name paymentlifecycle -exec sh -c 'test -d "$1/web" && test -d "$1/service" && test -d "$1/repository" && test -d "$1/domain"' _ {} \;` → PASS (2026-07-14).
- [x] **Dodaj test architektury Spring Modulith** (`ApplicationModules.of(...).verify()`) — najważniejszy test tego epika, bo wymusza granice modułów dla każdego kolejnego modułu.
      `verify: ./mvnw -f backend test -Dtest=ModularityTest` → PASS (2026-07-14).

## Story 3.2 — moduł `payment-lifecycle`: Controller → Service → Repository

status: done
depends_on: [Story 3.1]

Opis: pełny pionowy przekrój warstwowy jednego modułu — źródło: linie 342-414.

Kryterium ukończenia: `POST /api/v1/payments` zwraca 201, żaden `@TenantId`, RFC 7807 na błędach.

Taski:
- [x] **`PaymentEntity`** (JPA, mapowanie `payment.payments`) — bez `@TenantId` (RLS jest jedyną warstwą egzekwowania).
      `verify: ./mvnw -f backend -q compile` → PASS (2026-07-14).
- [x] **`PaymentRepository`** (cienki interfejs Spring Data, schema-scoped, bez metod cross-tenant).
      `verify: ./mvnw -f backend -q compile` → PASS (2026-07-14).
- [x] **`SubmitPaymentRequest` DTO** (wąski, nazwany — nigdy generyczny entity-binding `PUT`, obrona przed mass-assignment).
      `verify: ./mvnw -f backend -q compile` → PASS (2026-07-14).
- [x] **`PaymentService.submitPayment(...)`** — właściciel reguły biznesowej (sprawdzenie idempotencji, przypisanie statusu), jeden `@Transactional` na komendę.
      `verify: ./mvnw -f backend test -Dtest=PaymentServiceTest` → PASS (2026-07-14).
- [x] **`PaymentController`** — parsuje/waliduje request, woła dokładnie jedną metodę service, bez logiki biznesowej.
      `verify: ./mvnw -f backend test -Dtest=PaymentControllerTest` → PASS (2026-07-14; Spring Boot 4.1 `@SpringBootTest` + `MockMvc`, POST 201 i `Location`).
- [x] **Kształt błędu RFC 7807 dla wszystkich 4xx/5xx** na tym kontrolerze — ustanów konwencję teraz, zanim powstanie więcej endpointów.
      `verify: ./mvnw -f backend test -Dtest=PaymentControllerErrorTest` → PASS (2026-07-14; duplicate 409, `application/problem+json`, `correlationId`).

## Story 3.3 — Spring Security Resource Server

status: done
depends_on: [Story 3.2, EPIC-02-keycloak-realm-iteration-0]

Opis: walidacja JWT + method security — źródło: linie 416-423.

Kryterium ukończenia: brak tokena → 401, zła rola → 403, właściwa rola → 201.

Taski:
- [x] **Skonfiguruj Resource Server** do walidacji JWT wobec realmu Keycloak z EPIC-02, mapując `realm_access.roles` na Spring `GrantedAuthority`.
      `verify: ./mvnw -f backend test -Dtest=SecurityConfigTest` → PASS (2026-07-14; 401 bez auth, 201 dla `payment_submitter`, nested `realm_access.roles`).
- [x] **Method security**: `@PreAuthorize("hasRole('payment_submitter')")` na `submitPayment`.
      `verify: ./mvnw -f backend test -Dtest=PaymentAuthorizationTest` → PASS (2026-07-14; `operator` jest odrzucony przez method security).
- [x] **Zaszkieletuj własny `AuthorizationManager<MethodInvocation>`** z security review — pełne użycie dopiero przy endpointach approve/reject w Iteracji 1, ale bean ma istnieć już teraz.
      `verify: ./mvnw -f backend -q compile` → PASS (2026-07-14; fail-closed placeholder bean ładuje się w kontekście Spring).

## Story 3.4 — RLS GUC-setting

status: done
depends_on: [Story 3.3, EPIC-01-postgresql-foundation/Story 1.2]

Opis: ustawianie `app.tenant_id` na poziomie transakcji z JWT — źródło: linie 425-433.

Kryterium ukończenia: dwa requesty z różnymi `tenant_id` widzą tylko własne wiersze; brak claimu → zero wierszy.

Taski:
- [x] **Napisz `StatementInspector`/interceptor połączenia** ustawiający `SET LOCAL app.tenant_id = '<value>'` na starcie każdej transakcji, czytając wartość z claimu `tenant_id` bieżącego JWT.
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=TenantGucIntegrationTest` → PASS (2026-07-14; transaction-bound Hibernate `doWork` używa parametryzowanego `SELECT set_config('app.tenant_id', ?, true)`).
- [x] **Test negatywny: request bez claimu `tenant_id` widzi zero wierszy**, nie błąd i nie wszystkie wiersze (reguła empty-GUC-zero-rows na poziomie aplikacji, nie tylko surowego SQL).
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=MissingTenantClaimTest` → PASS (2026-07-14).

## Defekty planningu

- `[PLANNING-DEFECT]` Wcześniejsza polityka `current_setting('app.tenant_id', true)::uuid` rzucała błąd dla pustego GUC zwracanego po resetowaniu połączenia. Dodano addytywną migrację `V7__payment_rls_empty_guc_zero_rows.sql` z `NULLIF(..., '')::uuid`, aby zachować kontrakt zero-wierszy bez zmiany checksum historii Flyway.
