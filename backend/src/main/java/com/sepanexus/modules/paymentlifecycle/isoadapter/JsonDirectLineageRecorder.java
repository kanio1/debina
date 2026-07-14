package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Records identifiers + lineage for the REST-JSON channel through the seeded {@code JSON_DIRECT}
 * pseudo message-version (sepa-nexus-message-flow-and-data-blueprint.md §2.2a, ADR-N7) — so a
 * JSON-submitted payment is correlatable exactly like any ISO-XML channel, with no nullable
 * {@code iso_message_id} shortcut.
 */
@Component
public class JsonDirectLineageRecorder {

    public static final String MESSAGE_TYPE = "JSON_DIRECT";

    private final JdbcTemplate jdbcTemplate;

    public JsonDirectLineageRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(UUID paymentId, UUID rawMessageId, String endToEndId) {
        UUID isoMessageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status, raw_message_id)
                VALUES (?, 'INBOUND', ?, 'SKIPPED', ?)
                """, isoMessageId, MESSAGE_TYPE, rawMessageId);

        jdbcTemplate.update("""
                INSERT INTO iso.payment_iso_identifiers (payment_id, source_message_type, iso_message_id, end_to_end_id)
                VALUES (?, ?, ?, ?)
                """, paymentId, MESSAGE_TYPE, isoMessageId, endToEndId);

        jdbcTemplate.update("""
                INSERT INTO iso.message_lineage (lineage_role, iso_message_id, raw_message_id, payment_id)
                VALUES ('ORIGINAL_INSTRUCTION', ?, ?, ?)
                """, isoMessageId, rawMessageId, paymentId);
    }
}
