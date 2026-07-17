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
 * EPIC-32 Story 32.3 + EPIC-13 Story 13.4 (shared, ownership evidence for both): {@code
 * ledger.journal_lines} is append-only — {@code ledger_role} may {@code INSERT}/{@code SELECT},
 * never {@code UPDATE}/{@code DELETE}/{@code TRUNCATE} (§4.5 "the only way to correct a mistake is
 * a new reversal entry" — never a mutation). Real grants against a real PostgreSQL 18
 * Testcontainer, real SQLSTATE checks.
 */
@Testcontainers
class JournalLinesImmutabilityTest {

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
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @Test
    void ledgerRoleCanInsertJournalLines() throws Exception {
        UUID entryId = seedBalancedEntry();
        assertEquals(2, countLines(entryId));
    }

    @Test
    void ledgerRoleCanSelectJournalLines() throws Exception {
        UUID entryId = seedBalancedEntry();
        try (Connection connection = ledgerConnection(); Statement statement = connection.createStatement()) {
            var result = statement.executeQuery("SELECT * FROM ledger.journal_lines WHERE entry_id = '" + entryId + "'");
            int rows = 0;
            while (result.next()) {
                rows++;
            }
            assertEquals(2, rows);
        }
    }

    @Test
    void ledgerRoleCannotUpdateJournalLines() throws Exception {
        UUID entryId = seedBalancedEntry();
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = ledgerConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE ledger.journal_lines SET amount_minor = 1 WHERE entry_id = '" + entryId + "'");
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    @Test
    void ledgerRoleCannotDeleteJournalLines() throws Exception {
        UUID entryId = seedBalancedEntry();
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = ledgerConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM ledger.journal_lines WHERE entry_id = '" + entryId + "'");
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    @Test
    void ledgerRoleCannotTruncateJournalLines() {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = ledgerConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("TRUNCATE ledger.journal_lines");
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    // -- helpers ---------------------------------------------------------------------------------

    private UUID seedBalancedEntry() throws Exception {
        // autoCommit defaults to true, meaning each statement is its own implicit transaction —
        // since the balance trigger is DEFERRABLE INITIALLY DEFERRED (checked at COMMIT, not per
        // statement), inserting the two lines under autocommit would check (and reject) the first,
        // still-unbalanced line before the second is ever inserted. Explicit transaction control
        // is required here, exactly like UnbalancedEntryAtCommitTest.
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();
        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                        VALUES ('%s', 'POST', CURRENT_DATE)
                        """.formatted(entryId));
                statement.executeUpdate("""
                        INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, at)
                        VALUES ('%s', 1, '%s', 'EUR', -100, now())
                        """.formatted(entryId, accountA));
                statement.executeUpdate("""
                        INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, at)
                        VALUES ('%s', 2, '%s', 'EUR', 100, now())
                        """.formatted(entryId, accountB));
            }
            connection.commit();
        }
        return entryId;
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

    private int countLines(UUID entryId) throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            var result = statement.executeQuery("SELECT count(*) FROM ledger.journal_lines WHERE entry_id = '" + entryId + "'");
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
