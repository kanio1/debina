---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants, EPIC-32-ledger-core]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-5, line 1261); sepa-nexus-blueprint-ownership-integration.md §9 (line 349)"
---

# EPIC-13 — Ownership: ledger (EPIC-OWN-5)

Najbardziej krytyczny grant-test w całym projekcie: `settlement` fizycznie nie może pisać do `ledger.journal_*`.

## Story 13.1 — `ledger_role` jedynym writerem

status: done

depends_on: []

`[DONE 2026-07-17]`: readiness full PASS — `EPIC-32` Story 32.1 (`ledger` schema + `ledger_role` + tables + grants) done in this same session, no decision gate. `LedgerSchemaOwnershipTest`: real `INSERT` against a real PostgreSQL 18 Testcontainer (never metadata-only) — `ledger_role` legally inserts into `journal_entries` (the table it actually has an INSERT grant on — `liquidity_accounts` deliberately does not, §4.7); `sepa_app` (payment-lifecycle), `settlement_role`, `egress_role`, `outbox_dispatcher_role`, and an unrelated synthetic role are all denied with SQLSTATE `42501`. `6/6 PASS`.

Taski:
- [x] **Grant-test: tylko `ledger_role` pisze do `ledger.*`.**
      `verify: ./mvnw -f backend test -Dtest=*LedgerSchemaOwnershipTest*` → `Tests run: 6, Failures: 0, Errors: 0` — PASS (2026-07-17).

## Story 13.2 — `LedgerPort` jako jedyna droga zapisu

status: not-started
depends_on: [Story 13.1]

Taski:
- [ ] **Test: `settlement` woła wyłącznie `LedgerPort`, nigdy repository `ledger.*` bezpośrednio.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementNoDirectLedgerWriteTest*`

## Story 13.3 — Test: `settlement` bez grantu `ledger`

status: done

depends_on: [Story 13.1]

`[DONE 2026-07-17]`: `settlement_role` did not exist anywhere in this repository before this story — `com.sepanexus.settlement` (EPIC-35, built earlier this same session) is a pure Java domain resolver with no schema/DB role of its own. Source names `settlement_role` explicitly (blueprint line 234: "a DB grant test — `settlement_role` has no write on `ledger.*`"; ownership doc's forbidden-access list), so creating it — with deliberately **no** grants anywhere, not even schema `USAGE` — is this story's own minimal, in-scope foundation, mirroring how Story 43.1 (this same session) created `outbox_dispatcher_role` for the first time when the story that needed to test it required it to exist. `SettlementRoleNoLedgerGrantTest`: `settlement_role` denied `INSERT`/`UPDATE`/`DELETE`/`TRUNCATE` on `ledger.liquidity_accounts` and `INSERT` on `ledger.journal_entries`; `SELECT` also denied (fail-closed — no source document grants `settlement_role` read access to any ledger table). `6/6 PASS`.

Explicitly not implemented (Story 13.2, forbidden this session): `settlement` calling only `LedgerPort`, never a direct repository — that ArchUnit-fixture-based test is separate future work once `LedgerPort` itself exists (Story 32.2).

Taski:
- [x] **SQL grant-test: rola `settlement` nie ma żadnego uprawnienia zapisu na `ledger.*`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementRoleNoLedgerGrantTest*` → `Tests run: 6, Failures: 0, Errors: 0` — PASS (2026-07-17).

## Story 13.4 — Testy deferred trigger + niemutowalność

status: done

depends_on: [Story 13.1, EPIC-32-ledger-core/Story 32.1]

`[SCOPE-MISMATCH RESOLVED 2026-07-17 — Variant A]`: tytuł tej story wymieniał "reversal" obok trigger+niemutowalność, ale jej WŁASNA lista tasków zawsze miała dokładnie dwa taski — żaden dotyczący reversal. Zgodnie z tej sesji regułą rozstrzygania (Wariant A: "jeżeli repo traktuje taski jako pełny zakres story — wykonaj dwa wskazane testy; popraw opis tak, aby nie sugerował ukończonego reversal; oznacz 13.4 jako done"): tytuł/opis poprawione (usunięto "+ reversal"); `depends_on` zawężone z całego `EPIC-32-ledger-core` do konkretnie potrzebnej `Story 32.1` (jedyna faktycznie wykorzystywana capability — schemat+trigger+granty). Reversal pozostaje wyłącznie zakresem `EPIC-32` Story 32.4 (obecnie `[CAPABILITY-BLOCKED]`, patrz tam) — ta story nigdy go nie obejmowała operacyjnie, tylko w tytule.

`[DONE 2026-07-17]`: `UnbalancedJournalEntryRejectedAtCommitTest` — dedykowany, WŁASNY dowód własności EPIC-13 (osobna instancja Testcontainers, nie współdzieli żywej bazy z EPIC-32's `UnbalancedEntryAtCommitTest`, zgodnie z tej sesji zaleceniem "nie duplikuj kosztownej konfiguracji Testcontainers... ale nie współdziel działającej bazy między klasami") — 2 przypadki (unbalanced odrzucony na COMMIT, balanced przechodzi), framed jako "ledger_role jako jedyny writer" ownership invariant, nie pełna macierz brzegowych przypadków (tę posiada `EPIC-32` Story 32.3's własny test). `JournalLinesImmutabilityTest` — WSPÓŁDZIELONY dosłownie z `EPIC-32` Story 32.3 (ten sam plik/klasa satysfakcjonuje `verify:` obu stories, zgodnie z ustalonym w tym repo precedensem współdzielonych testów, np. `DeliveredNotFinalTest`).

`7/7 PASS` (`UnbalancedJournalEntryRejectedAtCommitTest` 2/2 + `JournalLinesImmutabilityTest` 5/5). Mutation-proof, 3/3 złapane i cofnięte, każda wykryta przez test NAZWANY w tej story (nie tylko gdziekolwiek w suicie): (1) `ledger_role` z grantem UPDATE na `journal_lines` → `JournalLinesImmutabilityTest.ledgerRoleCannotUpdateJournalLines` FAIL; (2) trigger wyłączony (`DISABLE TRIGGER`) → `UnbalancedJournalEntryRejectedAtCommitTest.ledgerRoleAsSoleWriterCannotCommitAnUnbalancedEntry` FAIL; (3) osłabiony warunek `HAVING` → ten sam test FAIL. `git diff --check` czyste po każdym cofnięciu.

Taski:
- [x] **Test: `journal_lines` odrzuca niezbalansowany wpis na COMMIT (deferred constraint trigger).**
      `verify: ./mvnw -f backend test -Dtest=*UnbalancedJournalEntryRejectedAtCommitTest*` → `Tests run: 2, Failures: 0, Errors: 0` — PASS (2026-07-17).
- [x] **Test: UPDATE/DELETE na `journal_lines` odrzucone na poziomie grantów.**
      `verify: ./mvnw -f backend test -Dtest=*JournalLinesImmutabilityTest*` → `Tests run: 5, Failures: 0, Errors: 0` — PASS (2026-07-17).
