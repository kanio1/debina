---
status: done
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

status: done
depends_on: [EPIC-31-signature-module/Story 31.1, EPIC-31-signature-module/Story 31.2]

Opis: kolejność egzekwowana jako test na łańcuchu filtrów, nie tylko dokumentacja (G1).

`[PLANNING-DEFECT 2026-07-14, naprawione 2026-07-15]`: `depends_on` wcześniej wskazywał cały `EPIC-31-signature-module` w sposób tworzący pozorny cykl z `EPIC-31` Story 31.2 (która z kolei zależała od `19.2`). Naprawione na granulację story-level: `19.2` (integracja kolejności filtrów w `ingress`) zależy od `31.1` **i** `31.2`, oba teraz `done`.

`[2026-07-15 — zaimplementowane]`: `SignedChannelIngestionPipeline` (`com.sepanexus.modules.paymentlifecycle.ingress`) — nowy, minimalny komponent egzekwujący `archive → verify → parse`, wywołujący istniejący `RawMessageArchive` (Story 19.1) i `HardenedXmlFactory` (Story 19.3), oraz `SignaturePort` z modułu `signature` (nowa zależność deklarowana w `com.sepanexus.modules`' `package-info.java` — `allowedDependencies = {"shared", "signature"}`, zweryfikowana przez `ModularityTest`). `FAILED` werdykt zatrzymuje pipeline przed `HardenedXmlFactory.parse` — raw bytes archiwizowane niezależnie od werdyktu. To NIE jest pełny endpoint REST pain.001 (to Story 19.4, nadal blocked) — tylko komponent kolejności, dokładnie zakres tego story. Nie przeniesiono parsera do modułu `signature`, nie zbudowano drugiego raw archive.

Taski:
- [x] **Wpięcie `SignatureVerificationPort` w łańcuch filtrów przed jakimkolwiek parsowaniem XML dla kanałów bankowych/plikowych.**
      `verify: ./mvnw -f backend test -Dtest=SignatureBeforeParseOrderingTest` → `Tests run: 4, Failures: 0` — PASS (2026-07-15). Cztery scenariusze: (1) poprawny podpis → `ARCHIVE→VERIFY→PARSE`, parser wywołany dokładnie raz (`InOrder` na trzech prawdziwych collaboratorach owiniętych `@MockitoSpyBean` — kryptografia Ed25519 działa naprawdę pod spy, nie zmockowana); (2) tampered signature → `ARCHIVE→VERIFY`, parser NIGDY nie wywołany (`never()`); (3) brak wymaganego podpisu → to samo; (4) kanał opcjonalny (jak JSON_DIRECT) bez podpisu → `NOT_APPLICABLE`, parser nadal wywołany (dowód, że JSON_DIRECT nie zostałby przypadkowo zablokowany, gdyby przechodził przez ten pipeline). Niepróżność testu potwierdzona mutacją (patrz EPIC-31.md Story 31.2). Regresja JSON_DIRECT: `JsonDirectIngestionTest`/`PaymentControllerTest`/`WalkingSkeletonIntegrationTest`/`InboxConsumerIdempotencyTest` — `10/10 PASS`, niezmienione (JSON_DIRECT nie przechodzi przez ten nowy pipeline, własna, nietknięta ścieżka z Story 19.1). Pełny regres backendu: `90/90 PASS` (było 73/73 przed tą sesją).

## Story 19.3 — Hartowanie XML

status: done
depends_on: []

Opis: konfiguracja odporna na XXE/bomby, z fixture'ami negatywnymi.

Taski:
- [x] **Skonfiguruj parser XML odporny na XXE/entity-expansion, z fixture'ami negatywnymi (XXE payload, billion-laughs).**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=XmlHardeningTest` → `Tests run: 3, Failures: 0` — PASS (2026-07-14). Nowy `backend/src/main/java/com/sepanexus/modules/paymentlifecycle/ingress/HardenedXmlFactory.java`: `DocumentBuilderFactory` z `disallow-doctype-decl=true`, zewnętrzne encje wyłączone, `FEATURE_SECURE_PROCESSING=true` (limity ekspansji encji), `XInclude`/`expandEntityReferences` wyłączone. Test `XmlHardeningTest` dowodzi nie-próżności: poprawny XML akceptowany; XXE (`SYSTEM "file:///etc/passwd"`) i billion-laughs (zagnieżdżone encje) oba odrzucone z realnym `[Fatal Error] DOCTYPE is disallowed` w logu (widoczny dowód odrzucenia, nie cichy no-op). Ta konfiguracja nie jest jeszcze wpięta w żaden endpoint REST XML (to Story 19.4, blocked) — istnieje jako gotowy, przetestowany komponent do wpięcia.

## Story 19.4 — REST XML pain.001

status: done
depends_on: [Story 19.3, EPIC-31-signature-module/Story 31.2 (done), CanonicalMapper capability (done, this session)]

Opis: taksonomia błędów 422 dla nieprawidłowego pain.001.

`[OQ-13 CLOSED 2026-07-15]` **`CanonicalMapper` owner resolved from source, not invented**:
`sepa-nexus-message-flow-and-data-blueprint.md` §3.1 (line 221) lists the port explicitly —
"Ports: `IdempotencyStore`, `RawMessageArchive`, `CanonicalMapper` (implemented by `iso-adapter`),
`ClockPort`" — so no `[PLANNING-DEFECT]` was needed; the only judgment call was *where* to put it
given `iso-adapter` is still a subpackage of `payment-lifecycle`, not its own Modulith module (same
`[PLANNING-DEFECT 2026-07-14]` noted at the top of this file for Story 19.1/19.2) — resolved by
placing it in the existing `com.sepanexus.modules.paymentlifecycle.isoadapter` subpackage,
consistent with `JsonDirectLineageRecorder`/`IsoIdentifierLookup` already there.

**Built this session:**
- `CanonicalPaymentCommand`/`CanonicalMapper`/`Pain001CanonicalMapper` (`isoadapter` package): pure
  mapping (no DB/HTTP/JPA), pinned to `pain.001.001.09` (blueprint names `pain.001` but does not pin
  an exact SRU — resolved pragmatically as `[OPEN-QUESTION]`, same discipline as Story 31.2's Ed25519
  pick: current real ISO 20022 SCT release, not a synthetic placeholder). Single-transaction only
  (§2.1 channel matrix: `POST /api/v1/iso/pain001` is the single-payment REST channel; the batch file
  rail — multiple `PmtInf`/`CdtTrfTxInf` — is a separate channel, EPIC-73, out of scope) — more than
  one `PmtInf` or `CdtTrfTxInf` is a controlled `UNSUPPORTED_TRANSACTION_COUNT` rejection, never a
  silent first-element pick.
- `HardenedXmlFactory.HardenedParseResult` extended (additively) to carry the parsed `Document` so
  the mapper consumes the already-hardened DOM instead of re-parsing.
- Migration `V15__iso_pain001_identifier_fields.sql`: nullable `msg_id`/`cre_dt_tm` on
  `iso.iso_messages`, nullable `msg_id`/`pmt_inf_id`/`instr_id`/`uetr` on
  `iso.payment_iso_identifiers` — the richer §4.3c fields V11's own comment deferred "until real XML
  channels... are built." `tx_id`/`orgnl_*` deliberately not added — pain.001 doesn't carry `TxId`
  (that's a `pacs.008` concept) and `orgnl_*` is R-message correlation (EPIC-27), not this story's
  scope. Verified on fresh Testcontainers DB and on the real long-running `infra_postgres_1` (13→15
  via `flyway:migrate`).
- `Pain001LineageRecorder` (`isoadapter`): writes `iso.iso_messages`/`iso.payment_iso_identifiers`
  (with the new V15 columns)/`iso.message_lineage` for the pain.001 channel.
- `Pain001IngestionService` + `Pain001PersistenceService` (two separate Spring beans, not one —
  `@Transactional` only honors a proxy boundary from a *different* bean, so self-invocation would
  have silently dropped the guarantee): the outer method (archive→verify→hardened-parse→canonical-map)
  is deliberately **not** `@Transactional` so raw evidence and the signature verdict survive a later
  rejection; only the idempotency+payment+identifiers+lineage+outbox tail is one atomic unit.
  `PaymentCreationWriter` extracted from `PaymentService` (behavior-preserving refactor, still
  covered by the full regression) so both the JSON and XML channels share the "insert payment +
  write outbox" step instead of duplicating it.
- `POST /api/v1/iso/pain001` on `PaymentController` (blueprint §3.1 names both paths on one
  `PaymentSubmissionController`) — raw XML bytes, `Idempotency-Key`, and `X-Signer-Id`/`X-Signature`/
  `X-Signature-Algo` headers (blueprint doesn't pin exact header names for the bank-XML channel —
  `[OPEN-QUESTION]` resolved pragmatically, same pattern as the pain.001 version pin).
- RFC 7807 taxonomy in `PaymentProblemHandler`: `SIGNATURE_FAILED`, `MALFORMED_XML`,
  `UNSUPPORTED_MESSAGE_TYPE`/`UNSUPPORTED_MESSAGE_VERSION`/`MISSING_REQUIRED_ELEMENT`/
  `INVALID_FIELD_FORMAT`/`UNSUPPORTED_TRANSACTION_COUNT`/`MAPPING_FAILED` — all HTTP 422, no raw
  XML/signature/key material in the response body. `iso.iso_message_parse_errors` persistence is
  deliberately **not** built here — that table/story is EPIC-28 Story 28.1's scope, not started this
  session per the session's own stop rule.

Taski:
- [x] **Endpoint REST XML pain.001 z taksonomią błędów 422.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=Pain001CanonicalMapperTest,Pain001SubmissionEndpointTest` → `Tests run: 22, Failures: 0` — PASS (2026-07-15). 13 mapper unit tests (valid minimal/realistic, missing MsgId/EndToEndId/amount/currency/debtor+creditor IBAN, unsupported version, unrecognized type, single- and multi-`PmtInf` transaction-count rejection, determinism) + 9 endpoint integration tests (real Testcontainers Postgres + real Ed25519 signing, no mocked crypto): happy path creates payment+identifiers+lineage+outbox+VERIFIED signature evidence; tampered signature and missing-required-signature both reject with zero payment rows while still archiving raw bytes + a FAILED signature-verification-event; XXE rejected as `MALFORMED_XML`; unsupported version rejected; missing required field rejected with zero payment/identifier/lineage/outbox rows (atomicity); idempotency replay returns the same `Location`; idempotency conflict (same key, different body) returns 409 without a second payment row; unauthorized role (`payment_viewer`) returns 403. Mutation non-vacuousness proven and reverted (swapped `EndToEndId`→`InstrId` mapping, 4 tests failed as expected, reverted, confirmed clean). Full backend regression: `112/112 PASS` (was `90/90` at session start — 90 baseline + 13 mapper + 9 endpoint = 112, exact).
