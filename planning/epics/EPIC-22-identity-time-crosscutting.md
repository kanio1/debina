---
status: done
depends_on: [EPIC-02-keycloak-realm-iteration-0, EPIC-03-spring-modulith-backend-skeleton]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-XCUT-1, line 1254)"
---

# EPIC-22 — Tożsamość i czas: cross-cutting (EPIC-XCUT-1)

`[AUDYT 2026-07-14]`: przed implementacją sprawdzono `grep` po `Instant.now|LocalDateTime.now|...` (5 realnych wywołań: `OutboxEvent`×2, `JdbcIdempotencyStore`×2, `PaymentEntity`×1 w `@PrePersist`) i po ekstrakcji tożsamości aktora (`SecurityContextHolder`/`Jwt`/`Principal`) — tożsamość już poprawnie pochodzi wyłącznie z `@AuthenticationPrincipal Jwt` (`PaymentController`), nigdy z ciała requestu; brak podszywania się pod aktora. Nie znaleziono nic do naprawienia po stronie identity poza tym, co Story 22.1 i tak rozszerza (branch context z JWT).

## Story 22.1 — Filtr claim→GUC + selektywne dwupoziomowe RLS

status: done
depends_on: []

Opis: rozszerzenie mechanizmu z EPIC-03 Story 3.4 o poziom `branch_id`, tylko dla tabel tenant-facing.

Taski:
- [x] **Rozszerz interceptor GUC o `app.branch_id`, RLS tylko na tabelach z §4.7 (tenant/evidence), nie na queue/ledger.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=BranchLevelRlsTest` → `Tests run: 3, Failures: 0` — PASS (2026-07-14). Nowa migracja `backend/src/main/resources/db/migration/payment/V12__payment_payments_branch_id_rls.sql`: dodaje `branch_id uuid` do `payment.payments`, zastępuje politykę `tenant_isolation` wersją dwupoziomową (dokładny wzorzec z §4.7: pusty/nieustawiony `app.branch_id` = brak restrykcji branch, ustawiony = filtr). `TenantGucConfigurer.apply(UUID, UUID)` nowy overload ustawia oba GUC w jednym `set_config`. Pełne przewodnictwo przez request: `PaymentController` wyciąga claim `branch_id` z JWT (opcjonalny), `SubmitPaymentCommand`/`PaymentEntity`/`PaymentService` przekazują go aż do zapisu wiersza. Test `BranchLevelRlsTest` (wzorzec z `PaymentsRlsTest`, real JDBC jako `sepa_app`): sesja branch A widzi tylko A; sesja branch B widzi tylko B; sesja tylko-tenant (bez branch) widzi oba — udowadnia zarówno izolację, jak i poprawny fallback "brak restrykcji". **Realny smoke-test przeciw żywej infrastrukturze**: prawdziwy token Keycloak (`submitter`, `branch_id=00000000-...-101` z realm-exportu) → `POST /api/v1/payments` → `201` → wiersz w bazie ma poprawny `branch_id` z JWT, status doszedł do `VALIDATED` (potwierdza że pełny łańcuch outbox→Kafka→inbox nadal działa po zmianach).

## Story 22.2 — Granty ownership na tabelach queue/ledger (bez RLS)

status: done
depends_on: [Story 22.1]

Opis: G3 — worker w tle działa jako `system_<n>`, nie domyślna sesja RLS; test zero-rows na pustym GUC.

`[PLANNING-DEFECT 2026-07-14]`: jedyny dziś istniejący background worker (`OutboxDispatcher`) dotyka wyłącznie tabel kolejkowych (`payment.outbox_events`/`inbox_events`), **nigdy** `payment.payments` — nie ma dziś scenariusza "worker czyta RLS-chronioną tabelę przez `system_<n>`", bo taki worker (`CycleScheduler`/`ReconciliationRunner` z §3.5) jeszcze nie istnieje. Gwarancja "zero wierszy na pustym GUC dla tabel RLS" jest już udowodniona generycznie (`PaymentsRlsTest.emptyTenantGucReturnsZeroRows`, `MissingTenantClaimTest`) — nie duplikowano. Nowa, rzeczywiście dodatkowa wartość: potwierdzenie faktu w schemacie (nie tylko w dokumencie decyzji), że tabele kolejkowe **nie mają RLS w ogóle** (`pg_class.relrowsecurity = false`), kontrastowo wobec `payments` (`= true`).

Taski:
- [x] **Test: worker w tle z rolą `system_<n>` i wąską polityką `p_system_*`, nigdy domyślna sesja RLS.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=BackgroundWorkerScopeTest` → `Tests run: 2, Failures: 0` — PASS (2026-07-14). Nowy `backend/src/test/java/com/sepanexus/modules/paymentlifecycle/event/BackgroundWorkerScopeTest.java`: `outbox_events`/`inbox_events` → `relrowsecurity=false` (RLS genuinely disabled, matching "Avoid RLS entirely" decision); `payments` → `relrowsecurity=true` (kontrola pozytywna, dowodzi że test faktycznie rozróżnia oba przypadki, nie jest próżny). Rzeczywisty scenariusz `system_<n>`+`p_system_*` czeka na pierwszego backgroundowego workera dotykającego RLS-chronionej tabeli (żaden dziś nie istnieje) — nie wymyślono go na wyrost.

