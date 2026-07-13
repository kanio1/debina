---
status: not-started
depends_on: [EPIC-01-postgresql-foundation, EPIC-03-spring-modulith-backend-skeleton]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 4 (Story 4.1-4.3), lines 436-469"
---

# EPIC-04 — Outbox/Inbox + Kafka (cienkie)

Walidacja szkieletu event-driven (ADR-N5) wcześnie — jeden topic, jeden typ eventu, jeden producent, jeden konsument. Głębia przychodzi w późniejszych iteracjach.

## Story 4.1 — Topic Kafka i konfiguracja producenta

status: not-started
depends_on: [EPIC-00-repository-agent-foundation/Story 0.3]

Opis: jeden topic `payment.lifecycle.events.v1` — źródło: linie 442-447.

Kryterium ukończenia: topic istnieje w Kafce.

Taski:
- [ ] **Dodaj `spring-kafka` (4.1.x) do POM backendu.**
      `verify: ./mvnw -f backend -q compile` → kompiluje się czysto.
- [ ] **Zdefiniuj jeden topic**, `payment.lifecycle.events.v1`, 1 partycja (Iteracja 0 nie potrzebuje więcej).
      `verify: docker exec <kafka-container> /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep payment.lifecycle.events.v1` → topic wylistowany.

## Story 4.2 — Dyspozytor outbox (scheduled poller)

status: not-started
depends_on: [Story 4.1, EPIC-01-postgresql-foundation/Story 1.3]

Opis: `@Scheduled` poller publikujący nieopublikowane wiersze outbox — źródło: linie 449-463.

Kryterium ukończenia: wiersz outbox trafia do realnej Kafki w ≤5s, `published_at` ustawione.

Taski:
- [ ] **Napisz `@Scheduled` poller** (co 2s dla Iteracji 0 — CDC-relay w stylu Debezium to ulepszenie z późniejszej iteracji, nie wymóg walking skeleton), czytający nieopublikowane wiersze `payment.outbox_events`, publikujący do Kafki, oznaczający `published_at`.
      `verify: ./mvnw -f backend test -Dtest=OutboxDispatcherTest` → test Testcontainers: wstaw wiersz outbox bezpośrednio, poczekaj ≤5s, potwierdź konsumpcję z realnego topicu Kafka i ustawiony `published_at`.

## Story 4.3 — Szkielet konsumenta inbox

status: not-started
depends_on: [Story 4.2]

Opis: `@KafkaListener` z dedupem przez inbox — źródło: linie 465-468.

Kryterium ukończenia: powtórzone dostarczenie eventu aktualizuje wiersz dokładnie raz.

Taski:
- [ ] **Napisz `@KafkaListener`** konsumujący `payment.lifecycle.events.v1`, dedupujący przez `payment.inbox_events` (unikalne na id źródłowego eventu — redelivered message = bezpieczny no-op), aktualizujący minimalny wiersz odczytowy.
      `verify: ./mvnw -f backend test -Dtest=InboxConsumerIdempotencyTest` → opublikuj ten sam event dwukrotnie; wiersz odczytowy zaktualizowany dokładnie raz, drugie dostarczenie zalogowane jako duplikat.
