---
status: in-progress
depends_on: [EPIC-19-ingress-staging-pipeline/Story 19.3]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-3, line 1270), [MVP]"
---

# EPIC-28 — ISO: granice walidacji (EPIC-ISO-3)

`[FAILURE-STAGE MODEL, 2026-07-15]` Nie każdy błąd ISO ma ten sam właściciel/model persystencji —
rozstrzygnięte ze źródła (§4.3c), nie samodzielnie:

| Etap | Właściciel | Model persystencji | Story | Zbudowane teraz |
|---|---|---|---|---|
| Signature failure | `signature` | `signature.signature_verification_events`/`message_signatures` | 31.2/19.2 | już `done`, bez zmian |
| Hardening/parser failure | `iso-adapter` | `iso.iso_message_parse_errors` (id, raw_message_id, message_type_guess, error_code, error_path, error_message, created_at) | **28.1** | **tak** |
| Structural/schema/profile validation | `iso-adapter` | `iso.iso_message_validation_results` (`validation_type` enum, m.in. `XML_SCHEMA`/`ISO_STRUCTURAL`/`SCHEME_PROFILE`) | 28.2 | nie |
| Canonical mapping failure | `iso-adapter` | `iso.iso_message_validation_results` (`validation_type='CANONICAL_MAPPING'`) — źródło jawnie umieszcza mapping w TEJ SAMEJ tabeli co structural/schema, NIE w `iso_message_parse_errors` | 19.4 (HTTP RFC 7807, już `done`) / 28.2 (persystencja) | tylko HTTP, nie DB |
| Business rejection | `payment-lifecycle` | `payment.payment_status_history` | payment lifecycle | nie dotyczy tego epika |

## Story 28.1 — Wynik hartowania XML → `iso_message_parse_errors`

status: done
depends_on: []

Opis: `iso.iso_message_parse_errors` per §4.3c (linie 656-660) DDL: `id uuid PK, raw_message_id uuid NOT NULL, message_type_guess text, error_code text NOT NULL, error_path text, error_message text NOT NULL, created_at timestamptz(3) NOT NULL` — dokładnie 1:1 ze źródłem, żadna kolumna nie wymyślona. Brak `tenant_id`/`branch_id` w źródle → brak RLS (ten sam ownership-grant-only model co pozostałe tabele `iso.*` — żadna z nich nie ma dziś RLS). Brak `iso_message_id` w źródle → **potwierdza** zasadę z packetu: hardening/parser failure powstaje ZANIM jakikolwiek wiersz `iso.iso_messages` może istnieć (parser nie ustalił jeszcze wiarygodnie typu wiadomości), więc nie ma czego przypiąć.

