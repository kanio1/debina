# HANDOFF

## Zadanie

Repozytorium SEPA Nexus: dokumentacja + kod syntetycznej, deterministycznej platformy SEPA/ISO 20022 służącej jako poligon nauki Playwright/TypeScript/architektury enterprise dla Senior QA/SDET. Backend to Spring Modulith monolit (Spring Boot 4.1, JDK 25); planning/epiki w `/planning/`.

## Zrobione

Sesja `EGRESS OWNERSHIP, OUTBOX ISOLATION AND ARTIFACT TAXONOMY TRAIN` (2026-07-17), kontynuacja tego samego dnia po sesji `LEDGER TRANSACTION, INVARIANT AND OWNERSHIP TRAIN` (lokalny `main` startował na `800721c`, `main == origin/main`, worktree czysty poza pre-existing `build/generated-spring-modulith/javadoc.json`). Siedmiofazowy plan: **Phase A** (baseline) → **Phase B** (audyt i korekta ukrytych zależności egress) → **Phase C** (`EPIC-18` Story 18.2) → **Phase D** (`EPIC-18` Story 18.3) → **Phase E** (`EPIC-14` Story 14.2) → **Phase F** (`EPIC-44` Story 44.3) → **Phase G** (`EPIC-45` Story 45.3). `PUSH/FETCH/PULL: FORBIDDEN`, `ACT: FORBIDDEN`, nowe migracje dozwolone WYŁĄCZNIE dla Story 18.2 — honorowane.

**Phase A**: baseline potwierdzony zielony — pełna regresja `303/303 PASS`, `BUILD SUCCESS` (log poza repo).

**Phase B — audyt capability graph**: pięć realnych korekt zależności/statusów, wszystkie zapisane bezpośrednio w plikach epików:
- `EPIC-14` Story 14.2: zawężone z `[Story 14.1]` (transytywnie blokowana przez `EPIC-46`/`EPIC-47`, oba `not-started`) do `EPIC-43/Story 43.1` — własny test story potrzebuje tylko `egress_role` + `payment.payments`, nigdy `transport_attempts`/`delivery_receipts`.
- `EPIC-18` Story 18.2 i 18.3: zawężone z `[Story 18.1]` (kryterium "każdy przyszły moduł", nigdy nieukończalne) do trzech konkretnych, już `done` capabilities (`EPIC-01/Story 1.3`, `EPIC-27/Story 27.2C`, `EPIC-43/Story 43.1`) — oba testy są dynamiczne (odkrywają aktualne outboxy/role), więc automatycznie obejmą przyszłe moduły bez ponownego zawężania.
- `EPIC-44` Story 44.3: zawężone z `[Story 44.1]` (`[CAPABILITY-BLOCKED]`) do `EPIC-43/Story 43.1` — czysty katalog Java nie potrzebuje `egress_profile`/DB.
- `EPIC-45` Story 45.3: zawężone z `[Story 45.1]` (`[CAPABILITY-BLOCKED]`) do `EPIC-43/Story 43.1` — `[SHARED CAPABILITY]`, dispatcher już istnieje i jest mutation-proofed.
- `EPIC-44` Story 44.1: **`[PLANNING-DEFECT]` znaleziony i skorygowany** — task/`verify:` odwoływały się do `egress.egress_profile`, ale §6.8 jednoznacznie mówi "a `reference-data` catalog row" — poprawiono na `reference_data.egress_profiles`. Story pozostaje `[CAPABILITY-BLOCKED]` — `retry_policy`/`allowed_artifact_types`/model wersjonowania nie mają rozstrzygniętego kształtu DDL.
- `EPIC-43` Story 43.2: pełny audyt ukrytych zależności — `[CAPABILITY-BLOCKED]`. Odkryto nierozstrzygnięty konflikt źródeł §6.4 (`outbound_messages.signature bytea` bezpośrednio na tabeli transportu) vs §6.7 (rendering na `iso.iso_outbound_artifacts`, "no field overlap" z transportem) — 7 otwartych pytań spisanych w epic file.
- `EPIC-45` Story 45.1: `[CAPABILITY-BLOCKED]` — gałąź `RENDERED→SIGNED` vs `RENDERED→CLAIMED_FOR_DELIVERY` zależy od `signing_required` z nieistniejącego profile snapshot.
- `EPIC-45` Story 45.2: `[CAPABILITY-BLOCKED]` transytywnie + `[PLANNING-OVERLAP]` z `EPIC-43` Story 43.2 (ten sam łańcuch render→sign→deliver opisany w dwóch epikach — zapisane, żeby przyszła sesja nie zbudowała go dwukrotnie).

**Phase C — `EPIC-18` Story 18.2**: audyt potwierdził `outbox_dispatcher_role` miał granty WYŁĄCZNIE na `egress.outbox_events` — zero na `payment.*`/`iso.*`. Utworzono `V28__outbox_dispatcher_role_grants.sql` (`GRANT USAGE ON SCHEMA payment/iso` + `GRANT SELECT, UPDATE (published_at)` na oba outboxy — nigdy INSERT/DELETE/TRUNCATE, nigdy tabela domenowa; aplikacyjny dispatcher świadomie NIETKNIĘTY). `OutboxDispatcherNoDomainWriteSweepTest` (dynamiczne odkrycie wszystkich outboxów + pełny metadata sweep `has_table_privilege` przez wszystkie tabele domenowe + realne negatywne INSERT na 6 reprezentatywnych tabelach) + `OutboxDispatcherGrantsMigrationUpgradePathTest`. `23/23 PASS`. Mutation-proof 5/5.

