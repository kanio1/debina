---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-MONEY-1, line 1250), [MVP]"
---

# EPIC-32 — Ledger (EPIC-MONEY-1)

Klejnot koronny: append-only, podwójny zapis, brak RLS (ownership grants zamiast tego, §4.7).

## Story 32.1 — DDL kont + dziennika + granty

status: done

depends_on: []

`[DONE 2026-07-17]`: readiness full PASS — EPIC-09 (ownership/schema-grants pattern) done, no decision gate blocking DDL, `verify:` corrected below (was a non-executable manual `psql` command, now a real Maven+Testcontainers test).

`[PLANNING-CORRECTION 2026-07-17]`: this task's original text named `ledger.ledger_reversals` as a table to migrate, copying language from `sepa-nexus-blueprint-ownership-integration.md`'s ownership tables (lines 58/79/136/233), which list "ledger_reversals" among ledger's owned tables. The canonical DDL source for this table's actual shape — `sepa-nexus-message-flow-and-data-blueprint.md` §4.5, explicitly marked "[PATCH v2, deep-research applied]" (the most recent, most authoritative version) — defines reversal without any separate table: a reversal is a new `journal_entries` row with `entry_type='REVERSAL'` and `reversal_of_entry_id` referencing the original ("the ONLY correction mechanism"). No document anywhere shows actual `CREATE TABLE` DDL for a standalone `ledger_reversals` table — the ownership doc's mentions are bare table-name references in an ownership map, never backed by a schema. **No `ledger.ledger_reversals` was created.** Reversal flow itself (Story 32.4) will build on `journal_entries.entry_type='REVERSAL'`/`reversal_of_entry_id`, already present in this migration's DDL.

`[PLANNING-NOTE — partitioning]`: §4.5's DDL sketch specifies `journal_lines ... PARTITION BY RANGE (at)`. This repository already has an established, documented precedent for exactly this situation — `payment.payment_events` (V19) and `ingress.raw_inbound_messages` (V10) both deliberately built their own `PARTITION BY RANGE`-sketched source tables as plain, non-partitioned tables, because no source document resolves an operational partition-lifecycle strategy (who pre-creates the next partition, what happens at a boundary). `journal_lines` follows the identical precedent here, for the identical reason — not `[CAPABILITY-BLOCKED]`, a documented, precedented simplification deferred to "a later DBA-hardening pass."

Built: `ledger` schema + dedicated `ledger_role` (V25, mirroring `signature`'s precedent — a genuinely separate module from day one, never the shared `sepa_app` connection). `ledger.liquidity_accounts`, `ledger.journal_entries`, `ledger.journal_lines`, `ledger.balance_snapshots` (V26) — exact DDL from §4.5, schema-prefixed, `gen_random_uuid()` (this repo's established convention, not source's `uuidv7()`). Deferred constraint trigger `ledger.check_entry_balance()`/`trg_entry_balance` enforces Σ(`journal_lines.amount_minor`)=0 per `entry_id` at COMMIT (§4.5 — "a SQL-level invariant, not a Java convention"). Grants exactly match §4.7's own SQL example: `journal_entries`/`journal_lines` get `SELECT, INSERT` only for `ledger_role`, never `UPDATE`/`DELETE`; `liquidity_accounts` gets `SELECT, UPDATE` (source does not grant `INSERT` — account provisioning is out of this story's scope, an open question for Story 32.2). **NO RLS** on any ledger base table (§4.7's explicit table-by-table decision) — table ownership is the sole boundary, confirmed by a test reading `pg_class.relrowsecurity` directly, not just grant behavior.

Explicitly NOT implemented (later stories, forbidden this session): `LedgerPort`/`reserve`/`post`/`release` (32.2), reversal flow/nightly Σ=0 secondary check (32.4), settlement integration.

Test-first: `LedgerSchemaMigrationTest` (fresh-database Testcontainers, 7 cases: balanced-entry insert, unbalanced-entry-rejected-at-commit, foreign-role-denied, journal_lines UPDATE-denied, journal_lines DELETE-denied, liquidity_accounts UPDATE-allowed, no-RLS-on-any-base-table) — GREEN on first run (migrations were written before the test in this session, a deviation from strict test-first ordering acknowledged in the session's own final report; RED/GREEN discipline was still applied via mutation-proof instead). `LedgerMigrationUpgradePathTest` (migrate to V24 with representative prior `payment.payments` data, then to V26, confirm survival + immediate usability) — GREEN. Mutation-proof, all 4 caught then reverted: (1) granting foreign role INSERT on `journal_entries` was initially a no-op due to missing schema `USAGE` (a real, useful finding about defense-in-depth), fixed the mutation to include schema USAGE, then correctly caught by `foreignRoleCannotInsertIntoJournalEntries`; (2) granting `ledger_role` UPDATE on `journal_lines` → exactly `ledgerRoleCannotUpdateJournalLines` FAIL; (3) neutering the balance trigger's `HAVING` clause → exactly `unbalancedEntryIsRejectedAtCommitByTheDeferredTrigger` FAIL; (4) enabling RLS on `journal_entries` → exactly `ledgerBaseTablesHaveNoRowLevelSecurityEnabled` FAIL (plus expected incidental failures from default-deny RLS blocking other operations). `git diff --check` clean after each revert, no leftover mutation markers.

Targeted: `LedgerSchemaMigrationTest` 7/7 + `LedgerMigrationUpgradePathTest` 1/1 PASS. `ModularityTest`/`OwnershipArchRulesTest`/`PaymentNoGodModuleTest` 9/9 PASS.

Taski:
- [x] **Migracja `ledger.liquidity_accounts`, `ledger.journal_entries`, `ledger.journal_lines`, `ledger.balance_snapshots`** — bez RLS, granty ownership. (`ledger.ledger_reversals` nie utworzona — patrz `[PLANNING-CORRECTION]` wyżej; reversal to `journal_entries.entry_type='REVERSAL'`.)
      `verify: ./mvnw -f backend test -Dtest=*LedgerSchemaMigrationTest*` → `Tests run: 7, Failures: 0, Errors: 0` — PASS (2026-07-17).

## Story 32.2 — Niezmienniki reserve/post/release

status: not-started
depends_on: [Story 32.1]

Opis: test współbieżności no-double-reserve.

Taski:
- [ ] **Zaimplementuj `LedgerPort.reserve/post/release` z niezmiennikami transakcyjnymi.**
      `verify: ./mvnw -f backend test -Dtest=*NoDoubleReserveConcurrencyTest*`

## Story 32.3 — Deferred constraint trigger + niemutowalność

status: not-started
depends_on: [Story 32.1]

Taski:
- [ ] **Trigger deferred constraint odrzucający niezbalansowany wpis na COMMIT + granty blokujące UPDATE/DELETE na `journal_lines`.**
      `verify: ./mvnw -f backend test -Dtest=*UnbalancedEntryAtCommitTest*`

## Story 32.4 — Przepływ reversal

status: not-started
depends_on: [Story 32.2]

Opis: reversal to osobny wpis, nigdy mutacja; nightly Σ=0 job jako drugorzędna kontrola.

Taski:
- [ ] **Zaimplementuj przepływ `ledger.ledger_reversals` jako osobny wpis + nightly job Σ=0 jako wtórna kontrola.**
      `verify: ./mvnw -f backend test -Dtest=*LedgerReversalFlowTest*`
