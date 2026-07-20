package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fresh and V30-upgrade proof for ADR-N10's reservation DDL and compatibility expansion. */
@Testcontainers
class LedgerReservationMigrationProofTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @Test
    void v30UpgradePreservesExistingLedgerEvidenceAndAddsReservationGuards() throws Exception {
        createMigrationRole();
        migrate("30");

        UUID account = UUID.randomUUID();
        UUID entry = UUID.randomUUID();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("""
                    INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                    VALUES ('%s', gen_random_uuid(), gen_random_uuid(), 'EUR', 900)
                    """.formatted(account));
            statement.executeUpdate("""
                    INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                    VALUES ('%s', 'POST', CURRENT_DATE)
                    """.formatted(entry));
            statement.executeUpdate("""
                    INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, at)
                    VALUES ('%s', 1, '%s', 'EUR', 0, now())
                    """.formatted(entry, account));
            connection.commit();
        }

        migrate(null);

        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            var evidence = statement.executeQuery("""
                    SELECT balance_component FROM ledger.journal_lines WHERE entry_id = '%s'
                    """.formatted(entry));
            evidence.next();
            assertThat(evidence.getString(1)).isEqualTo("AVAILABLE");

            UUID reserveEntry = entry("RESERVE");
            UUID reservation = UUID.randomUUID();
            statement.executeUpdate("""
                    INSERT INTO ledger.reservations (id, settlement_attempt_id, payment_id, debtor_account_id,
                        amount_minor, currency, state, reserve_entry_id)
                    VALUES ('%s', '%s', gen_random_uuid(), '%s', 1, 'EUR', 'ACTIVE', '%s')
                    """.formatted(reservation, UUID.randomUUID(), account, reserveEntry));

            SQLException positiveAmount = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO ledger.reservations (settlement_attempt_id, payment_id, debtor_account_id,
                        amount_minor, currency, state, reserve_entry_id)
                    VALUES (gen_random_uuid(), gen_random_uuid(), '%s', 0, 'EUR', 'ACTIVE', '%s')
                    """.formatted(account, entry("RESERVE"))));
            assertThat(positiveAmount.getSQLState()).isEqualTo("23514");

            SQLException currencyMatch = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO ledger.reservations (settlement_attempt_id, payment_id, debtor_account_id,
                        amount_minor, currency, state, reserve_entry_id)
                    VALUES (gen_random_uuid(), gen_random_uuid(), '%s', 1, 'USD', 'ACTIVE', '%s')
                    """.formatted(account, entry("RESERVE"))));
            assertThat(currencyMatch.getSQLState()).isEqualTo("23503");

            SQLException duplicateAttempt = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO ledger.reservations (settlement_attempt_id, payment_id, debtor_account_id,
                        amount_minor, currency, state, reserve_entry_id)
                    SELECT settlement_attempt_id, gen_random_uuid(), debtor_account_id, 1, currency, 'ACTIVE', '%s'
                    FROM ledger.reservations WHERE id = '%s'
                    """.formatted(entry("RESERVE"), reservation)));
            assertThat(duplicateAttempt.getSQLState()).isEqualTo("23505");

            UUID terminalEntry = entry("POST");
            statement.executeUpdate("""
                    UPDATE ledger.reservations SET state = 'POSTED', terminal_entry_id = '%s',
                        terminal_command_id = gen_random_uuid(), completed_at = now()
                    WHERE id = '%s'
                    """.formatted(terminalEntry, reservation));
            SQLException secondTerminalTransition = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    UPDATE ledger.reservations SET state = 'RELEASED', terminal_entry_id = '%s',
                        terminal_command_id = gen_random_uuid(), completed_at = now()
                    WHERE id = '%s'
                    """.formatted(entry("RELEASE"), reservation)));
            assertThat(secondTerminalTransition.getMessage()).contains("already terminal");
        }
    }

    private void createMigrationRole() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
    }

    private UUID entry(String type) throws Exception {
        UUID entry = UUID.randomUUID();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                    VALUES ('%s', '%s', CURRENT_DATE)
                    """.formatted(entry, type));
        }
        return entry;
    }

    private void migrate(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
