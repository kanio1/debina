package com.sepanexus.settlement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Settlement-owned persistence boundary for the source's G6 membership lock, cycle FSM and P8
 * one-statement netting. It has no ledger/payment/repository dependency and opens only the
 * settlement runtime role connection.
 */
public final class DeferredSettlementCycleService {

    private final SettlementConnectionFactory connections;

    public DeferredSettlementCycleService(SettlementConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public CycleResult createOpenCycle(UUID cycleId, UUID profileId, LocalDate businessDate, short sessionNo, Instant cutoffAt) {
        require(cycleId, "cycleId"); require(profileId, "profileId"); require(businessDate, "businessDate"); require(cutoffAt, "cutoffAt");
        if (sessionNo < 0) throw new IllegalArgumentException("sessionNo must not be negative");
        return transaction(connection -> {
            Cycle existing = cycle(connection, cycleId, false);
            if (existing != null) {
                if (existing.profileId().equals(profileId) && existing.businessDate().equals(businessDate)
                        && existing.sessionNo() == sessionNo && existing.cutoffAt().equals(cutoffAt)) {
                    return new CycleResult(cycleId, existing.state(), true);
                }
                throw new DeferredCycleException("cycle id conflicts with different immutable attributes");
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO settlement.settlement_cycles
                        (id, profile_id, business_date, session_no, state, cutoff_at)
                    VALUES (?, ?, ?, ?, 'OPEN', ?)
                    """)) {
                insert.setObject(1, cycleId); insert.setObject(2, profileId); insert.setObject(3, businessDate);
                insert.setShort(4, sessionNo); insert.setTimestamp(5, Timestamp.from(cutoffAt)); insert.executeUpdate();
            }
            return new CycleResult(cycleId, DeferredCycleState.OPEN, false);
        });
    }

    /** G6: the cycle row is locked before membership is inspected or inserted. */
    public AssignmentResult assign(DeferredCycleAssignment command) {
        Objects.requireNonNull(command, "command");
        return transaction(connection -> {
            Cycle cycle = requiredCycle(connection, command.cycleId(), true);
            if (cycle.state() != DeferredCycleState.OPEN) {
                throw new DeferredCycleException("cycle " + command.cycleId() + " does not accept membership in state " + cycle.state());
            }
            if (!cycle.profileId().equals(command.profileId())) {
                throw new DeferredCycleException("assignment profile does not match the cycle profile");
            }
            Item existing = itemForPayment(connection, command.cycleId(), command.paymentId());
            if (existing != null) {
                if (existing.matches(command)) return new AssignmentResult(existing.id(), true);
                throw new DeferredCycleException("payment already has conflicting cycle membership");
            }
            insertAttempt(connection, command);
            UUID itemId = UUID.randomUUID();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO settlement.settlement_items
                        (id, cycle_id, settlement_attempt_id, payment_id, debtor_participant_id,
                         creditor_participant_id, amount_minor)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setObject(1, itemId); insert.setObject(2, command.cycleId()); insert.setObject(3, command.settlementAttemptId());
                insert.setObject(4, command.paymentId()); insert.setObject(5, command.debtorParticipantId());
                insert.setObject(6, command.creditorParticipantId()); insert.setLong(7, command.amountMinor()); insert.executeUpdate();
            }
            try (PreparedStatement event = connection.prepareStatement("""
                    INSERT INTO settlement.deferred_cycle_attempt_events
                        (settlement_attempt_id, seq, state, at)
                    VALUES (?, 1, 'CYCLE_ASSIGNED', now())
                    """)) {
                event.setObject(1, command.settlementAttemptId()); event.executeUpdate();
            }
            return new AssignmentResult(itemId, false);
        });
    }

    public CycleResult beginClosing(CycleCommand command) {
        return transition(command, "BEGIN_CLOSING", DeferredCycleState.OPEN, DeferredCycleState.CLOSING);
    }

    public CycleResult close(CycleCommand command) {
        return transition(command, "CLOSE", DeferredCycleState.CLOSING, DeferredCycleState.CLOSED);
    }

    /** P8: a single INSERT...SELECT derives all participant positions from immutable items. */
    public NettingResult net(CycleCommand command) {
        Objects.requireNonNull(command, "command");
        return transaction(connection -> {
            CommandReceipt receipt = receipt(connection, command, "NET");
            if (receipt != null) return new NettingResult(command.cycleId(), positionCount(connection, command.cycleId()), true);
            Cycle cycle = requiredCycle(connection, command.cycleId(), true);
            if (cycle.state() != DeferredCycleState.CLOSED) {
                throw new DeferredCycleException("only CLOSED cycles can be netted");
            }
            try (PreparedStatement net = connection.prepareStatement("""
                    WITH participant_deltas AS (
                        SELECT debtor_participant_id AS participant_id, -amount_minor AS delta
                        FROM settlement.settlement_items WHERE cycle_id = ?
                        UNION ALL
                        SELECT creditor_participant_id AS participant_id, amount_minor AS delta
                        FROM settlement.settlement_items WHERE cycle_id = ?
                    )
                    INSERT INTO settlement.settlement_positions (cycle_id, participant_id, net_minor)
                    SELECT ?, participant_id, SUM(delta)
                    FROM participant_deltas
                    GROUP BY participant_id
                    """)) {
                net.setObject(1, command.cycleId()); net.setObject(2, command.cycleId()); net.setObject(3, command.cycleId()); net.executeUpdate();
            }
            if (netSum(connection, command.cycleId()) != 0L) throw new DeferredCycleException("net positions must sum to zero");
            updateState(connection, command.cycleId(), DeferredCycleState.NETTED, "netted_at");
            insertReceipt(connection, command, "NET");
            return new NettingResult(command.cycleId(), positionCount(connection, command.cycleId()), false);
        });
    }

    /** Establishes the durable source fact; finality recording is a separately idempotent authority action. */
    public SettledCycle settle(CycleSettlementCommand command) {
        Objects.requireNonNull(command, "command");
        return transaction(connection -> {
            CommandReceipt receipt = receipt(connection, command.asCycleCommand(), "SETTLE");
            Cycle cycle = requiredCycle(connection, command.cycleId(), true);
            if (receipt != null) return new SettledCycle(command.cycleId(), cycle.settledAt(), items(connection, command.cycleId()), true);
            if (cycle.state() != DeferredCycleState.NETTED) throw new DeferredCycleException("only NETTED cycles can settle");
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE settlement.settlement_cycles SET state = 'SETTLED', settled_at = ? WHERE id = ?
                    """)) {
                update.setTimestamp(1, Timestamp.from(command.settledAt())); update.setObject(2, command.cycleId()); update.executeUpdate();
            }
            insertReceipt(connection, command.asCycleCommand(), "SETTLE");
            return new SettledCycle(command.cycleId(), command.settledAt(), items(connection, command.cycleId()), false);
        });
    }

