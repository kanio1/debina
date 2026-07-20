---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants, EPIC-04-outbox-inbox-kafka-thin]
source: "sepa-nexus-blueprint-ownership-integration.md §9 (EPIC-OWN-11, line 355, [ADD, ADR-N5, closed] — nieobecne w MFB §8)"
---

`[UNBLOCKED 2026-07-17]`: EPIC-43 Story 43.1 (this session) built `egress`'s first migration, the first of the modules this epic's own text names as its unblocking condition ("odblokuj przy pierwszej migracji pierwszego modułu spośród iso-adapter/routing/settlement/egress/reconciliation/case"). `egress.outbox_events`/`egress.inbox_events` exist with the exact ADR-N5 pattern (§4.4), and `outbox_dispatcher_role` was created for the first time in this repository, scoped narrowly to `egress` only. **Story 18.1 is not marked `done`** — its completion criterion is "every newly-created module", and `iso-adapter`/`routing`/`settlement`/`reconciliation`/`case` still have no schema of their own, and `payment.outbox_events` (pre-dates ADR-N5) has not been retrofitted with the dispatcher-role pattern either. Both remain this epic's own, separate, not-started scope.

# EPIC-18 — Ownership: rollout outbox/inbox per schemat na pozostałe moduły (EPIC-OWN-11)

EPIC-01/EPIC-04 (Iteracja 0) zrealizowały wzorzec ADR-N5 tylko dla schematu `payment`. Ten epik rozszerza go na każdy pozostały publikujący/konsumujący schemat, w miarę jak dany moduł powstaje (nie wszystko naraz — `[DEFER]` z §3.6.3: schematy tworzone per moduł, per iteracja).

`[PLANNING-DEFECT, potwierdzone 2026-07-14]`: dosłownie "w miarę jak dany moduł powstaje" — dziś **żaden kolejny moduł z outbox/inbox nie istnieje**. `reference_data` (EPIC-12, powstały w tej sesji) jest jedynym nowym schematem, ale per §3.6.3 jest czysto katalogowy (nie publikuje/konsumuje eventów), więc świadomie **nie dostaje** pary outbox/inbox — nie jest to przeoczenie. **Status `blocked`** (jedyna wartość dozwolona przez `.claude/skills/epic-story-task-catalog/SKILL.md`) — odblokuj przy pierwszej migracji pierwszego modułu spośród `iso-adapter`/`routing`/`settlement`/`egress`/`reconciliation`/`case`, każdy z których per §3.6.3 rzeczywiście potrzebuje własnej pary `<schema>.outbox_events`/`<schema>.inbox_events`.

## Story 18.1 — DDL `<schema>.outbox_events`/`<schema>.inbox_events` per moduł

status: not-started
depends_on: []

Kryterium ukończenia: każdy nowo powstający moduł dostaje własną parę outbox/inbox w tej samej migracji co jego pierwszy schemat.

Taski:
- [ ] **Szablon migracji Flyway dla pary outbox/inbox**, powielany przy pierwszej migracji każdego kolejnego modułu (analogicznie do `payment.outbox_events` z EPIC-01 Story 1.3).
      `verify: dla każdego nowego modułu M: psql -c "\d M.outbox_events" i "\d M.inbox_events"` → obie tabele istnieją.

## Story 18.2 — `outbox_dispatcher_role` grant + negatywny sweep

status: done

depends_on: [EPIC-01-postgresql-foundation/Story 1.3, EPIC-27-iso-correlation-engine/Story 27.2C, EPIC-43-egress-rail-outbound-dispatch/Story 43.1]

`[DEPENDENCY NARROWED 2026-07-17 — egress ownership train, Phase B]`: was `depends_on: [Story 18.1]`, whose own completion criterion is "every newly-created module" — a moving target that can never be `done` while future modules remain unbuilt. This story's own test (`OutboxDispatcherNoDomainWriteSweepTest`) is dynamic — it discovers whatever outbox tables currently exist and sweeps negatively across whatever domain tables currently exist — so it only needs the outboxes that exist *today*: `payment.outbox_events` (Story 1.3), `iso.outbox_events` (Story 27.2C), `egress.outbox_events` (Story 43.1, which also created `outbox_dispatcher_role` itself). It will automatically pick up future outboxes once Story 18.1 adds them, with no re-narrowing required. Story 18.1 remains its own, separate, not-started scope.

