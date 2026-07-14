---
status: in-progress
depends_on: [EPIC-08-walking-skeleton-verification]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-IN-1, line 1246); ADR-N7 (JSON_DIRECT)"
---

# EPIC-19 — Ingress: staging pipeline (EPIC-IN-1)

Pierwszy prawdziwy epik domenowy Iteracji 1 — rozszerza cienki submit z EPIC-06/EPIC-08 o idempotencję na poziomie domeny, signature-before-parse i kanał XML.

`[PLANNING-DEFECT 2026-07-14]`: `ingress`/`iso-adapter` nie są jeszcze osobnymi modułami Spring Modulith (istnieje tylko `payment-lifecycle` + `security`, per EPIC-09). Ten epik implementuje ich odpowiedzialności (raw archive, idempotencja, lineage JSON_DIRECT) **wewnątrz** istniejącego modułu `payment-lifecycle` (nowe podpakiety `.ingress`/`.isoadapter`), zgodnie z dosłownym brzmieniem taska ("Rozszerz `PaymentController`/`PaymentService` z EPIC-03"). `sepa_app` pozostaje jedynym writerem nowych schematów `ingress`/`iso` (tak jak już jest jedynym writerem `payment`) — one-writer-per-schema nadal prawdziwe. Rzeczywisty rozdział na osobne moduły Modulith czeka na przyszły epik ownership (analogiczny do EPIC-10, gdy `iso-adapter` faktycznie stanie się osobnym modułem).

## Story 19.1 — REST JSON submit + idempotencja (JSON_DIRECT, ADR-N7)

status: done
depends_on: []

Opis: Controller→IdempotencyStore (dwukrokowy, PG18)→IngestionService (TX)→outbox→happy-path. Realizuje ADR-N7: `JSON_DIRECT` jako seedowany pseudo-message-version, `iso.iso_messages(parse_status='SKIPPED')`.

Kryterium ukończenia: powtórzone zgłoszenie z tym samym kluczem idempotencji zwraca ten sam `paymentId`/409, identyfikatory zapisane przez `iso.payment_iso_identifiers` nawet dla JSON.

