# Grants and writer-isolation tests

Real example: `backend/src/test/java/com/sepanexus/payment/SchemaGrantMatrixTest.java`.

## Positive + negative, always both

A grant test that only proves the owning role *can* do the intended operation has proven half of one-writer-per-schema. The other half — that a foreign role (a different module's DB role, or an unauthenticated/wrong-privilege connection) is *rejected* — needs its own explicit test:

```java
@Test
void foreignRoleCannotWriteToPaymentSchema() throws Exception {
    try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), "signature_role", "dev-only-signature")) {
        var statement = connection.createStatement();
        var ex = assertThrows(SQLException.class, () ->
            statement.execute("INSERT INTO payment.payments (...) VALUES (...)"));
        assertEquals("42501", ex.getSQLState()); // insufficient_privilege
    }
}
```

## What to check, per schema/role pair

- Owning role: full intended CRUD on its own schema — succeeds.
- Every other module's role: zero privileges on this schema — `SELECT` alone should already fail, not just writes.
- Migration role: DDL succeeds; but the migration role should never be the one used for the positive-case business-write assertions (that's what conflates DDL and DML privilege boundaries — see `postgres-rls-migration` skill's `grant-matrix.md`).
- `PUBLIC`: explicitly confirm no residual privilege — a new schema/table migration should have revoked `PUBLIC` explicitly; a passing "owning role can write" test does not by itself prove `PUBLIC` was revoked.

## `SchemaGrantMatrixTest` as the pattern to extend

New schemas/modules should get an entry in (or a sibling test following the same shape as) `SchemaGrantMatrixTest` rather than a one-off, differently-structured grant test — consistency here makes it easy to see the whole one-writer-per-schema matrix at a glance across the codebase.