**Phase D — `EPIC-18` Story 18.3**: `CrossModuleOutboxWriteDeniedTest` — writer-role registry zbudowany ze źródła (`payment`+`iso` = ta sama rola `sepa_app`, świadomie wyłączone z macierzy negatywnej — `iso-adapter` jeszcze nie ma własnej roli, `EPIC-10` zablokowana; `egress` = `egress_role`, jedyna realna granica cross-module). `16/16 PASS`. **Realne odkrycie**: pierwsza wersja mutacji 4 (`UPDATE(published_at)` bez `SELECT`) nie została złapana — PostgreSQL wymaga `SELECT` na kolumnach w `WHERE` niezależnie od grantu `UPDATE` — naprawione dodaniem `SELECT` do mutacji. Mutation-proof 4/4 po poprawce.

**Phase E — `EPIC-14` Story 14.2**: `EgressCannotWritePaymentStatusTest` — `has_schema_privilege` (czysty sygnał grantowy) + realne SELECT/INSERT/UPDATE/DELETE/TRUNCATE odrzucone. `6/6 PASS`. **RLS-vs-grant distinction udokumentowana**: `payment.payments` ma `FORCE ROW LEVEL SECURITY`, więc nawet błędny grant byłby dodatkowo zablokowany przez RLS — warstwa grantowa potwierdzona NIEZALEŻNIE przez metadata-only test. Mutation-proof 3/3.

**Phase F — `EPIC-44` Story 44.3**: `com.sepanexus.egress.internal.artifact` — czysty, niemutowalny katalog Java (`OutboundArtifactType`/`ArtifactRendererOwner`/`ArtifactPriority`/`TriggerName`/`ArtifactTriggerDefinition`/`ArtifactTriggerCatalog`), dokładnie 8 wierszy §6.9. `TriggerName` świadomie neutralny (nie deklaruje KAFKA_EVENT/INTERNAL_DOMAIN_EVENT) — sprawdzone przez `infra/asyncapi/asyncapi.yaml`, część triggerów to zarejestrowane topiki, część czysta proza źródła, brak jednoznacznej klasyfikacji dla WSZYSTKICH wpisów. Structural RED → GREEN, `8/8 PASS`. Mutation-proof 5/5.

**Phase G — `EPIC-45` Story 45.3**: `[SHARED CAPABILITY]` z `EPIC-43` Story 43.1 — bez reimplementacji, `DoubleDispatcherTest` ponownie zweryfikowany `5/5 PASS` po dodaniu `V28` (brak regresji/interakcji).

**Database review** (skill `sepa-nexus-database-review`): werdykt **PASS**, brak blocking findings (raport pełny: `/tmp/sepa-nexus-egress-ownership-train/database-review.md`, poza repo).

**Planning zaktualizowany**: `planning/README.md` (EPIC-14/18/43/44/45), `planning/epics/EPIC-{14,18,43,44,45}-*.md`, `planning/story-inventory.json` (regenerowany, `279 stories`). `planning/capabilities.yaml`/`capability-graph.json`/`.mmd` **nietknięte** — wszystkie dotknięte epiki poza `EPIC-43` to węzły `epic-only` (nie `story-backed`), a moje zawężenia zostały wewnątrz istniejących krawędzi epic-level, zgodnie z udokumentowanym podziałem granularności (`BACKLOG-REDESIGN.md`). Wszystkie 5 walidatorów shell/Python: PASS. `git diff --check`: czyste. JSON valid.

**Commity tej sesji**: 4 commity implementacyjne (Phase C/D/E/F, każdy osobno — Phase G nie dała nowego kodu, Phase B dała tylko planning) + 1-2 planning/HANDOFF. `build/generated-spring-modulith/javadoc.json` nigdy w żadnym commicie.

## Utknęliśmy na

Nic nie blokuje bieżącej pracy tej sesji. Otwarte pytania architektoniczne (wymagają decyzji użytkownika/zespołu, nie do rozstrzygnięcia samodzielnie):

1. **`EPIC-44` Story 44.1** (`reference_data.egress_profiles`): jaki jest dokładny model kolumn dla `retry_policy` (jsonb? osobne kolumny `max_attempts`/`backoff_strategy`? child table?), `allowed_artifact_types` (array? child table?) i wersjonowania profilu (`egress_profile_snapshots` — jaki dokładnie kształt "profile + reference-data version")?
2. **`EPIC-43` Story 43.2** (renderer + signer): konflikt §6.4 (`outbound_messages.signature bytea` bezpośrednio na tabeli transportu) vs §6.7 (rendering na osobnej `iso.iso_outbound_artifacts`, "no field overlap") — który dokument obowiązuje, czy oba dla różnych klas artefaktów? Kto implementuje `ArtifactRenderPort`? Jaka biblioteka/wersja Prowide?
3. **`EPIC-45` Story 45.1** (pełny FSM): zależy od odpowiedzi na (1) — `signing_required` z profilu decyduje o gałęzi `RENDERED→SIGNED` vs `RENDERED→CLAIMED_FOR_DELIVERY`.