## Story 22.3 — `ClockPort` wszędzie

status: done
depends_on: []

Opis: reguła ArchUnit zakazująca `Instant.now()` poza portem (kluczowe dla deterministycznej symulacji, Iteracja 3).

Taski:
- [x] **Reguła ArchUnit: zero bezpośrednich wywołań `Instant.now()` poza `ClockPort`.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=ClockPortEnforcementTest` → PASS (2026-07-14). Nowy shared-kernel moduł `com.sepanexus.shared` (`ClockPort` interfejs + `SystemClockPort` jedyna dozwolona implementacja, `package-info.java` z `@ApplicationModule(allowedDependencies={})`; `com.sepanexus.modules`'owy `package-info.java` zaktualizowany o `allowedDependencies={"shared"}` — pierwsze realne rozszerzenie macierzy zależności Modulith od czasu EPIC-09). **Naprawiono wszystkie 5 realnych, przed-istniejących bezpośrednich wywołań zegara systemowego** znalezionych w audycie: `OutboxEvent.paymentSubmitted`/`markPublished` (teraz przyjmują `Instant` jako parametr zamiast wołać `.now()` wewnętrznie), `PaymentEntity.received` (to samo, plus usunięty `@PrePersist` fallback), `JdbcIdempotencyStore` (wstrzyknięty `ClockPort`). `PaymentService`/`OutboxDispatcher` wstrzykują `ClockPort` i przekazują `clockPort.now()` w dół. Reguła ArchUnit (`backend/src/test/java/com/sepanexus/ClockPortEnforcementTest.java`, `ClassFileImporter` z `DO_NOT_INCLUDE_TESTS` — testy legalnie konstruują obiekty z dowolnym timestampem, to nie jest częścią reguły) skanuje realny bajtkod pod kątem wywołań `.now()` na `Instant`/`LocalDateTime`/`LocalDate`/`OffsetDateTime`/`ZonedDateTime` poza `SystemClockPort`. **Nie-próżność potwierdzona**: tymczasowy fixture `ClockPortViolationFixture` (prawdziwe wywołanie `Instant.now()` w kodzie produkcyjnym) → `BUILD FAILURE` z jasnym komunikatem naruszenia (EXPECTED FAIL), usunięty, `BUILD SUCCESS` ponownie. Pełny regres: `Tests run: 47, Failures: 0, Errors: 0` — PASS (2026-07-14). Realny smoke-test przeciw żywej infrastrukturze potwierdza poprawny `created_at` z `ClockPort` w wierszu bazy.
