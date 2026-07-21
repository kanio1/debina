package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.ApprovalQueueQuery;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payment-lifecycle owns the approval read model and its RLS/GUC context. */
@Service
class ApprovalQueueReadModel implements ApprovalQueueQuery {
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

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('payment_approver')")
    public QueuePage pending(UUID tenantId, UUID branchId, Integer requestedLimit, String afterCursor) {
        tenantGucConfigurer.apply(tenantId, branchId);
        Cursor after = afterCursor == null ? null : Cursor.decode(afterCursor);
        int limit = clamp(requestedLimit);
        Instant now = clockPort.now();
        List<QueueItem> fetched = after == null
                ? jdbcTemplate.query(queueSql(""), (rs, row) -> item(rs, now), limit + 1)
                : jdbcTemplate.query(queueSql("AND (a.submitted_for_approval_at, a.id) > (?, ?)"),
                        (rs, row) -> item(rs, now), Timestamp.from(after.submittedAt()), after.approvalId(), limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<QueueItem> items = hasMore ? fetched.subList(0, limit) : fetched;
        return new QueuePage(items, hasMore ? Cursor.from(items.getLast()).encode() : null);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('payment_approver')")
    public ApprovalDetail approval(UUID tenantId, UUID branchId, UUID paymentId) {
        tenantGucConfigurer.apply(tenantId, branchId);
        Instant now = clockPort.now();
        List<ApprovalDetail> results = jdbcTemplate.query("""
                SELECT a.id AS approval_id, a.payment_id, a.status, a.maker_user_id,
                       a.submitted_for_approval_at, a.expires_at, a.matrix_rule_id,
                       a.decision_comment, a.decided_at, p.amount, p.currency, p.debtor_iban, p.creditor_iban
                  FROM payment.payment_approvals a JOIN payment.payments p ON p.id = a.payment_id
                 WHERE a.payment_id = ?
                """, (rs, row) -> detail(rs, now), paymentId);
        return results.isEmpty() ? null : results.getFirst();
    }

    private static String queueSql(String cursorClause) {
        return """
                SELECT a.id AS approval_id, a.payment_id, a.status, a.maker_user_id,
                       a.submitted_for_approval_at, a.expires_at, a.matrix_rule_id,
                       p.amount, p.currency, p.debtor_iban, p.creditor_iban
                  FROM payment.payment_approvals a JOIN payment.payments p ON p.id = a.payment_id
                 WHERE a.status = 'PENDING_APPROVAL'
                """ + cursorClause + """
                 ORDER BY a.submitted_for_approval_at ASC, a.id ASC LIMIT ?
                """;
    }

    private static QueueItem item(java.sql.ResultSet rs, Instant now) throws java.sql.SQLException {
        Instant expires = rs.getTimestamp("expires_at").toInstant();
        return new QueueItem((UUID) rs.getObject("approval_id"), (UUID) rs.getObject("payment_id"),
                rs.getString("status"), rs.getString("maker_user_id"),
                rs.getTimestamp("submitted_for_approval_at").toInstant(), expires,
                (UUID) rs.getObject("matrix_rule_id"), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("debtor_iban"), rs.getString("creditor_iban"), !expires.isAfter(now));
    }

    private static ApprovalDetail detail(java.sql.ResultSet rs, Instant now) throws java.sql.SQLException {
        Instant expires = rs.getTimestamp("expires_at").toInstant();
        Timestamp decided = rs.getTimestamp("decided_at");
        return new ApprovalDetail((UUID) rs.getObject("approval_id"), (UUID) rs.getObject("payment_id"),
                rs.getString("status"), rs.getString("maker_user_id"), rs.getTimestamp("submitted_for_approval_at").toInstant(),
                expires, (UUID) rs.getObject("matrix_rule_id"), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("debtor_iban"), rs.getString("creditor_iban"), !expires.isAfter(now),
                rs.getString("decision_comment"), decided == null ? null : decided.toInstant());
    }

    private static int clamp(Integer requested) { return requested == null || requested <= 0 ? DEFAULT_LIMIT : Math.min(requested, MAX_LIMIT); }

    record Cursor(Instant submittedAt, UUID approvalId) {
        static Cursor from(QueueItem item) { return new Cursor(item.submittedAt(), item.approvalId()); }
        String encode() { return Base64.getUrlEncoder().withoutPadding().encodeToString((submittedAt + "|" + approvalId).getBytes(StandardCharsets.UTF_8)); }
        static Cursor decode(String value) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\\\|", -1);
                if (parts.length != 2) throw new IllegalArgumentException("Invalid approval cursor");
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid approval cursor", exception); }
        }
    }
}
