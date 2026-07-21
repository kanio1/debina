package com.sepanexus.modules.paymentlifecycle.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment-lifecycle's internal checker queue.  It deliberately has no web adapter: external reads
 * stay blocked on the frozen GraphQL owner.  Payment RLS remains the visibility authority for the
 * approval join, while method authorization controls who may request decision work.
 */
@Service
class ApprovalQueueReadModel {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TenantGucConfigurer tenantGucConfigurer;
    private final com.sepanexus.shared.ClockPort clockPort;

    ApprovalQueueReadModel(JdbcTemplate jdbcTemplate, TenantGucConfigurer tenantGucConfigurer,
            com.sepanexus.shared.ClockPort clockPort) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantGucConfigurer = tenantGucConfigurer;
        this.clockPort = clockPort;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('payment_approver')")
    QueuePage pending(UUID tenantId, UUID branchId, Integer requestedLimit, Cursor after) {
        tenantGucConfigurer.apply(tenantId, branchId);
        int limit = clamp(requestedLimit);
        Instant now = clockPort.now();
        List<QueueItem> fetched;
        if (after == null) {
            fetched = jdbcTemplate.query(baseQuery(""), (rs, row) -> map(rs, now), limit + 1);
        } else {
            fetched = jdbcTemplate.query(baseQuery("AND (a.submitted_for_approval_at, a.id) > (?, ?)") ,
                    (rs, row) -> map(rs, now), Timestamp.from(after.submittedAt()), after.approvalId(), limit + 1);
        }
        boolean hasMore = fetched.size() > limit;
        List<QueueItem> items = hasMore ? fetched.subList(0, limit) : fetched;
        Cursor next = hasMore ? Cursor.from(items.getLast()) : null;
        return new QueuePage(items, next);
    }

    private static String baseQuery(String cursorClause) {
        return """
                SELECT a.id AS approval_id, a.payment_id, a.status, a.maker_user_id,
                       a.submitted_for_approval_at, a.expires_at, a.matrix_rule_id,
                       p.amount, p.currency, p.debtor_iban, p.creditor_iban
                FROM payment.payment_approvals a
                JOIN payment.payments p ON p.id = a.payment_id
                WHERE a.status = 'PENDING_APPROVAL'
                """ + cursorClause + """
                ORDER BY a.submitted_for_approval_at ASC, a.id ASC
                LIMIT ?
                """;
    }

    private static QueueItem map(java.sql.ResultSet rs, Instant now) throws java.sql.SQLException {
        Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
        return new QueueItem((UUID) rs.getObject("approval_id"), (UUID) rs.getObject("payment_id"),
                rs.getString("status"), rs.getString("maker_user_id"),
                rs.getTimestamp("submitted_for_approval_at").toInstant(), expiresAt,
                (UUID) rs.getObject("matrix_rule_id"), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("debtor_iban"), rs.getString("creditor_iban"), !expiresAt.isAfter(now));
    }

    private static int clamp(Integer requested) {
        return requested == null || requested <= 0 ? DEFAULT_LIMIT : Math.min(requested, MAX_LIMIT);
    }

    record Cursor(Instant submittedAt, UUID approvalId) {
        static Cursor from(QueueItem item) {
            return new Cursor(item.submittedAt(), item.approvalId());
        }
    }

    record QueueItem(UUID approvalId, UUID paymentId, String approvalStatus, String makerUserId,
            Instant submittedAt, Instant expiresAt, UUID matrixRuleId, BigDecimal amount, String currency,
            String debtorIban, String creditorIban, boolean expired) {
    }

    record QueuePage(List<QueueItem> items, Cursor nextCursor) {
    }
}
