---
status: in-progress
depends_on: [EPIC-26-iso-message-lineage-core/Story 26.3]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-2, line 1269), [MVP]"
---

# EPIC-27 — ISO: silnik korelacji (EPIC-ISO-2)

Wiążąca zasada: adapter koreluje, payment-lifecycle przechodzi FSM. 9-krokowa polityka korelacji pacs.002.

`[NARROWED 2026-07-16 — dual-agent governance/backlog-redesign session]`: `depends_on` narrowed from the whole `EPIC-26-iso-message-lineage-core` epic to `Story 26.3` specifically (richer identifier extraction — the actual capability the correlation engine reads pacs.002 identifiers against). Found while building `planning/capability-graph.json`: `EPIC-26` as a whole is not `done` (Story 26.4, an unrelated GraphQL read-model panel, is still `blocked`), which made this epic read as transitively blocked even though everything it actually needs (`26.1`–`26.3`) has been `done` since 2026-07-15. **This epic is READY today** under the narrowed dependency — see `planning/BACKLOG-REDESIGN.md`.

## Story 27.1 — Ekstrakcja identyfikatorów pacs.002

status: done
depends_on: []

`[DONE 2026-07-16]`: zaimplementowano jako czysta ekstrakcja (bez korelacji, bez zapisu DB, bez decyzji biznesowej) w `com.sepanexus.modules.paymentlifecycle.isoadapter` (tymczasowa lokalizacja, ta sama co pozostałe klasy `iso-adapter` do czasu `EPIC-10`). Pinned na `pacs.002.001.10` (rzeczywista, aktualna wersja ISO 20022 FIToFIPaymentStatusReport, parowana z `pacs.008.001.08`/`pain.001.001.09`) — ta sama metoda rozstrzygnięcia niedopiętej wersji co `Pain001CanonicalMapper` dla pain.001. Nowe klasy: `Pacs002IdentifierExtractor` (ekstrakcja), `Pacs002OriginalIdentifiers` (rekord `orgnlMsgId`+`orgnlEndToEndId` per `TxInfAndSts`), `Pacs002IdentifierExtractionResult` (sukces/porażka, reużywa `MappingError`/`MappingErrorCode` z pain.001). Obsługuje wiele `TxInfAndSts` w jednym dokumencie (każdy dostaje własny `OrgnlEndToEndId`, dzieląc wspólny `OrgnlMsgId` z `OrgnlGrpInfAndSts`), whitespace/namespace zgodnie z istniejącym `HardenedXmlFactory`, malformed XML odrzucany przez granicę hardening przed dotarciem do ekstraktora.