**Zbudowane:**
- Migracja `backend/src/main/resources/db/migration/iso/V16__iso_message_parse_errors.sql` — tabela 1:1 ze źródłem + FK `raw_message_id → ingress.raw_inbound_messages(id)` (ta sama konwencja co `iso.iso_messages.raw_message_id` w V11 — źródło zapisuje FK jako komentarz `-- → ingress.raw_inbound_messages`, kodowa baza zawsze tłumaczy to na prawdziwy `REFERENCES`). Append-only: `REVOKE UPDATE, DELETE ON iso.iso_message_parse_errors FROM sepa_app` (ten sam wzorzec co V14 dla `signature.*`). Zweryfikowana na świeżej bazie Testcontainers ORAZ na realnej `infra_postgres_1` (`flyway:migrate` 15→16, potwierdzone `\d`/`\dp`: `sepa_app=ar` — SELECT+INSERT, bez UPDATE/DELETE).
- `IsoParseErrorRecorder` (`isoadapter` package — schema owner, nie `shared`, nie zmieszany z `PaymentProblemHandler`) — jedna metoda `record(...)`, `ClockPort`-dyscyplina (żaden `Instant.now()`).
- Wpięty w `Pain001IngestionService.submit` dokładnie tam, gdzie wcześniej (Story 19.4) rzucany był `XmlHardeningRejectedException` — TA SAMA nie-transakcyjna metoda co archive/verify (Story 19.4's projektowa decyzja), więc `INSERT` auto-commituje się natychmiast i przeżywa cokolwiek stanie się później; żadna nowa granica transakcyjna nie była potrzebna.
- Jeden kod błędu: `MALFORMED_XML` (stała `XmlHardeningRejectedException.ERROR_CODE`, reużyta identycznie w RFC 7807 i w DB evidence) — **świadomie NIE** rozbudowano do `DOCTYPE_DISALLOWED`/`EXTERNAL_ENTITY_DISALLOWED`/`ENTITY_EXPANSION_REJECTED`/`PARSER_CONFIGURATION_FAILURE` z packetu, ponieważ `HardenedXmlFactory.parse()` łapie jeden ogólny `SAXException` i nie rozróżnia tych przypadków w kodzie — wymyślanie 5 kodów, których parser faktycznie nie potrafi rozróżnić, byłoby złamaniem zasady "tylko source-backed, tylko rzeczywiste zachowania parsera".
- `message_type_guess = 'pain.001'` (kanał, przez który przyszła wiadomość — najlepsza dostępna "domysł", zgodnie z nazwą kolumny). `error_path = null` (parser nie rejestruje dziś line/column przez `Locator` — nie budowano tego mechanizmu, brak źródłowego wymogu). `error_message` = bezpieczna wiadomość `SAXException` (opisuje regułę parsera, np. "DOCTYPE is disallowed" — nigdy treść dokumentu).
- Mapping failures (`CanonicalMappingException`) **NIE** są zapisywane do tej tabeli — źródło (§4.3c) jawnie umieszcza `CANONICAL_MAPPING` jako jedną z wartości `validation_type` w INNEJ tabeli (`iso.iso_message_validation_results`, Story 28.2), nie w `iso_message_parse_errors`. To rozstrzygnięcie ze źródła, nie `[PLANNING-DEFECT]`.
- Testy: `IsoMessageParseErrorOwnershipTest` (4: `sepa_app` INSERT ok; `signature_role` (obcy writer) INSERT → `42501`; `sepa_app` UPDATE → `42501`; `sepa_app` DELETE → `42501`) + rozszerzenia `Pain001SubmissionEndpointTest` (XXE i entity-expansion → dokładnie 1 wiersz `iso.iso_message_parse_errors` z poprawnymi `error_code`/`message_type_guess`/FK do `raw_message_id`; happy path, signature failure, unsupported version, missing field → 0 wierszy w tej tabeli).
- **Niepróżność potwierdzona dwiema mutacjami** (obie odwrócone, `grep` potwierdza brak pozostałości): (1) wyłączenie wywołania recordera → dokładnie 2 testy (XXE, entity-expansion) zawiodły; (2) wyłączenie guardu zatrzymującego pipeline po parse error (`if (false)`) → wykonanie spadło do `canonicalMapper.map(null)` → `MAPPING_FAILED` zamiast `MALFORMED_XML` → te same 2 testy zawiodły z innym powodem (dowód, że guard jest load-bearing, nie tylko evidence-write).

Taski:
- [x] **Migracja `iso.iso_message_parse_errors`, zapis wyniku hartowania XML z EPIC-19 Story 19.3/19.4.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=IsoMessageParseErrorOwnershipTest,Pain001SubmissionEndpointTest` → `Tests run: 14, Failures: 0` — PASS (2026-07-15). Pełny regres: `117/117 PASS` (było `112/112` na wejściu tej sesji).

## Story 28.2 — Wyniki walidacji schema/structural/profile/mapping

status: blocked
depends_on: [Story 28.1 (done), EPIC-12-reference-data-ownership/Story 12.2 (blocked)]

`[REEWALUACJA 2026-07-15]`: `Story 28.1` teraz `done`, ale `EPIC-12` Story 12.2 (katalogi `validation_profiles`/`mapping_profiles`/`render_profiles`, §4.13a) pozostaje `blocked` — bez zmian, wciąż jawnie `[NO-CODE]` w źródle: "full DDL lands with the iteration that implements ISO validation (Iteration 5)". Pierwszy realny kanał XML (pain.001, EPIC-19 Story 19.4) i `iso.iso_message_parse_errors` (28.1) **nie zmieniają** tego blokera — źródło sekwencjonuje katalogi profili do Iteracji 5 niezależnie od tego, czy kanał XML istnieje. Nie budowano lokalnej hardcoded listy walidacyjnej jako obejścia (zakazane przez packet tej sesji).

**Status `blocked`** (nie `not-started` — jeden z dwóch `depends_on` jest realnie zablokowany, capability-first per `planning/README.md`).

Taski:
- [ ] **Zaimplementuj cztery poziomy walidacji przez katalogi profili z EPIC-12.**
      `verify: ./mvnw -f backend test -Dtest=*ValidationLevelsTest*` — `NOT RUN`, `blocked` (patrz wyżej).

## Story 28.3 — Rozdział ISO-reject vs business-reject

status: blocked
depends_on: [Story 28.2]

`[REEWALUACJA 2026-07-15]`: bez zmian — `Story 28.2` nadal `blocked`, więc `28.3` pozostaje transytywnie zablokowana. Nie odblokowywano.

Taski:
- [ ] **Test: odrzucenie na poziomie ISO nie jest tym samym statusem co odrzucenie biznesowe (pięcioosiowy rozdział statusów).**
      `verify: ./mvnw -f backend test -Dtest=*IsoRejectVsBusinessRejectTest*` — `NOT RUN`, `blocked` (patrz wyżej).

## Story 28.4 — Integracja katalogu reason/status

status: blocked
depends_on: [Story 28.2]

`[REEWALUACJA 2026-07-15]`: bez zmian — `Story 28.2` nadal `blocked`, więc `28.4` pozostaje transytywnie zablokowana. Nie odblokowywano.

Taski:
- [ ] **Integracja FK z katalogiem reason/status z EPIC-12.**
      `verify: ./mvnw -f backend test -Dtest=*ReasonStatusCatalogIntegrationTest*` — `NOT RUN`, `blocked` (patrz wyżej).
