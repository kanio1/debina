---
status: blocked
depends_on: [EPIC-09-ownership-schema-grants, EPIC-04-outbox-inbox-kafka-thin]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-7, line 1263); sepa-nexus-blueprint-ownership-integration.md §9 (line 351, wersja z ADR-N8: generacja z §3.7 v2, trzy topiki terminalne)"
---

# EPIC-15 — Ownership: własność topiców Kafka (EPIC-OWN-7)

Generowane z §3.7 v2 głównego blueprintu (jedyne źródło prawdy, ADR-N8) — żaden dokument patch nie definiuje lokalnie własnej tabeli topiców.

`[PLANNING-DEFECT 2026-07-14, znaleziony i naprawiony w tej sesji]`: podczas budowy tego epika odkryto, że rzeczywisty, działający topic Kafka z EPIC-04 (`payment.lifecycle.events.v1`) **nie odpowiada żadnej nazwie z zamrożonej tabeli §3.7 v2** — dokładnie ten "topic name appearing anywhere else that is not in this table is a documentation defect — [EVENT-RISK]" z opisu §3.7. Kanoniczna nazwa dla eventu produkowanego przez `payment-lifecycle` to `payment.validated`. Za zgodą użytkownika: **przemianowano** `PaymentLifecycleTopicConfig.TOPIC` na `payment.validated` (jedna stała, wszystkie odwołania przez `OutboxDispatcher`/`InboxConsumer`/testy używają tej stałej, nie literału — zero innych miejsc do zmiany). Pełny regres backendu (30/30) i realny smoke-test przeciw żywej infrastrukturze (real Keycloak token → POST → outbox → Kafka → inbox → `VALIDATED`) potwierdzone po zmianie.

`[PLANNING-DEFECT/BUG, znaleziony i naprawiony w tej sesji, niezwiązany z powyższym rename]`: podczas weryfikacji rename'u przez żywą infrastrukturę odkryto, że **żaden prawdziwy `@KafkaListener`/consumer group nigdy nie działał** przeciw `infra/docker-compose.yml`'owemu Kafce — broker (KRaft, jeden węzeł) nie mógł utworzyć wewnętrznego topicu `__consumer_offsets` (domyślny `replication.factor=3` niemożliwy przy jednym brokerze → `INVALID_REPLICATION_FACTOR`, cyklicznie w logu). To pre-istniejący defekt infrastruktury z EPIC-01, niewykryty wcześniej, bo dotychczasowa weryfikacja end-to-end (EPIC-08 `WalkingSkeletonIntegrationTest`) używa Kafki z Testcontainers (inne domyślne ustawienia), nie długo-działającego `docker-compose`'a. Naprawione: `infra/docker-compose.yml` → dodano `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"` i `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR/MIN_ISR: "1"`. Po `podman compose up -d kafka` (przetworzenie kontenera) i restarcie backendu: prawdziwy smoke-test (realny token Keycloak → `POST /api/v1/payments` → `payment.validated` → `InboxConsumer` → status `VALIDATED`) przeszedł przeciw żywej infrastrukturze, potwierdzone w bazie i logach (zero błędów).

## Story 15.1 — Generacja AsyncAPI z §3.7 v2

status: done
depends_on: []

Taski:
- [x] **Skrypt/proces generujący specyfikację AsyncAPI wyłącznie z §3.7 v2 tabeli topiców (main blueprint), nie z lokalnej kopii.**
      `verify: test -f infra/asyncapi/asyncapi.yaml && grep -q "generated from §3.7 v2" infra/asyncapi/asyncapi.yaml` → PASS (2026-07-14). Nowy `infra/asyncapi/generate.py` parsuje **bezpośrednio** tabelę §3.7 v2 z `sepa-nexus-message-flow-and-data-blueprint.md` (regex po nagłówku sekcji + nagłówku tabeli, nie ręcznie przepisana kopia — spełnia dosłownie `[FREEZE]` "generated from this table, never authored independently") i emituje `infra/asyncapi/asyncapi.yaml` (AsyncAPI 3.0, 28 kanałów). Zweryfikowano jako poprawny YAML (`python3 -c "import yaml; yaml.safe_load(...)"`) i ręcznie sprawdzono, że nazwy "retired" (`reconciliation.completed`, `reconciliation.mismatch.detected`) NIE pojawiają się jako kanały (poprawnie pominięte, bo tabela już je zastępuje inną nazwą w tym samym wierszu). Dwie realne poprawki parsera podczas budowy: (1) markdown backticks/`**bold**`/`[ADD]`/`[RENAME from ...]` musiały być usuwane ZE WSZYSTKICH komórek poza kolumną priorytetu, gdzie same są danymi (`[MVP]`/`[P1]`/`[P2]`) — pierwsza wersja skryptu zostawiała surowe backticki w kluczach YAML; (2) `**normal domain ingress/CSM paths only**` (markdown bold) w kolumnie consumers renderowało się jako YAML alias (`*...`), łamiąc parsowanie — naprawione przez usunięcie `**` i cytowanie wszystkich elementów listy `x-consumers`.

