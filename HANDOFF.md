# HANDOFF

## Zadanie

SEPA Nexus, Iteracja 0. Batch ukończył EPIC-02 — Keycloak Realm, EPIC-03 — Spring Boot/Modulith Backend Skeleton oraz EPIC-04 — Outbox/Inbox + Kafka. EPIC-05 nie został rozpoczęty.

## Zrobione

- EPIC-02: realm `sepa-nexus`, 4 role, `sepa-web` confidential, `sepa-api` bearer-only, mappery `tenant_id`/`branch_id`, czterech użytkowników local-dev; OIDC/kcadm/token claim checks PASS. Keycloak bind mount używa wymaganego przez Fedora SELinux `:Z`.
- EPIC-03: Spring Boot 4.1.0, JDK 25, Spring Modulith 2.0.6, runtime `sepa_app` i Flyway `sepa_migration`, moduł `paymentlifecycle`, RFC 7807/correlation ID, Resource Server/role mapping/method security, transaction-bound parametryzowany GUC i V7 naprawiająca empty-GUC-zero-rows.
- EPIC-04: `spring-boot-starter-kafka` (Spring Kafka 4.1.0), deklaratywny `payment.lifecycle.events.v1` (1 partycja), scheduled outbox dispatcher co 2 s i Kafka inbox consumer z `ON CONFLICT DO NOTHING`.
- Payment submission zapisuje outbox event w tej samej transakcji; dispatcher publikuje at-least-once, następnie oznacza `published_at`; consumer ustawia tenant GUC z event payloadu, przechodzi istniejący payment `RECEIVED`→`VALIDATED`, a inbox unique constraint blokuje drugi efekt.
- Faktyczne PASS: `./mvnw -f backend -q compile`; `./mvnw -f backend test -Dtest=ModularityTest`; `./mvnw -f backend test -Dtest=PaymentServiceTest,PaymentControllerTest,PaymentControllerErrorTest,SecurityConfigTest,PaymentAuthorizationTest`; `./mvnw -f backend test -Dtest=TenantGucIntegrationTest,MissingTenantClaimTest`; `./mvnw -f backend test -Dtest=OutboxDispatcherTest`; `./mvnw -f backend test -Dtest=InboxConsumerIdempotencyTest`; pełne `./mvnw -f backend test` (15 testów, 0 błędów), wszystkie z `DOCKER_HOST=unix://${XDG_RUNTIME_DIR}/podman/podman.sock` gdy używały Testcontainers.
- Faktyczne Podman PASS: `podman compose -f infra/docker-compose.yml up -d kafka`, `ps`, `exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -Fx payment.lifecycle.events.v1`; lokalne PostgreSQL, Keycloak i Kafka pozostają uruchomione.

## Utknęliśmy na

Nic nie blokuje. Batch trzech epików został ukończony i zweryfikowany.

## Plan na następny krok

Otwórz `planning/epics/EPIC-05-nextjs-bff.md`, sprawdź `depends_on` oraz source i rozpocznij pierwszy nieodhaczony task. Nie wykonuj tego kroku w bieżącej sesji.

## Pułapki, których nie wolno powtórzyć

- Fedora 44 KDE używa rootless Podmana; nie używaj Dockera. `podman-compose 1.6.0` wymaga pełnego `podman compose ... ps` bez filtra serwisu.
- Testcontainers potrzebuje `DOCKER_HOST=unix://${XDG_RUNTIME_DIR}/podman/podman.sock`; Ryuk działa i nie wolno go wyłączać bez rzeczywistego powodu.
- Nie usuwaj named volumes. Keycloak realm bind mount potrzebuje `:Z`, lecz nie dodawaj tej etykiety globalnie.
- Nie obchodź RLS ani nie używaj `@TenantId`; `set_config('app.tenant_id', ?, true)` musi być transaction-local i na połączeniu Hibernate. Pusty GUC wymaga `NULLIF(..., '')::uuid` dla zero-wierszy.
- Outbox to at-least-once, nie exactly-once: dopiero inbox unique constraint czyni redelivery bezpiecznym. Nie dodawaj CDC, Debezium, registry, DLQ ani kolejnych topiców w tym zakresie.
- Node.js pozostaje 24.18.0. TypeScript 6.x i frontend nie zostały zainicjalizowane. Playwright jest zakazany w Iteracji 0.