Bez odpowiedzi na (1), `EPIC-44` Story 44.2, `EPIC-45` Story 45.1/45.2 i `EPIC-43` Story 43.2 pozostają `[CAPABILITY-BLOCKED]`.

## Plan na następny krok

Otwórz `planning/epics/EPIC-44-egress-profile-artifact-taxonomy.md`, Story 44.1 — przeczytaj otwarte pytanie o model `retry_policy`/`allowed_artifact_types`/wersjonowania i zdecyduj z użytkownikiem/zespołem (nowy ADR albo uzupełnienie `sepa-nexus-message-flow-and-data-blueprint.md` §6.8 o konkretny DDL). To odblokowuje jednocześnie `EPIC-44` Story 44.2 i `EPIC-45` Story 45.1. Równolegle, niezależnie: `planning/epics/EPIC-43-egress-rail-outbound-dispatch.md` Story 43.2's siedem otwartych pytań (§6.4 vs §6.7 konflikt) wymaga osobnej decyzji. Do tego czasu następna analitycznie `READY` praca to `EPIC-32` Story 32.2/32.4 (ledger reserve/post/release + reversal) — również `[CAPABILITY-BLOCKED]` na inny, niezależny otwarty problem (patrz poprzednia sesja) — więc REALNIE żadna duża nowa vertical slice nie jest dziś `READY` bez jednej z powyższych decyzji użytkownika. Rozważ zamiast tego mniejsze, niezależne sprzątanie: `EPIC-18` Story 18.4 (dedup inbox + replay-safe, `depends_on: [Story 18.1]` — sprawdzić czy da się analogicznie zawęzić jak 18.2/18.3).

## Pułapki, których nie wolno powtórzyć

- **Column-scoped `UPDATE(column)` grant testy MUSZĄ też przyznać `SELECT`, jeśli test używa `WHERE` po innej kolumnie** — PostgreSQL wymaga `SELECT` na każdej kolumnie czytanej w `WHERE`/`RETURNING`, niezależnie od grantu `UPDATE` na kolumnie docelowej. Mutacja, która przyznaje TYLKO `UPDATE(col)` bez `SELECT`, może "zostać złapana" przez test z niewłaściwego powodu (brak `SELECT`, nie brak zamierzonego `UPDATE`) — sprawdź to explicite przy projektowaniu mutation-proof dla każdego column-scoped grantu, nie zakładaj że sam `UPDATE`-mutation wystarczy.
- **RLS z `FORCE ROW LEVEL SECURITY` maskuje warstwę grantową** — jeśli tabela ma `FORCE RLS` i test nigdy nie ustawia właściwego GUC, KAŻDY hipotetyczny błędny grant zostanie i tak zablokowany przez RLS. To dobra, dodatkowa obrona — ale żeby udowodnić, że warstwa GRANTOWA sama w sobie jest poprawna (nie tylko RLS), potrzebny jest osobny test czytający WYŁĄCZNIE metadane uprawnień (`has_schema_privilege`/`has_table_privilege`), którego RLS nie dotyczy.
- **Gdy story ma dependency na epik, którego kryterium ukończenia jest "każdy przyszły X" (nigdy formalnie nieukończalne)** — sprawdź, czy WŁASNY test/`verify:` tej story faktycznie potrzebuje pełnego zakresu epika, czy tylko aktualnie istniejących instancji. Jeśli test jest dynamiczny (odkrywa stan na żywo), zawęź zależność do konkretnych, już `done` capabilities zamiast czekać na formalnie nigdy-nieukończalny epik.
- **Gdy dwa dokumenty źródłowe opisują tę samą tabelę z bezpośrednio sprzecznymi polami (tu: §6.4 vs §6.7 dla `outbound_messages`/`iso_outbound_artifacts`)** — nie zgaduj, który "wygrywa" tylko po dacie/numerze patcha, jeśli oba są wciąż aktywne w dokumencie i nie ma jawnego "supersedes". Zapisz jako otwarte pytanie z konkretnymi, wymienionymi opcjami, nie rozstrzygaj samodzielnie.
- Pułapki z poprzednich sesji (niezmienione, wciąż aktualne): `build/generated-spring-modulith/javadoc.json` regeneruje się przy KAŻDYM `./mvnw test` — zapisać PRZED pierwszym testem sesji, przywrócić PRZED każdym commitem; `GRANT ... ON <table>` bez wcześniejszego `GRANT USAGE ON SCHEMA` jest cichym no-opem; `act`+Testcontainers = zawsze błąd, nie używać; scratch migracje do mutation-proof (np. `V29__temp_mutation_test.sql`) muszą być USUNIĘTE (nie tylko treściowo cofnięte) przed commitem, żeby nie zostawić nieużywanego pliku.
