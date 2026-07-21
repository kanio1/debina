package com.sepanexus.modules.paymentlifecycle.isoadapter;

import com.sepanexus.modules.PaymentIsoEvidenceQuery;
import com.sepanexus.modules.PaymentVisibilityQuery;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** ISO owner read model. It reads only iso.*; payment visibility is delegated to its public port. */
@Component
class IsoPaymentEvidenceReadModel implements PaymentIsoEvidenceQuery {
    private final JdbcTemplate jdbcTemplate;
    private final PaymentVisibilityQuery paymentVisibility;

    IsoPaymentEvidenceReadModel(JdbcTemplate jdbcTemplate, PaymentVisibilityQuery paymentVisibility) {
        this.jdbcTemplate = jdbcTemplate;
        this.paymentVisibility = paymentVisibility;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentIsoEvidence evidence(UUID tenantId, UUID branchId, UUID paymentId) {
        paymentVisibility.requireVisible(tenantId, branchId, paymentId);
        List<IsoMessageEvidence> messages = jdbcTemplate.query("""
                SELECT ml.iso_message_id, im.message_type, version.effective_from, ml.lineage_role, ml.created_at
                FROM iso.message_lineage ml
                JOIN iso.iso_messages im ON im.id = ml.iso_message_id
                LEFT JOIN LATERAL (
                    SELECT effective_from FROM iso.iso_message_versions imv
                    WHERE imv.message_type = im.message_type
                      AND imv.effective_from <= COALESCE(im.cre_dt_tm, im.created_at)::date
                    ORDER BY imv.effective_from DESC LIMIT 1
                ) version ON true
                WHERE ml.payment_id = ? AND im.tenant_id = ?
                ORDER BY ml.created_at ASC, ml.iso_message_id ASC, ml.id ASC
                """, (rs, ignored) -> {
                    String messageType = rs.getString(2);
                    return new IsoMessageEvidence(UUID.fromString(rs.getString(1)), messageType,
                            messageVersion(messageType),
                            rs.getObject(3, Date.class) == null ? null : rs.getObject(3, Date.class).toLocalDate(),
                            rs.getString(4), rs.getObject(5, Timestamp.class).toInstant());
                }, paymentId, tenantId);
        List<IsoIdentifierEvidence> identifiers = jdbcTemplate.query("""
                SELECT pii.iso_message_id, identifier.type, identifier.value
                FROM iso.payment_iso_identifiers pii
                JOIN iso.iso_messages im ON im.id = pii.iso_message_id
                CROSS JOIN LATERAL (VALUES
                  ('MSG_ID', pii.msg_id), ('PMT_INF_ID', pii.pmt_inf_id), ('INSTR_ID', pii.instr_id),
                  ('END_TO_END_ID', pii.end_to_end_id), ('TX_ID', pii.tx_id), ('UETR', pii.uetr)
                ) AS identifier(type, value)
                WHERE pii.payment_id = ? AND im.tenant_id = ? AND identifier.value IS NOT NULL
                ORDER BY pii.iso_message_id ASC, identifier.type ASC
                """, (rs, ignored) -> new IsoIdentifierEvidence(UUID.fromString(rs.getString(1)),
                IsoIdentifierType.valueOf(rs.getString(2)), rs.getString(3)), paymentId, tenantId);
        return new PaymentIsoEvidence(paymentId, messages, identifiers);
    }

    private static String messageVersion(String messageType) {
        return Pain001LineageRecorder.MESSAGE_TYPE.equals(messageType) ? Pain001CanonicalMapper.MESSAGE_VERSION : null;
    }
}
