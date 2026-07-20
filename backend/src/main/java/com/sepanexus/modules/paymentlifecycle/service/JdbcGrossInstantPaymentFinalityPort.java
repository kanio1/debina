package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.GrossInstantPaymentFinalityPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** ADR-N11 payment adapter: its only writes are through payment-owned command functions. */
@Component("grossInstantPaymentFinalityPort")
public class JdbcGrossInstantPaymentFinalityPort implements GrossInstantPaymentFinalityPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcGrossInstantPaymentFinalityPort(@Qualifier("grossInstantJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ProjectionResult projectGrossInstant(UUID tenantId, UUID paymentId, UUID commandId, UUID finalityRecordId,
            Instant finalityAt, Instant occurredAt) {
        CommandRow row = jdbcTemplate.queryForObject("""
                SELECT projection_outcome, replayed
                FROM payment.project_gross_instant_finality(?::uuid, ?::uuid, ?::uuid, ?::uuid,
                    ?::timestamptz, ?::timestamptz)
                """, (resultSet, ignored) -> new CommandRow(resultSet.getString(1), resultSet.getBoolean(2)),
                tenantId, paymentId, commandId, finalityRecordId, Timestamp.from(finalityAt), Timestamp.from(occurredAt));
        if (!"PROJECTED".equals(row.outcome())) throw new IllegalStateException("Unexpected payment projection result");
        return row.replayed() ? ProjectionResult.ALREADY_PROJECTED : ProjectionResult.PROJECTED;
    }

    @Override
    public RejectionResult recordGrossInstantInsufficientLiquidity(UUID tenantId, UUID paymentId, UUID commandId,
            Instant occurredAt) {
        CommandRow row = jdbcTemplate.queryForObject("""
                SELECT rejection_outcome, replayed
                FROM payment.record_gross_instant_insufficient_liquidity(?::uuid, ?::uuid, ?::uuid, ?::timestamptz)
                """, (resultSet, ignored) -> new CommandRow(resultSet.getString(1), resultSet.getBoolean(2)),
                tenantId, paymentId, commandId, Timestamp.from(occurredAt));
        if (!"REJECTED".equals(row.outcome())) throw new IllegalStateException("Unexpected payment rejection result");
        return row.replayed() ? RejectionResult.ALREADY_REJECTED : RejectionResult.REJECTED;
    }

    /** The legacy finality path has no command id/status-report outcome and must not use this adapter. */
    @Override
    public ProjectionResult project(UUID tenantId, UUID paymentId, UUID finalityRecordId, Instant finalityAt) {
        throw new UnsupportedOperationException("Use projectGrossInstant for ADR-N11 gross-instant finality");
    }

    private record CommandRow(String outcome, boolean replayed) { }
}