Taski:
- [x] **Ekstrakcja `OrgnlMsgId`/`OrgnlEndToEndId` z pacs.002.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs002IdentifierExtractionTest*` → `Tests run: 11, Failures: 0, Errors: 0` — PASS (2026-07-16). Test-first: RED (compile failure, production classes nie istniały) → GREEN (11/11) → mutation-proof (zamiana tagu `OrgnlMsgId` na nieistniejący → 7/11 testów poprawnie zawiodło → mutacja cofnięta → 11/11 ponownie PASS, brak pozostałości w `git diff --check`). Pełny regresja backendu: `./mvnw -f backend test` → `Tests run: 178, Failures: 0, Errors: 0` — PASS.

## Story 27.2 (split) — Korelacja pacs.002 (steps 1–7 of the blueprint's 9-step policy)

`[CORRECTED 2026-07-16 — EPIC-27 Story 27.2 readiness/implementation session]`: the blueprint's §2.4/§4.3b "9-step" correlation policy actually spans three distinct, independently-verifiable concerns that this story previously conflated into one task/verify — split below into 27.2A/27.2B/27.2C (same convention as `EPIC-10` Story 10.1 → 10.1A–10.1E), with no residual "Story 27.2" of its own left to track.
- **steps 1–7** (the 5 ordered match strategies + `AMBIGUOUS`(`>1`)/`ORPHANED`(`0`) outcomes) are the actual scope of 27.2A/27.2B/27.2C;
- **step 8** (`IGNORED_DUPLICATE`, an inbox-gate concern that runs *before* correlation even starts) belongs to Story 27.3, not here;
- **step 9** (matched-but-late/weaker → `payment-lifecycle` FSM policy) belongs to Story 27.3 + `EPIC-20` Story 20.3, not here — `iso-adapter` correlates, it never decides what a late/weaker match means for the payment's state (root `AGENTS.md` binding rule).

**Step 5 (fallback `EndToEndId+amount+currency+participant+time-window`) is explicitly out of scope for all three sub-stories below — `[OPEN-QUESTION]`.** `iso.payment_iso_identifiers`/`iso.iso_messages` (the only tables `iso-adapter` may query) carry no amount/currency/participant column, and none is defined as belonging to that schema anywhere in the source blueprint (`payment.payments` owns those fields, and `iso-adapter` may never read `payment.*` directly — §3.6.3). pacs.002.001.10's optional `OrgnlTxRef` block could theoretically carry them, but no simulation seed populates it and no read port into `payment-lifecycle` exposing exactly those 3 fields exists. Implementing step 5 today would mean either inventing a data source not in the blueprint or reaching into another module's schema — both forbidden. Steps 1–4 run in order; if none of them produce a unique match, the policy returns `ORPHANED` today (against 4 strategies, not 5) — a documented, deliberate limitation, not a silent gap. A future session should either add a source-backed read port for amount/currency/participant, or get an explicit user/team decision on where that data should live, before implementing step 5.

## Story 27.2A — Source-backed correlation input model

status: done
depends_on: [Story 27.1]

`[DONE 2026-07-16]`: `Pacs002CorrelationInput` (record: `orgnlMsgId`, `orgnlTxId`, `orgnlInstrId`, `orgnlEndToEndId`, `orgnlUetr`) + `Pacs002CorrelationExtractionResult`, and a new `Pacs002IdentifierExtractor.extractCorrelationInputs(Document)` method (additive — Story 27.1's `extract()`/`Pacs002OriginalIdentifiers` untouched, its own 11 tests still pass unmodified). Test-first: 10 new tests, mutation-proofed (swapped the `OrgnlTxId` tag name → 3 tests correctly failed → reverted → 10/10 green again, `git diff --check` clean).

Taski:
- [x] **`Pacs002CorrelationInput` (OrgnlMsgId, OrgnlTxId, OrgnlInstrId, OrgnlEndToEndId, OrgnlUETR — each optional except OrgnlMsgId/OrgnlEndToEndId, per real pacs.002.001.10 `TxInfAndSts` fields), extending `Pacs002IdentifierExtractor` (Story 27.1's `Pacs002OriginalIdentifiers`/`extract()` untouched — this is an additive extraction path, not a replacement).**
      `verify: ./mvnw -f backend test -Dtest=*Pacs002CorrelationInputTest*` → `Tests run: 10, Failures: 0, Errors: 0` — PASS (2026-07-16).

## Story 27.2B — Ordered candidate-match policy (steps 1–4, 6, 7)

status: done
depends_on: [Story 27.2A]

`[DONE 2026-07-16]`: `Pacs002CorrelationPolicy` (pure, no Spring context needed beyond `@Component` wiring) over `CorrelationCandidateLookup` port, `CorrelationCandidate`/`CorrelationDecision`/`CorrelationOutcome`/`CorrelationMatchStrategy`. 9 tests (step 1 unique/ambiguous, step 1→2 fallthrough, step 3 UETR unique/ambiguous, step 4 unique, all-empty→ORPHANED, binding order, optional-identifier-absent skips its own strategy without querying). Mutation-proofed twice: (1) `decide()`'s `size==1` branch removed → always MATCHED → 2 AMBIGUOUS-branch tests correctly failed → reverted; (2) step 4's early-return removed → falls through to ORPHANED even on a real match → 1 test correctly failed → reverted. 9/9 green after each revert, `git diff --check` clean.

Taski:
- [x] **`Pacs002CorrelationPolicy` over a `CorrelationCandidateLookup` port: strategy order 1 (`OrgnlMsgId+OrgnlTxId`) → 2 (`OrgnlMsgId+OrgnlInstrId+OrgnlEndToEndId`) → 3 (`UETR` if unique) → 4 (`OrgnlMsgId+OrgnlEndToEndId`); exactly one candidate at any strategy → `MATCHED`; more than one → `AMBIGUOUS` immediately (no fallback to a weaker strategy); zero after all four → `ORPHANED`. No best-guessing, no FSM call, no duplicate handling (Story 27.3).**
      `verify: ./mvnw -f backend test -Dtest=*Pacs002CorrelationPolicyTest*` → `Tests run: 9, Failures: 0, Errors: 0` — PASS (2026-07-16), pokrywa MATCHED/AMBIGUOUS/ORPHANED/strategy ordering.

## Story 27.2C — Persistence + `iso.message.correlated` publication

status: done
depends_on: [Story 27.2B]

`[DONE 2026-07-16]`: migration `V21__iso_message_correlation.sql` — `iso.iso_message_correlation` (blueprint §4.3c DDL verbatim, append-only via `REVOKE UPDATE, DELETE`), `iso.iso_messages.tenant_id` (completes source DDL, backfilled from `ingress.raw_inbound_messages` via the existing `raw_message_id` FK; `JsonDirectLineageRecorder`/`Pain001LineageRecorder` updated to populate it on every new write), `iso.payment_iso_identifiers.tx_id` (completes source DDL, structurally ready for a future `pacs.008` channel, always `NULL` today — no channel populates it yet), `iso.outbox_events` (same per-schema pattern as `payment.outbox_events`). Production classes: `JdbcCorrelationCandidateLookup` (tenant-scoped, entirely inside the `iso` schema), `IsoMessageCorrelationRecorder`, `IsoOutboxRecorder`/`IsoOutboxDispatcher`/`IsoCorrelationTopicConfig` (topic `iso.message.correlated`), `Pacs002CorrelationService` (one `@Transactional` unit: policy → persist → outbox on `MATCHED` only). `score` always `NULL`; `matched_by` the controlled `CorrelationMatchStrategy` enum name. 7 Testcontainers persistence tests (MATCHED/AMBIGUOUS/ORPHANED persisted, tenant scoping, strategy-column isolation, append-only grants, foreign-role rejection) + 1 dedicated upgrade-path Testcontainers test (migrate to V20, seed pre-V21 rows including a legacy no-`raw_message_id` row, apply V21, confirm backfill + the legacy row deliberately stays `NULL`). Mutation-proofed: `IsoMessageCorrelationRecorder` forced to always write `ORPHANED` → 2 tests (MATCHED/AMBIGUOUS status assertions) correctly failed → reverted, 7/7 green again. Full backend regression: `205/205 PASS`.

Taski:
- [x] **Migration `iso.iso_message_correlation` (blueprint §4.3c DDL) + tenant-safe candidate lookup (adds `iso.iso_messages.tenant_id`, backfilled from `ingress.raw_inbound_messages.tenant_id` via the existing `raw_message_id` FK — completing source DDL §4.3c's already-specified column, not a new cross-schema query pattern) + `iso.outbox_events`/`IsoOutboxDispatcher` (same per-schema pattern as `payment.outbox_events`, per §4.4's own explicit `iso.outbox_events` naming) publishing `iso.message.correlated` on `MATCHED` only. `score` always `NULL` (no scoring formula in source); `matched_by` a controlled enum, never free text.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs002CorrelationPersistenceTest*` → `Tests run: 7, Failures: 0, Errors: 0` — PASS (2026-07-16).

