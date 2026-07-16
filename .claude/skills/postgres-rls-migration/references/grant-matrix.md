# Grant matrix: migration role vs. app role vs. `PUBLIC`

SEPA Nexus enforces **one-writer-per-schema** at three independent layers (Spring Modulith `allowedDependencies`, ArchUnit, SQL grants — see root `AGENTS.md`). This file covers the SQL-grant layer, which RLS migrations must never weaken.

## Two roles, never one

| Role | Purpose | Capabilities | Never has |
|---|---|---|---|
| migration role (e.g. `sepa_migration`) | Runs Flyway, owns DDL | `CREATE`/`ALTER`/`DROP` on its own schema's objects | `BYPASSRLS`; access to another module's schema |
| app role (e.g. `sepa_app`, or a per-module role once modules split) | Runs application queries at request time | `SELECT`/`INSERT`/`UPDATE`/`DELETE` on its own schema's tables, per RLS policy | `CREATE`/`ALTER`/`DROP`; `BYPASSRLS`; superuser; access to another module's schema |

The application must never connect as the migration role, and the migration role must never be used to run business-transaction queries — mixing them collapses the DDL/DML privilege boundary migrations exist to enforce.

## `PUBLIC` is always revoked explicitly

New schemas/tables in PostgreSQL default to granting some privileges to `PUBLIC`. Every migration that creates a new schema must immediately:

```sql
REVOKE ALL ON SCHEMA <schema> FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA <schema> FROM PUBLIC;
```

before granting narrowly to the owning role(s). An un-revoked `PUBLIC` grant is a silent cross-module read path that RLS alone does not close (a different role querying the same table still sees rows RLS would otherwise filter for *that role's* GUC state — the grant, not the policy, is what's missing here).

## One-writer-per-schema at the grant level

A module's DB role gets **zero** privileges on another module's schema — not even `SELECT`. Cross-module reads happen through a public port / published event / read model owned by the source module, never through a foreign role querying the schema directly. A migration that grants `SELECT` on `payment.*` to a `signature_role` (or vice versa) is a one-writer-per-schema violation, regardless of RLS being correctly configured on the target table.

## Required negative test

Every schema/grant migration needs a foreign-role test: connect as a role that should have *no* access, attempt `SELECT`/`INSERT` against the new schema, assert `42501` (insufficient_privilege). A migration that only tests the *positive* (owning role can read/write) has not proven isolation — see `sepa-nexus-database-testing` skill's grants-and-writer-isolation matrix.
