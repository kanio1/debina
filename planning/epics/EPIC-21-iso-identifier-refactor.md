---
status: done
depends_on: [EPIC-19-ingress-staging-pipeline]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CORE-2, line 1249)"
---

# EPIC-21 — Refaktor identyfikatorów ISO (EPIC-CORE-2)

Przeniesienie identyfikatorów z `payments` do `iso.payment_iso_identifiers` (G4 — ISO identity nie może być spłaszczone w stan biznesowy).

## Story 21.1 — Schemat + migracja `iso.payment_iso_identifiers`

status: done
depends_on: []

`[PLANNING-DEFECT 2026-07-14]`: ta tabela **już powstała w EPIC-19** (Story 19.1, migracja `V11__iso_json_direct_lineage.sql`) — dokładnie z kluczem złożonym `(payment_id, source_message_type, iso_message_id)` wymaganym tutaj. Nie tworzono drugiej migracji; ten task jest już spełniony przez istniejący stan bazy, potwierdzone ponownie w tej sesji.

Taski:
- [x] **Migracja tworząca `iso.payment_iso_identifiers` z kluczem złożonym `(payment_id, source_message_type, iso_message_id)`.**
      `verify: podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus -c "\d iso.payment_iso_identifiers"` → `PRIMARY KEY, btree (payment_id, source_message_type, iso_message_id)` — PASS (2026-07-14, przeciw realnej, długo działającej bazie po zastosowaniu `V10`/`V11` przez `flyway:migrate`).

## Story 21.2 — Przekierowanie zapytań korelacyjnych

status: done
depends_on: [Story 21.1]

`[DECISION 2026-07-15, dwa źródła w konflikcie — rozstrzygnięte przez użytkownika]`: implementacja tego story wymagała rozstrzygnięcia realnego konfliktu między dwoma dokumentami źródłowymi:

- `sepa-nexus-iteration-0-foundation-plan.md` (walking-skeleton spec, jawnie "no signature, no ISO lineage yet") jawnie definiuje `UNIQUE INDEX payments_tenant_e2e_idx ON payment.payments(tenant_id, end_to_end_id)` i `DuplicatePaymentException` — to jest źródło pochodzenia obu mechanizmów w kodzie, nie ad-hoc wynalazek wcześniejszej sesji.
- `sepa-nexus-message-flow-and-data-blueprint.md` §4.3 (v2 patch, **wskazany jako `source:` tego epika**) jawnie mówi: *"the old `pay_corr` unique index on (tenant_id, end_to_end_id) is REMOVED — correlation now lives entirely in `iso.payment_iso_identifiers`... a payment can be found by any ISO id it ever carried."* Zastępczy indeks tam to `pii_e2e` — zwykły, NIEUNIKALNY indeks wyszukiwawczy.

Zapytany wprost, użytkownik rozstrzygnął na rzecz nowszego blueprintu (autorytatywne źródło tego epika), z doprecyzowaniem: `EndToEndId` jest identyfikatorem ISO lineage/korelacji, **nie** kluczem unikalności płatności. Nie budowano żadnego zastępczego unikalnego indeksu i **nie dodano** `tenant_id` do `iso.payment_iso_identifiers` wyłącznie po to, by odtworzyć starą unikalność — dokładnie zgodnie z jawną instrukcją. Bezpieczeństwo ponowień pozostaje wyłącznie zadaniem `Idempotency-Key` (`ingress.idempotency_keys`); dwa niezależne requesty (różne `Idempotency-Key`) z tym samym biznesowym `EndToEndId` tworzą teraz **dwie odrębne płatności** — to jest zamierzone zachowanie, przetestowane wprost (patrz niżej), nie regresja.

