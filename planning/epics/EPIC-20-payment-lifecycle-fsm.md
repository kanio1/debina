---
status: in-progress
depends_on: [EPIC-19-ingress-staging-pipeline]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CORE-1, line 1248)"
---

# EPIC-20 — Payment Lifecycle: FSM (EPIC-CORE-1)

## Story 20.1 — Tabela przejść + guardy

status: done
depends_on: []

Taski:
- [x] **Zaimplementuj tabelę przejść FSM `Payment`/`PaymentLifecycle` z guardami zgodnie z modelem statusów w main blueprincie.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=PaymentFsmTransitionTest` → `Tests run: 4, Failures: 0` — PASS (2026-07-14). Nowa `backend/src/main/java/com/sepanexus/modules/paymentlifecycle/domain/PaymentTransitionTable.java` (mapa `Map<PaymentStatus, Set<PaymentStatus>>` dozwolonych przejść, dokładnie wzorzec "FSM jako dane" z §3.5: "a forbidden pair throws IllegalTransition") + `IllegalPaymentTransitionException`. `PaymentEntity.markValidated()` przepisane: było ciche `if (status==RECEIVED) status=VALIDATED` (no-op na nielegalnym przejściu), teraz **rzuca** na nielegalnym przejściu zamiast cicho nic nie robić. Zakres oparty o **istniejący** 4-wartościowy `PaymentStatus` (RECEIVED/VALIDATED/REJECTED/DISPATCHED) z Iteracji 0 — pełny model `business_status_code` z §4.3 (RECEIVED/VALIDATED/ACCEPTED/ROUTED/SETTLED/REJECTED/RETURNED...) należy do przebudowy tabeli `payments` zaplanowanej w EPIC-11 ("cienki wiersz payments"), nie tego story. Test dowodzi nie-próżności: RECEIVED→VALIDATED legalne; VALIDATED→VALIDATED (self-loop) nielegalne i rzuca; REJECTED→VALIDATED (cofnięcie ze stanu terminalnego) nielegalne; DISPATCHED (terminalny) nie ma żadnych wyjść. Pełny regres (40/40, w tym `InboxConsumerIdempotencyTest`/`WalkingSkeletonIntegrationTest`, które wywołują `markValidated()` przez prawdziwy przepływ Kafka) niezmieniony — potwierdza, że jedyne realne wywołanie w produkcji (`InboxConsumer`, dokładnie raz na unikalny event dzięki dedup) pozostaje legalne.

## Story 20.2 — Konsument Kafka + inbox

status: done
depends_on: [Story 20.1, EPIC-04-outbox-inbox-kafka-thin]

Opis: testy duplikatów/kolejności.

`[PLANNING-DEFECT 2026-07-14]`: połowa "duplikaty" tego story **już istnieje i jest już przetestowana** — `InboxConsumer` (EPIC-04) i `InboxConsumerIdempotencyTest` już dowodzą dokładnie tego: `@KafkaListener` na `payment.validated` z dedupem przez `payment.inbox_events` (unikalny `source_event_id`), z realnym testem wysyłającym ten sam event dwukrotnie przez prawdziwą Kafkę (Testcontainers) i potwierdzającym `inboxCount==1`/status zmienia się dokładnie raz. Re-implementowanie tego byłoby czystą duplikacją — nie zrobiono tego ponownie. Połowa "kolejności" (out-of-order) **nie ma dziś realnej treści do przetestowania** — przez ten topic płynie dziś dokładnie JEDEN typ eventu cyklu życia na płatność (submitted→validated), więc nie ma sekwencji wielu zdarzeń do przestawienia w złej kolejności; napisanie testu "out-of-order" teraz byłoby fabrykowaniem próżnego testu. Odblokuje się naturalnie, gdy przybędzie drugi typ eventu cyklu życia (np. status z routing/settlement).

Taski:
- [x] **`@KafkaListener` na eventach cyklu życia z dedupem przez `payment.inbox_events`, testy duplikatu i out-of-order.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=InboxConsumerIdempotencyTest` → `Tests run: 1, Failures: 0` — PASS (2026-07-14, pre-istniejące pokrycie z EPIC-04, potwierdzone ponownie w tej sesji). Test "out-of-order" świadomie nie napisany — patrz `[PLANNING-DEFECT]` wyżej.

## Story 20.3 — Korelacja status-inbound (G4)

status: blocked
depends_on: [Story 20.1, EPIC-27-iso-correlation-engine/Story 27.2]

Opis: lookup przez `iso.payment_iso_identifiers`, orphan → DLQ.

`[CORRECTED 2026-07-16 — dual-agent governance/backlog-redesign session, H3]`: `depends_on` was `EPIC-26-iso-message-lineage-core`, with this file's own note claiming "`depends_on` już wskazuje realny bloker: EPIC-26... jest `not-started`." Both parts of that claim are now stale: `EPIC-26` is mostly `done` (Stories 26.1–26.3), and `EPIC-26`'s own file explicitly says the real blocking capability for this story is `EPIC-27` (correlation engine), not `EPIC-26` itself — quoted verbatim there: *"literalne `depends_on: EPIC-26` jest spełnione, ale rzeczywista zdolność potrzebna do korelacji statusu przychodzącego ... należy do `EPIC-27`."* Repointed at `EPIC-27-iso-correlation-engine/Story 27.2` specifically (the 9-step correlation policy that actually produces matchable correlation results), not the whole epic (which also includes the `[P1]` manual-correlation half, Story 27.5, irrelevant to this story). `EPIC-27` remains `not-started` — this story is still genuinely blocked, just on the correct dependency now.

Taski:
- [ ] **Korelacja statusu przychodzącego przez `iso.payment_iso_identifiers`; brak dopasowania → DLQ, nie cichy no-op.**
      `verify: ./mvnw -f backend test -Dtest=*StatusInboundCorrelationTest*` — `NOT RUN`, `blocked` do `EPIC-27-iso-correlation-engine/Story 27.2`.