    private CycleResult transition(CycleCommand command, String commandType, DeferredCycleState expected, DeferredCycleState target) {
        Objects.requireNonNull(command, "command");
        return transaction(connection -> {
            CommandReceipt receipt = receipt(connection, command, commandType);
            Cycle cycle = requiredCycle(connection, command.cycleId(), true);
            if (receipt != null) return new CycleResult(command.cycleId(), cycle.state(), true);
            if (cycle.state() != expected) {
                throw new DeferredCycleException("illegal cycle transition " + cycle.state() + " -> " + target);
            }
            updateState(connection, command.cycleId(), target, target == DeferredCycleState.CLOSING ? null : "closed_at");
            insertReceipt(connection, command, commandType);
            return new CycleResult(command.cycleId(), target, false);
        });
    }

    private static void insertAttempt(Connection connection, DeferredCycleAssignment command) throws SQLException {
        try (PreparedStatement existing = connection.prepareStatement("""
                SELECT id, payment_id, profile_id, settlement_basis, liquidity_mode, state
                FROM settlement.deferred_cycle_attempts WHERE id = ? OR payment_id = ?
                """)) {
            existing.setObject(1, command.settlementAttemptId()); existing.setObject(2, command.paymentId());
            try (ResultSet rows = existing.executeQuery()) {
                if (rows.next()) {
                    boolean same = command.settlementAttemptId().equals(rows.getObject("id", UUID.class))
                            && command.paymentId().equals(rows.getObject("payment_id", UUID.class))
                            && command.profileId().equals(rows.getObject("profile_id", UUID.class))
                            && SettlementBasis.NET_DEFERRED.name().equals(rows.getString("settlement_basis"))
                            && LiquidityMode.ISOLATED_SUBACCOUNT.name().equals(rows.getString("liquidity_mode"));
                    if (same) return;
                    throw new DeferredCycleException("settlement attempt conflicts with existing payment or identity");
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO settlement.deferred_cycle_attempts
                    (id, payment_id, profile_id, settlement_basis, liquidity_mode, state)
                VALUES (?, ?, ?, 'NET_DEFERRED', 'ISOLATED_SUBACCOUNT', 'CYCLE_ASSIGNED')
                """)) {
            insert.setObject(1, command.settlementAttemptId()); insert.setObject(2, command.paymentId());
            insert.setObject(3, command.profileId()); insert.executeUpdate();
        }
    }

    private static Cycle requiredCycle(Connection connection, UUID cycleId, boolean lock) throws SQLException {
        Cycle cycle = cycle(connection, cycleId, lock);
        if (cycle == null) throw new DeferredCycleException("unknown settlement cycle " + cycleId);
        return cycle;
    }

    private static Cycle cycle(Connection connection, UUID cycleId, boolean lock) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, profile_id, business_date, session_no, state, cutoff_at, settled_at
                FROM settlement.settlement_cycles WHERE id = ? %s
                """.formatted(lock ? "FOR UPDATE" : ""))) {
            select.setObject(1, cycleId);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? new Cycle((UUID) row.getObject(1), (UUID) row.getObject(2), row.getObject(3, LocalDate.class),
                        row.getShort(4), DeferredCycleState.valueOf(row.getString(5)), row.getTimestamp(6).toInstant(),
                        row.getTimestamp(7) == null ? null : row.getTimestamp(7).toInstant()) : null;
            }
        }
    }

