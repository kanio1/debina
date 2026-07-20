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
                FinalityRulePolicy.ON_LEDGER_POST, 1, LEDGER_ENTRY_SOURCE, terminal.terminalEntryId(),
                terminal.sourceOccurredAt(), evidenceHash);
        paymentFinalityPort.project(tenantId, paymentId, outcome.recordId(), outcome.finalityAt());
        return outcome;
    }

    private FinalityOutcome recordAuthority(UUID tenantId, UUID attemptId, UUID paymentId, String ruleCode,
            int ruleVersion, String sourceType, UUID sourceId, Instant sourceOccurredAt, byte[] evidenceHash) {
        if (!policy.isCatalogued(ruleCode, ruleVersion) || !policy.isExecutableNow(ruleCode)) {
            throw new IllegalArgumentException("Finality rule has no executable laboratory trigger: " + ruleCode);
        }
        Instant authoritativeTime = sourceOccurredAt.truncatedTo(ChronoUnit.MILLIS);
        for (int tries = 0; tries < 2; tries++) {
            try (Connection connection = connections.open()) {
                connection.setAutoCommit(false);
                try {
                    if (!ruleExists(connection, ruleCode, ruleVersion)) {
                        throw new IllegalArgumentException("Finality rule is absent from the versioned catalog: " + ruleCode);
                    }
                    Snapshot snapshot = snapshot(connection, attemptId, ruleCode, ruleVersion);
                    FinalityRecord existing = existing(connection, paymentId, sourceType, sourceId);
                    if (existing != null) {
                        FinalityOutcome outcome = matchOrConflict(existing, paymentId, attemptId, snapshot, ruleCode,
                                ruleVersion, sourceType, sourceId, authoritativeTime, evidenceHash);
                        connection.commit();
                        return outcome;
                    }
                    UUID recordId = UUID.randomUUID();
                    insertRecord(connection, recordId, paymentId, snapshot, ruleCode, ruleVersion, sourceType, sourceId,
                            authoritativeTime, evidenceHash);
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
    private record Snapshot(UUID id, UUID attemptId, String ruleCode, int ruleVersion) { }
    private record FinalityRecord(UUID id, UUID paymentId, UUID attemptId, UUID snapshotId, String ruleCode,
            int ruleVersion, String sourceType, UUID sourceId, Instant sourceOccurredAt, Instant finalityAt,
            byte[] evidenceHash) { }
}
