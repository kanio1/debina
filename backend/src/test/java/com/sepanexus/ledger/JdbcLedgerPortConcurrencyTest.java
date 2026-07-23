package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.shared.ClockPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Deterministic PostgreSQL lock proofs for ADR-N10's reservation lifecycle. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class JdbcLedgerPortConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    private static JdbcLedgerPort port;

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        ClockPort clock = () -> Instant.parse("2026-07-20T10:15:30Z");
        port = new JdbcLedgerPort(
                new LedgerConnectionFactory(POSTGRES.getJdbcUrl(), "ledger_role", "dev-only-ledger"), clock);
    }

    @Test
    void concurrentReserveCallsCannotOverReserveTheSameAccount() throws Exception {
        UUID debtor = account("EUR", 1_000);
        List<LedgerPort.ReserveResult> results = concurrently(
                () -> port.reserve(UUID.randomUUID(), UUID.randomUUID(), debtor, 700),
                () -> port.reserve(UUID.randomUUID(), UUID.randomUUID(), debtor, 700));

        assertThat(results).filteredOn(LedgerPort.Reserved.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(LedgerPort.InsufficientLiquidity.class::isInstance).hasSize(1);
        assertThat(balance(debtor)).containsExactly(300L, 700L);
        assertThat(count("SELECT count(*) FROM ledger.reservations WHERE debtor_account_id = '" + debtor + "'"))
                .isEqualTo(1);
    }

    @Test
    void concurrentReplaysForOneAttemptReturnTheSameReservationWithoutSecondEffect() throws Exception {
        UUID debtor = account("EUR", 1_000);
        UUID attempt = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        List<LedgerPort.ReserveResult> results = concurrently(
                () -> port.reserve(attempt, payment, debtor, 400),
                () -> port.reserve(attempt, payment, debtor, 400));

        LedgerPort.Reserved first = (LedgerPort.Reserved) results.getFirst();
        LedgerPort.Reserved second = (LedgerPort.Reserved) results.getLast();
        assertThat(second).isEqualTo(first);
        assertThat(balance(debtor)).containsExactly(600L, 400L);
        assertThat(count("SELECT count(*) FROM ledger.reservations WHERE settlement_attempt_id = '" + attempt + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + payment + "'"))
                .isEqualTo(1);
    }

    @Test
    void concurrentPostAndReleaseCreateExactlyOneTerminalEffect() throws Exception {
        UUID debtor = account("EUR", 1_000);
        UUID creditor = account("EUR", 100);
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) port.reserve(UUID.randomUUID(), UUID.randomUUID(), debtor, 400);
        UUID postCommand = UUID.randomUUID();
        UUID releaseCommand = UUID.randomUUID();

        List<Object> outcomes = concurrentlyCapturing(
                () -> port.post(reserved.reservationId(), creditor, postCommand),
                () -> port.release(reserved.reservationId(), releaseCommand));

        assertThat(outcomes).filteredOn(LedgerPort.TerminalResult.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ReservationTerminalConflictException.class::isInstance).hasSize(1);
        String state = string("SELECT state FROM ledger.reservations WHERE id = '" + reserved.reservationId() + "'");
        assertThat(state).isIn("POSTED", "RELEASED");
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE id <> '" + reserved.reserveEntryId()
                + "' AND payment_id = (SELECT payment_id FROM ledger.reservations WHERE id = '"
                + reserved.reservationId() + "')")).isEqualTo(1);
    }

    @Test
    void deterministicAccountOrderingAllowsCrossPostsWithoutDeadlock() throws Exception {
        UUID first = account("EUR", 1_000);
        UUID second = account("EUR", 1_000);
        LedgerPort.Reserved left = (LedgerPort.Reserved) port.reserve(UUID.randomUUID(), UUID.randomUUID(), first, 300);
        LedgerPort.Reserved right = (LedgerPort.Reserved) port.reserve(UUID.randomUUID(), UUID.randomUUID(), second, 300);

        List<LedgerPort.TerminalResult> results = concurrently(
                () -> port.post(left.reservationId(), second, UUID.randomUUID()),
                () -> port.post(right.reservationId(), first, UUID.randomUUID()));

        assertThat(results).allSatisfy(result -> assertThat(result.state()).isEqualTo(ReservationState.POSTED));
        assertThat(balance(first)).containsExactly(1_000L, 0L);
        assertThat(balance(second)).containsExactly(1_000L, 0L);
    }

    @Test
    void aDifferentTerminalCommandFailsClosedAndDoesNotCreateAnotherMoneyEffect() throws Exception {
        UUID debtor = account("EUR", 700);
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) port.reserve(UUID.randomUUID(), UUID.randomUUID(), debtor, 500);
        port.release(reserved.reservationId(), UUID.randomUUID());
        int terminalEntries = count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id ="
                + " (SELECT payment_id FROM ledger.reservations WHERE id = '" + reserved.reservationId() + "')");

        assertThatThrownBy(() -> port.release(reserved.reservationId(), UUID.randomUUID()))
                .isInstanceOf(ReservationTerminalConflictException.class);
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id ="
                + " (SELECT payment_id FROM ledger.reservations WHERE id = '" + reserved.reservationId() + "')"))
                .isEqualTo(terminalEntries);
    }

    @SafeVarargs
    private static <T> List<T> concurrently(Callable<T>... calls) throws Exception {
        CyclicBarrier start = new CyclicBarrier(calls.length);
        ExecutorService executor = Executors.newFixedThreadPool(calls.length);
        try {
            List<Future<T>> futures = java.util.Arrays.stream(calls)
                    .map(call -> executor.submit(() -> { start.await(); return call.call(); }))
                    .toList();
            return futures.stream().map(JdbcLedgerPortConcurrencyTest::await).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    @SafeVarargs
    private static List<Object> concurrentlyCapturing(Callable<?>... calls) throws Exception {
        CyclicBarrier start = new CyclicBarrier(calls.length);
        ExecutorService executor = Executors.newFixedThreadPool(calls.length);
        try {
            List<Future<Object>> futures = java.util.Arrays.stream(calls)
                    .map(call -> executor.submit(() -> {
                        start.await();
                        try { return call.call(); } catch (Exception expected) { return expected; }
                    }))
                    .toList();
            return futures.stream().map(JdbcLedgerPortConcurrencyTest::await).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("concurrent operation did not complete cleanly", failure);
        }
    }

    private static UUID account(String currency, long available) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                VALUES (?, gen_random_uuid(), gen_random_uuid(), ?, ?)
                """)) {
            statement.setObject(1, id); statement.setString(2, currency); statement.setLong(3, available);
            statement.executeUpdate();
        }
        return id;
    }

    private static long[] balance(UUID account) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT available_minor, reserved_minor FROM ledger.liquidity_accounts WHERE id = ?
                """)) {
            statement.setObject(1, account);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new long[] {result.getLong(1), result.getLong(2)};
            }
        }
    }

    private static int count(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String string(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
