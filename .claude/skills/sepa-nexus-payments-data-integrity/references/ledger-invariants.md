# Ledger invariants

`ledger` is the single append-only ledger behind a triple-enforced `LedgerPort` (root `AGENTS.md` frozen architecture — `settlement` never writes `ledger.journal_*` directly, all money movement goes through `LedgerPort`).

## Append-only, no exceptions

A ledger entry, once written, is never `UPDATE`d or `DELETE`d. A correction is a new entry. This is stronger than "avoid editing" — it must be enforced at the SQL grant level (app role has no `UPDATE`/`DELETE` on `ledger.journal_*`), not just by convention.

## Reversal is a new, opposite-direction entry

Per the frozen rule: **"a return-after-finality is a new, opposite-direction payment, never a ledger reversal."** Concretely: correcting a ledger mistake, or processing a return after finality, always means writing a new journal entry (or entry pair, for double-entry) in the opposite direction — never deleting or negating the original entry in place. The original entry remains exactly as it was; the ledger's history is the full, honest sequence of what was recorded, including the correction, not a "clean" corrected-in-place version.

## Balance is derived, never stored-and-trusted alone

If a running balance is cached/materialized for read performance, it must be reconcilable back to a sum over the append-only journal at any time — the journal is the source of truth, the balance is a projection. A test that only checks the cached balance without also verifying it against a fresh sum over `journal_*` has not proven correctness, only internal consistency of the cache.

## `LedgerPort` is the only write path

Any new code that needs to move money writes through `LedgerPort`, never a direct repository/SQL write into `ledger.*` from another module. This is enforced the same three ways as one-writer-per-schema generally (Spring Modulith `allowedDependencies`, ArchUnit, SQL grants) — a new `settlement` (or any other module) code path that reaches into `ledger.journal_*` directly is an architecture violation regardless of whether the write itself would have been "correct" data.

## Test obligations

- Append-only: attempt `UPDATE`/`DELETE` on a journal row as the app role, assert `42501`.
- Reversal shape: process a return-after-finality scenario, assert two entries exist (original + opposite-direction correction), never one mutated entry.
- Balance reconciliation: assert a materialized/cached balance matches a fresh aggregate over the journal for the same account/scope.
