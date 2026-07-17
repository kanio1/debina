# HANDOFF

## Zadanie

Sesja `MULTI-EPIC-INFRASTRUCTURE-SETTLEMENT-GUARD-EGRESS-QUEUE-LEDGER-OWNERSHIP-TRAIN` (2026-07-17, nowa sesja Claude Code CLI, branch `main`). `EXPECTED_LOCAL_HEAD_PREFIX: d4dc89b` — potwierdzony dokładnie. Worktree czysty poza jedną zgodną z oczekiwaniem, pre-existing modyfikacją `build/generated-spring-modulith/javadoc.json` (regeneruje się przy każdym `./mvnw test`, chroniona i przywrócona do stanu początkowego przed każdym commitem tej sesji — patrz "Pułapki"). `main == origin/main` na starcie (użytkownik wypchnął poprzednią sesję poza tym ciągiem sesji, zgodnie z jawnym zastrzeżeniem promptu "stan remote mógł zostać zmieniony poza sesją").

Pięciofazowy "pociąg capabilities": **Phase A** (odzyskanie stabilnego baseline Testcontainers/Podman) → **Phase B** (`EPIC-35` Story 35.2) → **Phase C** (`EPIC-43` Story 43.1) → **Phase D** (`EPIC-32` Story 32.1) → **Phase E** (`EPIC-13` Stories 13.1+13.3). Każda faza wymagała zamknięcia własnego gate'u (targeted+mutation-proof+architecture+full regression) przed przejściem do następnej. `PUSH: FORBIDDEN`, `FETCH: FORBIDDEN`, `PULL: FORBIDDEN`, `ACT: ABSOLUTELY FORBIDDEN`, `DATABASE_MIGRATIONS: ALLOWED ONLY IN PHASE C AND D` — wszystkie honorowane. **Wszystkie pięć faz ukończone.**

## Zrobione

### Phase A — Testcontainers/Podman baseline recovery

**Diagnoza:** poprzednia sesja pozostawiła `JsonDirectIngestionTest`'s `ContainerLaunchException`/broken-pipe (4/4 reprodukcji w sumie, licząc poprzednią sesję) jako `INFRASTRUCTURE BLOCKED`, nierozwiązany. Ta sesja znalazła i zweryfikowała rzeczywistą przyczynę: pojedynczy współdzielony `DockerClient`/pula połączeń HTTP do gniazda rootless-Podman, reużywana przez ~60+ sekwencyjnych operacji na kontenerach w jednym długo działającym forku JVM — okazjonalnie łamie się (broken pipe) przy tworzeniu KOLEJNEGO kontenera.

**Hipoteza 1 (odrzucona)**: manualny `isolatedPostgres.stop()` w `IsoOutboxTopicRoutingTest` (jedyne miejsce w całym repo z ręcznym mid-suite stop kontenera) zaburza pulę połączeń. Usunięty, uruchomiony pełny przebieg → nadal `5 errors` w `JsonDirectIngestionTest`, identyczny stack trace. Cofnięty (`git diff` czyste).

**Naprawa (zweryfikowana)**: `.withStartupAttempts(3)` na `JsonDirectIngestionTest`'s kontenerze Postgres — oficjalny, udokumentowany mechanizm odporności Testcontainers na dokładnie tę klasę przejściowej awarii (nie ręczna pętla retry). Mutation-proof: cofnięcie do `withStartupAttempts(1)` odtworzyło dokładnie oryginalny błąd w pełnym przebiegu (`5 errors`, identyczny), przywrócenie do `3` dało 3. czysty przebieg z rzędu.

**Gate A**: 3 kolejne czyste pełne przebiegi (`251/251` każdy) + 1 przebieg reprodukujący oryginalny błąd bez naprawy = mocny, empiryczny dowód przyczynowości.

**Commit**: `fe854c7 test(infrastructure): stabilize Testcontainers full-suite lifecycle` (1 plik: `JsonDirectIngestionTest.java`).

### Phase B — EPIC-35 Story 35.2 (`NoProfileNameSwitchTest`)

