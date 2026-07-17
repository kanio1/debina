package com.sepanexus.egress;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.egress.internal.OutboundMessageDispatcher;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-43 Story 43.1: two independent dispatcher instances, each on its own JDBC connection/
 * transaction, must claim disjoint batches of {@code egress.outbound_messages} — {@code FOR
 * UPDATE SKIP LOCKED} makes concurrent claiming horizontally safe (§6.2). Real PostgreSQL 18
 * Testcontainer, real concurrent transactions via two threads synchronized on a {@link
 * CyclicBarrier} — no sleeps, no mocked locking. Rollback scenarios use a real Spring {@link
 * DataSourceTransactionManager}/{@link TransactionTemplate} pair (not a hand-rolled connection
 * hack) so {@link JdbcTemplate} correctly participates in the externally-controlled transaction.
 */
@Testcontainers
class DoubleDispatcherTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    @BeforeAll
    static void migrateDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void cleanTable() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE egress.outbound_messages");
        }
    }

    @Test
    void twoDispatchersClaimDisjointBatchesWithNoDoubleClaim() throws Exception {
        List<UUID> inserted = insertPendingRows(20);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<UUID>> workerA = pool.submit(() -> claimAfterBarrier(barrier, 12));
            Future<List<UUID>> workerB = pool.submit(() -> claimAfterBarrier(barrier, 12));

            List<UUID> claimedByA = workerA.get();
            List<UUID> claimedByB = workerB.get();

            Set<UUID> intersection = new HashSet<>(claimedByA);
            intersection.retainAll(claimedByB);
            assertThat(intersection).isEmpty();

            Set<UUID> allClaimed = new HashSet<>(claimedByA);
            allClaimed.addAll(claimedByB);
            assertThat(allClaimed).hasSize(20).containsExactlyInAnyOrderElementsOf(inserted);

            assertThat(countInState("CLAIMED_FOR_DELIVERY")).isEqualTo(20);
            assertThat(countInState("REQUESTED")).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rolledBackClaimReleasesRowsForRetry() throws Exception {
        List<UUID> inserted = insertPendingRows(3);
        DataSource dataSource = systemRelayDataSource();
        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        List<UUID> claimed = transactionTemplate.execute(status -> {
            List<UUID> result = new OutboundMessageDispatcher(new JdbcTemplate(dataSource)).claimPendingBatch(10);
            status.setRollbackOnly();
            return result;
        });
        assertThat(claimed).containsExactlyInAnyOrderElementsOf(inserted);

        assertThat(countInState("REQUESTED")).isEqualTo(3);
        assertThat(countInState("CLAIMED_FOR_DELIVERY")).isZero();

        List<UUID> reClaimed = transactionTemplate
                .execute(status -> new OutboundMessageDispatcher(new JdbcTemplate(dataSource)).claimPendingBatch(10));
        assertThat(reClaimed).containsExactlyInAnyOrderElementsOf(inserted);
        assertThat(countInState("CLAIMED_FOR_DELIVERY")).isEqualTo(3);
    }

    @Test
    void recordsBeyondTheClaimLimitRemainPending() throws Exception {
        insertPendingRows(5);

        List<UUID> claimed = new OutboundMessageDispatcher(new JdbcTemplate(systemRelayDataSource())).claimPendingBatch(2);
        assertThat(claimed).hasSize(2);

        assertThat(countInState("CLAIMED_FOR_DELIVERY")).isEqualTo(2);
        assertThat(countInState("REQUESTED")).isEqualTo(3);
    }

    @Test
    void rowsNotInRequestedStateAreNeverClaimed() throws Exception {
        List<UUID> requested = insertPendingRows(3);
        List<UUID> alreadyClaimed = insertRowsInState(2, "CLAIMED_FOR_DELIVERY");
        List<UUID> closed = insertRowsInState(2, "CLOSED");

        List<UUID> claimed = new OutboundMessageDispatcher(new JdbcTemplate(systemRelayDataSource())).claimPendingBatch(10);

        assertThat(claimed).containsExactlyInAnyOrderElementsOf(requested);
        assertThat(claimed).doesNotContainAnyElementsOf(alreadyClaimed);
        assertThat(claimed).doesNotContainAnyElementsOf(closed);
        assertThat(countInState("CLOSED")).isEqualTo(2);
    }

    /**
     * Deterministic proof of why {@link OutboundMessageDispatcher#claimPendingBatch} must be one
     * atomic statement rather than a separate {@code SELECT ... FOR UPDATE SKIP LOCKED} followed
     * by an {@code UPDATE} (the anti-pattern section 13/30 of this session's own instructions name
     * explicitly). A thread-based concurrency reproduction of this specific danger is inherently
     * racy in the other direction too: if both SELECTs are genuinely issued to PostgreSQL close
     * enough together, {@code SKIP LOCKED} correctly skips whichever rows the other transaction's
     * still-open (not yet auto-committed) lock covers, so the double-claim does not reproduce
     * every run — the danger requires worker A's SELECT to fully complete and auto-commit
     * (releasing its lock) strictly *before* worker B's SELECT is even issued. This test forces
     * exactly that ordering, sequentially and deterministically, on two separate auto-committing
     * connections — no thread scheduling luck involved: A selects (and implicitly releases its
     * lock on return), then B selects and — because the rows are still {@code REQUESTED} and now
     * unlocked — claims the very same rows, before either connection's UPDATE ever runs.
     */
    @Test
    void splittingSelectAndUpdateAcrossSeparateStatementsAllowsADoubleClaim() throws Exception {
        List<UUID> inserted = insertPendingRows(5);

        List<UUID> selectedByA;
        List<UUID> selectedByB;
        try (Connection connectionA = systemRelayDataSource().getConnection();
                Connection connectionB = systemRelayDataSource().getConnection()) {
            selectedByA = selectForUpdateSkipLocked(connectionA, 5);
            // connectionA's SELECT already returned, so its single-statement autocommit
            // transaction already committed and released its row locks — before connectionB's
            // SELECT below is even issued.
            selectedByB = selectForUpdateSkipLocked(connectionB, 5);

            updateToClaimedForDelivery(connectionA, selectedByA);
            updateToClaimedForDelivery(connectionB, selectedByB);
        }

        Set<UUID> overlap = new HashSet<>(selectedByA);
        overlap.retainAll(selectedByB);

        assertThat(overlap)
                .as("splitting the claim SELECT and UPDATE across two separate auto-committing "
                        + "statements releases the FOR UPDATE row lock before the claim is recorded, "
                        + "letting a second worker's SELECT see the same still-REQUESTED rows")
                .isNotEmpty();
        assertThat(overlap).containsExactlyInAnyOrderElementsOf(inserted);
    }

    private List<UUID> selectForUpdateSkipLocked(Connection connection, int limit) throws Exception {
        List<UUID> selected = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM egress.outbound_messages
                WHERE state = 'REQUESTED'
                ORDER BY created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    selected.add((UUID) result.getObject("id"));
                }
            }
        }
        return selected;
    }

    private void updateToClaimedForDelivery(Connection connection, List<UUID> ids) throws Exception {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "'" + id + "'").toList());
        try (Statement update = connection.createStatement()) {
            update.executeUpdate(
                    "UPDATE egress.outbound_messages SET state = 'CLAIMED_FOR_DELIVERY' WHERE id IN (" + placeholders + ")");
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    private List<UUID> claimAfterBarrier(CyclicBarrier barrier, int limit) throws Exception {
        OutboundMessageDispatcher dispatcher = new OutboundMessageDispatcher(new JdbcTemplate(systemRelayDataSource()));
        barrier.await();
        return dispatcher.claimPendingBatch(limit);
    }

    /** Every connection is stamped {@code app.role = 'system_relay'} immediately on open, so the
     * dispatcher's cross-tenant claim query is visible under {@code egress.outbound_messages}'s
     * narrow {@code dispatcher_claim} RLS policy — the dispatcher is a background system job, not
     * a tenant-scoped request. */
    private static DataSource systemRelayDataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "egress_role", "dev-only-egress") {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET app.role = 'system_relay'");
                }
                return connection;
            }
        };
    }

    private List<UUID> insertPendingRows(int count) throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (Connection connection = adminConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO egress.outbound_messages
                            (id, tenant_id, recipient_id, channel, artifact_type, correlation_msg_id, payload, payload_sha256)
                        VALUES (?, ?, ?, 'WEBHOOK', 'PACS002', ?, 'payload'::bytea, sha256('payload'::bytea))
                        """)) {
            for (int i = 0; i < count; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                statement.setObject(1, id);
                statement.setObject(2, TENANT_ID);
                statement.setObject(3, UUID.randomUUID());
                statement.setString(4, "corr-" + id);
                statement.executeUpdate();
            }
        }
        return ids;
    }

    private List<UUID> insertRowsInState(int count, String state) throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (Connection connection = adminConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO egress.outbound_messages
                            (id, tenant_id, recipient_id, channel, artifact_type, correlation_msg_id, payload, payload_sha256, state)
                        VALUES (?, ?, ?, 'WEBHOOK', 'PACS002', ?, 'payload'::bytea, sha256('payload'::bytea), ?)
                        """)) {
            for (int i = 0; i < count; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                statement.setObject(1, id);
                statement.setObject(2, TENANT_ID);
                statement.setObject(3, UUID.randomUUID());
                statement.setString(4, "corr-" + id);
                statement.setString(5, state);
                statement.executeUpdate();
            }
        }
        return ids;
    }

    private int countInState(String state) throws Exception {
        try (Connection connection = adminConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT count(*) FROM egress.outbound_messages WHERE state = ?")) {
            statement.setString(1, state);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
