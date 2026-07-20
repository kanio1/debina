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

status: in-progress

depends_on: [Story 32.1]

`[READY 2026-07-20]`: ADR-N10 resolves the former contract gaps: `ledger.reservations`, the
AVAILABLE/RESERVED journal-line component, one terminal transition, command-id replay, deterministic
locks and atomicity. Implement the complete port, not a partial reserve-only slice.

Opis: test współbieżności no-double-reserve.

`[HISTORICAL — superseded by ADR-N10 2026-07-20]`: the prior source audit lacked the following
contract facts and correctly blocked implementation at that time. The table remains provenance for
the newly accepted decision.

| Contract question | Source/code evidence | Result |
|---|---|---|
| `reserve()` account lock strategy | §5 GROSS_INSTANT: "row-lock account, check `available ≥ amount`, move to `reserved`" — jednoznaczne, `SELECT ... FOR UPDATE` na `liquidity_accounts` | PASS |
| Insufficient-liquidity result shape | §4.11: `reserve(account,amount)→reservationId\|INSUFFICIENT` — typowany wynik, nie wyjątek | PASS |
| `reservationId` identity | §5: "journal RESERVE entry"; `journal_entries.entry_type` CHECK zawiera `'RESERVE'` — `reservationId` = `journal_entries.id` dla wpisu RESERVE | PASS |
| `POST` journal line model | §5: "journal POST entry (debit debtor available/reserved, credit creditor available)" — jednoznaczny, dwustronny wpis | PASS |
| `RESERVE`/`RELEASE` journal line model | **Brak** — żaden dokument nie pokazuje konkretnego DDL/przykładu, jakie `journal_lines` (jeśli w ogóle) powstają dla wpisu RESERVE/RELEASE. RESERVE dotyczy przesunięcia WEWNĄTRZ jednego konta (`available_minor`↔`reserved_minor`), nie transferu między dwoma kontami — nie pasuje naturalnie do modelu Σ(`amount_minor`)=0 między RÓŻNYMI `account_id` | **OPEN QUESTION** |
| Mechanizm zapobiegania podwójnemu `post()`/`release()` tej samej rezerwacji | **Brak.** `journal_entries.entry_status` (CHECK `'POSTED'`\|`'REVERSED'`, DEFAULT `'POSTED'`) nie pasuje semantycznie — domyślna wartość `'POSTED'` obowiązuje już dla świeżo utworzonego RESERVE, więc nie może służyć jako flaga "already posted/released"; `journal_entries` nie ma w ogóle kolumn `account_id`/`amount_minor` (mają je tylko `journal_lines`), więc nie da się z samego `journal_entries` odtworzyć, ile faktycznie zarezerwowano. `sepa-nexus-full-blueprint-review-and-task-plan.md` linia 170 wspomina słowo "reservations" w jednej komórce podsumowującej tabeli — nigdy nie poparte żadnym DDL ani przykładem | **OPEN QUESTION — blokujące** |
| Nowa tabela `ledger.reservations`? | Zabroniona explicite przez tę sesję bez jednoznacznego źródła (`sepa-nexus-decision-gate.md` linia 170 to za mało — gołe słowo w komórce tabeli, nie DDL) | brak zgody na wynalezienie |
| `NoDoubleReserveConcurrencyTest` może być non-vacuous? | Sama współbieżność `reserve()` (rywalizacja o `FOR UPDATE` na TYM SAMYM koncie) jest w pełni testowalna i jednoznaczna — ale "podwójny post/release TEJ SAMEJ rezerwacji" (jawnie wymagane przez taski Story 32.2's "niezmienniki transakcyjne" i tę sesję's §18 "drugi post/release nie tworzy drugiego efektu") zależy od nierozstrzygniętego elementu wyżej | PASS (dla samego reserve) / **BLOCKED** (dla post/release idempotency) |

