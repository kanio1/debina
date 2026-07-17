package com.sepanexus.egress.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * EPIC-43 Story 43.1: claims a batch of {@code egress.outbound_messages} rows still in {@code
 * REQUESTED} and advances them to {@code CLAIMED_FOR_DELIVERY}, atomically and horizontally
 * safely against any number of concurrent dispatcher instances. The {@code SELECT ... FOR UPDATE
 * SKIP LOCKED} and the {@code UPDATE} are one single SQL statement (a CTE), never two separate
 * statements — this is what makes claiming safe without any application-level locking. Never
 * renders, signs, or delivers an artifact; those are later stories' scope (43.2-43.5).
 */
@Component
public class OutboundMessageDispatcher {

    private final JdbcTemplate jdbcTemplate;

    public OutboundMessageDispatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<UUID> claimPendingBatch(int limit) {
        return jdbcTemplate.query("""
                WITH claimable AS (
                    SELECT id
                    FROM egress.outbound_messages
                    WHERE state = 'REQUESTED'
                    ORDER BY created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE egress.outbound_messages
                SET state = 'CLAIMED_FOR_DELIVERY'
                WHERE id IN (SELECT id FROM claimable)
                RETURNING id
                """, this::mapId, limit);
    }

    private UUID mapId(ResultSet rs, int rowNum) throws SQLException {
        return (UUID) rs.getObject("id");
    }
}
