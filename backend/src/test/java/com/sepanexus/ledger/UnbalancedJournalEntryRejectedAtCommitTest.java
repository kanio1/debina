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
 * EPIC-13 Story 13.4: {@code ledger}'s own ownership evidence that the balance invariant is
 * enforced at the database layer — a distinct, EPIC-13-owned proof (its own Testcontainers
 * instance, not sharing a live database with {@code EPIC-32}'s {@link UnbalancedEntryAtCommitTest})
 * of the same underlying {@code ledger.check_entry_balance()} trigger, framed as this epic's own
 * "one-writer/ownership-boundary" concern rather than duplicating that test's exhaustive edge-case
 * matrix — {@code EPIC-32} Story 32.3 owns the full matrix; this story owns confirming the
 * invariant holds from {@code ledger_role}'s own perspective as the schema's sole writer.
 *
 * <p>Story 13.4's own task list (title mentions "reversal", but the actual checked-off tasks do
 * not) covers exactly two things: this test and {@link JournalLinesImmutabilityTest} (shared with
 * EPIC-32 Story 32.3) — reversal itself is EPIC-32 Story 32.4's separate, currently
 * {@code [CAPABILITY-BLOCKED]} scope, not this story's.
 */
@Testcontainers
class UnbalancedJournalEntryRejectedAtCommitTest {

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
    void ledgerRoleAsSoleWriterCannotCommitAnUnbalancedEntry() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID account = seedLiquidityAccount();

        try (Connection connection = ledgerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                        VALUES ('%s', 'POST', CURRENT_DATE)
                        """.formatted(entryId));
                statement.executeUpdate("""
                        INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, at)
                        VALUES ('%s', 1, '%s', 'EUR', -250, now())
                        """.formatted(entryId, account));
            }
            assertThatThrownBy(connection::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("does not balance");
        }

        assertThat(countLines(entryId)).isZero();
    }

    @Test
    void ledgerRoleAsSoleWriterCanCommitABalancedEntry() throws Exception {
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
                        VALUES ('%s', 1, '%s', 'EUR', -250, now())
                        """.formatted(entryId, accountA));
                statement.executeUpdate("""
                        INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, at)
                        VALUES ('%s', 2, '%s', 'EUR', 250, now())
                        """.formatted(entryId, accountB));
            }
            connection.commit();
        }

        assertThat(countLines(entryId)).isEqualTo(2);
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
