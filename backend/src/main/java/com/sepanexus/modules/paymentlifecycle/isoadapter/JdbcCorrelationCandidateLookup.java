package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * EPIC-27 Story 27.2C: the production {@link CorrelationCandidateLookup} — every query joins
 * {@code iso.payment_iso_identifiers} to {@code iso.iso_messages} and filters on {@code
 * iso.iso_messages.tenant_id} (this migration's own new column, backfilled from {@code
 * ingress.raw_inbound_messages}), entirely inside the {@code iso} schema. Never reads {@code
 * payment.*} (root {@code AGENTS.md}/§3.6.3 binding rule) — a candidate is identified only by
 * {@code payment_id}, the FK value already carried on {@code iso.payment_iso_identifiers}.
 *
 * <p>Candidates today are only ever pain.001/{@code JSON_DIRECT} original-instruction rows (the
 * only source message types this system writes to {@code iso.payment_iso_identifiers} as of this
 * story) — a future session adding a pacs.002/R-message's own identifier row to this table would
 * need to revisit whether {@code source_message_type} filtering belongs here.
 */
@Component
public class JdbcCorrelationCandidateLookup implements CorrelationCandidateLookup {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCorrelationCandidateLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlTxId(UUID tenantId, String orgnlMsgId, String orgnlTxId) {
        return query("pii.msg_id = ? AND pii.tx_id = ?", tenantId, orgnlMsgId, orgnlTxId);
    }

    @Override
    public List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlInstrIdAndOrgnlEndToEndId(
            UUID tenantId, String orgnlMsgId, String orgnlInstrId, String orgnlEndToEndId) {
        return query("pii.msg_id = ? AND pii.instr_id = ? AND pii.end_to_end_id = ?",
                tenantId, orgnlMsgId, orgnlInstrId, orgnlEndToEndId);
    }

    @Override
    public List<CorrelationCandidate> findByUetr(UUID tenantId, String uetr) {
        return query("pii.uetr = ?", tenantId, uetr);
    }

    @Override
    public List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlEndToEndId(UUID tenantId, String orgnlMsgId, String orgnlEndToEndId) {
        return query("pii.msg_id = ? AND pii.end_to_end_id = ?", tenantId, orgnlMsgId, orgnlEndToEndId);
    }

    private List<CorrelationCandidate> query(String predicate, UUID tenantId, Object... predicateArgs) {
        String sql = """
                SELECT DISTINCT pii.payment_id
                FROM iso.payment_iso_identifiers pii
                JOIN iso.iso_messages im ON im.id = pii.iso_message_id
                WHERE im.tenant_id = ? AND %s
                """.formatted(predicate);
        Object[] args = new Object[predicateArgs.length + 1];
        args[0] = tenantId;
        System.arraycopy(predicateArgs, 0, args, 1, predicateArgs.length);
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new CorrelationCandidate(UUID.fromString(rs.getString("payment_id"))),
                args);
    }
}
