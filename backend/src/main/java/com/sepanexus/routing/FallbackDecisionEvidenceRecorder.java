package com.sepanexus.routing;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Routing-owned atomic writer for the fallback segment of an immutable decision snapshot. It reads
 * a static rule only to verify the explicit selection and writes exclusively {@code routing.*}.
 */
public final class FallbackDecisionEvidenceRecorder {

    private static final String UNCONDITIONAL_ONLY = "UNCONDITIONAL_ONLY";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public FallbackDecisionEvidenceRecorder(
            JdbcTemplate jdbcTemplate, TransactionTemplate transactions, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public UUID record(FallbackDecisionCommand command) {
        Objects.requireNonNull(command, "command");
        return transactions.execute(status -> {
            jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class,
                    command.tenantId().toString());
            verifyRuleStillMatches(command.selection());
            jdbcTemplate.update("""
                    INSERT INTO routing.route_decisions
                        (id, payment_id, tenant_id, selected_profile_id, outcome, fallback_applied,
                         fallback_rule_id, decided_at)
                    VALUES (?, ?, ?, ?, 'FALLBACK_SELECTED', true, ?, ?)
                    """, command.decisionId(), command.paymentId(), command.tenantId(),
                    command.selection().fallbackProfileId(), command.selection().ruleId(),
                    Timestamp.from(command.decidedAt()));
            for (RouteCandidateEvidence candidate : command.candidates()) {
                jdbcTemplate.update("""
                        INSERT INTO routing.route_candidate_results
                            (decision_id, profile_id, seq, passed, reject_reason)
                        VALUES (?, ?, ?, ?, ?)
                        """, command.decisionId(), candidate.profileId(), candidate.sequence(),
                        candidate.passed(), candidate.rejectReason());
            }
            jdbcTemplate.update("""
                    INSERT INTO routing.route_decision_explanations
                        (decision_id, explanation, reference_data_version, sim_scenario_id, created_at)
                    VALUES (?, CAST(? AS jsonb), ?, NULL, ?)
                    """, command.decisionId(), snapshot(command), command.referenceDataVersion(),
                    Timestamp.from(command.decidedAt()));
            return command.decisionId();
        });
    }

    private void verifyRuleStillMatches(FallbackSelection selection) {
        Optional<FallbackRule> stored = jdbcTemplate.query("""
                SELECT id, profile_id, fallback_profile_id, priority, condition
                FROM reference_data.profile_fallback_rules
                WHERE id = ?
                """, resultSet -> resultSet.next()
                ? Optional.of(new FallbackRule(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("profile_id", UUID.class),
                        resultSet.getObject("fallback_profile_id", UUID.class),
                        resultSet.getShort("priority"), resultSet.getString("condition")))
                : Optional.empty(), selection.ruleId());
        FallbackRule rule = stored.orElseThrow(() -> new IllegalArgumentException("fallback rule is not configured"));
        if (rule.condition() != null) {
            throw new UnsupportedFallbackConditionException(rule);
        }
        if (!rule.profileId().equals(selection.sourceProfileId())
                || !rule.fallbackProfileId().equals(selection.fallbackProfileId())
                || rule.priority() != selection.priority()) {
            throw new IllegalArgumentException("fallback selection does not match configured rule");
        }
    }

    private String snapshot(FallbackDecisionCommand command) {
        try {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("ruleId", command.selection().ruleId());
            fallback.put("sourceProfileId", command.selection().sourceProfileId());
            fallback.put("fallbackProfileId", command.selection().fallbackProfileId());
            fallback.put("priority", command.selection().priority());
            fallback.put("conditionSupport", UNCONDITIONAL_ONLY);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("fallback", fallback);
            snapshot.put("candidates", command.candidates());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot serialize fallback decision snapshot", exception);
        }
    }
}
