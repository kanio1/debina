package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-side counterpart to {@link JsonDirectLineageRecorder}: the ISO identifiers panel
 * (EPIC-24 Story 24.2) reads through {@code iso.payment_iso_identifiers} only, never through a
 * field flattened onto {@code payment.payments} (G4 — ISO identity is not business state).
 */
@Component
public class IsoIdentifierLookup {

    private final JdbcTemplate jdbcTemplate;

    public IsoIdentifierLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IsoIdentifierView> findByPaymentId(UUID paymentId) {
        return jdbcTemplate.query("""
                SELECT source_message_type, end_to_end_id, iso_message_id
                FROM iso.payment_iso_identifiers
                WHERE payment_id = ?
                ORDER BY source_message_type
                """,
                (rs, rowNum) -> new IsoIdentifierView(
                        rs.getString("source_message_type"),
                        rs.getString("end_to_end_id"),
                        UUID.fromString(rs.getString("iso_message_id"))),
                paymentId);
    }

    public record IsoIdentifierView(String sourceMessageType, String endToEndId, UUID isoMessageId) {
    }
}
