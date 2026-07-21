package com.sepanexus.modules.paymentlifecycle.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The initial source-backed matrix subset is deliberately narrow: one active, tenant-wide rule
 * with no amount, payment-type, batch-size or risk selector. A rule with an unavailable selector,
 * step-up requirement, or ambiguous peer fails closed rather than being silently bypassed.
 */
@Component
class ApprovalMatrixEvaluator {

    private final JdbcTemplate jdbcTemplate;

    ApprovalMatrixEvaluator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Evaluation evaluate(UUID tenantId, BigDecimal amount, Instant now) {
        List<Rule> rules = jdbcTemplate.query("""
                SELECT id, min_amount, payment_type, max_batch_size, risk_level, requires_approval, requires_step_up
                FROM reference_data.approval_matrix_rules
                WHERE tenant_id = ? AND valid_from <= ? AND (valid_to IS NULL OR valid_to >= ?)
                ORDER BY id
                """, (rs, row) -> new Rule((UUID) rs.getObject("id"), rs.getBigDecimal("min_amount"),
                rs.getString("payment_type"), (Integer) rs.getObject("max_batch_size"), rs.getString("risk_level"),
                rs.getBoolean("requires_approval"), rs.getBoolean("requires_step_up")), tenantId,
                Date.valueOf(now.atZone(ZoneOffset.UTC).toLocalDate()), Date.valueOf(now.atZone(ZoneOffset.UTC).toLocalDate()));

        if (rules.isEmpty()) {
            return Evaluation.notRequired(null);
        }
        if (rules.stream().anyMatch(Rule::hasUnsupportedSelector)) {
            throw new ApprovalMatrixPolicyException("An active approval matrix rule uses an unsupported selector");
        }
        if (rules.stream().anyMatch(Rule::requiresStepUp)) {
            throw new ApprovalMatrixPolicyException("An active approval matrix rule requires unavailable step-up");
        }
        if (rules.size() != 1) {
            throw new ApprovalMatrixPolicyException("More than one active approval matrix rule matches the payment");
        }
        Rule rule = rules.getFirst();
        return new Evaluation(rule.id(), rule.requiresApproval());
    }

    record Evaluation(UUID ruleId, boolean requiresApproval) {
        static Evaluation notRequired(UUID ruleId) {
            return new Evaluation(ruleId, false);
        }
    }

    private record Rule(UUID id, BigDecimal minAmount, String paymentType, Integer maxBatchSize, String riskLevel,
            boolean requiresApproval, boolean requiresStepUp) {
        boolean hasUnsupportedSelector() {
            return minAmount != null || paymentType != null || maxBatchSize != null || riskLevel != null;
        }
    }
}
