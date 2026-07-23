package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
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

/** PostgreSQL 18 proof for Story 32.5's account and one-currency journal invariants. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class JournalAccountCurrencyIntegrityTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @BeforeAll static void migrate() throws Exception {
        try (var c = admin(); var s = c.createStatement()) {
            s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    @Test void rejectsUnknownAccount() throws Exception {
        UUID entry = entry();
        SQLException error = assertThrows(SQLException.class, () -> line(entry, 1, UUID.randomUUID(), "EUR", 0));
        assertThat(error.getSQLState()).isEqualTo("23503");
    }

    @Test void rejectsAccountCurrencyMismatch() throws Exception {
        UUID entry = entry();
        UUID eur = account("EUR");
        SQLException error = assertThrows(SQLException.class, () -> line(entry, 1, eur, "PLN", 0));
        assertThat(error.getSQLState()).isEqualTo("23503");
    }

    @Test void rejectsMixedCurrenciesEvenWhenEachCurrencyBalances() throws Exception {
        UUID entry = entry();
        UUID eurA = account("EUR"); UUID eurB = account("EUR");
        UUID plnA = account("PLN"); UUID plnB = account("PLN");
        SQLException error = assertThrows(SQLException.class, () -> {
            try (var c = admin(); var s = c.createStatement()) {
                c.setAutoCommit(false);
                insertLine(s, entry, 1, eurA, "EUR", -100);
                insertLine(s, entry, 2, eurB, "EUR", 100);
                insertLine(s, entry, 3, plnA, "PLN", -200);
                insertLine(s, entry, 4, plnB, "PLN", 200);
                c.commit();
            }
        });
        assertThat(error.getMessage()).contains("one currency");
        assertThat(countLines(entry)).isZero();
    }

    @Test void commitsOneCurrencyOnlyWhenBalancedAtCommit() throws Exception {
        UUID entry = entry(); UUID a = account("EUR"); UUID b = account("EUR");
        try (var c = admin(); var s = c.createStatement()) {
            c.setAutoCommit(false);
            insertLine(s, entry, 1, a, "EUR", -100);
            insertLine(s, entry, 2, b, "EUR", 100);
            c.commit();
        }
        assertThat(countLines(entry)).isEqualTo(2);
    }

    @Test void stillRejectsOneCurrencyUnbalancedAtCommitAndRollsBack() throws Exception {
        UUID entry = entry(); UUID a = account("EUR");
        SQLException error = assertThrows(SQLException.class, () -> {
            try (var c = admin(); var s = c.createStatement()) {
                c.setAutoCommit(false); insertLine(s, entry, 1, a, "EUR", -1); c.commit();
            }
        });
        assertThat(error.getMessage()).contains("does not balance");
        assertThat(countLines(entry)).isZero();
    }

    private static UUID entry() throws Exception {
        UUID id = UUID.randomUUID();
        try (var c = admin(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO ledger.journal_entries (id, entry_type, business_date) VALUES ('%s', 'POST', CURRENT_DATE)".formatted(id)); }
        return id;
    }
    private static UUID account(String currency) throws Exception {
        UUID id = UUID.randomUUID();
        try (var c = admin(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor) VALUES ('%s', gen_random_uuid(), gen_random_uuid(), '%s', 0)".formatted(id, currency)); }
        return id;
    }
    private static void line(UUID entry, int n, UUID account, String currency, long amount) throws Exception { try (var c = admin(); var s = c.createStatement()) { insertLine(s, entry, n, account, currency, amount); } }
    private static void insertLine(Statement s, UUID entry, int n, UUID account, String currency, long amount) throws SQLException { s.executeUpdate("INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, at) VALUES ('%s', %d, '%s', '%s', %d, now())".formatted(entry, n, account, currency, amount)); }
    private static int countLines(UUID entry) throws Exception { try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM ledger.journal_lines WHERE entry_id = '%s'".formatted(entry))) { r.next(); return r.getInt(1); } }
    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}
