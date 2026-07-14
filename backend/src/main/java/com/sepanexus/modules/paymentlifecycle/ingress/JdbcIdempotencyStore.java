package com.sepanexus.modules.paymentlifecycle.ingress;

import com.sepanexus.shared.ClockPort;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbcTemplate;
    private final ClockPort clockPort;

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate, ClockPort clockPort) {
        this.jdbcTemplate = jdbcTemplate;
        this.clockPort = clockPort;
    }

    @Override
    public IdempotencyClaim claim(UUID sourceId, String idempotencyKey, byte[] requestHash) {
        Timestamp now = Timestamp.from(clockPort.now());
        int inserted = jdbcTemplate.update("""
                INSERT INTO ingress.idempotency_keys (source_id, idem_key, request_hash, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (source_id, idem_key) DO NOTHING
                """, sourceId, idempotencyKey, requestHash, now, now);

        if (inserted == 1) {
            return IdempotencyClaim.claimed();
        }

        // A concurrent claimant on the same (source_id, idem_key) blocks this INSERT until the
        // first transaction commits (Postgres locks the conflicting unique-index entry), so by the
        // time we reach this SELECT the winner's row is fully committed, including payment_id.
        return jdbcTemplate.queryForObject("""
                SELECT request_hash, payment_id, response_code
                FROM ingress.idempotency_keys
                WHERE source_id = ? AND idem_key = ?
                """, (rs, rowNum) -> {
            byte[] storedHash = rs.getBytes("request_hash");
            UUID paymentId = (UUID) rs.getObject("payment_id");
            Integer responseCode = (Integer) rs.getObject("response_code");
            if (!Arrays.equals(storedHash, requestHash) || paymentId == null || responseCode == null) {
                return IdempotencyClaim.conflict();
            }
            return IdempotencyClaim.replay(paymentId, responseCode);
        }, sourceId, idempotencyKey);
    }

    @Override
    public void complete(UUID sourceId, String idempotencyKey, UUID paymentId, int responseCode) {
        jdbcTemplate.update("""
                UPDATE ingress.idempotency_keys
                SET payment_id = ?, response_code = ?, last_seen_at = ?
                WHERE source_id = ? AND idem_key = ?
                """, paymentId, responseCode, Timestamp.from(clockPort.now()), sourceId, idempotencyKey);
    }
}
