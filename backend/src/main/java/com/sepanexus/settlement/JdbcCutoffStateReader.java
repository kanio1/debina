package com.sepanexus.settlement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Real settlement-role implementation; it exposes no repository/JDBC object and never mutates. */
public final class JdbcCutoffStateReader implements CutoffStateReader {
    private final SettlementConnectionFactory connections;
    public JdbcCutoffStateReader(SettlementConnectionFactory connections) { this.connections = Objects.requireNonNull(connections, "connections"); }
    @Override public Optional<CutoffState> read(CutoffStateQuery query) {
        Objects.requireNonNull(query, "query");
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement("""
                SELECT cycle.id, cycle.state, calendar.cutoff_at, calendar.business_date, calendar.session_no
                FROM reference_data.settlement_cutoff_calendar calendar
                JOIN settlement.settlement_cycles cycle ON cycle.profile_id = calendar.profile_id
                    AND cycle.business_date = calendar.business_date AND cycle.session_no = calendar.session_no
                WHERE calendar.profile_id = ? AND calendar.business_date = ? AND calendar.session_no = ?
                """)) {
            statement.setObject(1, query.profileId()); statement.setObject(2, query.businessDate()); statement.setShort(3, query.sessionNo());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new CutoffState(rows.getObject(1, java.util.UUID.class),
                        DeferredCycleState.valueOf(rows.getString(2)), rows.getTimestamp(3).toInstant(),
                        rows.getObject(4, LocalDate.class), rows.getShort(5))) : Optional.empty();
            }
        } catch (SQLException failure) { throw new IllegalStateException("could not read settlement cutoff/cycle state", failure); }
    }
}
