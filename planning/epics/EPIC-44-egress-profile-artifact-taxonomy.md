---
status: not-started
depends_on: [EPIC-43-egress-rail-outbound-dispatch]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-1, line 1303), [MVP]"
---

# EPIC-44 — Egress: profil i taksonomia artefaktów (EPIC-EGRESS-1)

## Story 44.1 — Katalog `egress_profile` + snapshoty

status: not-started (`[CAPABILITY-BLOCKED]`)
depends_on: []

`[CAPABILITY-BLOCKED 2026-07-17 — egress ownership train, Phase B]`:

`[PLANNING-DEFECT znaleziony i skorygowany]`: task tekst i `verify:` odwoływały się do `egress.egress_profile`, ale `sepa-nexus-message-flow-and-data-blueprint.md` §6.8 jednoznacznie mówi: "Egress behaviour is selected by `egress_profile` (**a `reference-data` catalog row**), never by CSM name" — właścicielem schematu jest `reference_data`, nie `egress`. Poprawny `verify` docelowo: `psql -c "\d reference_data.egress_profiles"`.

Nawet po tej korekcie, story pozostaje `[CAPABILITY-BLOCKED]` — §6.8 definiuje TYLKO część modelu z dokładnymi, typowanymi wartościami:
- `transport_type` — `WEBHOOK|FILE_DROP|INTERNAL_TOPIC` (jednoznaczne, wystarczające do kolumny `text CHECK`).
- `emission_mode` — `IMMEDIATE|BATCHED` (jednoznaczne).
- `receipt_expectation` — `NONE|ASYNC_ACK|SYNC_ACK` (jednoznaczne).
- `signing_required` — sugerowany typ boolean, jednoznaczne.

Ale pozostałe elementy nie mają rozstrzygniętego kształtu kolumny/reprezentacji, mimo że są jawnie wymagane przez §6.8:
- `retry_policy` — opisane wyłącznie prozą ("max attempts + backoff"); żaden dokument nie mówi, czy to `jsonb`, dwie osobne kolumny (`max_attempts int`, `backoff_strategy text`), czy child table.
- `allowed_artifact_types` — brak reprezentacji (array kolumna? child table? nie rozstrzygnięte).
- validity/version (wspomniane przy `egress_profile_snapshots` — "profile + reference-data version") — brak konkretnego modelu wersjonowania.

Zgodnie z tej sesji zasadą "nie wybieraj samodzielnie między jsonb/arrays/child tables/text columns/normalized policy tables" — nie utworzono żadnej migracji. Wymaga nowego ADR albo uzupełnienia blueprintu o konkretny DDL.

Taski:
- [ ] **Katalog `reference_data.egress_profiles` + snapshoty per artefakt.** `[CAPABILITY-BLOCKED]`
      `verify: psql -c "\d reference_data.egress_profiles"`

## Story 44.2 — `outbound_artifacts` + klucz idempotencji

status: not-started (`[CAPABILITY-BLOCKED]` — transitive)
depends_on: [Story 44.1]

`[CAPABILITY-BLOCKED 2026-07-17 — transitive from Story 44.1]`: `outbound_artifacts` references the profile-snapshot model that Story 44.1 leaves unresolved. Not implemented.

Opis: `UNIQUE(trigger_event_id, artifact_type, recipient)`.

Taski:
- [ ] **Migracja `egress.outbound_artifacts` z unikalnym kluczem idempotencji `(trigger_event_id, artifact_type, recipient)`.** `[CAPABILITY-BLOCKED]`
      `verify: psql -c "\d egress.outbound_artifacts"` → unique constraint obecny.

## Story 44.3 — Mapa typ-artefaktu→trigger

status: done

depends_on: [EPIC-43-egress-rail-outbound-dispatch/Story 43.1]

`[DEPENDENCY NARROWED 2026-07-17 — egress ownership train, Phase B]`: was `depends_on: [Story 44.1]` (now `[CAPABILITY-BLOCKED]`). §6.9's artifact-type→trigger→renderer→priority table is a pure, immutable, source-backed data catalog — it needs no `egress_profile` row, no snapshot, no `outbound_artifacts` table, no database at all. It only needs the `egress` module to exist as a home package (`EPIC-43` Story 43.1, `done`). Narrowed so this story is not falsely blocked by Story 44.1's genuine DDL gap.

`[DONE 2026-07-17]`: built `com.sepanexus.egress.internal.artifact` (pure Java, no Spring context, no DB): `OutboundArtifactType` (8 values, exactly §6.9's rows), `ArtifactRendererOwner` (`ISO_ADAPTER`/`EGRESS_TEMPLATING`/`ISO_ADAPTER_AND_COLLECTOR`/`REPORTING_AND_ISO_ADAPTER` — controlled values, never a per-CSM class), `ArtifactPriority` (`MVP`/`P1`/`P2`), `TriggerName` (deliberately neutral value record — see `[TRIGGER CLASSIFICATION NOTE]` below), `ArtifactTriggerDefinition` (record, immutable `triggers` list), `ArtifactTriggerCatalog` (`List.of(...)`, immutable, `definitionFor(type)` lookup).

`[TRIGGER CLASSIFICATION NOTE]`: cross-checked every §6.9 trigger name against `infra/asyncapi/asyncapi.yaml` (the sole AsyncAPI source, ADR-N8). `settlement.completed`/`settlement.failed`/`payment.status.reported`/`payment.received`/`payment.routed`/`route.failed`/`egress.dead_lettered`/`case.resolved` all match real, registered topic addresses — but `FileProcessed`, `ReturnInitiated`, `payment.accepted`, and prose like "ingress rejection"/"recon exception" are NOT registered topics (the closest real topic, `reconciliation.exception.detected`, uses different naming than the source prose "recon exception" — not an exact-match, not assumed identical). Since not every entry can be unambiguously classified `KAFKA_EVENT` vs `INTERNAL_DOMAIN_EVENT`, `TriggerName` stays a neutral value type declaring no transport, per this session's own fallback rule.

Test-first: `ArtifactTriggerMapTest` written before any production class existed → structural RED (`cannot find symbol`) → implemented → GREEN, `8/8 PASS` (all 8 rows present, no invented row, `pacs.002`'s all 3 triggers present, exact priority per row, exact renderer per row, lookup returns the queried type not the first entry, catalog immutable at both the definition-list and per-definition `triggers`-list level, no CSM/profile name literal anywhere in the catalog).

**Mutation-proof, 5/5 caught then reverted**: (1) removed one of `pacs.002`'s three triggers → `pacs002StatusReportHasAllThreeSourceDefinedTriggers` FAIL; (2) `JSON_STATUS_REPORT` renderer changed to `ISO_ADAPTER` → `everyDefinitionHasTheExactSourceRendererOwner` FAIL; (3) `OPERATOR_NOTIFICATION` priority changed `P1`→`MVP` → `everyDefinitionHasTheExactSourcePriority` FAIL; (4) invented CSM-specific trigger literal (`TIPS_LIKE.settled`) injected into an existing definition → 2 tests FAIL (exact-trigger-set check AND the dedicated `noSelectionByCsmOrProfileName` guard); (5) `allDefinitions()` returned a mutable `ArrayList` copy instead of the immutable source list → `catalogIsImmutable` FAIL. `git diff --check` clean after each revert (diffed byte-identical against a clean backup after the final revert).

Taski:
- [x] **Mapa artifact-type/trigger jako katalog konfiguracyjny.**
      `verify: ./mvnw -f backend test -Dtest=*ArtifactTriggerMapTest*` → `Tests run: 8, Failures: 0, Errors: 0` — PASS (2026-07-17).
