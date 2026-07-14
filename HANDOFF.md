# HANDOFF

## Zadanie

SEPA Nexus. Ta sesja (CACHE-STABLE EXECUTION CONTRACT v5): Etap A (potwierdzenie baseline'u — bez pełnego re-audytu, zgodnie z instrukcją, bo `pom.xml`/`package.json`/lockfile niezmienione od poprzedniej sesji), Etap B (rozstrzygnięcie TypeScript — **realna korekta błędu poprzedniej sesji**), Etap C (formalne domknięcie Fazy 1 przez skorygowanie statusów na zgodne z `.claude/skills/epic-story-task-catalog/SKILL.md`), Etap D (wybór i wykonanie trzech epików Iteracji 1 — Payment Spine: EPIC-19, EPIC-20, EPIC-21).

## Zrobione

### Etap B — TypeScript: realna korekta błędu

Poprzednia sesja twierdziła "TypeScript nigdy nie wydał stabilnego 6.0 GA" i zostawiła pin na `5.9.3`. **To było błędne.** Bezpośrednie sprawdzenie `npm view typescript versions --json` / `npm view typescript dist-tags --json` pokazuje: `6.0.2` i `6.0.3` są prawdziwymi, nie-prerelease, opublikowanymi wersjami GA (odróżnialne od `6.0.0-beta`/`6.0.0-dev.*`/`6.0.1-rc`, które są prerelease). Tag `latest` po prostu przeszedł już na `7.0.2` — stąd błąd poprzedniej sesji (sprawdzała prawdopodobnie tylko `latest`). Kompatybilność potwierdzona przed pinowaniem: `typescript-eslint@8.64.0` (najnowszy, faktycznie używany) deklaruje `typescript: ">=4.8.4 <6.1.0"` (obejmuje `6.0.3`); `eslint-config-next@16.2.10` wymaga tylko `>=3.3.1`; `next` nie ma w ogóle peer dependency na `typescript`. Żadna z breaking changes TS 6.0 (usunięcie `moduleResolution: classic`, wymuszony ESM interop) nie dotyczy `tsconfig.json` tego projektu (już `"bundler"`/`true`). **Przypięto `typescript: "6.0.3"`** — zgodnie z zamrożonym baseline'em "TypeScript 6.x — exact pin". Pełna regresja czysta: `pnpm exec tsc --version` → `6.0.3`; `lint`/`typecheck`/`build` bez błędów; `pnpm audit` → 0 podatności. `frontend/README.md` i `docs/dependency-audit-2026-07-14.md` skorygowane (nie: nowy raport, tylko poprawka błędnego stwierdzenia + dodanie sekcji o pinowaniu).

### Etap C — Reconciliation checkpoint: Faza 1 formalnie domknięta

Odkryto, że `.claude/skills/epic-story-task-catalog/SKILL.md` dopuszcza **wyłącznie** cztery wartości statusu: `not-started | in-progress | blocked | done` — "deferred" (użyte przeze mnie w poprzedniej sesji jako `not-started (deferred)`) **nie jest** dozwoloną wartością. Skorygowano: `EPIC-12` Story 12.2 i `EPIC-15` Story 15.3 → `blocked` (nie "deferred"). Dodatkowo znaleziono i skorygowano ukryte blokery nieujęte w `depends_on`: `EPIC-16` (GraphQL/`reporting` nie istnieją), `EPIC-17` (moduł `simulation` ma zero kodu), `EPIC-18` (brak kolejnych modułów z outbox/inbox) — wszystkie trzy ustawione na `blocked` z udokumentowanym uzasadnieniem w plikach epików. **Faza 1 formalnie zamknięta w `planning/README.md`**: `EPIC-09` w pełni `done`; pozostałe osiem epików mają dziś realny zakres strukturalnie zależny od modułów z późniejszych faz (iso-adapter, FSM, ledger, egress, reconciliation, simulation, reporting, GraphQL) — zgodnie z zasadą "nie traktuj jako blokady elementów zależnych od nieistniejącego jeszcze modułu późniejszej fazy". Przejście do Iteracji 1 odblokowane.

### Etap D — trzy epiki Iteracji 1 (Payment Spine)

**Kandydaci ocenieni**: EPIC-19 (odblokowany, EPIC-08 done), EPIC-20/21 (odblokowane przez EPIC-19 w tej samej sesji), EPIC-22 (odblokowany, ale cross-cutting infra, nie core domain thread), EPIC-23 (odblokowany, ale polityka "backend przed UI"), EPIC-25 (odblokowany, ale Story 25.2 explicite buduje na EPIC-19). Wybrano **EPIC-19 → EPIC-20 → EPIC-21** — spójny, w pełni uzasadniony fragment: ingress→lifecycle→identifier-lineage.

**EPIC-19 — Ingress: staging pipeline → `in-progress`.** Story 19.1 (REST JSON submit + idempotencja JSON_DIRECT) **done**: nowe schematy `ingress`/`iso` (migracje `V10`/`V11`), `IdempotencyStore`/`JdbcIdempotencyStore` (PG18 dwukrokowy `INSERT...ON CONFLICT DO NOTHING`+`SELECT`), `RawMessageArchive`, `JsonDirectLineageRecorder`. `PaymentController` wymaga teraz `Idempotency-Key` (6 dotykających testów zaktualizowanych). Nowy `JsonDirectIngestionTest` (Testcontainers): ten sam klucz+treść → ten sam `paymentId`; ten sam klucz+inna treść → 409; identyfikatory w `iso.payment_iso_identifiers`. Story 19.3 (hartowanie XML) **done**: `HardenedXmlFactory` + `XmlHardeningTest` dowodzący realnego odrzucenia XXE i billion-laughs (widoczny `[Fatal Error]` w logu, nie cichy no-op). Story 19.2 (signature-before-parse) **blocked** — `EPIC-31-signature-module` nie istnieje. Story 19.4 (REST XML pain.001) **blocked** — wymaga pełnego `CanonicalMapper` iso-adapter, zbyt duże zadanie na tę sesję, świadomie odłożone.

**EPIC-20 — Payment Lifecycle: FSM → `in-progress`.** Story 20.1 (tabela przejść + guardy) **done**: `PaymentTransitionTable` (mapa dozwolonych przejść, rzuca `IllegalPaymentTransitionException` zamiast cichego no-op), wpięta w `PaymentEntity.markValidated()`. `PaymentFsmTransitionTest` dowodzi nie-próżności (self-loop VALIDATED→VALIDATED odrzucony, REJECTED→VALIDATED odrzucony, DISPATCHED bez wyjść) — pełny regres (41/41) potwierdza, że jedyne realne wywołanie produkcyjne (`InboxConsumer`, dokładnie raz dzięki dedup) pozostaje legalne. Story 20.2 (Kafka consumer + inbox) **done** — połowa "duplikaty" już w pełni pokryta przez pre-istniejący `InboxConsumer`/`InboxConsumerIdempotencyTest` z EPIC-04 (nie duplikowano); połowa "out-of-order" świadomie nie napisana — nie ma dziś drugiego typu eventu cyklu życia do przestawienia w złej kolejności, napisanie testu byłoby fabrykowaniem próżnego przypadku. Story 20.3 (korelacja status-inbound) **blocked** — `EPIC-26-iso-message-lineage-core` nie istnieje.

**EPIC-21 — Refaktor identyfikatorów ISO → `in-progress`.** Story 21.1 (schemat `iso.payment_iso_identifiers`) **done** — już zbudowana w EPIC-19 Story 19.1 z dokładnie wymaganym kluczem złożonym, potwierdzone ponownie `\d` przeciw żywej bazie. Story 21.3 (lineage per `source_message_type`) **done**: nowy `LineageBySourceMessageTypeTest` dowodzi, że dwa niezależne wiersze identyfikatorów (JSON_DIRECT + symulowany camt.056) współistnieją dla jednej płatności bez kolizji klucza złożonego. Story 21.2 (usunięcie identyfikatorów z `payments`) **blocked** — `end_to_end_id` jest dziś aktywnie używany przez unikalny indeks współbieżnościowy, duplicate-check w kodzie, ORAZ read-model frontendu jednocześnie; bezpieczne usunięcie wymaga nowego projektu (tenant-scoped uniqueness na `iso.*`, przepięcie read-modelu) — świadomie odłożone jako zbyt ryzykowne do zrobienia pospiesznie pod koniec budżetu tej sesji.

**Finalny pełny regres (2026-07-14):** `./mvnw -f backend test` → `41/41`. `pnpm run lint/typecheck/build` → czyste (TypeScript 6.0.3). `act` backend+frontend+`-l` → PASS. `podman compose ps` → wszystkie trzy kontenery `Up`. `git diff --check`/`git status --short` → czyste.

## Zmienione/nowe pliki tej sesji

- `frontend/package.json` (`typescript: 6.0.3`), `frontend/README.md`, `docs/dependency-audit-2026-07-14.md` (korekta TypeScript)
- `planning/README.md`, `planning/epics/EPIC-{12,15,16,17,18}-*.md` (statusy `blocked` skorygowane; Faza 1 formalnie zamknięta)
- `planning/epics/EPIC-{19,20,21}-*.md` (nowe stories done/blocked)
- Nowe migracje: `backend/src/main/resources/db/migration/ingress/V10__*.sql`, `.../iso/V11__*.sql`
- Nowy kod: `modules/paymentlifecycle/ingress/{IdempotencyStore,JdbcIdempotencyStore,IdempotencyClaim,RawMessageArchive,HardenedXmlFactory}.java`, `.../isoadapter/JsonDirectLineageRecorder.java`, `.../domain/{PaymentTransitionTable,IllegalPaymentTransitionException}.java`, `.../service/IdempotencyConflictException.java`
- Zmienione: `PaymentController`/`PaymentService`/`SubmitPaymentCommand`/`PaymentProblemHandler`/`PaymentEntity` (wymagany `Idempotency-Key`, idempotencja, FSM guard)
- Zaktualizowane testy (6 plików): `PaymentControllerTest`, `PaymentControllerErrorTest`, `SecurityConfigTest`, `PaymentAuthorizationTest`, `PaymentServiceTest`, `WalkingSkeletonIntegrationTest`
- Nowe testy: `JsonDirectIngestionTest`, `XmlHardeningTest`, `PaymentFsmTransitionTest`, `LineageBySourceMessageTypeTest`

Nieśledzone, nie moje: `.claude/skills/impeccable/` (istniało na wejściu, nie ruszane).

## Utknęliśmy na

Nigdzie technicznie. Świadomie odłożone (nie blokady, uzasadnione w plikach epików):
- EPIC-19: Story 19.2 (signature module), Story 19.4 (CanonicalMapper pain.001) — realnie zbyt duże na jedną sesję.
- EPIC-20: Story 20.3 (koreluje ze status-inbound, wymaga EPIC-26).
- EPIC-21: Story 21.2 (usunięcie `end_to_end_id` z `payments` — realny redesign dotykający read-model + współbieżność, celowo nie robiony pod presją czasu).

## Plan na następny krok

1. Otwórz `planning/README.md` na nowo. Iteracja 1 (Faza 2) ma teraz trzy epiki `in-progress` (19/20/21) z jasno udokumentowanymi blokerami per story.
2. Naturalny następny krok: **EPIC-22 — Tożsamość i czas: cross-cutting** (w pełni odblokowany, `depends_on: EPIC-02, EPIC-03`, oba `done`) — zwłaszcza Story 22.3 (`ClockPort` + reguła ArchUnit `zero Instant.now() poza portem`), bo to dokładnie luka odnotowana w EPIC-09 Story 9.4 jako "czeka na przyszły epik".
3. Alternatywnie: dokończ zablokowane story z EPIC-19/20/21, jeśli `EPIC-31` (signature), `EPIC-26` (iso lineage core), lub decyzja o read-model redesign (Story 21.2) pojawią się jako możliwe do wykonania.
4. **Nie zakładaj** który epik jest "kolejny" bez ponownego sprawdzenia `planning/README.md` — ten plan może się zmienić.

Backend/frontend dev servery nieuruchomione na koniec tej sesji (tylko testy). Postgres/Keycloak/Kafka (Podman) powinny wciąż działać — zweryfikuj przez `podman compose -f infra/docker-compose.yml ps`.

## Pułapki, których nie wolno powtórzyć

- **Zawsze zweryfikuj twierdzenie poprzedniej sesji o "braku wersji X" bezpośrednio w źródle, zanim je powtórzysz.** Poprzednia sesja błędnie stwierdziła "brak TypeScript 6.0 GA" sprawdzając prawdopodobnie tylko `dist-tags.latest` (który już wskazywał 7.0.2) — pełna lista wersji (`npm view <pkg> versions --json`) pokazała, że 6.0.2/6.0.3 istnieją i są stabilne. Nie kopiuj ustaleń z HANDOFF-a bez re-weryfikacji, jeśli coś wygląda na "brak czegoś, co powinno istnieć".
- **Planning ma dokładnie cztery dozwolone statusy: `not-started|in-progress|blocked|done`** (`.claude/skills/epic-story-task-catalog/SKILL.md`) — nigdy "deferred" ani żadna adnotacja w nawiasie jako osobna wartość statusu. Użyj `blocked` + notatkę w treści pliku dla uzasadnienia.
- **Rozszerzanie istniejącego, już-przetestowanego REST endpointu o nowy wymagany nagłówek (np. `Idempotency-Key`) ma szeroki blast radius** — sprawdź WSZYSTKIE testy dotykające tego endpointu (`grep -rln` po nazwie endpointu/metody) przed zmianą kontraktu, nie tylko te oczywiste.
- **`iso`/`ingress` nie są jeszcze osobnymi modułami Spring Modulith** — kod dla nich żyje w podpakietach wewnątrz istniejącego `payment-lifecycle` (`.ingress`/`.isoadapter`), `sepa_app` pozostaje jedynym writerem tych nowych schematów. Rzeczywisty rozdział modułowy to przyszły epik ownership, nie coś do zrobienia przy okazji budowania samej funkcjonalności.
- Zanim usuniesz kolumnę/pole używane przez read-model LUB duplicate-check LUB indeks unikalności jednocześnie (jak `payments.end_to_end_id`) — to nie jest "rozszerzenie o kilka linii", to redesign wymagający dedykowanej uwagi na współbieżność i regresję UI, nawet jeśli epik nazywa to "refaktorem".
- Wszystkie pułapki z poprzednich sesji (Podman nie Docker, `DOCKER_HOST` dla Testcontainers, trzy dodatkowe flagi `act`+Podman+SELinux+Ryuk, RLS GUC empty-zero-rows, Next.js env gettery leniwe, `pnpm`/Node 24.18.0 — domyślny `node` w PATH tej sesji był `24.15.0`, trzeba `export PATH="/home/suso/.nvm/versions/node/v24.18.0/bin:$PATH"` jawnie, `pnpm` samo się rozwiązuje poprawnie niezależnie od PATH node — Kafka jednobrokerowa KRaft potrzebuje `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` inaczej żaden `@KafkaListener` nie działa przeciw żywej infrastrukturze, `psql` niedostępny lokalnie — `podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus`) wciąż obowiązują.
