---
status: done
depends_on: [EPIC-01-postgresql-foundation, EPIC-03-spring-modulith-backend-skeleton]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 4 (Story 4.1-4.3), lines 436-469"
---

# EPIC-04 — Outbox/Inbox + Kafka (cienkie)

Walidacja szkieletu event-driven (ADR-N5) wcześnie — jeden topic, jeden typ eventu, jeden producent, jeden konsument. Głębia przychodzi w późniejszych iteracjach.

## Story 4.1 — Topic Kafka i konfiguracja producenta

status: done
depends_on: [EPIC-00-repository-agent-foundation/Story 0.3]

Opis: jeden topic `payment.lifecycle.events.v1` — źródło: linie 442-447.

Kryterium ukończenia: topic istnieje w Kafce.

Taski:
- [x] **Dodaj `spring-kafka` (4.1.x) do POM backendu.**
      `verify: ./mvnw -f backend -q compile` → PASS (2026-07-14; `spring-boot-starter-kafka` zarządza `spring-kafka` 4.1.0 i auto-konfiguracją).
- [x] **Zdefiniuj jeden topic**, `payment.lifecycle.events.v1`, 1 partycja (Iteracja 0 nie potrzebuje więcej).
      `verify: podman compose -f infra/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -Fx payment.lifecycle.events.v1` → PASS (2026-07-14).

## Story 4.2 — Dyspozytor outbox (scheduled poller)

status: done
depends_on: [Story 4.1, EPIC-01-postgresql-foundation/Story 1.3]

Opis: `@Scheduled` poller publikujący nieopublikowane wiersze outbox — źródło: linie 449-463.

Kryterium ukończenia: wiersz outbox trafia do realnej Kafki w ≤5s, `published_at` ustawione.

Taski:
- [x] **Napisz `@Scheduled` poller** (co 2s dla Iteracji 0 — CDC-relay w stylu Debezium to ulepszenie z późniejszej iteracji, nie wymóg walking skeleton), czytający nieopublikowane wiersze `payment.outbox_events`, publikujący do Kafki, oznaczający `published_at`.
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=OutboxDispatcherTest` → PASS (2026-07-14; real PostgreSQL+Kafka, ≤5s, key=aggregate ID, publish confirmation przed `published_at`).

## Story 4.3 — Szkielet konsumenta inbox

status: done
depends_on: [Story 4.2]

Opis: `@KafkaListener` z dedupem przez inbox — źródło: linie 465-468.

Kryterium ukończenia: powtórzone dostarczenie eventu aktualizuje wiersz dokładnie raz.

Taski:
- [x] **Napisz `@KafkaListener`** konsumujący `payment.lifecycle.events.v1`, dedupujący przez `payment.inbox_events` (unikalne na id źródłowego eventu — redelivered message = bezpieczny no-op), aktualizujący minimalny wiersz odczytowy.
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=InboxConsumerIdempotencyTest` → PASS (2026-07-14; event opublikowany dwa razy, jeden inbox row, status `RECEIVED`→`VALIDATED`, drugi delivery zalogowany jako duplicate).

## Defekty planningu

- `[PLANNING-DEFECT]` „Minimalny wiersz odczytowy” nie jest zdefiniowany przez source ani ADR-N5. Zachowano najmniejszy poprawny efekt: consumer ustawia tenant GUC z payloadu i wykonuje dozwolone przejście istniejącego `payment.payments` z `RECEIVED` do `VALIDATED`; unique `payment.inbox_events.source_event_id` gwarantuje zastosowanie tylko raz.