**Zbudowane (expand/backfill → switch writes → switch reads → contract, bez fazy "expand" — nie było potrzeby dodawania kolumn, patrz decyzja wyżej):**
- **Backfill** (`V17__backfill_missing_payment_identifiers.sql`): 5 z 14 wierszy `payment.payments` na realnej, długo działającej bazie nie miało odpowiadającego wiersza `iso.payment_iso_identifiers` (przeddaty Story 19.1/`JsonDirectLineageRecorder` — wczesne smoke-testy EPIC-04/06/08). Backfillowane z ich istniejącej wartości `end_to_end_id`, `source_message_type='JSON_DIRECT'`. Zero konfliktów (potwierdzone zapytaniem przed migracją: brak płatności z więcej niż jednym wierszem identyfikatorów). Zweryfikowane: `0` sierot po backfillu.
- **Contract** (`V18__drop_payments_end_to_end_id.sql`): `DROP INDEX payment.payments_tenant_e2e_idx`, `ALTER TABLE payment.payments DROP COLUMN end_to_end_id`, `CREATE INDEX pii_e2e ON iso.payment_iso_identifiers (end_to_end_id)` (nieunikalny, dokładnie jak w §4.3). Zweryfikowane na świeżej bazie Testcontainers ORAZ na realnej `infra_postgres_1` (`flyway:migrate` 16→17→18, `\d payment.payments` potwierdza brak kolumny, `\d iso.payment_iso_identifiers` potwierdza `pii_e2e`).
- **Kod usunięty**: `PaymentEntity.endToEndId` (pole, kolumna, getter, parametr konstruktora), `PaymentRepository.existsByTenantIdAndEndToEndId`, `DuplicatePaymentException` (cała klasa + handler RFC7807 + jedyny dedykowany test `PaymentControllerErrorTest`, usunięty w całości — testował wyłącznie usunięty mechanizm).
- **Read model przepięty na `iso.payment_iso_identifiers`/`iso.message_lineage`** (główny `EndToEndId` = identyfikator z wiersza `lineage_role='ORIGINAL_INSTRUCTION'`, deterministyczne, nigdy pierwszy-z-brzegu): nowe `IsoIdentifierLookup.findPrimaryEndToEndId`/`findPrimaryEndToEndIds` (batch, bez N+1 dla listy). `PaymentService.visiblePayments`/`paymentDetail` zwracają teraz `PaymentSummary`/`PaymentDetail` (nowe pola `endToEndId`), nie `PaymentEntity` bezpośrednio. Brak głównego identyfikatora → `MissingPrimaryIdentifierException` (500, bez nazwy tabeli w odpowiedzi) — nie maskowane jako `"UNKNOWN"`/`"-"`, zgodnie z zasadą "brak identyfikatora to naruszenie invariant po backfillu".
- **JSON contract niezmieniony**: `PaymentSummaryResponse`/`PaymentDetailResponse` nadal mają pole `endToEndId` o tej samej nazwie — frontend nie wymagał żadnej zmiany (`pnpm run lint`/`typecheck`/`build` — wszystkie PASS, zero zmian w `frontend/`).
- **9 plików testowych zaktualizowanych** (SQL fixtures referencujące usuniętą kolumnę): `TenantGucIntegrationTest`/`PaymentAuthorizationTest` (fixture teraz wstawia też wiersze `iso.iso_messages`/`iso.payment_iso_identifiers`/`iso.message_lineage`, asercje przez `PaymentSummary::endToEndId`/`PaymentDetail::endToEndId`), `SchemaGrantMatrixTest`/`ReferenceDataOwnershipTest`/`NonSignatureRoleCannotWriteSignatureTest`/`InboxConsumerIdempotencyTest` (kolumna po prostu usunięta z INSERT — nie testowały nic związanego z `EndToEndId` samym w sobie), `PaymentsRlsTest`/`BranchLevelRlsTest` (asercje przepięte z `end_to_end_id`-jako-proxy na bezpośrednie `tenant_id`/`branch_id` — dokładniejszy test tego, co RLS faktycznie sprawdza), `WalkingSkeletonIntegrationTest` (zapytania przepięte na JOIN przez `iso.payment_iso_identifiers`, sama ścieżka HTTP niezmieniona), `PaymentControllerTest`/`PaymentServiceTest`/`PaymentFsmTransitionTest` (sygnatury/konstruktory).
- **Nowe testy dowodzące zamierzonego zachowania** (`JsonDirectIngestionTest.twoDifferentIdempotencyKeysWithSameEndToEndIdCreateTwoPayments`, `Pain001SubmissionEndpointTest.twoDifferentIdempotencyKeysWithSameEndToEndIdCreateTwoPayments`): dwa różne `Idempotency-Key`, ten sam biznesowy `EndToEndId` → dwie odrębne płatności, każda z własnym `iso_message_id`/`ORIGINAL_INSTRUCTION` lineage/outbox event (żaden nie współdzielony).

Taski:
- [x] **Wszystkie zapytania korelacyjne przepięte na nową tabelę, usunięte pola identyfikatorów z `payment.payments`.**
      `verify: pnpm run build (frontend) && ./mvnw -f backend test` → backend `118/118 PASS` (było `117/117` na wejściu tej sesji — 117 + 2 nowe testy − 1 usunięty (`PaymentControllerErrorTest`, testował wyłącznie usunięty mechanizm) = 118); frontend `lint`/`typecheck`/`build` wszystkie PASS bez żadnej zmiany kodu frontendowego — PASS (2026-07-15). Migracja `V17`/`V18` zweryfikowana na świeżej bazie Testcontainers i na realnej długo działającej `infra_postgres_1` (`flyway:migrate` 16→18).

## Story 21.3 — Test lineage per `source_message_type`

status: done
depends_on: [Story 21.1]

Taski:
- [x] **Test: lineage pacs.002/R-message poprawny per `source_message_type`.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=LineageBySourceMessageTypeTest` → `Tests run: 1, Failures: 0` — PASS (2026-07-14). Nowy `backend/src/test/java/com/sepanexus/modules/paymentlifecycle/isoadapter/LineageBySourceMessageTypeTest.java`: prawdziwe zgłoszenie JSON_DIRECT (przez `PaymentService`), następnie symulowany drugi wiersz `iso.iso_messages`/`iso.payment_iso_identifiers` dla `source_message_type='camt.056'` na TĘ SAMĄ płatność (odzwierciedlający przyszły R-message/recall, którego prawdziwy producent — `EPIC-26`/`iso-adapter` — jeszcze nie istnieje) — dowodzi, że klucz złożony `(payment_id, source_message_type, iso_message_id)` genuinie pozwala dwóm niezależnym wierszom identyfikatorów współistnieć dla jednej płatności bez kolizji, dokładnie zgodnie z modelem "message lineage, not flattened state" z §4.3. Prawdziwe pacs.002/R-message correlation (9-step policy, `iso.iso_message_correlation`) czeka na `EPIC-26`/`EPIC-ISO-2` — ten test dowodzi poprawności modelu danych, nie silnika korelacji.
