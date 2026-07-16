# RLS tests must run on isolated Testcontainers PostgreSQL

RLS correctness depends on real PostgreSQL policy evaluation — it cannot be meaningfully mocked. But "real PostgreSQL" must still mean an isolated, disposable instance per test run, not the long-lived local `infra_postgres_1` Compose database.

## Why isolated, not shared

- The long-lived Compose database accumulates state across sessions (named volumes are explicitly not disposable — see `infra/AGENTS.md`). A cross-tenant test that happens to pass because a *previous* session's leftover data made a query return zero rows for the wrong reason is a false-positive risk.
- Testcontainers gives every test class (or method, depending on `@Testcontainers` lifecycle) a fresh database: migrations apply cleanly from zero, and cross-tenant fixtures are exactly what the test itself created — nothing inherited, nothing left behind for the next run.
- `act` does not provide a working Testcontainers-compatible Docker environment in this repository's setup — never route RLS test execution through it (see root/`backend/AGENTS.md`).

## Standard shape

```java
@Testcontainers
class PaymentRlsTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
        .withDatabaseName("sepa_nexus_test");

    // Flyway runs against the container via Spring Boot's dynamic datasource
    // properties (@DynamicPropertySource) -- same migrations as production,
    // fresh every run.

    @Test
    void emptyGucReturnsZeroRows() { ... }

    @Test
    void crossTenantReadReturnsZeroRows() { ... }

    @Test
    void foreignWriterRoleRejected() { ... }
}
```

## What the long-lived Compose database is still for

Manual inspection, exploratory `psql` sessions, UI development against realistic data, and — as one *additional* piece of evidence, never a substitute — confirming a migration behaves the same way against a database that has accumulated real upgrade history. A test suite that only passes against `infra_postgres_1` and has no Testcontainers equivalent is a documented gap (mark it as technical debt explicitly, per `sepa-nexus-database-testing` skill), not a pattern to extend.
