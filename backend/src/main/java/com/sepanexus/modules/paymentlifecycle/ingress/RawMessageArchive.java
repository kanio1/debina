package com.sepanexus.modules.paymentlifecycle.ingress;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Append-only evidence archive (sepa-nexus-message-flow-and-data-blueprint.md §4.2) — never
 * deduplicates; a legitimate resend of the same bytes still archives a second row. Idempotency is
 * {@link IdempotencyStore}'s job, not this archive's.
 */
@Component
public class RawMessageArchive {

    private final JdbcTemplate jdbcTemplate;

    public RawMessageArchive(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID archive(String channel, UUID tenantId, String messageType, byte[] payload) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ingress.raw_inbound_messages (id, channel, tenant_id, message_type, payload, payload_sha256)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, channel, tenantId, messageType, payload, sha256(payload));
        return id;
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
