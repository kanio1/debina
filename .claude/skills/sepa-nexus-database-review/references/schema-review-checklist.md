# Schema review checklist

- [ ] Table belongs to exactly one schema/module, matching the module boundary map in root `AGENTS.md` (16 bounded-context modules, one PostgreSQL schema each) — no table straddling two modules' concerns.
- [ ] Tenant-scoped tables have `tenant_id uuid NOT NULL`, `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, and a policy with both `USING` and `WITH CHECK` if the table accepts writes (`postgres-rls-migration` skill).
- [ ] Queue/ledger tables use ownership grants instead of RLS, per `infra/AGENTS.md` — confirm this is the intended pattern for the table in question rather than defaulting to RLS everywhere.
- [ ] Money columns are `numeric` with explicit precision/scale, currency stored explicitly alongside (`sepa-nexus-payments-data-integrity` skill's `money-and-currency.md`).
- [ ] ISO-identifier columns use the correct field per the identifier family (`MsgId`/`EndToEndId`/`TxId`/`UETR`/`Orgnl*`) — no field storing a value that actually belongs to a different identifier concept (`iso20022-identifiers.md`).
- [ ] Append-only tables (`evidence.*`, `audit.*`, `ledger.journal_*`) have no `UPDATE`/`DELETE` grant to the app role.
- [ ] Foreign keys reference the correct owning table/schema, added `NOT VALID` + validated separately if the referencing table has existing rows.
- [ ] No `@TenantId`/Hibernate-level tenant filter anywhere in the entity mapping — tenant isolation is RLS-only (root `AGENTS.md` frozen rule).
- [ ] Column nullability matches actual source-carried-or-not semantics — no synthetic placeholder value standing in for a genuinely absent field (`iso20022-identifiers.md`'s "never generate a missing identifier" rule).
- [ ] Schema/table naming is consistent with existing conventions in the same schema directory (check sibling files before introducing a new naming pattern).
