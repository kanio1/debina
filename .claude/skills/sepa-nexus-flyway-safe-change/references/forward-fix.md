# Forward-fix, not rollback

This repo has no Flyway "undo" migration mechanism in active use, and append-only migration history (see `append-only-migrations.md`) means a mistake already applied anywhere that matters is never corrected by deleting or editing the migration that caused it.

## What "forward-fix" means here

A migration that turns out to be wrong (wrong constraint, wrong default, wrong column type, wrong index) is corrected by a **new migration** that undoes or adjusts the effect — never by editing history. This mirrors the same append-only discipline the project applies to `evidence`/`audit`/ledger data itself (see `sepa-nexus-payments-data-integrity` skill).

```sql
-- V12 (already applied): added CHECK (amount > 0), turns out zero-amount reversals are valid
-- V13 (forward-fix, new migration):
ALTER TABLE payment.payments DROP CONSTRAINT payments_amount_positive;
ALTER TABLE payment.payments ADD CONSTRAINT payments_amount_nonnegative CHECK (amount >= 0) NOT VALID;
ALTER TABLE payment.payments VALIDATE CONSTRAINT payments_amount_nonnegative;
```

## Before writing a forward-fix

1. Confirm the migration is genuinely already applied somewhere that matters (see `append-only-migrations.md` — "when in doubt, treat it as applied").
2. State in the impact analysis what specifically was wrong and why the new migration is the minimal correct fix — not a broader rewrite of the same area.
3. If the original migration's mistake caused bad data to be written (not just a wrong schema shape), the forward-fix migration may also need a backfill/correction `UPDATE` — treat that `UPDATE` with the same lock/row-count scrutiny as any other DML against a live table (see `postgres-lock-analysis.md`).

## What this explicitly rules out

- Editing the original migration file's SQL, even if "it would look cleaner."
- Deleting a row from `flyway_schema_history` to force a re-run.
- Any workflow that makes "what actually ran in production" ambiguous after the fact.
