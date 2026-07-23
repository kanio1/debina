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
 * EPIC-13 Story 13.3: {@code settlement_role} has no write grant whatsoever on {@code ledger.*} —
 * settlement will only ever move money through {@code LedgerPort} (a future story), never direct
 * SQL. {@code SELECT} is also denied: no source document grants {@code settlement_role} read
 * access to any ledger table, so this fails closed rather than assuming one.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class SettlementRoleNoLedgerGrantTest {

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
    void settlementRoleCannotInsertIntoLiquidityAccounts() {
        assertDenied(() -> """
                INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                VALUES ('%s', gen_random_uuid(), gen_random_uuid(), 'EUR', 0)
                """.formatted(UUID.randomUUID()));
    }

    @Test
    void settlementRoleCannotInsertIntoJournalEntries() {
        assertDenied(() -> """
                INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                VALUES ('%s', 'POST', CURRENT_DATE)
                """.formatted(UUID.randomUUID()));
    }

    @Test
    void settlementRoleCannotInsertOrSelectReservations() {
        assertDenied(() -> """
                INSERT INTO ledger.reservations (settlement_attempt_id, payment_id, debtor_account_id,
                    amount_minor, currency, state, reserve_entry_id)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 1, 'EUR', 'ACTIVE', gen_random_uuid())
                """);
        assertDenied(() -> "SELECT * FROM ledger.reservations");
    }

    @Test
    void settlementRoleCannotUpdateLiquidityAccounts() throws Exception {
        UUID accountId = seedLiquidityAccount();
        assertDenied(() -> "UPDATE ledger.liquidity_accounts SET available_minor = 999 WHERE id = '" + accountId + "'");
    }

    @Test
    void settlementRoleCannotDeleteFromLiquidityAccounts() throws Exception {
        UUID accountId = seedLiquidityAccount();
        assertDenied(() -> "DELETE FROM ledger.liquidity_accounts WHERE id = '" + accountId + "'");
    }

    @Test
    void settlementRoleCannotTruncateLiquidityAccounts() {
        assertDenied(() -> "TRUNCATE ledger.liquidity_accounts");
    }

    @Test
    void settlementRoleCannotSelectFromLedgerTables() throws Exception {
        seedLiquidityAccount();
        assertDenied(() -> "SELECT * FROM ledger.liquidity_accounts");
    }

    private void assertDenied(java.util.function.Supplier<String> sql) {
        SQLException exception = assertThrows(SQLException.class, () -> {
            try (Connection connection = settlementConnection(); Statement statement = connection.createStatement()) {
                statement.execute(sql.get());
            }
        });
        assertEquals("42501", exception.getSQLState());
    }

    private UUID seedLiquidityAccount() throws Exception {
        // test_admin (superuser), not ledger_role: ledger_role's own grant on liquidity_accounts
        // is deliberately SELECT+UPDATE only (no INSERT, §4.7) — this seed step only needs to get
        // a row into place for the UPDATE/DELETE/SELECT denial checks below, it is not itself
        // testing ledger_role's own grant shape.
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                    VALUES ('%s', gen_random_uuid(), gen_random_uuid(), 'EUR', 0)
                    """.formatted(id));
        }
        return id;
    }

    private static Connection settlementConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "settlement_role", "dev-only-settlement");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