**Readiness**: PASS — Story 35.1 done, resolver istnieje, brak DB/Kafka.

**Rzeczywisty invariant**: ArchUnit nie czyta semantyki `if`/`switch` ani literałów String — reguła wymuszona strukturalnie na granicy API: (1) żadna publiczna metoda w klasie `*Resolver`/`*Selector` w `com.sepanexus.settlement` nie przyjmuje `String`/`CharSequence`; (2) moduł nie zależy od typu modelu profilu; (3) brak klas nazwanych per-CSM.

**Fixtures** (non-vacuous, `com.sepanexus.architecturefixtures.settlementselection.{forbidden,allowed,testprofile}`): `StringProfileSelector`, `CsmNameSelector`, `ProfileDependentSelector`, `TipsSettlementEngine` (forbidden — muszą być wykryte); `TypedPairSelector` (allowed — musi przejść).

**Structural/semantic RED**: test napisany przed fixtures (referencje pakietów jako stringi, nie symbole — brak błędu kompilacji, za to prawdziwy semantic RED: "failed to check any classes"). Pierwsza wersja reguły String/CharSequence fałszywie wykrywała JDK-generowane `enum.valueOf(String)` — naprawiona przez zawężenie do klas `*Resolver`/`*Selector`. **Złapany i naprawiony błąd we WŁASNYM harnessie testowym**: `assertRuleViolated` początkowo łapał TEŻ wyjątek "brak dopasowanych klas" (ten sam typ co prawdziwe naruszenie) jako fałszywy PASS — naprawiony, żeby wymagać dokładnie komunikatu "Architecture Violation".

**GREEN**: `7/7 PASS`. **Mutation-proof, 3/3 złapane i cofnięte**: (1) wyłączenie sprawdzania String/CharSequence → dokładnie `forbiddenFixtureSelectorsAcceptingStringOrCharSequenceAreDetected` FAIL; (2) reguła zależności od profilu wskazana na nieistniejący pakiet → dokładnie `forbiddenFixtureDependencyOnProfileModelIsDetected` FAIL; (3) reguła odrzucająca DOWOLNY parametr → `allowedFixtureTypedPairSelectorAcceptsNoStringOrCharSequence` FAIL (+ incydentalnie produkcyjny test też, poprawnie).

**Pełna regresja**: `258/258` (251+7), BUILD SUCCESS.

**Commit**: `4991773 test(architecture): forbid profile-named settlement selection` (7 plików).

### Phase C — EPIC-43 Story 43.1 (`egress.outbound_messages` + `SKIP LOCKED`)

