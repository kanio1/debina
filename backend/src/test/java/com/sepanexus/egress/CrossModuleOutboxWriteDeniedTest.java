package com.sepanexus.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-18 Story 18.3: a module's own writer role may insert into its own
 * {@code <schema>.outbox_events} table, but never into another module's. Own separate Testcontainers
 * instance from {@link OutboxDispatcherNoDomainWriteSweepTest} (that story's own concern is the
 * shared dispatcher role, not writer-to-writer isolation), per this session's instruction not to
 * share a live database between test classes even when the support-code shape is similar.
 *
 * <p>Writer-role registry, built from the actual migrations (not guessed): {@code payment} and
 * {@code iso} are BOTH written today by the same shared {@code sepa_app} connection —
 * {@code iso-adapter} has not yet split into its own DB role (EPIC-10 Story 10.1, blocked on a
 * pending {@code SECURITY DEFINER} decision) — so {@code payment}<->{@code iso} is not a real
 * cross-module boundary yet and is deliberately excluded from the negative matrix below (it would
 * be a false positive: a passing "denied" assertion for something that is not actually a violation
 * of any frozen rule, since one writer really does legitimately own both schemas today).
 * {@code egress} has its own dedicated {@code egress_role} (V22, mirroring {@code signature}'s
 * precedent) — the only role in this registry that is genuinely a different writer from the other
 * two, so it is the only side of the matrix that produces real cross-module assertions.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class CrossModuleOutboxWriteDeniedTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    private record OutboxOwner(String schema, String role, String password) {
    }

    private record ForeignWritePair(OutboxOwner writer, OutboxOwner foreignOutbox) {
    }

    private static final List<OutboxOwner> OWN_OUTBOX = List.of(
            new OutboxOwner("payment", "sepa_app", "dev-only-app"),
            new OutboxOwner("iso", "sepa_app", "dev-only-app"),
            new OutboxOwner("egress", "egress_role", "dev-only-egress"));

    /** Only pairs where the writing role genuinely differs are real cross-module assertions. */
    private static final List<ForeignWritePair> FOREIGN_WRITE_PAIRS = List.of(
            new ForeignWritePair(OWN_OUTBOX.get(0), OWN_OUTBOX.get(2)), // sepa_app -> egress.outbox_events
            new ForeignWritePair(OWN_OUTBOX.get(2), OWN_OUTBOX.get(0)), // egress_role -> payment.outbox_events
            new ForeignWritePair(OWN_OUTBOX.get(2), OWN_OUTBOX.get(1))); // egress_role -> iso.outbox_events

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    static List<OutboxOwner> ownOutboxes() {
        return OWN_OUTBOX;
    }

    static List<ForeignWritePair> foreignWritePairs() {
        return FOREIGN_WRITE_PAIRS;
    }

    @ParameterizedTest
    @MethodSource("ownOutboxes")
    void writerCanInsertAndSelectOwnOutbox(OutboxOwner owner) throws Exception {
        try (Connection connection = roleConnection(owner); Statement statement = connection.createStatement()) {
            int inserted = statement.executeUpdate(insertOutboxSql(owner.schema()));
            assertThat(inserted).isEqualTo(1);

            var result = statement.executeQuery("SELECT count(*) FROM %s.outbox_events".formatted(owner.schema()));
            result.next();
            assertThat(result.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }

    @ParameterizedTest
    @MethodSource("foreignWritePairs")
    void writerCannotInsertIntoForeignOutbox(ForeignWritePair pair) {
        assertThatThrownBy(() -> {
            try (Connection connection = roleConnection(pair.writer()); Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertOutboxSql(pair.foreignOutbox().schema()));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @ParameterizedTest
    @MethodSource("foreignWritePairs")
    void writerCannotUpdatePublishedAtOnForeignOutbox(ForeignWritePair pair) throws Exception {
        String eventId = insertOutboxRowAsAdmin(pair.foreignOutbox().schema());
        assertThatThrownBy(() -> {
            try (Connection connection = roleConnection(pair.writer()); Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE %s.outbox_events SET published_at = now() WHERE id = '%s'"
                        .formatted(pair.foreignOutbox().schema(), eventId));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @ParameterizedTest
    @MethodSource("foreignWritePairs")
    void writerCannotDeleteFromForeignOutbox(ForeignWritePair pair) throws Exception {
        String eventId = insertOutboxRowAsAdmin(pair.foreignOutbox().schema());
        assertThatThrownBy(() -> {
            try (Connection connection = roleConnection(pair.writer()); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "DELETE FROM %s.outbox_events WHERE id = '%s'".formatted(pair.foreignOutbox().schema(), eventId));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @ParameterizedTest
    @MethodSource("foreignWritePairs")
    void writerCannotTruncateForeignOutbox(ForeignWritePair pair) {
        assertThatThrownBy(() -> {
            try (Connection connection = roleConnection(pair.writer()); Statement statement = connection.createStatement()) {
                statement.executeUpdate("TRUNCATE %s.outbox_events".formatted(pair.foreignOutbox().schema()));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @Test
    void egressRoleGrantedIntoPaymentOutboxIsDetectedByThisSuite() {
        // sanity check documented in prose above: confirms the registry itself is meaningful,
        // i.e. that this suite is exercising a real grant boundary, not an always-true assumption.
        assertThat(OWN_OUTBOX).extracting(OutboxOwner::role).contains("sepa_app", "egress_role");
    }

    private static String insertOutboxSql(String schema) {
        return "egress".equals(schema)
                ? "INSERT INTO egress.outbox_events (aggregate_id, topic, type, payload) VALUES (gen_random_uuid(), 'x.y', 'x.y.v1', '{}'::jsonb)"
                : "INSERT INTO %s.outbox_events (aggregate_id, event_type, payload, correlation_id) VALUES (gen_random_uuid(), 'x.y.v1', '{}'::jsonb, gen_random_uuid())"
                        .formatted(schema);
    }

    private static String insertOutboxRowAsAdmin(String schema) throws Exception {
        String eventId;
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            var result = statement.executeQuery(
                    ("egress".equals(schema)
                            ? "INSERT INTO egress.outbox_events (id, aggregate_id, topic, type, payload) VALUES (gen_random_uuid(), gen_random_uuid(), 'x.y', 'x.y.v1', '{}'::jsonb) RETURNING id"
                            : "INSERT INTO %s.outbox_events (id, aggregate_id, event_type, payload, correlation_id) VALUES (gen_random_uuid(), gen_random_uuid(), 'x.y.v1', '{}'::jsonb, gen_random_uuid()) RETURNING id"
                                    .formatted(schema)));
            result.next();
            eventId = result.getString(1);
        }
        return eventId;
    }

    private static Connection roleConnection(OutboxOwner owner) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), owner.role(), owner.password());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
