package com.sepanexus.ledger;

import com.sepanexus.shared.ClockPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Transactional ADR-N10 reservation implementation. Each method uses one {@code ledger_role}
 * connection, locks rows before inspecting them, and commits the journal, balance and reservation
 * transition together.
 */
@Component
public class JdbcLedgerPort implements LedgerPort {
    private final LedgerConnectionFactory connections;
    private final ClockPort clockPort;

    public JdbcLedgerPort(LedgerConnectionFactory connections, ClockPort clockPort) {
        this.connections = connections;
        this.clockPort = clockPort;
    }

    @Override
    public ReserveResult reserve(UUID settlementAttemptId, UUID paymentId, UUID debtorAccountId, long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                Reservation existing = lockReservationByAttempt(connection, settlementAttemptId);
                if (existing != null) {
                    connection.commit();
                    return new Reserved(existing.id(), existing.reserveEntryId());
                }
                Account debtor = lockAccounts(connection, List.of(debtorAccountId)).getFirst();
                // A concurrent reservation for this attempt can have committed while this call
                // waited for the debtor row. Re-read under that same account lock before any
                // money effect so the unique attempt invariant is an idempotent result, not an
                // avoidable duplicate-key exception.
                existing = lockReservationByAttempt(connection, settlementAttemptId);
                if (existing != null) {
                    connection.commit();
                    return new Reserved(existing.id(), existing.reserveEntryId());
                }
                if (debtor.availableMinor() < amountMinor) {
                    connection.commit();
                    return new InsufficientLiquidity();
                }
                UUID reserveEntryId = insertEntry(connection, "RESERVE", paymentId, clockPort);
                UUID reservationId = UUID.randomUUID();
                insertReservation(connection, reservationId, settlementAttemptId, paymentId, debtorAccountId,
                        amountMinor, debtor.currency(), reserveEntryId);
                insertLines(connection, reserveEntryId, debtorAccountId, debtor.currency(), amountMinor,
                        "AVAILABLE", "RESERVED", clockPort);
                updateAccount(connection, debtorAccountId, -amountMinor, amountMinor);
                connection.commit();
                return new Reserved(reservationId, reserveEntryId);
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not reserve liquidity", exception);
        }
    }

    @Override
    public TerminalResult post(UUID reservationId, UUID creditorAccountId, UUID commandId) {
        return consume(reservationId, creditorAccountId, commandId, ReservationState.POSTED);
    }

    @Override
    public TerminalResult release(UUID reservationId, UUID commandId) {
        return consume(reservationId, null, commandId, ReservationState.RELEASED);
    }

    private TerminalResult consume(UUID reservationId, UUID creditorAccountId, UUID commandId, ReservationState target) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                Reservation reservation = lockReservation(connection, reservationId);
                if (reservation == null) {
                    throw new IllegalArgumentException("Unknown reservation " + reservationId);
                }
                if (reservation.state() != ReservationState.ACTIVE) {
                    if (commandId.equals(reservation.terminalCommandId())) {
                        connection.commit();
                        return new TerminalResult(reservation.id(), reservation.terminalEntryId(), reservation.state());
                    }
                    throw new ReservationTerminalConflictException(reservationId, commandId);
                }
                List<Account> accounts = lockAccounts(connection, creditorAccountId == null
                        ? List.of(reservation.debtorAccountId())
                        : List.of(reservation.debtorAccountId(), creditorAccountId));
                Account debtor = accounts.stream().filter(account -> account.id().equals(reservation.debtorAccountId()))
                        .findFirst().orElseThrow();
                if (!debtor.currency().equals(reservation.currency())) {
                    throw new IllegalStateException("Reservation currency no longer matches debtor account");
                }
                if (target == ReservationState.POSTED) {
                    Account creditor = accounts.stream().filter(account -> account.id().equals(creditorAccountId))
                            .findFirst().orElseThrow();
                    if (!creditor.currency().equals(reservation.currency())) {
                        throw new IllegalArgumentException("Creditor account currency must match reservation currency");
                    }
                    UUID entryId = insertEntry(connection, "POST", reservation.paymentId(), clockPort);
                    insertTransferLines(connection, entryId, debtor.id(), creditor.id(), reservation.currency(),
                            reservation.amountMinor(), "RESERVED", "AVAILABLE", clockPort);
                    updateAccount(connection, debtor.id(), 0, -reservation.amountMinor());
                    updateAccount(connection, creditor.id(), reservation.amountMinor(), 0);
                    terminalize(connection, reservationId, target, entryId, commandId, clockPort);
                    connection.commit();
                    return new TerminalResult(reservationId, entryId, target);
                }
                UUID entryId = insertEntry(connection, "RELEASE", reservation.paymentId(), clockPort);
                insertLines(connection, entryId, debtor.id(), reservation.currency(), reservation.amountMinor(),
                        "RESERVED", "AVAILABLE", clockPort);
                updateAccount(connection, debtor.id(), reservation.amountMinor(), -reservation.amountMinor());
                terminalize(connection, reservationId, target, entryId, commandId, clockPort);
                connection.commit();
                return new TerminalResult(reservationId, entryId, target);
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not consume reservation", exception);
        }
    }

    private static List<Account> lockAccounts(Connection connection, List<UUID> ids) throws SQLException {
        List<UUID> ordered = ids.stream().distinct().sorted().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(ordered.size(), "?"));
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, currency, available_minor, reserved_minor FROM ledger.liquidity_accounts
                WHERE id IN (%s) ORDER BY id FOR UPDATE
                """.formatted(placeholders))) {
            for (int index = 0; index < ordered.size(); index++) statement.setObject(index + 1, ordered.get(index));
            try (ResultSet result = statement.executeQuery()) {
                java.util.ArrayList<Account> accounts = new java.util.ArrayList<>();
                while (result.next()) accounts.add(new Account((UUID) result.getObject(1), result.getString(2),
                        result.getLong(3), result.getLong(4)));
                if (accounts.size() != ordered.size()) throw new IllegalArgumentException("Unknown liquidity account");
                return accounts;
            }
        }
    }

    private static UUID insertEntry(Connection connection, String type, UUID paymentId, ClockPort clockPort)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger.journal_entries (id, entry_type, payment_id, business_date)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setObject(1, id); statement.setString(2, type); statement.setObject(3, paymentId);
            statement.setObject(4, LocalDate.ofInstant(clockPort.now(), ZoneOffset.UTC)); statement.executeUpdate();
        }
        return id;
    }

    private static void insertReservation(Connection c, UUID id, UUID attempt, UUID payment, UUID debtor, long amount,
            String currency, UUID reserveEntry) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO ledger.reservations (id, settlement_attempt_id, payment_id, debtor_account_id, amount_minor,
                    currency, state, reserve_entry_id) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """)) {
            s.setObject(1, id); s.setObject(2, attempt); s.setObject(3, payment); s.setObject(4, debtor);
            s.setLong(5, amount); s.setString(6, currency); s.setObject(7, reserveEntry); s.executeUpdate();
        }
    }

    private static void insertLines(Connection c, UUID entry, UUID account, String currency, long amount,
            String debitComponent, String creditComponent, ClockPort clockPort) throws SQLException {
        insertLine(c, entry, 1, account, currency, -amount, debitComponent, clockPort);
        insertLine(c, entry, 2, account, currency, amount, creditComponent, clockPort);
    }

    private static void insertTransferLines(Connection c, UUID entry, UUID debtor, UUID creditor, String currency,
            long amount, String debitComponent, String creditComponent, ClockPort clockPort) throws SQLException {
        insertLine(c, entry, 1, debtor, currency, -amount, debitComponent, clockPort);
        insertLine(c, entry, 2, creditor, currency, amount, creditComponent, clockPort);
    }

    private static void insertLine(Connection c, UUID entry, int line, UUID account, String currency, long amount,
            String component, ClockPort clockPort) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, balance_component, at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            s.setObject(1, entry); s.setInt(2, line); s.setObject(3, account); s.setString(4, currency);
            s.setLong(5, amount); s.setString(6, component);
            s.setTimestamp(7, Timestamp.from(clockPort.now())); s.executeUpdate();
        }
    }

    private static void updateAccount(Connection c, UUID id, long availableDelta, long reservedDelta) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                UPDATE ledger.liquidity_accounts SET available_minor = available_minor + ?,
                    reserved_minor = reserved_minor + ?, version = version + 1 WHERE id = ?
                """)) {
            s.setLong(1, availableDelta); s.setLong(2, reservedDelta); s.setObject(3, id);
            if (s.executeUpdate() != 1) throw new IllegalStateException("Liquidity account disappeared");
        }
    }

    private static void terminalize(Connection c, UUID id, ReservationState state, UUID entry, UUID command,
            ClockPort clockPort) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                UPDATE ledger.reservations SET state = ?, terminal_entry_id = ?, terminal_command_id = ?,
                    completed_at = ?, version = version + 1 WHERE id = ? AND state = 'ACTIVE'
                """)) {
            s.setString(1, state.name()); s.setObject(2, entry); s.setObject(3, command);
            s.setTimestamp(4, Timestamp.from(clockPort.now())); s.setObject(5, id);
            if (s.executeUpdate() != 1) throw new IllegalStateException("Reservation disappeared");
        }
    }

    private static Reservation lockReservationByAttempt(Connection c, UUID attempt) throws SQLException {
        return queryReservation(c, "SELECT * FROM ledger.reservations WHERE settlement_attempt_id = ? FOR UPDATE", attempt);
    }
    private static Reservation lockReservation(Connection c, UUID id) throws SQLException {
        return queryReservation(c, "SELECT * FROM ledger.reservations WHERE id = ? FOR UPDATE", id);
    }
    private static Reservation queryReservation(Connection c, String sql, UUID id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) { s.setObject(1, id); try (ResultSet r = s.executeQuery()) {
            if (!r.next()) return null;
            return new Reservation((UUID) r.getObject("id"), (UUID) r.getObject("payment_id"),
                    (UUID) r.getObject("debtor_account_id"), r.getLong("amount_minor"), r.getString("currency"),
                    ReservationState.valueOf(r.getString("state")), (UUID) r.getObject("reserve_entry_id"),
                    (UUID) r.getObject("terminal_entry_id"), (UUID) r.getObject("terminal_command_id"));
        }}
    }
    private record Account(UUID id, String currency, long availableMinor, long reservedMinor) { }
    private record Reservation(UUID id, UUID paymentId, UUID debtorAccountId, long amountMinor, String currency,
            ReservationState state, UUID reserveEntryId, UUID terminalEntryId, UUID terminalCommandId) { }
}
