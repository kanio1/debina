package com.sepanexus.settlement;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** ADR-N11 settlement adapter, deliberately free of direct settlement-table DML. */
@Component
public class JdbcGrossInstantSettlementCommandPort implements GrossInstantSettlementCommandPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcGrossInstantSettlementCommandPort(@Qualifier("grossInstantJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public FinalityResult recordPosted(UUID tenantId, UUID attemptId, UUID paymentId, UUID commandId, UUID reserveEntryId,
            UUID postEntryId, long amountMinor, String currency, Instant occurredAt, byte[] evidenceHash) {
        return jdbcTemplate.queryForObject("""
                SELECT finality_record_id, finality_at, replayed
                FROM settlement.record_gross_instant_post(?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid,
                    ?::bigint, ?::char(3), ?::timestamptz, ?::bytea)
                """, (resultSet, ignored) -> new FinalityResult(resultSet.getObject(1, UUID.class),
                resultSet.getTimestamp(2).toInstant(), resultSet.getBoolean(3)), tenantId, attemptId, paymentId,
                commandId, reserveEntryId, postEntryId, amountMinor, currency, Timestamp.from(occurredAt), evidenceHash);
    }

    @Override
    public RejectedAttemptResult recordInsufficientLiquidity(UUID tenantId, UUID attemptId, UUID paymentId,
            UUID commandId, long amountMinor, String currency, Instant occurredAt) {
        return jdbcTemplate.queryForObject("""
                SELECT attempt_id, replayed
                FROM settlement.record_gross_instant_insufficient_liquidity(?::uuid, ?::uuid, ?::uuid, ?::uuid,
                    ?::bigint, ?::char(3), ?::timestamptz)
                """, (resultSet, ignored) -> new RejectedAttemptResult(resultSet.getObject(1, UUID.class),
                resultSet.getBoolean(2)), tenantId, attemptId, paymentId, commandId, amountMinor, currency,
                Timestamp.from(occurredAt));
    }
}
