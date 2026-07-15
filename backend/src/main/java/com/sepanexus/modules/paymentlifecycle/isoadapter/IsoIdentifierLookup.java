package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-side counterpart to {@code JsonDirectLineageRecorder}/{@code Pain001LineageRecorder}: the
 * ISO identifiers panel (EPIC-24 Story 24.2) and the payment list/detail's main {@code EndToEndId}
 * (EPIC-21 Story 21.2) read through {@code iso.payment_iso_identifiers}/{@code iso.message_lineage}
 * only, never through a field flattened onto {@code payment.payments} (G4 — ISO identity is not
 * business state; {@code payment.payments.end_to_end_id} was removed in Story 21.2).
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

    /**
     * The main {@code EndToEndId} shown for a payment — deterministically the identifier from the
     * {@code ORIGINAL_INSTRUCTION} lineage row, never the first row returned by an unordered scan.
     * Throws {@link MissingPrimaryIdentifierException} rather than returning {@code null}: after
     * Story 21.2's backfill, every payment must have exactly one.
     */
    public String findPrimaryEndToEndId(UUID paymentId) {
        List<String> results = jdbcTemplate.query(PRIMARY_QUERY + "ml.payment_id = ?",
                (rs, rowNum) -> rs.getString("end_to_end_id"), paymentId);
        if (results.isEmpty()) {
            throw new MissingPrimaryIdentifierException(paymentId);
        }
        return results.get(0);
    }

    /** Batch form for the payment list, to avoid one query per row. */
    public Map<UUID, String> findPrimaryEndToEndIds(List<UUID> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        return jdbcTemplate.query(PRIMARY_QUERY + "ml.payment_id = ANY(?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", paymentIds.toArray())),
                rs -> {
                    Map<UUID, String> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(UUID.fromString(rs.getString("payment_id")), rs.getString("end_to_end_id"));
                    }
                    return result;
                });
    }

    private static final String PRIMARY_QUERY = """
            SELECT ml.payment_id, pii.end_to_end_id
            FROM iso.message_lineage ml
            JOIN iso.payment_iso_identifiers pii
                ON pii.iso_message_id = ml.iso_message_id AND pii.payment_id = ml.payment_id
            WHERE ml.lineage_role = 'ORIGINAL_INSTRUCTION' AND
            """;

    public record IsoIdentifierView(String sourceMessageType, String endToEndId, UUID isoMessageId) {
    }
}
