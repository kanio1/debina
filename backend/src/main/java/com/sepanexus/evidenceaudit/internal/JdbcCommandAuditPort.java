package com.sepanexus.evidenceaudit.internal;

import com.sepanexus.evidenceaudit.CommandAuditEntry;
import com.sepanexus.evidenceaudit.CommandAuditPort;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Transaction-bound adapter for the evidence-audit-owned SECURITY DEFINER append boundary.
 * It has no repository and never issues direct DML against {@code audit.audit_log}.
 */
@Component
public class JdbcCommandAuditPort implements CommandAuditPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCommandAuditPort(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID append(CommandAuditEntry entry) {
        return jdbcTemplate.queryForObject("""
                SELECT audit.append_command_audit(
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?)
                """, (rs, rowNum) -> (UUID) rs.getObject(1),
                entry.tenantId(), entry.branchId(), entry.actorType().name(), entry.actorId(),
                entry.authorizedRole(), entry.sessionId(), entry.correlationId(), entry.commandType(),
                entry.targetType(), entry.targetId(), entry.paymentId(), entry.batchId(), entry.decisionComment(),
                canonicalJson(entry.beforeState()), canonicalJson(entry.afterState()), entry.outcome().name(),
                entry.commandExecutionId(), entry.occurredAt());
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Command audit state is not serializable", exception);
        }
    }
}
