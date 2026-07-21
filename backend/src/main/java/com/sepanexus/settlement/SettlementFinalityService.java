package com.sepanexus.settlement;

import com.sepanexus.ledger.LedgerPort;
import com.sepanexus.ledger.ReservationState;
import com.sepanexus.modules.PaymentFinalityPort;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Settlement-owned finality authority for the one executable ADR-N10 laboratory trigger. */
@Service
public class SettlementFinalityService {

    private static final String LEDGER_ENTRY_SOURCE = "LEDGER_ENTRY";
    private static final String CYCLE_ITEM_SETTLED_SOURCE = "CYCLE_ITEM_SETTLED";
    private final SettlementConnectionFactory connections;
    private final PaymentFinalityPort paymentFinalityPort;
    private final FinalityRulePolicy policy = new FinalityRulePolicy();

    public SettlementFinalityService(SettlementConnectionFactory connections,
            @Qualifier("jdbcPaymentFinalityProjection") PaymentFinalityPort paymentFinalityPort) {
        this.connections = connections;
        this.paymentFinalityPort = paymentFinalityPort;
    }

    public FinalityOutcome recordLedgerPost(UUID tenantId, UUID settlementAttemptId, UUID paymentId,
            LedgerPort.TerminalResult terminal, byte[] evidenceHash) {
        if (terminal.state() != ReservationState.POSTED) {
            throw new IllegalArgumentException("Only a ledger POST can establish ON_LEDGER_POST finality");
        }
        if (evidenceHash == null || evidenceHash.length == 0) {
            throw new IllegalArgumentException("evidenceHash is required");
        }
        FinalityOutcome outcome = recordAuthority(tenantId, settlementAttemptId, paymentId,
                FinalityRulePolicy.ON_LEDGER_POST, 1, connection -> new AuthoritativeSource(
                        LEDGER_ENTRY_SOURCE, terminal.terminalEntryId(), terminal.sourceOccurredAt()), evidenceHash,
                connection -> { });
        paymentFinalityPort.project(tenantId, paymentId, outcome.recordId(), outcome.finalityAt());
        return outcome;
    }

    /**
     * The authoritative time and source are read from a durable SETTLED cycle/item fact, never
     * supplied by routing, ISO, delivery or a caller clock.
     */
    public FinalityOutcome recordCycleItemSettled(UUID tenantId, UUID cycleId, UUID settlementItemId,
            UUID settlementAttemptId, UUID paymentId, byte[] evidenceHash) {
        if (cycleId == null || settlementItemId == null) throw new IllegalArgumentException("cycle and item are required");
        FinalityOutcome outcome = recordAuthority(tenantId, settlementAttemptId, paymentId,
                FinalityRulePolicy.ON_CYCLE_SETTLED, 1,
                connection -> cycleItemSource(connection, cycleId, settlementItemId, settlementAttemptId, paymentId), evidenceHash,
                connection -> markDeferredAttemptFinal(connection, settlementAttemptId, paymentId));
        paymentFinalityPort.project(tenantId, paymentId, outcome.recordId(), outcome.finalityAt());
        return outcome;
    }

    private FinalityOutcome recordAuthority(UUID tenantId, UUID attemptId, UUID paymentId, String ruleCode,
            int ruleVersion, SourceReader sourceReader, byte[] evidenceHash, RecordFinalizer finalizer) {
        if (tenantId == null || attemptId == null || paymentId == null) throw new IllegalArgumentException("tenant, attempt and payment are required");
        if (evidenceHash == null || evidenceHash.length == 0) throw new IllegalArgumentException("evidenceHash is required");
        if (!policy.isCatalogued(ruleCode, ruleVersion) || !policy.isExecutableNow(ruleCode)) {
            throw new IllegalArgumentException("Finality rule has no executable laboratory trigger: " + ruleCode);
        }
        for (int tries = 0; tries < 2; tries++) {
            try (Connection connection = connections.open()) {
                connection.setAutoCommit(false);
                try {
                    AuthoritativeSource source = sourceReader.read(connection);
                    Instant authoritativeTime = source.occurredAt().truncatedTo(ChronoUnit.MILLIS);
                    if (!ruleExists(connection, ruleCode, ruleVersion)) {
                        throw new IllegalArgumentException("Finality rule is absent from the versioned catalog: " + ruleCode);
                    }
                    Snapshot snapshot = snapshot(connection, attemptId, ruleCode, ruleVersion);
                    FinalityRecord existing = existing(connection, paymentId, source.type(), source.id());
                    if (existing != null) {
                        FinalityOutcome outcome = matchOrConflict(existing, paymentId, attemptId, snapshot, ruleCode,
                                ruleVersion, source.type(), source.id(), authoritativeTime, evidenceHash);
                        finalizer.finish(connection);
                        connection.commit();
                        return outcome;
                    }
                    UUID recordId = UUID.randomUUID();
                    insertRecord(connection, recordId, paymentId, snapshot, ruleCode, ruleVersion, source.type(), source.id(),
                            authoritativeTime, evidenceHash);
                    finalizer.finish(connection);
                    connection.commit();
                    return new FinalityOutcome(recordId, authoritativeTime, false);
                } catch (SQLException failure) {
                    connection.rollback();
                    if (isUniqueViolation(failure) && tries == 0) {
                        continue;
                    }
                    throw failure;
                } catch (RuntimeException failure) {
                    connection.rollback();
                    throw failure;
                }
            } catch (SQLException failure) {
                throw new IllegalStateException("Could not record settlement finality", failure);
            }
        }
        throw new IllegalStateException("Could not resolve concurrent settlement finality recording");
    }

