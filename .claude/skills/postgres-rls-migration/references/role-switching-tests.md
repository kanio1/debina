# Required RLS test matrix

Every RLS-bearing migration needs all of the following, run against a real PostgreSQL (see `rls-testcontainers.md` — never mocked):

1. **Same-tenant positive**: set `app.tenant_id` to tenant A's UUID, insert/read a tenant-A row, assert it is visible/writable.
2. **Cross-tenant negative**: set `app.tenant_id` to tenant A, attempt to read/write a tenant-B row, assert zero rows returned (`SELECT`) or rejection (`WITH CHECK` violation on `INSERT`/`UPDATE`).
3. **Empty-GUC**: do not set `app.tenant_id` at all (or explicitly `RESET`), assert the query returns zero rows — never "no filter applied." This is the single most common RLS regression: a policy written as `tenant_id = current_setting('app.tenant_id')::uuid` (without the `true` missing_ok flag) throws instead of filtering, while one written carelessly around a `COALESCE` fallback can accidentally show everything. Assert the *specific* zero-rows behavior, not just "no error."
4. **Invalid-GUC**: set `app.tenant_id` to a non-UUID string (`'not-a-uuid'`), assert the query raises (the `::uuid` cast fails) rather than silently matching or exposing rows.
5. **`WITH CHECK` (writable tables only)**: attempt an `INSERT`/`UPDATE` that would place a row in a tenant other than the session's GUC value, assert rejection. See `policy-using-with-check.md`.
6. **Foreign writer role**: connect as a role that owns a *different* schema (not the table's own app role), attempt read/write, assert `42501` (insufficient_privilege) at the grant level — this is a distinct failure mode from RLS and must be proven separately (RLS can be perfectly correct while a grant is accidentally too broad, or vice versa). See `grant-matrix.md`.

## `SET LOCAL ROLE` pitfall

If a test (or production code) uses `SET LOCAL ROLE` to switch database role mid-transaction, be aware of the proven JPA flush-timing hazard documented in `EPIC-10`'s decision memo: a deferred JPA write executes under whatever role is active *at flush/commit time*, not at the time `save()` was called. A role switch between `save()` and the transaction boundary can produce a `42501` at a point in the code that looks unrelated to the role switch. If your test exercises `SET LOCAL ROLE` across a JPA-managed transaction, assert the flush actually happens under the intended role — do not assume call-order implies flush-order.

## Test naming convention

Match the existing repo pattern: `*RlsTest` for policy-level tests (empty-GUC, cross-tenant, WITH CHECK), grant-specific negative tests can live alongside or in a dedicated `*OwnershipTest`/`*GrantMatrixTest` — see existing examples like `SchemaGrantMatrixTest`, `ReferenceDataOwnershipTest`, `PaymentAuthorizationTest`.
