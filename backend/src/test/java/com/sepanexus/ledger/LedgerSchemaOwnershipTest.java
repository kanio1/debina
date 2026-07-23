package com.sepanexus.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-13 Story 13.1: {@code ledger_role} is the sole writer of {@code ledger.*} — the most
 * critical grant test in the project (sepa-nexus-message-flow-and-data-blueprint.md line 234:
 * "the most critical grant test in the entire project" — {@code settlement} must be physically
 * unable to write {@code ledger.journal_*}). Every foreign role that could plausibly attempt a
 * write (payment-lifecycle's {@code sepa_app}, {@code settlement_role}, {@code egress_role},
 * {@code outbox_dispatcher_role}, an unrelated {@code other_module_role}) is proven denied via a
 * real {@code INSERT} against a real PostgreSQL 18 Testcontainer, never a metadata-only assertion.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class LedgerSchemaOwnershipTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
            statement.execute("CREATE ROLE other_module_role LOGIN PASSWORD 'dev-only-other-module'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @Test
    void ledgerRoleIsTheSoleWriterLegalInsertSucceeds() throws Exception {
        // journal_entries, not liquidity_accounts: ledger_role's grant on liquidity_accounts is
        // deliberately SELECT+UPDATE only (§4.7 — no INSERT, account provisioning is out of this
        // story's scope), so journal_entries is the table that actually demonstrates the "sole
        // writer can legally write" half of this test.
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ledger_role", "dev-only-ledger");
                Statement statement = connection.createStatement()) {
            int inserted = statement.executeUpdate(insertJournalEntrySql());
            assertEquals(1, inserted);
        }
    }

    @Test
    void paymentLifecycleRoleCannotWriteLedger() {
        assertDenied("sepa_app", "dev-only-app");
    }

    @Test
    void settlementRoleCannotWriteLedger() {
        assertDenied("settlement_role", "dev-only-settlement");
    }

    @Test
    void egressRoleCannotWriteLedger() {
        assertDenied("egress_role", "dev-only-egress");
    }

    @Test
    void outboxDispatcherRoleCannotWriteLedger() {
        assertDenied("outbox_dispatcher_role", "dev-only-outbox-dispatcher");
    }

    @Test
    void unrelatedModuleRoleCannotWriteLedger() {
        assertDenied("other_module_role", "dev-only-other-module");
    }

    private void assertDenied(String role, String password) {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), role, password);
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertJournalEntrySql());
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    private static String insertJournalEntrySql() {
        return """
                INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                VALUES ('%s', 'POST', CURRENT_DATE)
                """.formatted(UUID.randomUUID());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
