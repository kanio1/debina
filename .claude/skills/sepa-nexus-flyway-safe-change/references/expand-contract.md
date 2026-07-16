# Expand/contract for backward-compatible schema changes

Since the application (`sepa_app`/module-specific roles) and the migration role deploy independently of each other in principle (even though today they run from the same build), treat every column/table rename or removal as two migrations, never one.

## The pattern

1. **Expand**: add the new shape alongside the old. New column nullable (or with a safe default), new table created, both old and new paths readable/writable by the application during the transition.
2. **Migrate application code** to read/write the new shape (a separate, non-DB commit).
3. **Contract**: once the application no longer references the old shape (confirmed by grep, not assumption), a later migration drops the old column/table.

## Concrete example already in this repo's history

`EPIC-21` Story 21.2 removed `payment.payments.end_to_end_id` and its unique index — but only after: the new lineage-based identifier storage (`iso.payment_iso_identifiers`) existed and was populated (expand), application code was repointed to read from there (migrate), and the removal was a distinct, later migration (contract) with an explicit decision recorded (source-conflict resolution, not a routine cleanup — see `planning/epics/EPIC-21-iso-identifier-refactor.md` Story 21.2).

## When expand/contract is overkill

A brand-new table with no existing readers/writers needs no expand/contract — there's nothing to be backward-compatible with. Reserve the two-phase pattern for changes to a table/column something already depends on.

## Backward-compatibility checklist for the "expand" migration

- Does any existing query (`grep` the codebase) assume the old shape exclusively? If yes, the expand migration must not remove or narrow that shape yet.
- Is the new column/table nullable or defaulted, so existing `INSERT` statements that don't yet know about it keep working?
- Does adding the new shape require a lock incompatible with concurrent writes? See `postgres-lock-analysis.md`.
