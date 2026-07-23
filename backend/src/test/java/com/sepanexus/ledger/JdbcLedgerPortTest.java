package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.shared.ClockPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** ADR-N10 runtime proof: only the ledger writer creates balanced, single-terminal money effects. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class JdbcLedgerPortTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    private static JdbcLedgerPort port;

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection c = admin(); Statement s = c.createStatement()) {
            s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        ClockPort fixedClock = () -> java.time.Instant.parse("2026-07-20T10:15:30Z");
        port = new JdbcLedgerPort(
                new LedgerConnectionFactory(POSTGRES.getJdbcUrl(), "ledger_role", "dev-only-ledger"), fixedClock);
    }

    @Test
    void reserveThenPostMovesAvailableAndReservedOnceAndReplaysSameCommand() throws Exception {
        UUID debtor = account(1_000); UUID creditor = account(100); UUID attempt = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) port.reserve(attempt, payment, debtor, 400);
        assertThat(balance(debtor)).containsExactly(600L, 400L);
        assertThat(lines(reserved.reserveEntryId())).containsExactly(
                new JournalLine("AVAILABLE", -400L), new JournalLine("RESERVED", 400L));

        UUID command = UUID.randomUUID();
        LedgerPort.TerminalResult posted = port.post(reserved.reservationId(), creditor, command);
        LedgerPort.TerminalResult replay = port.post(reserved.reservationId(), creditor, command);
        assertThat(replay).isEqualTo(posted);
        assertThat(balance(debtor)).containsExactly(600L, 0L);
        assertThat(balance(creditor)).containsExactly(500L, 0L);
        assertThat(lines(posted.terminalEntryId())).containsExactly(
                new JournalLine("RESERVED", -400L), new JournalLine("AVAILABLE", 400L));
        assertThatThrownBy(() -> port.post(reserved.reservationId(), creditor, UUID.randomUUID()))
                .isInstanceOf(ReservationTerminalConflictException.class);
        assertThat(countEntriesForPayment(payment)).isEqualTo(2);
    }

    @Test
    void releaseRestoresFundsAndInsufficientLiquidityCreatesNoReservation() throws Exception {
        UUID debtor = account(250);
        UUID insufficientPayment = UUID.randomUUID();
        assertThat(port.reserve(UUID.randomUUID(), insufficientPayment, debtor, 251))
                .isInstanceOf(LedgerPort.InsufficientLiquidity.class);
        assertThat(balance(debtor)).containsExactly(250L, 0L);
        assertThat(countEntriesForPayment(insufficientPayment)).isZero();

        LedgerPort.Reserved reserved = (LedgerPort.Reserved) port.reserve(UUID.randomUUID(), UUID.randomUUID(), debtor, 200);
        LedgerPort.TerminalResult released = port.release(reserved.reservationId(), UUID.randomUUID());
        assertThat(released.state()).isEqualTo(ReservationState.RELEASED);
        assertThat(balance(debtor)).containsExactly(250L, 0L);
        assertThat(lines(released.terminalEntryId())).containsExactly(
                new JournalLine("RESERVED", -200L), new JournalLine("AVAILABLE", 200L));
    }

    private static UUID account(long available) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                VALUES (?, gen_random_uuid(), gen_random_uuid(), 'EUR', ?)
                """)) { s.setObject(1, id); s.setLong(2, available); s.executeUpdate(); }
        return id;
    }
    private static long[] balance(UUID account) throws Exception {
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement(
                "SELECT available_minor, reserved_minor FROM ledger.liquidity_accounts WHERE id = ?")) {
            s.setObject(1, account); try (ResultSet r = s.executeQuery()) { r.next(); return new long[] {r.getLong(1), r.getLong(2)}; }
        }
    }
    private static java.util.List<JournalLine> lines(UUID entry) throws Exception {
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement(
                "SELECT balance_component, amount_minor FROM ledger.journal_lines WHERE entry_id = ? ORDER BY line_no")) {
            s.setObject(1, entry); try (ResultSet r = s.executeQuery()) { java.util.List<JournalLine> out = new java.util.ArrayList<>(); while (r.next()) out.add(new JournalLine(r.getString(1), r.getLong(2))); return out; }
        }
    }
    private static int countEntriesForPayment(UUID payment) throws Exception { try (Connection c = admin(); PreparedStatement s = c.prepareStatement("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = ?")) { s.setObject(1, payment); try (ResultSet r = s.executeQuery()) { r.next(); return r.getInt(1); } } }
    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private record JournalLine(String component, long amountMinor) { }
}
