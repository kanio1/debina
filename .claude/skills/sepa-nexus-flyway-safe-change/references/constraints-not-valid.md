# Adding constraints to existing tables without a long lock

`ALTER TABLE ... ADD CONSTRAINT ... CHECK (...)` (and `FOREIGN KEY`) validates every existing row by default, holding a lock for the duration of that scan. On a table with meaningful row count, split it into two steps.

## The pattern

```sql
-- Migration N: add the constraint, skip validation
ALTER TABLE payment.payments
  ADD CONSTRAINT payments_amount_positive CHECK (amount > 0) NOT VALID;

-- Migration N+1: validate separately (only takes a SHARE UPDATE EXCLUSIVE lock,
-- which permits concurrent reads/writes, unlike the combined form above)
ALTER TABLE payment.payments
  VALIDATE CONSTRAINT payments_amount_positive;
```

Between migration N and N+1, the constraint is already **enforced for all new writes** — `NOT VALID` only means existing rows haven't been checked yet, not that the constraint is inactive. This is the key property that makes the split safe: correctness for new data is immediate, and the expensive historical scan is decoupled from the write-blocking window.

## When the two-step split isn't necessary

A brand-new table (no existing rows to scan) or a table known to be small/low-traffic doesn't need the split — a plain `ADD CONSTRAINT ... CHECK (...)` is fine and simpler. State the row-count reasoning in the migration impact analysis either way.

## Foreign keys follow the same pattern

```sql
ALTER TABLE iso.payment_iso_identifiers
  ADD CONSTRAINT fk_payment_iso_identifiers_message
  FOREIGN KEY (message_id) REFERENCES iso.iso_messages(id) NOT VALID;

ALTER TABLE iso.payment_iso_identifiers
  VALIDATE CONSTRAINT fk_payment_iso_identifiers_message;
```

## Verification

After `VALIDATE CONSTRAINT`, confirm via `\d <table>` (or `pg_constraint.convalidated`) that the constraint is marked valid, not just present — a migration that adds `NOT VALID` and never runs the follow-up `VALIDATE CONSTRAINT` leaves the constraint silently unenforced against historical data indefinitely.
