package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * EPIC-32 Story 32.3: dedicated, exhaustive evidence for the deferred constraint trigger
 * ({@code ledger.check_entry_balance()}/{@code trg_entry_balance}, built in Story 32.1's V26
 * migration — no new migration needed here, confirmed by audit) — Σ({@code amount_minor})=0 per
 * {@code entry_id}, checked at COMMIT, never per-statement. Real PostgreSQL 18 Testcontainer, real
 * multi-statement transactions.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class UnbalancedEntryAtCommitTest {

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
    void singleUnbalancedLineIsRejectedAtCommitAndLeavesNoRows() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID account = seedLiquidityAccount();

        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertEntrySql(entryId));
                // The INSERT itself succeeds — the trigger is deferred, checked only at COMMIT.
                statement.executeUpdate(insertLineSql(entryId, 1, account, -1000));
            }
            assertThatThrownBy(connection::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("does not balance");
        }

        assertThat(countLines(entryId)).isZero();
    }

    @Test
    void balancedEntryCommitsSuccessfully() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();

        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertEntrySql(entryId));
                statement.executeUpdate(insertLineSql(entryId, 1, accountA, -500));
                statement.executeUpdate(insertLineSql(entryId, 2, accountB, 500));
            }
            connection.commit();
        }

        assertThat(countLines(entryId)).isEqualTo(2);
    }

    @Test
    void multiStatementTransactionMayBeTemporarilyUnbalancedBeforeCommit() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();
        UUID accountC = seedLiquidityAccount();

        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertEntrySql(entryId));
                statement.executeUpdate(insertLineSql(entryId, 1, accountA, -300));
                // At this point the entry is unbalanced (-300) — legal, because the trigger is
                // deferred and only checks the final state at COMMIT, one line at a time inserted.
                statement.executeUpdate(insertLineSql(entryId, 2, accountB, 100));
                statement.executeUpdate(insertLineSql(entryId, 3, accountC, 200));
                // Now -300 + 100 + 200 = 0.
            }
            connection.commit();
        }

        assertThat(countLines(entryId)).isEqualTo(3);
    }

    @Test
    void twoEntryIdsOneBalancedOneUnbalancedRollsBackTheWholeTransaction() throws Exception {
        UUID balancedEntry = UUID.randomUUID();
        UUID unbalancedEntry = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();

        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertEntrySql(balancedEntry));
                statement.executeUpdate(insertLineSql(balancedEntry, 1, accountA, -100));
                statement.executeUpdate(insertLineSql(balancedEntry, 2, accountB, 100));

                statement.executeUpdate(insertEntrySql(unbalancedEntry));
                statement.executeUpdate(insertLineSql(unbalancedEntry, 1, accountA, -50));
            }
            assertThatThrownBy(connection::commit).isInstanceOf(SQLException.class);
        }

        assertThat(countLines(balancedEntry))
                .as("the balanced entry's lines must also roll back — one transaction, one outcome")
                .isZero();
        assertThat(countLines(unbalancedEntry)).isZero();
    }

    @Test
    void negativeAndPositiveAmountsSummingToExactlyZeroCommit() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();
        UUID accountC = seedLiquidityAccount();

        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertEntrySql(entryId));
                statement.executeUpdate(insertLineSql(entryId, 1, accountA, -123456789L));
                statement.executeUpdate(insertLineSql(entryId, 2, accountB, 100000000L));
                statement.executeUpdate(insertLineSql(entryId, 3, accountC, 23456789L));
            }
            connection.commit();
        }

        assertThat(countLines(entryId)).isEqualTo(3);
    }

    @Test
    void representativeLargeBigintBoundaryValuesBalanceCorrectly() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID accountA = seedLiquidityAccount();
        UUID accountB = seedLiquidityAccount();
        long large = 1_000_000_000_000L; // 1e12 minor units — representative "large payment", far below Long.MAX_VALUE

        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertEntrySql(entryId));
                statement.executeUpdate(insertLineSql(entryId, 1, accountA, -large));
                statement.executeUpdate(insertLineSql(entryId, 2, accountB, large));
            }
            connection.commit();
        }

        assertThat(countLines(entryId)).isEqualTo(2);
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static String insertEntrySql(UUID entryId) {
        return """
                INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                VALUES ('%s', 'POST', CURRENT_DATE)
                """.formatted(entryId);
    }

    private static String insertLineSql(UUID entryId, int lineNo, UUID accountId, long amountMinor) {
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
