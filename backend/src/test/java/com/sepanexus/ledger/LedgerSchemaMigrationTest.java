package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
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
 * EPIC-32 Story 32.1: fresh-database migration evidence for the ledger DDL + grant foundation —
 * schema, tables, the deferred balance-invariant trigger (§4.5), no RLS on any ledger base table
 * (§4.7 — table ownership is the boundary instead), and the exact grant matrix §4.7 specifies
 * (journal_entries/journal_lines: SELECT+INSERT only, never UPDATE/DELETE; liquidity_accounts:
 * SELECT+UPDATE). Does not implement {@code LedgerPort}/reserve-post-release (Story 32.2) or the
 * reversal flow (Story 32.4) — DDL and ownership only.
 */
@Testcontainers
class LedgerSchemaMigrationTest {

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
    void ledgerRoleCanInsertBalancedJournalEntry() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();

        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertJournalEntrySql(entryId));
                statement.executeUpdate(insertJournalLineSql(entryId, 1, accountA, -1000));
                statement.executeUpdate(insertJournalLineSql(entryId, 2, accountB, 1000));
            }
            connection.commit();
        }

        assertEquals(2, countJournalLines(entryId));
    }

    @Test
    void unbalancedEntryIsRejectedAtCommitByTheDeferredTrigger() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();

        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = ledgerConnection()) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(insertJournalEntrySql(entryId));
                    statement.executeUpdate(insertJournalLineSql(entryId, 1, accountA, -1000));
                    // deliberately missing the balancing +1000 line
                }
                connection.commit();
            }
        });
        assertThat(exception.getMessage()).contains("does not balance");
    }

    @Test
    void foreignRoleCannotInsertIntoJournalEntries() {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), "other_module_role", "dev-only-other-module");
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertJournalEntrySql(UUID.randomUUID()));
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    @Test
    void ledgerRoleCannotUpdateJournalLines() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();
        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertJournalEntrySql(entryId));
                statement.executeUpdate(insertJournalLineSql(entryId, 1, accountA, -500));
                statement.executeUpdate(insertJournalLineSql(entryId, 2, accountB, 500));
            }
            connection.commit();
        }

        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = ledgerConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE ledger.journal_lines SET amount_minor = 999 WHERE entry_id = '" + entryId + "'");
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    @Test
    void ledgerRoleCannotDeleteJournalLines() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();
        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertJournalEntrySql(entryId));
                statement.executeUpdate(insertJournalLineSql(entryId, 1, accountA, -500));
                statement.executeUpdate(insertJournalLineSql(entryId, 2, accountB, 500));
            }
            connection.commit();
        }

        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = ledgerConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM ledger.journal_lines WHERE entry_id = '" + entryId + "'");
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    @Test
    void ledgerRoleCanUpdateLiquidityAccountBalance() throws Exception {
        UUID accountId = seedLiquidityAccount();
        try (Connection connection = ledgerConnection(); Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate(
                    "UPDATE ledger.liquidity_accounts SET available_minor = 500 WHERE id = '" + accountId + "'");
            assertEquals(1, updated);
        }
    }

    @Test
    void ledgerBaseTablesHaveNoRowLevelSecurityEnabled() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            var result = statement.executeQuery("""
                    SELECT relname, relrowsecurity FROM pg_class
                    WHERE relnamespace = 'ledger'::regnamespace
                      AND relname IN ('journal_entries', 'journal_lines', 'liquidity_accounts', 'balance_snapshots')
                    """);
            int checked = 0;
            while (result.next()) {
                checked++;
                assertThat(result.getBoolean("relrowsecurity"))
                        .as(result.getString("relname") + " must not have RLS enabled — table ownership is the boundary")
                        .isFalse();
            }
            assertEquals(4, checked, "expected to check all four ledger base tables");
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static String insertJournalEntrySql(UUID entryId) {
        return """
                INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                VALUES ('%s', 'POST', CURRENT_DATE)
                """.formatted(entryId);
    }

    private static String insertJournalLineSql(UUID entryId, int lineNo, UUID accountId, long amountMinor) {
        return """
                INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, at)
                VALUES ('%s', %d, '%s', 'EUR', %d, now())
                """.formatted(entryId, lineNo, accountId, amountMinor);
    }

    private UUID seedLiquidityAccount() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                    VALUES ('%s', gen_random_uuid(), gen_random_uuid(), 'EUR', 0)
                    """.formatted(id));
        }
        return id;
    }

    private int countJournalLines(UUID entryId) throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            var result = statement.executeQuery(
                    "SELECT count(*) FROM ledger.journal_lines WHERE entry_id = '" + entryId + "'");
            result.next();
            return result.getInt(1);
        }
    }

    private static Connection ledgerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ledger_role", "dev-only-ledger");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
