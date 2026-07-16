# PostgreSQL Testcontainers — standard shape in this repo

The repo already has a working pattern — follow it rather than inventing a new one. Real examples: `backend/src/test/java/com/sepanexus/payment/PaymentsRlsTest.java`, `backend/src/test/java/com/sepanexus/payment/SchemaGrantMatrixTest.java`.

```java
@Testcontainers
class SomeNewFeatureTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void migrateDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void seedFixtures() throws Exception {
        // test creates exactly the data it needs -- never assume leftover state
    }

    static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
```

## Key properties this pattern relies on

- `postgres:18` — matches the production version pinned in `infra/AGENTS.md` (PostgreSQL 19 is lab/experimental only, never used in tests that claim to represent this project).
- Real Flyway migration run from `src/main/resources/db/migration`, not a hand-rolled schema-creation script — the test proves the actual migrations work, not a paraphrase of them.
- A superuser `test_admin` connection is used only to create the migration role and do raw setup/verification — application-level test assertions connect as `sepa_app` (or whichever role is under test), never as `test_admin`, or the test would bypass the exact grant/RLS boundary it's meant to verify.
- `@BeforeEach` truncates/reseeds rather than relying on `@BeforeAll`-seeded data persisting correctly across test methods within the class.

## Shared-container pattern for a test suite

`KafkaIntegrationSupport` (`backend/src/test/java/com/sepanexus/modules/paymentlifecycle/event/KafkaIntegrationSupport.java`) shows the pattern for sharing one container across multiple test classes in a suite via a static `@DynamicPropertySource` with an `initialized` guard — use this shape when several related test classes would otherwise each pay full container-startup cost redundantly, but keep per-test data isolation (via `@BeforeEach` seed/reset) regardless of container sharing.
