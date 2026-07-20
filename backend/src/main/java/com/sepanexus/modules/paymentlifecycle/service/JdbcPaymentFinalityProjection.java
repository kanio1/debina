package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.PaymentFinalityPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Payment-owned, idempotent projection of settlement's authoritative finality record. */
@Component
public class JdbcPaymentFinalityProjection implements PaymentFinalityPort {

    private final JdbcTemplate jdbcTemplate;
    private final TenantGucConfigurer tenantGucConfigurer;

    public JdbcPaymentFinalityProjection(JdbcTemplate jdbcTemplate, TenantGucConfigurer tenantGucConfigurer) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantGucConfigurer = tenantGucConfigurer;
    }

    @Override
    @Transactional
    public ProjectionResult project(UUID tenantId, UUID paymentId, UUID finalityRecordId, Instant finalityAt) {
        tenantGucConfigurer.apply(tenantId);
        int updated = jdbcTemplate.update("""
                UPDATE payment.payments SET finality_at = ?, finality_record_id = ?
                WHERE id = ? AND finality_record_id IS NULL
                """, Timestamp.from(finalityAt), finalityRecordId, paymentId);
        if (updated == 1) {
            return ProjectionResult.PROJECTED;
        }
        ProjectionRow existing = jdbcTemplate.query("""
                SELECT finality_at, finality_record_id FROM payment.payments WHERE id = ?
                """, resultSet -> resultSet.next()
                ? new ProjectionRow(resultSet.getTimestamp(1).toInstant(), (UUID) resultSet.getObject(2))
                : null, paymentId);
        if (existing != null && existing.finalityRecordId().equals(finalityRecordId)
                && existing.finalityAt().equals(finalityAt)) {
            return ProjectionResult.ALREADY_PROJECTED;
        }
        throw new PaymentFinalityProjectionConflictException(paymentId);
    }

    private record ProjectionRow(Instant finalityAt, UUID finalityRecordId) { }
}
