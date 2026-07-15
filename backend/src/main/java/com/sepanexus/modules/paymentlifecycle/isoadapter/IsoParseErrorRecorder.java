package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Append-only evidence for XML hardening/parser failures (EPIC-28 Story 28.1,
 * sepa-nexus-message-flow-and-data-blueprint.md §4.3c line 656) — failures that occur BEFORE any
 * {@code iso.iso_messages} row can exist, so there is no {@code iso_message_id} to attach to
 * (source DDL declares none). Never records a {@code CanonicalMapper} mapping failure or a
 * signature failure — those are different stages/owners (mapping failures belong to
 * {@code iso.iso_message_validation_results}, source §4.3c's {@code CANONICAL_MAPPING} validation
 * type, not built until Story 28.2; signature failures are {@code signature.*}'s evidence, Story
 * 31.2, and the parser never even runs for them).
 */
@Component
public class IsoParseErrorRecorder {

    private final JdbcTemplate jdbcTemplate;

    public IsoParseErrorRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(UUID rawMessageId, String messageTypeGuess, String errorCode, String errorPath,
            String errorMessage, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO iso.iso_message_parse_errors
                    (raw_message_id, message_type_guess, error_code, error_path, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, rawMessageId, messageTypeGuess, errorCode, errorPath, errorMessage, Timestamp.from(occurredAt));
    }
}