Zgodnie z jawną instrukcją tej sesji ("jeżeli któregokolwiek kluczowego elementu brakuje: nie twórz `ledger.reservations`; nie koduj połowy portu; oznacz Story 32.2 `[CAPABILITY-BLOCKED]`") — **nie zaimplementowano żadnej części `LedgerPort`** (ani samego `reserve()`, mimo że ta konkretna operacja jest źródłowo w pełni zdefiniowana — implementacja połowy portu bez `post()`/`release()` naruszałaby explicit zakaz "nie koduj połowy portu"). Story 32.4 (reversal, `depends_on: [Story 32.2]`) jest przez to transytywnie zablokowana — patrz niżej.

**Otwarte pytanie do rozstrzygnięcia przez użytkownika/zespół**: czy `RESERVE`/`RELEASE` mają w ogóle tworzyć `journal_lines` (i jeśli tak — jaki jest dokładny kształt linii dla operacji dotyczącej jednego konta), oraz jaki mechanizm (nowa kolumna na `journal_entries`? nowa, źródłowo uzasadniona tabela? coś innego?) ma zapobiegać podwójnemu skonsumowaniu tej samej rezerwacji przez `post()`/`release()`.

Taski:
- [ ] **Zaimplementuj `LedgerPort.reserve/post/release` z niezmiennikami transakcyjnymi.** `[CAPABILITY-BLOCKED]`
      `verify: ./mvnw -f backend test -Dtest=*NoDoubleReserveConcurrencyTest*`

## Story 32.3 — Deferred constraint trigger + niemutowalność

status: done

depends_on: [Story 32.1]

`[DONE 2026-07-17]`: readiness PASS — depends only on `Story 32.1` (`done`), independent of the blocked `Story 32.2`. Migration audit confirmed the deferred constraint trigger (`ledger.check_entry_balance()`/`trg_entry_balance`, `DEFERRABLE INITIALLY DEFERRED`, fires `AFTER INSERT ON ledger.journal_lines`) and the immutability grants (`ledger_role`: `SELECT, INSERT` only, never `UPDATE`/`DELETE` on `journal_lines`) already exist correctly from Story 32.1's V26 migration — **no new migration created**, per this session's own instruction ("jeżeli trigger już istnieje i jest poprawny: nie twórz kolejnej migracji").