Taski:
- [x] **Rozszerz `PaymentController`/`PaymentService` z EPIC-03 o `IdempotencyStore`** (dwukrokowy zapis PG18) i tworzenie wiersza `iso.iso_messages(direction=INBOUND, message_type='JSON_DIRECT', parse_status='SKIPPED')` + `iso.payment_iso_identifiers` + `iso.message_lineage(lineage_role='ORIGINAL_INSTRUCTION')` dokładnie wg przepływu z ADR-N7.
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=JsonDirectIngestionTest` → `Tests run: 3, Failures: 0` — PASS (2026-07-14). Nowe migracje `backend/src/main/resources/db/migration/ingress/V10__ingress_raw_and_idempotency.sql` (schemat `ingress` + `raw_inbound_messages` append-only + `idempotency_keys` PG18 dwukrokowy) i `backend/src/main/resources/db/migration/iso/V11__iso_json_direct_lineage.sql` (schemat `iso` + `iso_message_versions` z seedem `JSON_DIRECT` + `iso_messages`/`payment_iso_identifiers`/`message_lineage`, minimalny wycinek pełnego 7-9-tabelowego §4.3c — reszta czeka na EPIC-10/21/EPIC-ISO-*). Nowe klasy: `IdempotencyStore`/`JdbcIdempotencyStore`/`IdempotencyClaim`/`RawMessageArchive` (pakiet `.ingress`), `JsonDirectLineageRecorder` (pakiet `.isoadapter`), `IdempotencyConflictException` (409 przez `PaymentProblemHandler`). `PaymentController` wymaga teraz nagłówka `Idempotency-Key` (wszystkie 6 dotykających go testów zaktualizowane: `PaymentControllerTest`, `PaymentControllerErrorTest`, `SecurityConfigTest`, `PaymentAuthorizationTest`, `PaymentServiceTest`, `WalkingSkeletonIntegrationTest`). Nowy `JsonDirectIngestionTest` (Testcontainers Postgres, wzorzec z `TenantGucIntegrationTest`): (1) powtórzone zgłoszenie tym samym kluczem+treścią → ten sam `paymentId`; (2) ten sam klucz, inna treść → `IdempotencyConflictException` (409); (3) `iso.payment_iso_identifiers`/`iso.message_lineage` zawierają dokładnie jeden poprawny wiersz. Pełny regres: `Tests run: 36, Failures: 0, Errors: 0` — PASS (2026-07-14).

## Story 19.2 — Filtr signature-before-parse

status: blocked
depends_on: [EPIC-31-signature-module]

Opis: kolejność egzekwowana jako test na łańcuchu filtrów, nie tylko dokumentacja (G1).

`depends_on` sam już wskazuje realny bloker: `EPIC-31-signature-module` jest `not-started`. Nie zaimplementowano — budowanie `SignatureVerificationPort` bez modułu `signature` byłoby wynajdywaniem architektury na wyrost.

Taski:
- [ ] **Wpięcie `SignatureVerificationPort` w łańcuch filtrów przed jakimkolwiek parsowaniem XML dla kanałów bankowych/plikowych.**
      `verify: ./mvnw -f backend test -Dtest=*SignatureBeforeParseOrderingTest*` — `NOT RUN`, `blocked` do `EPIC-31-signature-module`.

## Story 19.3 — Hartowanie XML

status: done
depends_on: []

Opis: konfiguracja odporna na XXE/bomby, z fixture'ami negatywnymi.

Taski:
- [x] **Skonfiguruj parser XML odporny na XXE/entity-expansion, z fixture'ami negatywnymi (XXE payload, billion-laughs).**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=XmlHardeningTest` → `Tests run: 3, Failures: 0` — PASS (2026-07-14). Nowy `backend/src/main/java/com/sepanexus/modules/paymentlifecycle/ingress/HardenedXmlFactory.java`: `DocumentBuilderFactory` z `disallow-doctype-decl=true`, zewnętrzne encje wyłączone, `FEATURE_SECURE_PROCESSING=true` (limity ekspansji encji), `XInclude`/`expandEntityReferences` wyłączone. Test `XmlHardeningTest` dowodzi nie-próżności: poprawny XML akceptowany; XXE (`SYSTEM "file:///etc/passwd"`) i billion-laughs (zagnieżdżone encje) oba odrzucone z realnym `[Fatal Error] DOCTYPE is disallowed` w logu (widoczny dowód odrzucenia, nie cichy no-op). Ta konfiguracja nie jest jeszcze wpięta w żaden endpoint REST XML (to Story 19.4, blocked) — istnieje jako gotowy, przetestowany komponent do wpięcia.

## Story 19.4 — REST XML pain.001

status: blocked
depends_on: [Story 19.3]

Opis: taksonomia błędów 422 dla nieprawidłowego pain.001.

`[PLANNING-DEFECT 2026-07-14]`: `depends_on` (Story 19.3, teraz `done`) jest spełnione, ale realizacja wymaga faktycznego mapowania ISO 20022 pain.001 XML→canonical (parser `CanonicalMapper`, per §3.8 odpowiedzialność `iso-adapter`), którego nie ma w kodzie i który jest znacznie większym zadaniem niż "podłącz hartowanie do endpointu" — to osobna, poważna zdolność (schemat pain.001, mapowanie pól, 422 taxonomy per `iso_message_validation_results`), nie rozszerzenie o kilka linii. Nie zaimplementowano w tej sesji ze względu na budżet czasu — świadomie odłożone, nie przeoczone. **Status `blocked`** — odblokuj przy dedykowanym podejściu do budowy `iso-adapter`'owego `CanonicalMapper` (prawdopodobnie razem z EPIC-10/EPIC-ISO-* epikami).

Taski:
- [ ] **Endpoint REST XML pain.001 z taksonomią błędów 422 (XML hardening result → `iso_message_parse_errors`).**
      `verify: ./mvnw -f backend test -Dtest=*Pain001XmlSubmissionTest*` — `NOT RUN`, `blocked` (patrz wyżej).
