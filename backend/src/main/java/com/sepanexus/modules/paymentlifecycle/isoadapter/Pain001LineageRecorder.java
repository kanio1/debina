package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Records identifiers + lineage for the pain.001 REST-XML channel (EPIC-19 Story 19.4) — the
 * richer counterpart to {@link JsonDirectLineageRecorder}, using the identifier fields V15 added
 * to {@code iso.payment_iso_identifiers}/{@code iso.iso_messages} for a real ISO message.
 */
@Component
public class Pain001LineageRecorder {

    public static final String MESSAGE_TYPE = "pain.001";

    private final JdbcTemplate jdbcTemplate;

    public Pain001LineageRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(UUID paymentId, UUID tenantId, UUID rawMessageId, CanonicalPaymentCommand command, Instant recordedAt) {
        UUID isoMessageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO iso.iso_messages
                    (id, direction, message_type, parse_status, raw_message_id, msg_id, cre_dt_tm, tenant_id)
                VALUES (?, 'INBOUND', ?, 'PARSED', ?, ?, ?, ?)
                """, isoMessageId, MESSAGE_TYPE, rawMessageId, command.msgId(), Timestamp.from(recordedAt), tenantId);

        jdbcTemplate.update("""
                INSERT INTO iso.payment_iso_identifiers
                    (payment_id, source_message_type, iso_message_id, end_to_end_id, msg_id, pmt_inf_id, instr_id, uetr)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, paymentId, MESSAGE_TYPE, isoMessageId, command.endToEndId(), command.msgId(), command.pmtInfId(),
                command.instrId(), command.uetr());

        jdbcTemplate.update("""
                INSERT INTO iso.message_lineage (lineage_role, iso_message_id, raw_message_id, payment_id)
                VALUES ('ORIGINAL_INSTRUCTION', ?, ?, ?)
                """, isoMessageId, rawMessageId, paymentId);
    }
}