Story 32.1's own `LedgerSchemaMigrationTest` already had incidental coverage of both properties, but this story's own `verify:` names a specific, dedicated class (`*UnbalancedEntryAtCommitTest*`) that a bare `LedgerSchemaMigrationTest` wildcard would never match — built `UnbalancedEntryAtCommitTest` (exhaustive: single unbalanced line rejected at COMMIT with zero rows surviving, balanced entry commits, multi-statement transaction temporarily unbalanced then balanced before COMMIT, two entry IDs (one balanced, one not) roll back together as one transaction, negative+positive amounts summing to exactly zero, large representative bigint values) and `JournalLinesImmutabilityTest` (INSERT/SELECT PASS, UPDATE/DELETE/TRUNCATE → `42501` for `ledger_role`) — the latter shared with `EPIC-13` Story 13.4 (see that story's own evidence).

Caught a real bug during test-first development: `JournalLinesImmutabilityTest`'s own seed helper used a plain autocommit connection to insert two balancing lines — since the trigger is deferred (checked only at COMMIT, not per-statement) but autocommit implicitly commits after *each* statement, the first (still-unbalanced) line was rejected immediately. Fixed to use explicit transaction control, matching `UnbalancedEntryAtCommitTest`'s already-correct pattern.

`11/11 PASS` (`UnbalancedEntryAtCommitTest` 6/6 + `JournalLinesImmutabilityTest` 5/5). Mutation-proof, 3/3 caught then reverted: (1) trigger made `NOT DEFERRABLE` → all 6 `UnbalancedEntryAtCommitTest` cases FAIL (breaks legitimate multi-line inserts entirely, not just the intended-unbalanced ones); (2) `HAVING` clause neutered → exactly the 2 tests expecting rejection FAIL; (3) `ledger_role` granted `UPDATE, DELETE` on `journal_lines` → exactly `ledgerRoleCannotUpdateJournalLines`/`ledgerRoleCannotDeleteJournalLines` FAIL. `git diff --check` clean after each revert.

Taski:
- [x] **Trigger deferred constraint odrzucający niezbalansowany wpis na COMMIT + granty blokujące UPDATE/DELETE na `journal_lines`.**
      `verify: ./mvnw -f backend test -Dtest=*UnbalancedEntryAtCommitTest*` → `Tests run: 6, Failures: 0, Errors: 0` — PASS (2026-07-17).

## Story 32.4 — Przepływ reversal

status: not-started (`[CAPABILITY-BLOCKED]` — transitive)

depends_on: [Story 32.2]

Opis: reversal to osobny wpis, nigdy mutacja; nightly Σ=0 job jako drugorzędna kontrola.

`[CAPABILITY-BLOCKED 2026-07-17 — transitive from Story 32.2]`: formalnie zależy od `Story 32.2`, która jest `[CAPABILITY-BLOCKED]` (patrz wyżej) — `LedgerPort` (na którym reversal musi zostać zbudowany, per §4.11 "reverse(entryId,reason)" jako część tego samego portu) nie istnieje. Dodatkowo, ta sesja przeprowadziła NIEZALEŻNY audyt pytań kontraktowych specyficznych dla reversal (sekcja "PHASE F" tej sesji) i potwierdziła DRUGI, osobny blocker: **mechanizm "pre-finality authority"** (kto i jak `ledger` dowiaduje się, czy oryginalny wpis jest jeszcze przed finalnością, bez bezpośredniego SQL do `payment.*`/`settlement.*`) nie istnieje w ogóle w tym repozytorium — `EPIC-39` (model finalności: katalog `finality_rule`, `FinalityPolicy`, `settlement_finality_records`) jest w całości `not-started`; `payment.payments` nie ma dziś kolumny `finality_at`. Nie ma więc ani publicznego portu, ani kolumny, z której `ledger` mogłoby bezpiecznie i zgodnie z granicami modułów odczytać ten stan. Nie zaimplementowano.

`[PLANNING-NOTE]`: task's tekst nadal wspomina `ledger.ledger_reversals` — ta sama, już raz skorygowana (Story 32.1) nieścisłość. Gdy ta story zostanie odblokowana, reversal ma być budowany jako nowy wiersz `journal_entries` (`entry_type='REVERSAL'`, `reversal_of_entry_id`), NIE osobna tabela — patrz Story 32.1's `[PLANNING-CORRECTION]`.

Taski:
- [ ] **Zaimplementuj przepływ reversal jako nowy wiersz `journal_entries` (`entry_type='REVERSAL'` + `reversal_of_entry_id`) + nightly job Σ=0 jako wtórna kontrola.** `[CAPABILITY-BLOCKED]`
      `verify: ./mvnw -f backend test -Dtest=*LedgerReversalFlowTest*`

## Story 32.5 — Ledger account, currency and reversal structural invariants

status: not-started
depends_on: [Story 32.1, Story 32.3]

Opis: closes the structural invalid-evidence gaps in the existing journal DDL without implementing
LedgerPort or a runtime reversal flow. This slice has one journal-entry currency; FX and
cross-currency entries are not defined in the repository. Source: `DEBINA-GAP-RISK-BACKLOG.md`
DATA-GAP-001/002 and `DEBINA-COMPREHENSIVE-PAYMENTS-ASSESSMENT.md` §25.

Kryterium ukończenia story: PostgreSQL rejects unknown accounts, account/currency mismatches,
mixed-currency or unbalanced entries, malformed/self/duplicate reversal links, while valid existing
ledger data survives an isolated Testcontainers upgrade path.

Taski:
- [ ] **Add one append-only ledger migration and Testcontainers fresh/upgrade/negative proof.**
      `verify: ./mvnw -f backend test -Dtest=*JournalAccountCurrencyIntegrityTest*,*ReversalStructuralIntegrityTest*,*LedgerIntegrityMigrationUpgradePathTest*`