`[DONE 2026-07-17]`: audit confirmed `outbox_dispatcher_role` had grants ONLY on `egress.outbox_events` — zero grants anywhere on `payment.*`/`iso.*` (no `GRANT ... TO outbox_dispatcher_role` in either schema's migrations). Per ADR-N5 (`[FREEZE]`, "explicit SELECT/UPDATE across every module's outbox table"), this was an incomplete rollout of an already-frozen decision, not a new architecture question — created `V28__outbox_dispatcher_role_grants.sql`: `GRANT USAGE ON SCHEMA payment/iso TO outbox_dispatcher_role` + `GRANT SELECT, UPDATE (published_at) ON payment.outbox_events/iso.outbox_events` — no `INSERT`/`DELETE`/`TRUNCATE`, no domain-table grant, application dispatcher code (`OutboxDispatcher`/`IsoOutboxDispatcher`, still running under `sepa_app`) deliberately untouched.

Built `OutboxDispatcherNoDomainWriteSweepTest` (dynamic — discovers all `<schema>.outbox_events` tables via `information_schema.tables`, parameterized positive+negative matrix per outbox, plus a full metadata sweep via `has_table_privilege` across every non-outbox base table, plus real negative `INSERT` attempts against `payment.payments`/`iso.iso_messages`/`egress.outbound_messages`/`signature.signature_keys`/`ledger.journal_entries`/`reference_data.scheme_profiles`) and `OutboxDispatcherGrantsMigrationUpgradePathTest` (migrate to V27, seed pre-existing rows in both outboxes, confirm dispatcher is denied *before* V28 — proving the upgrade actually changes something — then apply V28, confirm rows survive and become immediately claimable).

`23/23 PASS` (`OutboxDispatcherNoDomainWriteSweepTest` 22/22 + `OutboxDispatcherGrantsMigrationUpgradePathTest` 1/1). **Mutation-proof, 5/5 caught then reverted**: (1) `INSERT` granted on `payment.outbox_events` → `dispatcherCannotInsertIntoAnyOutbox[payment]` FAIL; (2) full-column `UPDATE` (not restricted to `published_at`) granted → `dispatcherCannotUpdateNonPublishedAtColumnOnAnyOutbox[payment]` FAIL; (3) `UPDATE` granted on domain table `egress.outbound_messages` → `dispatcherHasNoWritePrivilegeOnAnyNonOutboxTable` FAIL (metadata sweep, not a per-table allowlist); (4) `GRANT USAGE ON SCHEMA iso` omitted → claim test errors (schema unreachable); (5) `DELETE` granted on `iso.outbox_events` → `dispatcherCannotDeleteFromAnyOutbox[iso]` FAIL. `git diff --check` clean after each revert, no leftover `TEMPORARY MUTATION` markers.

Taski:
- [x] **Rozszerz `outbox_dispatcher_role`** o wąski grant `SELECT`/`UPDATE(published_at)` na outbox nowego modułu, bez grantu na jego tabele domenowe.
      `verify: ./mvnw -f backend test -Dtest=*OutboxDispatcherNoDomainWriteSweepTest*` → `Tests run: 22, Failures: 0, Errors: 0` — PASS (2026-07-17). Negatywny sweep przez wszystkie schematy, nie allowlist per tabela.

## Story 18.3 — Test: writer modułu nie pisze cudzego outbox

status: done

depends_on: [EPIC-01-postgresql-foundation/Story 1.3, EPIC-27-iso-correlation-engine/Story 27.2C, EPIC-43-egress-rail-outbound-dispatch/Story 43.1]

`[DEPENDENCY NARROWED 2026-07-17 — egress ownership train, Phase B]`: identical reasoning to Story 18.2 — `CrossModuleOutboxWriteDeniedTest` builds its own writer-role registry dynamically from currently-existing schemas/outboxes (`payment`/`sepa_app`, `iso`/`sepa_app`, `egress`/`egress_role`), not from a hypothetical future-complete rollout.

`[DONE 2026-07-17]`: writer-role registry built from source (grep across migrations, not guessed): `payment` and `iso` are TODAY written by the SAME shared role `sepa_app` (`iso-adapter` has not yet split into its own DB role — `EPIC-10` Story 10.1, blocked on a pending `SECURITY DEFINER` decision) — `payment`↔`iso` deliberately EXCLUDED from the negative matrix (not a real cross-module boundary today; asserting "denied" there would be a false positive). `egress` has its own dedicated `egress_role` (V22) — the only role in this registry genuinely different from the other two, so the only side of the matrix producing real cross-module assertions: `sepa_app→egress.outbox_events`, `egress_role→payment.outbox_events`, `egress_role→iso.outbox_events`.

Built `CrossModuleOutboxWriteDeniedTest` (own Testcontainers instance, separate from Story 18.2's test, per this session's instruction not to share a live database between test classes) — positive matrix (3 owners × own outbox: `INSERT`+`SELECT` PASS) + negative matrix (3 cross-module pairs × `INSERT`/`UPDATE(published_at)`/`DELETE`/`TRUNCATE` → `42501`).

**Real finding during mutation-proof**: the first version of mutation 4 (`sepa_app` granted only `UPDATE(published_at)` on `egress.outbox_events`, no `SELECT`) was NOT caught by `writerCannotUpdatePublishedAtOnForeignOutbox` — PostgreSQL requires `SELECT` on any column read in the `WHERE` clause (here: `id`) independent of the `UPDATE` grant, so the test "passed" but for the wrong reason (missing `SELECT`, not missing `UPDATE`) — a real risk of this specific boundary check passing vacuously. Fixed: the mutation was extended to also grant `SELECT`, so the test actually isolates the `UPDATE` boundary rather than the `SELECT`-in-`WHERE` boundary. Caught correctly after the fix.

`16/16 PASS`. **Mutation-proof, 4/4 caught then reverted** (scratch migration `V29`, deleted after each mutation, never committed): (1) `sepa_app` granted `INSERT` on `egress.outbox_events` → `writerCannotInsertIntoForeignOutbox[1]` FAIL; (2) `egress_role` granted `INSERT` on `iso.outbox_events` → `writerCannotInsertIntoForeignOutbox[3]` FAIL; (3) `egress_role` granted `INSERT` on `payment.outbox_events` → `writerCannotInsertIntoForeignOutbox[2]` FAIL; (4) `sepa_app` granted `SELECT`+`UPDATE(published_at)` on `egress.outbox_events` → `writerCannotUpdatePublishedAtOnForeignOutbox[1]` FAIL (see finding above). `git diff --check` clean after each revert, scratch file deleted (not merely reverted in content).

Taski:
- [x] **SQL grant-test: rola-writer modułu A nie ma zapisu na `<schemat B>.outbox_events`.**
      `verify: ./mvnw -f backend test -Dtest=*CrossModuleOutboxWriteDeniedTest*` → `Tests run: 16, Failures: 0, Errors: 0` — PASS (2026-07-17).

## Story 18.4 — Dedup inbox + replay-safe

status: not-started
depends_on: [Story 18.1]

Taski:
- [ ] **Test: redelivery Kafka na dowolny inbox nie duplikuje efektu domenowego (unique na id źródłowego eventu).**
      `verify: ./mvnw -f backend test -Dtest=*InboxReplayDoesNotDuplicateTest*`

## Story 18.5 — Runtime outbox relay identity and datasource wiring

status: done
depends_on: [Story 18.2, Story 18.3, EPIC-04-outbox-inbox-kafka-thin/Story 4.2, EPIC-27-iso-correlation-engine/Story 27.2C]

Opis: per ADR-N5, the runtime relay must use the already-granted `outbox_dispatcher_role`, not the
domain writer connection. Source: ADR-N5, `sepa-nexus-message-flow-and-data-blueprint.md` §2.4/§4.4,
and `DEBINA-GAP-RISK-BACKLOG.md` DATA-GAP-003.

Kryterium ukończenia story: payment and ISO relays use a non-primary relay datasource and transaction
manager, claim rows with `FOR UPDATE SKIP LOCKED`, publish before setting `published_at`, and cannot
write any domain data.

`[DONE 2026-07-20]`: committed runtime implementation `0dc30e1` supplies the dedicated
`outbox_dispatcher_role` datasource/transaction manager and qualified payment/ISO relay paths.
Fresh PostgreSQL 18 + Kafka evidence passes 7/7: identity and grants, concurrent disjoint claims,
Kafka failure, crash-window redelivery, acknowledgement-before-mark, and structural ownership.
Removing `SKIP LOCKED` made `RuntimeDatasourceOwnershipTest` fail, then was restored and the full
matrix rerun green. The relay uses no JPA repository and retains no domain-table write grant.

Taski:
- [x] **Wire the relay datasource and refactor payment/ISO polling to its restricted transaction boundary.**
      `verify: ./mvnw -f backend test -Dtest=OutboxRelayRuntimeWiringTest,OutboxRelayConcurrencyTest,OutboxRelayKafkaFailureTest,OutboxRelayCrashWindowRedeliveryTest,OutboxRelayAcknowledgementOrderTest,RuntimeDatasourceOwnershipTest` → `7/0/0 PASS` (2026-07-20).