## Story 27.3 — Duplikat i out-of-order

status: not-started
depends_on: [Story 27.2C]

Taski:
- [ ] **Test: duplikat → `IGNORED_DUPLICATE`; wiadomość out-of-order → polityka FSM, nie błąd.**
      `verify: ./mvnw -f backend test -Dtest=*DuplicateAndOutOfOrderTest*`

## Story 27.4 — Orphan → DLQ `[MVP]`

status: not-started
depends_on: [Story 27.2C]

`[SPLIT 2026-07-16 — dual-agent governance/backlog-redesign session, H6]`: was "Orphan → DLQ + read model operatora", one task/verify mixing an `[MVP]` deliverable (DLQ write) with a `[P1]` deliverable (manual-correlation operator read model) via a mid-sentence priority-tag split. Scope narrowed to the `[MVP]` half only; the `[P1]` half moved to the new Story 27.5 below, per ADR-N6's one-priority-taxonomy rule (iteration number primary, tag secondary — these two halves belong to different waves).

Taski:
- [ ] **Nierozpoznany status → DLQ.**
      `verify: ./mvnw -f backend test -Dtest=*OrphanDlqTest*`

## Story 27.5 — Read model operatora do ręcznej korelacji `[P1]`

status: not-started
depends_on: [Story 27.4]

`[SPLIT 2026-07-16 — see Story 27.4 above for the split rationale]`.

Taski:
- [ ] **Read model operatora nad DLQ do ręcznej korelacji.**
      `verify: ./mvnw -f backend test -Dtest=*OrphanOperatorReadModelTest*`