    private static AuthoritativeSource cycleItemSource(Connection connection, UUID cycleId, UUID itemId,
            UUID attemptId, UUID paymentId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT cycle.state, cycle.settled_at, item.settlement_attempt_id, item.payment_id
                FROM settlement.settlement_cycles cycle
                JOIN settlement.settlement_items item ON item.cycle_id = cycle.id
                WHERE cycle.id = ? AND item.id = ? FOR UPDATE OF cycle
                """)) {
            select.setObject(1, cycleId); select.setObject(2, itemId);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("settlement item is not a member of the supplied cycle");
                if (!DeferredCycleState.SETTLED.name().equals(row.getString(1)) || row.getTimestamp(2) == null) {
                    throw new IllegalArgumentException("only a persisted SETTLED cycle can establish ON_CYCLE_SETTLED finality");
                }
                if (!attemptId.equals(row.getObject(3, UUID.class)) || !paymentId.equals(row.getObject(4, UUID.class))) {
                    throw new FinalityConflictException(paymentId);
                }
                return new AuthoritativeSource(CYCLE_ITEM_SETTLED_SOURCE, itemId, row.getTimestamp(2).toInstant());
            }
        }
    }

    private static void markDeferredAttemptFinal(Connection connection, UUID attemptId, UUID paymentId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT payment_id, state FROM settlement.deferred_cycle_attempts WHERE id = ? FOR UPDATE
                """)) {
            select.setObject(1, attemptId);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next() || !paymentId.equals(row.getObject(1, UUID.class))) throw new FinalityConflictException(paymentId);
                if ("FINAL".equals(row.getString(2))) return;
                if (!"CYCLE_ASSIGNED".equals(row.getString(2))) throw new FinalityConflictException(paymentId);
            }
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE settlement.deferred_cycle_attempts SET state = 'FINAL' WHERE id = ?
                """)) {
            update.setObject(1, attemptId); update.executeUpdate();
        }
        try (PreparedStatement event = connection.prepareStatement("""
                INSERT INTO settlement.deferred_cycle_attempt_events (settlement_attempt_id, seq, state, at)
                SELECT ?, coalesce(max(seq), 0) + 1, 'FINAL', now()
                FROM settlement.deferred_cycle_attempt_events WHERE settlement_attempt_id = ?
                """)) {
            event.setObject(1, attemptId); event.setObject(2, attemptId); event.executeUpdate();
        }
    }

    private static Snapshot snapshot(Connection connection, UUID attemptId, String ruleCode, int ruleVersion)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO settlement.settlement_profile_snapshots
                    (id, settlement_attempt_id, finality_rule_code, finality_rule_version)
                VALUES (?, ?, ?, ?) ON CONFLICT (settlement_attempt_id) DO NOTHING
                """)) {
            insert.setObject(1, UUID.randomUUID()); insert.setObject(2, attemptId);
            insert.setString(3, ruleCode); insert.setInt(4, ruleVersion); insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, settlement_attempt_id, finality_rule_code, finality_rule_version
                FROM settlement.settlement_profile_snapshots WHERE settlement_attempt_id = ?
                """)) {
            select.setObject(1, attemptId);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Settlement profile snapshot was not created");
                Snapshot snapshot = new Snapshot((UUID) result.getObject(1), (UUID) result.getObject(2),
                        result.getString(3), result.getInt(4));
                if (!snapshot.ruleCode().equals(ruleCode) || snapshot.ruleVersion() != ruleVersion) {
                    throw new FinalityConflictException(attemptId);
                }
                return snapshot;
            }
        }
    }

    private static boolean ruleExists(Connection connection, String ruleCode, int ruleVersion) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT 1 FROM reference_data.finality_rules
                WHERE finality_rule_code = ? AND finality_rule_version = ?
                """)) {
            select.setString(1, ruleCode);
            select.setInt(2, ruleVersion);
            try (ResultSet result = select.executeQuery()) {
                return result.next();
            }
        }
    }

    private static FinalityRecord existing(Connection connection, UUID paymentId, String sourceType, UUID sourceId)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, payment_id, settlement_attempt_id, profile_snapshot_id, finality_rule_code,
                    finality_rule_version, source_type, source_id, source_occurred_at, finality_at, evidence_hash
                FROM settlement.settlement_finality_records
                WHERE payment_id = ? OR (source_type = ? AND source_id = ?)
                """)) {
            select.setObject(1, paymentId); select.setString(2, sourceType); select.setObject(3, sourceId);
            try (ResultSet result = select.executeQuery()) {
                return result.next() ? new FinalityRecord((UUID) result.getObject(1), (UUID) result.getObject(2),
                        (UUID) result.getObject(3), (UUID) result.getObject(4), result.getString(5), result.getInt(6),
                        result.getString(7), (UUID) result.getObject(8), result.getTimestamp(9).toInstant(),
                        result.getTimestamp(10).toInstant(), result.getBytes(11)) : null;
            }
        }
    }

    private static FinalityOutcome matchOrConflict(FinalityRecord record, UUID paymentId, UUID attemptId,
            Snapshot snapshot, String ruleCode, int ruleVersion, String sourceType, UUID sourceId,
            Instant sourceOccurredAt, byte[] evidenceHash) {
        boolean identical = record.paymentId().equals(paymentId) && record.attemptId().equals(attemptId)
                && record.snapshotId().equals(snapshot.id()) && record.ruleCode().equals(ruleCode)
                && record.ruleVersion() == ruleVersion && record.sourceType().equals(sourceType)
                && record.sourceId().equals(sourceId) && record.sourceOccurredAt().equals(sourceOccurredAt)
                && record.finalityAt().equals(sourceOccurredAt) && MessageDigest.isEqual(record.evidenceHash(), evidenceHash);
        if (!identical) throw new FinalityConflictException(paymentId);
        return new FinalityOutcome(record.id(), record.finalityAt(), true);
    }

    private static void insertRecord(Connection connection, UUID id, UUID paymentId, Snapshot snapshot,
            String ruleCode, int ruleVersion, String sourceType, UUID sourceId, Instant sourceOccurredAt,
            byte[] evidenceHash) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO settlement.settlement_finality_records
                    (id, payment_id, settlement_attempt_id, profile_snapshot_id, finality_rule_code,
                     finality_rule_version, source_type, source_id, source_occurred_at, finality_at, evidence_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setObject(1, id); insert.setObject(2, paymentId); insert.setObject(3, snapshot.attemptId());
            insert.setObject(4, snapshot.id()); insert.setString(5, ruleCode); insert.setInt(6, ruleVersion);
            insert.setString(7, sourceType); insert.setObject(8, sourceId);
            insert.setTimestamp(9, Timestamp.from(sourceOccurredAt)); insert.setTimestamp(10, Timestamp.from(sourceOccurredAt));
            insert.setBytes(11, evidenceHash); insert.executeUpdate();
        }
    }

    private static boolean isUniqueViolation(SQLException exception) {
        return "23505".equals(exception.getSQLState());
    }

    public record FinalityOutcome(UUID recordId, Instant finalityAt, boolean replayed) { }
    private record AuthoritativeSource(String type, UUID id, Instant occurredAt) { }
    @FunctionalInterface private interface SourceReader { AuthoritativeSource read(Connection connection) throws SQLException; }
    @FunctionalInterface private interface RecordFinalizer { void finish(Connection connection) throws SQLException; }
    private record Snapshot(UUID id, UUID attemptId, String ruleCode, int ruleVersion) { }
    private record FinalityRecord(UUID id, UUID paymentId, UUID attemptId, UUID snapshotId, String ruleCode,
            int ruleVersion, String sourceType, UUID sourceId, Instant sourceOccurredAt, Instant finalityAt,
            byte[] evidenceHash) { }
}
