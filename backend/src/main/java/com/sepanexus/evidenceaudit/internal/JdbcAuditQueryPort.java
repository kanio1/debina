package com.sepanexus.evidenceaudit.internal;

import com.sepanexus.evidenceaudit.ActorType;
import com.sepanexus.evidenceaudit.AuditQueryPort;
import com.sepanexus.evidenceaudit.CommandAuditOutcome;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** SQL stays inside evidence-audit; normal and auditor identities retain independent RLS defenses. */
@Component
class JdbcAuditQueryPort implements AuditQueryPort {
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate auditorJdbcTemplate;
    private final TransactionTemplate auditorTransaction;
    JdbcAuditQueryPort(JdbcTemplate jdbcTemplate, @Qualifier("auditAuditorJdbcTemplate") JdbcTemplate auditorJdbcTemplate,
            @Qualifier("auditAuditorTransactionManager") PlatformTransactionManager auditorTransactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditorJdbcTemplate = auditorJdbcTemplate;
        this.auditorTransaction = new TransactionTemplate(auditorTransactionManager);
        this.auditorTransaction.setReadOnly(true);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('operator', 'auditor')")
    public AuditPage paymentTrail(UUID paymentId, Integer requestedLimit, String afterCursor) {
        if (paymentId == null) throw new IllegalArgumentException("paymentId is required");
        AuditSearchFilter filter = new AuditSearchFilter(null, null, null, null, paymentId, null, null, null,
                null, null, null, null);
        return queryForCurrentScope(filter, requestedLimit, afterCursor);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('auditor')")
    public AuditPage search(AuditSearchFilter filter, Integer requestedLimit, String afterCursor) {
        return queryForCurrentScope(filter == null ? emptyFilter() : filter, requestedLimit, afterCursor);
    }

    private AuditPage queryForCurrentScope(AuditSearchFilter filter, Integer requestedLimit, String afterCursor) {
        Cursor cursor = afterCursor == null ? null : Cursor.decode(afterCursor);
        int limit = clamp(requestedLimit);
        if (isAuditor()) {
            AuditPage page = auditorTransaction.execute(status -> {
                applyAuditorContext();
                return query(auditorJdbcTemplate, filter, cursor, limit);
            });
            if (page == null) throw new IllegalStateException("Audit query transaction returned no result");
            return page;
        }
        applyOrdinaryContext(currentJwt());
        return query(jdbcTemplate, filter, cursor, limit);
    }

    private AuditPage query(JdbcTemplate template, AuditSearchFilter filter, Cursor cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT audit_entry_id, tenant_id, branch_id, occurred_at, actor_type, actor_id, authorized_role,
                       correlation_id, command_type, target_type, target_id, payment_id, batch_id, outcome,
                       decision_comment,
                       before_state ->> 'approvalId' AS before_approval_id,
                       before_state ->> 'approvalStatus' AS before_approval_status,
                       after_state ->> 'approvalId' AS after_approval_id,
                       after_state ->> 'approvalStatus' AS after_approval_status
                  FROM audit.audit_log
                 WHERE 1 = 1
                """);
        List<Object> values = new ArrayList<>();
        appendFilter(sql, values, "tenant_id", filter.tenantId());
        appendFilter(sql, values, "branch_id", filter.branchId());
        appendFilter(sql, values, "target_type", filter.targetType());
        appendFilter(sql, values, "target_id", filter.targetId());
        appendFilter(sql, values, "payment_id", filter.paymentId());
        appendFilter(sql, values, "batch_id", filter.batchId());
        appendFilter(sql, values, "actor_id", filter.actorId());
        appendFilter(sql, values, "command_type", filter.commandType());
        if (filter.outcome() != null) appendFilter(sql, values, "outcome", filter.outcome().name());
        appendFilter(sql, values, "correlation_id", filter.correlationId());
        if (filter.occurredFrom() != null) { sql.append(" AND occurred_at >= ?"); values.add(Timestamp.from(filter.occurredFrom())); }
        if (filter.occurredTo() != null) { sql.append(" AND occurred_at <= ?"); values.add(Timestamp.from(filter.occurredTo())); }
        if (cursor != null) {
            sql.append(" AND (occurred_at, audit_entry_id) < (?, ?)");
            values.add(Timestamp.from(cursor.occurredAt())); values.add(cursor.auditEntryId());
        }
        sql.append(" ORDER BY occurred_at DESC, audit_entry_id DESC LIMIT ?");
        values.add(limit + 1);
        List<AuditEntry> fetched = template.query(sql.toString(), (rs, row) -> entry(rs), values.toArray());
        boolean hasMore = fetched.size() > limit;
        List<AuditEntry> items = hasMore ? List.copyOf(fetched.subList(0, limit)) : List.copyOf(fetched);
        return new AuditPage(items, hasMore ? Cursor.from(items.getLast()).encode() : null);
    }

    private static void appendFilter(StringBuilder sql, List<Object> values, String column, Object value) {
        if (value != null) { sql.append(" AND ").append(column).append(" = ?"); values.add(value); }
    }

    private AuditEntry entry(ResultSet rs) throws SQLException {
        return new AuditEntry((UUID) rs.getObject("audit_entry_id"), (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("branch_id"), rs.getTimestamp("occurred_at").toInstant(),
                ActorType.valueOf(rs.getString("actor_type")), rs.getString("actor_id"), rs.getString("authorized_role"),
                (UUID) rs.getObject("correlation_id"), rs.getString("command_type"), rs.getString("target_type"),
                (UUID) rs.getObject("target_id"), (UUID) rs.getObject("payment_id"), (UUID) rs.getObject("batch_id"),
                CommandAuditOutcome.valueOf(rs.getString("outcome")), rs.getString("decision_comment"),
                new AuditSnapshot(rs.getString("before_approval_id"), rs.getString("before_approval_status")),
                new AuditSnapshot(rs.getString("after_approval_id"), rs.getString("after_approval_status")));
    }

    private void applyOrdinaryContext(Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalStateException("Tenant context is required");
        String branchId = jwt.getClaimAsString("branch_id");
        jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId);
        jdbcTemplate.queryForObject("SELECT set_config('app.branch_id', ?, true)", String.class, branchId == null ? "" : branchId);
        jdbcTemplate.queryForObject("SELECT set_config('app.role', '', true)", String.class);
    }

    private void applyAuditorContext() {
        auditorJdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', '', true)", String.class);
        auditorJdbcTemplate.queryForObject("SELECT set_config('app.branch_id', '', true)", String.class);
        auditorJdbcTemplate.queryForObject("SELECT set_config('app.role', 'auditor', true)", String.class);
    }

    private static int clamp(Integer requested) { return requested == null || requested <= 0 ? DEFAULT_LIMIT : Math.min(requested, MAX_LIMIT); }
    private static AuditSearchFilter emptyFilter() { return new AuditSearchFilter(null, null, null, null, null, null, null, null, null, null, null, null); }
    private static boolean isAuditor() { return authentication().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_auditor")); }
    private static Authentication authentication() { return SecurityContextHolder.getContext().getAuthentication(); }
    private static Jwt currentJwt() {
        Object principal = authentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) throw new IllegalStateException("JWT principal required");
        return jwt;
    }

    record Cursor(Instant occurredAt, UUID auditEntryId) {
        static Cursor from(AuditEntry entry) { return new Cursor(entry.occurredAt(), entry.auditEntryId()); }
        String encode() { return Base64.getUrlEncoder().withoutPadding().encodeToString(("v1|" + occurredAt + "|" + auditEntryId).getBytes(StandardCharsets.UTF_8)); }
        static Cursor decode(String value) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
                if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("Invalid audit cursor");
                return new Cursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
            } catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid audit cursor", exception); }
        }
    }
}
