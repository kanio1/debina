package com.sepanexus.ledger;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** ADR-N11 ledger adapter: invokes only ledger's narrow SECURITY DEFINER command function. */
@Component("grossInstantLedgerPort")
public class JdbcGrossInstantLedgerPort implements GrossInstantLedgerPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcGrossInstantLedgerPort(@Qualifier("grossInstantJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GrossInstantResult reserveAndPost(GrossInstantCommand command) {
        CommandRow row = jdbcTemplate.queryForObject("""
                SELECT outcome, reservation_id, terminal_entry_id, terminal_occurred_at, replayed
                FROM ledger.gross_instant_reserve_post(?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid,
                    ?::bigint, ?::char(3), ?::uuid, ?::timestamptz)
                """, (resultSet, ignored) -> new CommandRow(resultSet.getString("outcome"),
                resultSet.getObject("reservation_id", UUID.class), resultSet.getObject("terminal_entry_id", UUID.class),
                resultSet.getTimestamp("terminal_occurred_at"), resultSet.getBoolean("replayed")),
                command.tenantId(), command.settlementAttemptId(), command.paymentId(), command.debtorAccountId(),
                command.creditorAccountId(), command.amountMinor(), command.currency(), command.commandId(),
                Timestamp.from(command.occurredAt()));
        if ("INSUFFICIENT_LIQUIDITY".equals(row.outcome())) return new InsufficientLiquidity(row.replayed());
        if ("POSTED".equals(row.outcome()) && row.reservationId() != null && row.postEntryId() != null
                && row.occurredAt() != null) {
            return new Posted(row.reservationId(), row.postEntryId(), row.occurredAt().toInstant(), row.replayed());
        }
        throw new IllegalStateException("Unexpected gross-instant ledger result " + row.outcome());
    }

    /** Separate reserve/post/release are intentionally not exposed through the coordinator function. */
    @Override public ReserveResult reserve(UUID attemptId, UUID paymentId, UUID debtorAccountId, long amountMinor) {
        throw new UnsupportedOperationException("Use reserveAndPost for an ADR-N11 gross-instant command");
    }
    @Override public TerminalResult post(UUID reservationId, UUID creditorAccountId, UUID commandId) {
        throw new UnsupportedOperationException("Use reserveAndPost for an ADR-N11 gross-instant command");
    }
    @Override public TerminalResult release(UUID reservationId, UUID commandId) {
        throw new UnsupportedOperationException("Gross-instant command does not expose release");
    }

    private record CommandRow(String outcome, UUID reservationId, UUID postEntryId, Timestamp occurredAt,
            boolean replayed) { }
}
