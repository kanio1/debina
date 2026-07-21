package com.sepanexus.evidenceaudit.internal;

import com.sepanexus.evidenceaudit.DeniedCommandAuditEntry;
import com.sepanexus.evidenceaudit.DeniedCommandAuditPort;
import com.sepanexus.evidenceaudit.DeniedCommandAuditUnavailableException;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Own transaction for a denied attempt: it writes only evidence and never authorizes on failure. */
@Component
public class JdbcDeniedCommandAuditPort implements DeniedCommandAuditPort {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public JdbcDeniedCommandAuditPort(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public UUID appendDenied(DeniedCommandAuditEntry entry) {
        try {
            return transactions.execute(status -> {
                jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true), set_config('app.branch_id', ?, true)",
                        (rs, row) -> rs.getString(1), entry.tenantId().toString(),
                        entry.branchId() == null ? "" : entry.branchId().toString());
                return jdbcTemplate.queryForObject("""
                        SELECT audit.append_denied_command_audit(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, (rs, row) -> (UUID) rs.getObject(1), entry.tenantId(), entry.branchId(), entry.actorId(),
                        entry.authorizedRole(), entry.sessionId(), entry.correlationId(), entry.commandType(), entry.targetType(),
                        entry.targetId(), entry.paymentId(), entry.denialReason(), UUID.randomUUID(), Timestamp.from(entry.occurredAt()));
            });
        } catch (RuntimeException exception) {
            throw new DeniedCommandAuditUnavailableException(exception);
        }
    }
}