**Readiness**: PASS. Źródłowy audyt: `outbound_messages` NA PEWNO potrzebuje RLS (§4.7's tabela jawnie wymienia `outbound_messages` jako "Tenant-facing operational — Adopt (two-level)"), plus ADR-N5 wymaga pary `egress.outbox_events`/`inbox_events` przy pierwszej migracji modułu. Rozbieżność źródeł co do słownika stanów (§6.4 starszy, 5-wartościowy vs §6.2 nowszy, 11-wartościowy) rozstrzygnięta na korzyść §6.2 (bardziej szczegółowy, `CLAIMED_FOR_DELIVERY` dosłownie pasuje do terminologii tej story).

**Moduł**: `com.sepanexus.egress` (top-level, `allowedDependencies={}`). Migracje V22 (schemat+`egress_role`+`outbox_dispatcher_role` utworzona PIERWSZY RAZ w tym repo), V23 (`outbound_messages`, RLS dwupoziomowe + polityka `dispatcher_claim`/`system_relay`), V24 (ADR-N5 para). `OutboundMessageDispatcher.claimPendingBatch`: JEDNO atomowe zapytanie CTE (`SELECT...FOR UPDATE SKIP LOCKED` + `UPDATE`), nigdy dwa oddzielne.

**Złapany bug**: `outbox_dispatcher_role` brakowało `GRANT USAGE ON SCHEMA egress` — bez tego grant na tabeli był nieosiągalny.

**Test-first**: `DoubleDispatcherTest` napisany przed `OutboundMessageDispatcher` → structural RED (`cannot find symbol`) → GREEN po implementacji. **Ważne odkrycie o niedeterministyczności**: próba "zmutuj dispatcher, uruchom ponownie TEN SAM test współbieżności" okazała się zawodna W OBIE STRONY (3 uruchomienia z rzędu bez mutacji: część przeszła, część nie — realna, ale zbyt wąska okazja czasowa zależna od szeregowania wątków przez OS; SKIP LOCKED może POPRAWNIE zapobiec nakładaniu się, jeśli oba SELECT trafią do bazy wystarczająco blisko siebie w czasie). Rozwiązanie: dedykowany, w pełni deterministyczny test (`splittingSelectAndUpdateAcrossSeparateStatementsAllowsADoubleClaim`) wymuszający dokładnie niebezpieczną kolejność sekwencyjnie (bez wątków, bez zależności od szczęścia szeregowania OS).

**GREEN**: `DoubleDispatcherTest` 5/5, `EgressOwnershipTest` 6/6, `EgressMigrationUpgradePathTest` 1/1 = 12/12. **Mutation-proof, 4/4 złapane i cofnięte**: brak SKIP LOCKED, split SELECT/UPDATE (przez dedykowany deterministyczny test), brak predykatu stanu, `outbox_dispatcher_role` z grantem na tabelę domenową.

**Pełna regresja**: `270/270` (258+12), BUILD SUCCESS.

**Commit**: `d5c7366 feat(egress): add skip-locked outbound dispatch foundation` (8 plików).

### Phase D — EPIC-32 Story 32.1 (fundament schematu `ledger`)

**Readiness**: PASS. **Konflikt modelu reversal rozstrzygnięty**: pierwotny tekst Story 32.1 wymieniał `ledger.ledger_reversals` (skopiowany z gołych wzmianek nazw tabel w tabelach własności dokumentu ownership-integration — NIGDY nie poparty rzeczywistym DDL). Kanoniczny blueprint §4.5 (jawnie oznaczony "[PATCH v2, deep-research applied]" — NAJNOWSZA, najbardziej autorytatywna wersja) definiuje reversal WYŁĄCZNIE jako nowy wiersz `journal_entries` z `entry_type='REVERSAL'` + `reversal_of_entry_id`. **`ledger.ledger_reversals` NIE utworzona** — zapisana jako poprawka planningu w pliku epika, nie zgadywanie.

**Partycjonowanie**: §4.5 sugeruje `journal_lines ... PARTITION BY RANGE (at)`, ale repo ma już DOKŁADNY, udokumentowany precedens dla tej samej sytuacji (`payment.payment_events`/V19, `ingress.raw_inbound_messages`/V10 — obie świadomie NIE partycjonowane, bo brak źródłowej strategii cyklu życia partycji). `journal_lines` podąża identycznym precedensem, z identycznego powodu — nie `[CAPABILITY BLOCKED]`.

**Moduł**: `ledger` schemat + dedykowana `ledger_role` (V25), `liquidity_accounts`/`journal_entries`/`journal_lines`/`balance_snapshots` (V26) dokładnie wg §4.5 DDL. Deferred constraint trigger `check_entry_balance()` wymusza Σ(`amount_minor`)=0 na COMMIT. Granty dokładnie wg §4.7's przykładu SQL: `journal_entries`/`journal_lines` = SELECT+INSERT tylko, NIGDY UPDATE/DELETE; `liquidity_accounts` = SELECT+UPDATE (bez INSERT — provisioning poza zakresem tej story). **BRAK RLS** na żadnej tabeli bazowej ledger (§4.7) — zweryfikowane bezpośrednim odczytem `pg_class.relrowsecurity`, nie tylko wnioskowaniem z zachowania grantów.

**GREEN**: `LedgerSchemaMigrationTest` 7/7 (na pierwszy raz — migracje napisane przed testem w tej sesji, odstępstwo od ścisłego test-first, odnotowane uczciwie), `LedgerMigrationUpgradePathTest` 1/1. **Mutation-proof, 4/4 złapane i cofnięte**: obcy grant INSERT (pierwsza próba była no-opem z powodu brakującego `GRANT USAGE ON SCHEMA` — użyteczne odkrycie o defense-in-depth, mutacja poprawiona), `ledger_role` z UPDATE na `journal_lines`, osłabiony trigger balansu, włączone RLS.

**Pełna regresja**: `278/278` (270+8), BUILD SUCCESS.

**Commit**: `64650f5 feat(ledger): add append-only ledger schema foundation` (4 pliki).

### Phase E — EPIC-13 Stories 13.1 + 13.3

**Readiness**: PASS — `EPIC-32` Story 32.1 done w tej samej sesji. `settlement_role` NIE istniał nigdzie w repo (moduł `com.sepanexus.settlement` z Phase B to czysty resolver Java, bez schematu/roli DB) — utworzony PIERWSZY RAZ (V27), z celowo ZEREM grantów (nawet `USAGE` na schemacie) — dokładnie ten sam wzorzec co `outbox_dispatcher_role` w Phase C.

**Story 13.1** (`LedgerSchemaOwnershipTest`): `ledger_role` legalnie wstawia do `journal_entries` (jedyna tabela, na której faktycznie ma INSERT — `liquidity_accounts` celowo nie); `sepa_app`, `settlement_role`, `egress_role`, `outbox_dispatcher_role`, obca rola — wszystkie odrzucone `42501`, realny `INSERT` przeciw prawdziwemu PostgreSQL 18 Testcontainer, nie asercja metadanych.

**Story 13.3** (`SettlementRoleNoLedgerGrantTest`): `settlement_role` odrzucony na INSERT/UPDATE/DELETE/TRUNCATE/SELECT wobec `ledger.*` — SELECT też odrzucony (fail-closed, brak źródła przyznającego odczyt).

**Złapany bug testowy**: pierwsza wersja obu klas próbowała wstawiać/seedować `liquidity_accounts` jako `ledger_role`, który NIE MA tam grantu INSERT (tylko SELECT+UPDATE, dokładnie zgodnie ze źródłem) — naprawione: `journal_entries` dla testu pozytywnego, `test_admin` (superuser) do seedowania.

**GREEN**: `LedgerSchemaOwnershipTest` 6/6, `SettlementRoleNoLedgerGrantTest` 6/6 = 12/12. **Mutation-proof, 3/3 złapane i cofnięte**: `settlement_role` z INSERT, `settlement_role` z SELECT+UPDATE, INNA obca rola (`egress_role`) z INSERT.

**Pełna regresja**: `290/290` (278+12), BUILD SUCCESS.

**Commit**: `e0b12bd test(database): enforce ledger schema ownership` (3 pliki).

### Finalizacja

Wszystkie 6 walidatorów planningu/governance: PASS. `git diff --check`: czyste. Dwa końcowe pełne przebiegi regresji (`/tmp/multi-epic-final-regression-1.log`, `-2.log`): oba `290/290, BUILD SUCCESS`.

**Commit planningu**: `5afd95a chore(planning): record multi-epic execution train results` (9 plików: `planning/README.md`, `capability-graph.json/.mmd`, `story-inventory.json`, epiki EPIC-13/18/32/35/43).

**Razem 6 commitów tej sesji** (limit z promptu: max 6, dokładnie wykorzystany): `fe854c7`, `4991773`, `d5c7366`, `64650f5`, `e0b12bd`, `5afd95a`. Żaden nie zawiera `build/generated-spring-modulith/javadoc.json` (zweryfikowane `git log --oneline -- <plik>` per commit).

## Utknęliśmy na

Nic. Wszystkie pięć faz w pełni ukończone i zweryfikowane.

## Plan na następny krok

1. **`EPIC-43` Story 43.2** (renderer Prowide + `SignatureSigningPort`) — analitycznie `READY` (obie zależności `done`), celowo NIE rozpoczęta (poza zakresem tej sesji).
2. **`EPIC-32` Story 32.2** (`LedgerPort.reserve/post/release`, real concurrency + no-double-reserve Testcontainers).
3. **`EPIC-32` Story 32.3** (deferred balance trigger już zbudowany w 32.1 — ale dedykowany `UnbalancedEntryAtCommitTest`/immutability-focused test dla SAMEJ Story 32.3 wciąż nie napisany osobno; sprawdzić czy 32.1's `LedgerSchemaMigrationTest` już wystarczająco pokrywa kryterium tej story czy wymaga własnego).
4. **`EPIC-13` Story 13.2** (`settlement` woła wyłącznie `LedgerPort`) — wymaga najpierw `LedgerPort` (32.2).
5. **`EPIC-18` Story 18.1** pozostaje formalnie nieukończona — `iso-adapter`/`routing`/`settlement`/`reconciliation`/`case` wciąż bez własnych par outbox/inbox; `payment.outbox_events` bez retrofitu dispatcher-role.
6. Push `main` — nadal `FORBIDDEN` w tej sesji, nadal nie wykonany. Lokalny `main` teraz `5afd95a`, 6 commitów przed `origin/main` (`d4dc89b`).

## Pułapki, których nie wolno powtórzyć

- **`build/generated-spring-modulith/javadoc.json` regeneruje się przy KAŻDYM `./mvnw test`** — musi być zapisany PRZED pierwszym testem sesji (`cp` do `/tmp`) i PRZYWRÓCONY dokładnie przed KAŻDYM commitem (nie tylko na końcu) — inaczej zostanie przypadkowo zacommitowany albo zostawiony w niedeterministycznym stanie między fazami.
- **Testcontainers "zmutuj kod, uruchom TEN SAM test współbieżności, oczekuj FAIL" nie zawsze jest wiarygodne dla realnych race'ów zależnych od szeregowania wątków przez OS** — może nie złapać mutacji (SKIP LOCKED poprawnie zapobiega nakładaniu, jeśli oba SELECT-y trafią blisko siebie w czasie) LUB złapać ją tylko czasami. Dla TAKICH konkretnych scenariuszy (rozdzielenie SELECT+UPDATE między transakcjami) zbuduj dedykowany, w pełni sekwencyjny/deterministyczny test wymuszający dokładną, niebezpieczną kolejność — nie polegaj na szczęściu wątków.
- **Sprawdź DOKŁADNIE, który grant ma dana rola, zanim napiszesz test/seed używający tej roli** — `ledger_role` ma INSERT na `journal_entries`/`journal_lines`, ale TYLKO SELECT+UPDATE na `liquidity_accounts` (żadnego INSERT, dokładnie zgodnie ze źródłem §4.7) — próba INSERT jako `ledger_role` do `liquidity_accounts` (nawet do SEEDOWANIA danych testowych) zawsze zwróci 42501, niezależnie od tego, czy to "powinno" być dozwolone.
- **`GRANT ... ON <table> TO <role>` bez wcześniejszego `GRANT USAGE ON SCHEMA <schema> TO <role>` jest CICHYM no-opem** — rola nie zobaczy nawet istnienia tabeli. Dwukrotnie złapane w tej sesji (Phase C: `outbox_dispatcher_role`; Phase D mutation-proof: pierwsza próba mutacji nie zadziałała z tego samego powodu, sama mutacja musiała zostać poprawiona).
- **Gdy kanoniczny blueprint (najnowszy "[PATCH vN]") i dokument ownership-integration (starszy, "patch notes" do zastosowania) różnią się co do tego, czy jakaś tabela istnieje — zaufaj RZECZYWISTEMU DDL, nigdy gołej wzmiance nazwy w tabeli własności.** `ledger_reversals` istniała TYLKO jako nazwa w tabeli ownership, nigdy jako `CREATE TABLE`.
- Pułapki z poprzednich sesji (niezmienione): ArchUnit domyślnie skanuje test-classes (`DO_NOT_INCLUDE_TESTS` dla scope produkcyjnego); `act`+Testcontainers = zawsze błąd; `SECURITY DEFINER` wymaga jawnego `SET search_path`; nie ufaj własnej powłoce Bash jako dowodowi dostępu innego programu.
