package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * EPIC-27 Story 27.2C: persists one {@link CorrelationDecision} to {@code
 * iso.iso_message_correlation} (blueprint §4.3c DDL) — append-only (V21 revokes {@code
 * UPDATE}/{@code DELETE} from {@code sepa_app}). Writes exactly one row per correlation attempt,
 * for every outcome (MATCHED/AMBIGUOUS/ORPHANED), matching the blueprint §2.4 flow where all three
 * branches "write iso.iso_message_correlation". {@code score} is always {@code NULL} — no scoring
 * formula/values are defined anywhere in source (readiness audit's score decision); {@code
 * matched_by} is the controlled {@link CorrelationMatchStrategy} enum name, {@code NULL} for
 * anything but {@code MATCHED}. {@code created_at} always comes from {@link
 * com.sepanexus.shared.ClockPort}, never {@code Instant.now()}.
 */
@Component
public class IsoMessageCorrelationRecorder {

    private static final String PACS002_TO_PAYMENT = "PACS002_TO_PAYMENT";

    private final JdbcTemplate jdbcTemplate;

    public IsoMessageCorrelationRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID recordPacs002Correlation(UUID isoMessageId, CorrelationDecision decision, Instant createdAt) {
        UUID id = UUID.randomUUID();
        String matchedBy = decision.matchedBy() == null ? null : decision.matchedBy().name();
        jdbcTemplate.update("""
                INSERT INTO iso.iso_message_correlation
                    (id, iso_message_id, correlation_type, matched_payment_id, status, score, matched_by, created_at)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
                """, id, isoMessageId, PACS002_TO_PAYMENT, decision.matchedPaymentId(),
                decision.outcome().name(), matchedBy, Timestamp.from(createdAt));
        return id;
    }
}