## Story 15.2 — Jeden producent na topic + testy kontraktowe

status: done
depends_on: [Story 15.1]

Taski:
- [x] **Test kontraktowy: schemat producenta zgodny z §3.7 v2 dla każdego topicu.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=KafkaTopicContractTest` → PASS (2026-07-14). Nowy `backend/src/test/java/com/sepanexus/modules/paymentlifecycle/event/KafkaTopicContractTest.java`: parsuje wygenerowany `infra/asyncapi/asyncapi.yaml` (bez dodatkowej zależności YAML — proste, deterministyczne parsowanie linii, bo plik jest maszynowo generowany przez nasz własny skrypt) i sprawdza, że `PaymentLifecycleTopicConfig.TOPIC` (`payment.validated`) istnieje w katalogu z `x-producer-owner: payment-lifecycle`. **Nie-próżność potwierdzona**: tymczasowe przywrócenie starej nazwy `payment.lifecycle.events.v1` → `BUILD FAILURE` (EXPECTED FAIL, nazwa nie w katalogu), przywrócono `payment.validated` → `BUILD SUCCESS`, pełny regres 30/30 — PASS.
      `[PLANNING-DEFECT]`: dziś istnieje tylko jeden prawdziwy producent (`payment-lifecycle`), więc test kontraktowy sprawdza tylko ten jeden wpis — pełna macierz "jeden producent na topic" dla pozostałych 27 topiców w katalogu czeka na moduły, które je faktycznie produkują (EPIC-19+ i dalej).

## Story 15.3 — Trzy topiki terminalne (ADR-N8 delta)

status: blocked
depends_on: [Story 15.1]

Opis: `egress.dead_lettered`, `egress.manual_intervention_required`, `reconciliation.run.failed` — dodane przez ADR-N8 do §3.7 v2.

`[PLANNING-DEFECT 2026-07-14, rozstrzygnięte]`: task wymaga potwierdzenia "konsumentów operacyjnych" tych trzech topiców, ale ich producenci (`egress`, `reconciliation`) **nie istnieją jeszcze w kodzie** (oba `not-started` w `planning/README.md`). Tworzenie samych pustych topiców Kafka bez żadnego producenta/konsumenta za nimi nie dowodzi niczego realnego i byłoby pustą infrastrukturą — nie zaimplementowano.

**Status `blocked`** (jedyna wartość dozwolona przez `.claude/skills/epic-story-task-catalog/SKILL.md` dla tego przypadku — nie ma osobnej wartości "deferred"). `depends_on` (Story 15.1) jest spełnione; blokerem jest brak `egress`/`reconciliation` w kodzie. Docelowy epik: wykonać razem z pierwszymi taskami modułów `egress` (EPIC-EGRESS-1.., Faza 2+) i `reconciliation` (EPIC-RECON-1..), gdy te moduły faktycznie zaczną istnieć.

Taski:
- [ ] **Potwierdź istnienie trzech topiców terminalnych w katalogu i ich konsumentów operacyjnych.**
      `verify: docker exec <kafka-container> kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -E "egress.dead_lettered|egress.manual_intervention_required|reconciliation.run.failed"` — `NOT RUN`, świadomie odłożone (patrz `[PLANNING-DEFECT]` wyżej).

## Story 15.4 — Event type/topic/producer/consumer semantic contract

status: not-started
depends_on: [Story 15.1, Story 15.2]

Opis: a typed, source-backed catalog binds each actually produced payment event to the frozen §3.7
topic, producer, consumers and aggregate key. Source: `sepa-nexus-message-flow-and-data-blueprint.md`
§3.7, ADR-N8, and `DEBINA-GAP-RISK-BACKLOG.md` KAFKA-GAP-001.

Kryterium ukończenia story: no dispatcher chooses one hard-coded topic for every outbox row; known
events resolve to their catalog entry, unknown events remain unpublished, and the generated AsyncAPI
contract detects a semantic mismatch.

Taski:
- [ ] **Implement the catalog and non-vacuous semantic contract test for the currently produced payment events.**
      `verify: ./mvnw -f backend test -Dtest=*KafkaEventSemanticContractTest*`
