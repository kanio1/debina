package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CutoffStateReaderPortTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18").withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    private static DeferredSettlementCycleService cycles; private static CutoffStateReader reader;
    @BeforeAll static void migrate() throws Exception {
        try (Connection c = admin(); Statement s = c.createStatement()) { s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'"); }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration").locations("filesystem:src/main/resources/db/migration").load().migrate();
        SettlementConnectionFactory connection = new SettlementConnectionFactory(POSTGRES.getJdbcUrl(), "settlement_role", "dev-only-settlement");
        cycles = new DeferredSettlementCycleService(connection); reader = new JdbcCutoffStateReader(connection);
    }
    @Test void returnsCombinedConfiguredCutoffAndRuntimeCycleFactsWithoutMutation() throws Exception {
        UUID profile = UUID.randomUUID(), cycle = UUID.randomUUID(); LocalDate date = LocalDate.of(2026, 7, 21); Instant cutoff = Instant.parse("2026-07-21T10:00:00Z");
        cycles.createOpenCycle(cycle, profile, date, (short) 1, cutoff);
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement("INSERT INTO reference_data.settlement_cutoff_calendar (profile_id,business_date,session_no,cutoff_at) VALUES (?,?,?,?)")) {
            s.setObject(1, profile); s.setObject(2, date); s.setShort(3, (short) 1); s.setTimestamp(4, Timestamp.from(cutoff)); s.executeUpdate();
        }
        CutoffStateReader.CutoffState state = reader.read(new CutoffStateReader.CutoffStateQuery(profile, date, (short) 1)).orElseThrow();
        assertThat(state.cycleId()).isEqualTo(cycle); assertThat(state.cycleState()).isEqualTo(DeferredCycleState.OPEN); assertThat(state.cutoffAt()).isEqualTo(cutoff); assertThat(state.membershipAvailable()).isTrue();
        assertThat(reader.read(new CutoffStateReader.CutoffStateQuery(profile, date, (short) 2))).isEmpty();
        try (Connection c = admin(); Statement s = c.createStatement(); var rows = s.executeQuery("SELECT state FROM settlement.settlement_cycles WHERE id = '" + cycle + "'")) { rows.next(); assertThat(rows.getString(1)).isEqualTo("OPEN"); }
    }
    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}