    private static Item itemForPayment(Connection connection, UUID cycleId, UUID paymentId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, settlement_attempt_id, payment_id, debtor_participant_id, creditor_participant_id, amount_minor
                FROM settlement.settlement_items WHERE cycle_id = ? AND payment_id = ?
                """)) {
            select.setObject(1, cycleId); select.setObject(2, paymentId);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? new Item((UUID) row.getObject(1), (UUID) row.getObject(2), (UUID) row.getObject(3),
                        (UUID) row.getObject(4), (UUID) row.getObject(5), row.getLong(6)) : null;
            }
        }
    }

    private static List<SettledItem> items(Connection connection, UUID cycleId) throws SQLException {
        List<SettledItem> values = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, settlement_attempt_id, payment_id FROM settlement.settlement_items
                WHERE cycle_id = ? ORDER BY id
                """)) {
            select.setObject(1, cycleId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) values.add(new SettledItem((UUID) rows.getObject(1), (UUID) rows.getObject(2),
                        (UUID) rows.getObject(3)));
            }
        }
        return List.copyOf(values);
    }

    private static CommandReceipt receipt(Connection connection, CycleCommand command, String commandType) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT cycle_id, command_type FROM settlement.settlement_cycle_command_receipts WHERE command_id = ?
                """)) {
            select.setObject(1, command.commandId());
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) return null;
                CommandReceipt receipt = new CommandReceipt((UUID) row.getObject(1), row.getString(2));
                if (!receipt.cycleId().equals(command.cycleId()) || !receipt.commandType().equals(commandType)) {
                    throw new DeferredCycleException("cycle command identity conflicts with a different command");
                }
                return receipt;
            }
        }
    }

    private static void insertReceipt(Connection connection, CycleCommand command, String commandType) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO settlement.settlement_cycle_command_receipts (command_id, cycle_id, command_type)
                VALUES (?, ?, ?)
                """)) {
            insert.setObject(1, command.commandId()); insert.setObject(2, command.cycleId()); insert.setString(3, commandType);
            insert.executeUpdate();
        }
    }

    private static void updateState(Connection connection, UUID cycleId, DeferredCycleState state, String timestampColumn) throws SQLException {
        String sql = timestampColumn == null
                ? "UPDATE settlement.settlement_cycles SET state = ? WHERE id = ?"
                : "UPDATE settlement.settlement_cycles SET state = ?, " + timestampColumn + " = now() WHERE id = ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1, state.name()); update.setObject(2, cycleId); update.executeUpdate();
        }
    }

    private static int positionCount(Connection connection, UUID cycleId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT count(*) FROM settlement.settlement_positions WHERE cycle_id = ?")) {
            select.setObject(1, cycleId); try (ResultSet row = select.executeQuery()) { row.next(); return row.getInt(1); }
        }
    }

    private static long netSum(Connection connection, UUID cycleId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT COALESCE(sum(net_minor), 0) FROM settlement.settlement_positions WHERE cycle_id = ?")) {
            select.setObject(1, cycleId); try (ResultSet row = select.executeQuery()) { row.next(); return row.getLong(1); }
        }
    }

    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                T value = work.run(connection); connection.commit(); return value;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback(); throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("could not execute deferred settlement cycle command", failure);
        }
    }

    private static void require(Object value, String label) { if (value == null) throw new NullPointerException(label); }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
    private record Cycle(UUID id, UUID profileId, LocalDate businessDate, short sessionNo, DeferredCycleState state, Instant cutoffAt, Instant settledAt) { }
    private record CommandReceipt(UUID cycleId, String commandType) { }
    private record Item(UUID id, UUID attemptId, UUID paymentId, UUID debtorParticipantId, UUID creditorParticipantId, long amountMinor) {
        boolean matches(DeferredCycleAssignment command) { return attemptId.equals(command.settlementAttemptId())
                && paymentId.equals(command.paymentId()) && debtorParticipantId.equals(command.debtorParticipantId())
                && creditorParticipantId.equals(command.creditorParticipantId()) && amountMinor == command.amountMinor(); }
    }
    public record CycleResult(UUID cycleId, DeferredCycleState state, boolean replayed) { }
    public record AssignmentResult(UUID itemId, boolean replayed) { }
    public record NettingResult(UUID cycleId, int positionCount, boolean replayed) { }
    public record CycleCommand(UUID commandId, UUID cycleId) {
        public CycleCommand { require(commandId, "commandId"); require(cycleId, "cycleId"); }
    }
    public record CycleSettlementCommand(UUID commandId, UUID cycleId, Instant settledAt) {
        public CycleSettlementCommand { require(commandId, "commandId"); require(cycleId, "cycleId"); require(settledAt, "settledAt"); }
        private CycleCommand asCycleCommand() { return new CycleCommand(commandId, cycleId); }
    }
    public record SettledItem(UUID itemId, UUID settlementAttemptId, UUID paymentId) { }
    public record SettledCycle(UUID cycleId, Instant settledAt, List<SettledItem> items, boolean replayed) { }
}
