package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.modules.PaymentFinalityPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 18 proof for §4.6 G6 locking, P8 netting, grants and replay. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class DeferredSettlementCycleIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    private static DeferredSettlementCycleService cycles;
    private static final Instant CUTOFF = Instant.parse("2026-07-21T10:00:00Z");

    @BeforeAll static void migrate() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        cycles = new DeferredSettlementCycleService(
                new SettlementConnectionFactory(POSTGRES.getJdbcUrl(), "settlement_role", "dev-only-settlement"));
    }

    @BeforeEach void clear() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE settlement.settlement_cycle_command_receipts, settlement.settlement_positions, "
                    + "settlement.settlement_items, settlement.deferred_cycle_attempt_events, settlement.deferred_cycle_attempts, "
                    + "settlement.settlement_cycles CASCADE");
        }
    }

    @Test void openCycleAcceptsAnEligibleItemAndExactAssignmentReplayDoesNotDuplicateIt() throws Exception {
        UUID cycleId = openCycle();
        DeferredCycleAssignment assignment = assignment(cycleId, UUID.randomUUID(), UUID.randomUUID(), 125);

        DeferredSettlementCycleService.AssignmentResult first = cycles.assign(assignment);
        DeferredSettlementCycleService.AssignmentResult replay = cycles.assign(assignment);

        assertThat(first.replayed()).isFalse();
        assertThat(replay).isEqualTo(new DeferredSettlementCycleService.AssignmentResult(first.itemId(), true));
        assertThat(count("settlement.settlement_items")).isEqualTo(1);
        assertThat(count("settlement.deferred_cycle_attempt_events")).isEqualTo(1);
    }

    @Test void closingAndClosedCyclesFailClosedForNewMembershipAndIllegalBackwardTransition() throws Exception {
        UUID cycleId = openCycle();
        cycles.beginClosing(command(cycleId));

        assertThatThrownBy(() -> cycles.assign(assignment(cycleId, UUID.randomUUID(), UUID.randomUUID(), 125)))
                .isInstanceOf(DeferredCycleException.class).hasMessageContaining("does not accept membership");
        cycles.close(command(cycleId));
        assertThatThrownBy(() -> cycles.beginClosing(command(cycleId)))
                .isInstanceOf(DeferredCycleException.class).hasMessageContaining("illegal cycle transition");
        assertThat(count("settlement.settlement_items")).isZero();
    }

    @Test void g6CloseRaceHasOneDeterministicMembershipOutcomeAndNoPartialItem() throws Exception {
        UUID cycleId = openCycle();
        DeferredCycleAssignment assignment = assignment(cycleId, UUID.randomUUID(), UUID.randomUUID(), 250);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> assignmentResult = executor.submit(() -> {
                start.await();
                try { cycles.assign(assignment); return true; }
                catch (DeferredCycleException expected) { return false; }
            });
            Future<DeferredSettlementCycleService.CycleResult> closeResult = executor.submit(() -> {
                start.await(); return cycles.beginClosing(command(cycleId));
            });

            boolean assigned = assignmentResult.get(15, TimeUnit.SECONDS);
            assertThat(closeResult.get(15, TimeUnit.SECONDS).state()).isEqualTo(DeferredCycleState.CLOSING);
            assertThat(count("settlement.settlement_items")).isEqualTo(assigned ? 1 : 0);
            cycles.close(command(cycleId));
            assertThat(state(cycleId)).isEqualTo("CLOSED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void closedCycleNetsPositionsInOneDerivedSetWithZeroSumAndCommandReplayIsDuplicateFree() throws Exception {
        UUID cycleId = openCycle();
        UUID participantA = UUID.randomUUID(); UUID participantB = UUID.randomUUID(); UUID participantC = UUID.randomUUID();
        cycles.assign(new DeferredCycleAssignment(cycleId, UUID.randomUUID(), UUID.randomUUID(), profile(cycleId), participantA, participantB, 100));
        cycles.assign(new DeferredCycleAssignment(cycleId, UUID.randomUUID(), UUID.randomUUID(), profile(cycleId), participantB, participantC, 40));
        closeCycle(cycleId);

        DeferredSettlementCycleService.NettingResult first = cycles.net(command(cycleId));
        DeferredSettlementCycleService.NettingResult replay = cycles.net(replayCommand(cycleId, first));

        assertThat(first.positionCount()).isEqualTo(3);
        assertThat(replay.replayed()).isTrue();
        assertThat(count("settlement.settlement_positions")).isEqualTo(3);
        assertThat(net(cycleId, participantA)).isEqualTo(-100);
        assertThat(net(cycleId, participantB)).isEqualTo(60);
        assertThat(net(cycleId, participantC)).isEqualTo(40);
        assertThat(sum(cycleId)).isZero();
        assertThat(state(cycleId)).isEqualTo("NETTED");
    }

    @Test void nettingBeforeClosedFailsAndLeavesNoPartialPositions() throws Exception {
        UUID cycleId = openCycle();
        cycles.assign(assignment(cycleId, UUID.randomUUID(), UUID.randomUUID(), 100));

        assertThatThrownBy(() -> cycles.net(command(cycleId))).isInstanceOf(DeferredCycleException.class)
                .hasMessageContaining("only CLOSED cycles");

        assertThat(count("settlement.settlement_positions")).isZero();
        assertThat(state(cycleId)).isEqualTo("OPEN");
    }

    @Test void onlyPersistedCycleSettlementCreatesOneOnCycleSettledAuthorityRecord() throws Exception {
        UUID cycleId = openCycle();
        DeferredCycleAssignment assignment = assignment(cycleId, UUID.randomUUID(), UUID.randomUUID(), 100);
        DeferredSettlementCycleService.AssignmentResult assigned = cycles.assign(assignment);
        closeCycle(cycleId);
        cycles.net(command(cycleId));
        SettlementFinalityService finality = new SettlementFinalityService(
                new SettlementConnectionFactory(POSTGRES.getJdbcUrl(), "settlement_role", "dev-only-settlement"), new RecordingProjection());

        assertThatThrownBy(() -> finality.recordCycleItemSettled(UUID.randomUUID(), cycleId, assigned.itemId(),
                assignment.settlementAttemptId(), assignment.paymentId(), new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("persisted SETTLED cycle");
        assertThat(count("settlement.settlement_finality_records")).isZero();

        Instant settledAt = Instant.parse("2026-07-21T10:05:00.123Z");
        UUID tenant = UUID.randomUUID();
        DeferredCycleSettlementFinalizer finalizer = new DeferredCycleSettlementFinalizer(cycles, finality);
        DeferredCycleSettlementFinalizer.CompletionCommand completion = new DeferredCycleSettlementFinalizer.CompletionCommand(tenant,
                new DeferredSettlementCycleService.CycleSettlementCommand(UUID.randomUUID(), cycleId, settledAt),
                java.util.Map.of(assigned.itemId(), new byte[] {1, 2, 3}));
        SettlementFinalityService.FinalityOutcome first = finalizer.settleAndFinalize(completion).finalityOutcomes().getFirst();
        SettlementFinalityService.FinalityOutcome replay = finalizer.settleAndFinalize(completion).finalityOutcomes().getFirst();

        assertThat(first.replayed()).isFalse();
        assertThat(replay).isEqualTo(new SettlementFinalityService.FinalityOutcome(first.recordId(), settledAt, true));
        assertThat(text("SELECT finality_rule_code FROM settlement.settlement_finality_records WHERE id = '" + first.recordId() + "'"))
                .isEqualTo("ON_CYCLE_SETTLED");
        assertThat(text("SELECT source_type FROM settlement.settlement_finality_records WHERE id = '" + first.recordId() + "'"))
                .isEqualTo("CYCLE_ITEM_SETTLED");
        assertThat(text("SELECT state FROM settlement.deferred_cycle_attempts WHERE id = '" + assignment.settlementAttemptId() + "'"))
                .isEqualTo("FINAL");
        assertThatThrownBy(() -> finality.recordCycleItemSettled(tenant, cycleId, assigned.itemId(),
                assignment.settlementAttemptId(), assignment.paymentId(), new byte[] {9}))
                .isInstanceOf(FinalityConflictException.class);
        assertThat(count("settlement.settlement_finality_records")).isEqualTo(1);
    }

    @Test void settlementWriterHasOnlyItsOwnCycleTablesAndForeignWritersHaveNoCycleWriteGrant() throws Exception {
        assertThat(bool("SELECT has_table_privilege('settlement_role', 'settlement.settlement_cycles', 'INSERT, UPDATE, SELECT')")).isTrue();
        assertThat(bool("SELECT has_table_privilege('settlement_role', 'ledger.journal_entries', 'INSERT')")).isFalse();
        assertThat(bool("SELECT has_table_privilege('settlement_role', 'payment.payments', 'UPDATE')")).isFalse();
        assertThat(bool("SELECT has_table_privilege('routing_role', 'settlement.settlement_cycles', 'INSERT')")).isFalse();
        assertThat(count("information_schema.role_table_grants WHERE table_schema = 'settlement' AND grantee = 'PUBLIC'")).isZero();
    }

    private UUID openCycle() {
        UUID cycle = UUID.randomUUID(); UUID profile = UUID.randomUUID();
        cycles.createOpenCycle(cycle, profile, LocalDate.of(2026, 7, 21), (short) 1, CUTOFF);
        return cycle;
    }

    private UUID profile(UUID cycleId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(
                "SELECT profile_id FROM settlement.settlement_cycles WHERE id = ?")) {
            statement.setObject(1, cycleId); try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getObject(1, UUID.class); }
        }
    }

    private DeferredCycleAssignment assignment(UUID cycleId, UUID attemptId, UUID paymentId, long amount) throws Exception {
        return new DeferredCycleAssignment(cycleId, attemptId, paymentId, profile(cycleId), UUID.randomUUID(), UUID.randomUUID(), amount);
    }

    private void closeCycle(UUID cycleId) {
        cycles.beginClosing(command(cycleId)); cycles.close(command(cycleId));
    }

    private DeferredSettlementCycleService.CycleCommand command(UUID cycleId) {
        return new DeferredSettlementCycleService.CycleCommand(UUID.randomUUID(), cycleId);
    }

    private DeferredSettlementCycleService.CycleCommand replayCommand(UUID cycleId, DeferredSettlementCycleService.NettingResult ignored) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT command_id FROM settlement.settlement_cycle_command_receipts WHERE cycle_id = '" + cycleId + "' AND command_type = 'NET'")) {
            rows.next(); return new DeferredSettlementCycleService.CycleCommand(rows.getObject(1, UUID.class), cycleId);
        }
    }

    private static int count(String relationOrWhere) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + relationOrWhere)) {
            rows.next(); return rows.getInt(1);
        }
    }

    private static long net(UUID cycleId, UUID participant) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(
                "SELECT net_minor FROM settlement.settlement_positions WHERE cycle_id = ? AND participant_id = ?")) {
            statement.setObject(1, cycleId); statement.setObject(2, participant); try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private static long sum(UUID cycleId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(sum(net_minor), 0) FROM settlement.settlement_positions WHERE cycle_id = ?")) {
            statement.setObject(1, cycleId); try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private static String state(UUID cycleId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(
                "SELECT state FROM settlement.settlement_cycles WHERE id = ?")) {
            statement.setObject(1, cycleId); try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getString(1); }
        }
    }

    private static String text(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) { rows.next(); return rows.getString(1); }
    }

    private static boolean bool(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) { rows.next(); return rows.getBoolean(1); }
    }

    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }

    private static final class RecordingProjection implements PaymentFinalityPort {
        @Override public ProjectionResult project(UUID tenantId, UUID paymentId, UUID finalityRecordId, Instant finalityAt) {
            return ProjectionResult.PROJECTED;
        }
    }
}
