package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.ledger.JdbcGrossInstantLedgerPort;
import com.sepanexus.modules.paymentlifecycle.service.JdbcGrossInstantPaymentFinalityPort;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** PostgreSQL 18/Testcontainers proof for ADR-N11's actual one-transaction gross-instant path. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class GrossInstantOneTxFlowTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000003311");
    private static final Instant NOW = Instant.parse("2026-07-20T14:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    private TransactionTemplate transactions;
    private UUID paymentId;
    private UUID debtor;
    private UUID creditor;

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        installAuditTriggers();
    }

    @BeforeEach
    void setUp() throws Exception {
        clear();
        paymentId = UUID.randomUUID();
        debtor = account(1_000);
        creditor = account(100);
        payment(paymentId);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                "gross_instant_executor_role", "dev-only-gross-instant-executor");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void reservePostFinalityProjectionAndStatusReportUseOnePhysicalConnectionAndTxid_thenReplayWithoutDuplicates()
            throws Exception {
        GrossInstantStrategy strategy = strategy(ignored -> { });
        GrossInstantStrategy.GrossInstantCommand command = command(UUID.randomUUID(), UUID.randomUUID());

        GrossInstantStrategy.GrossInstantOutcome outcome = execute(strategy, command);
        assertThat(outcome).isInstanceOf(GrossInstantStrategy.Settled.class);
        assertThat(rows("SELECT DISTINCT txid FROM public.gross_instant_command_audit ORDER BY txid"))
                .hasSize(1);
        assertThat(rows("SELECT DISTINCT backend_pid FROM public.gross_instant_command_audit ORDER BY backend_pid"))
                .hasSize(1);
        assertThat(rows("SELECT stage FROM public.gross_instant_command_audit ORDER BY stage"))
                .containsExactly("FINALITY", "POST", "PROJECTION", "RESERVE");
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = '" + paymentId + "'"))
                .isEqualTo(1);

        assertThat(execute(strategy, command)).isInstanceOf(GrossInstantStrategy.Settled.class);
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payment_events WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = '" + paymentId + "'"))
                .isEqualTo(1);
    }

    @Test
    void insufficientLiquidityIsAtomic_rejectsBusinessStatusAndCreatesNoMoneyOrFinality() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE ledger.liquidity_accounts SET available_minor = 99 WHERE id = '" + debtor + "'");
        }
        GrossInstantStrategy.GrossInstantCommand command = command(UUID.randomUUID(), UUID.randomUUID());
        GrossInstantStrategy.GrossInstantOutcome outcome = execute(strategy(ignored -> { }), command);

        assertThat(outcome).isInstanceOf(GrossInstantStrategy.InsufficientLiquidity.class);
        assertThat(count("SELECT count(*) FROM ledger.reservations WHERE payment_id = '" + paymentId + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + paymentId + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + paymentId + "'"))
                .isZero();
        assertThat(value("SELECT status FROM payment.payments WHERE id = '" + paymentId + "'")).isEqualTo("REJECTED");
        assertThat(value("SELECT payload ->> 'isoStatus' FROM payment.outbox_events WHERE aggregate_id = '" + paymentId + "'"))
                .isEqualTo("RJCT");
        assertThat(execute(strategy(ignored -> { }), command)).isInstanceOf(GrossInstantStrategy.InsufficientLiquidity.class);
        assertThat(count("SELECT count(*) FROM settlement.settlement_attempt_events WHERE attempt_id = '" + command.attemptId() + "'"))
                .isEqualTo(3);
        assertThat(count("SELECT count(*) FROM payment.payment_events WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = '" + paymentId + "'"))
                .isEqualTo(1);
    }

    @Test
    void anyInjectedBoundaryFailureRollsBackAllDurableEffects() throws Exception {
        for (GrossInstantFailureInjector.Phase phase : GrossInstantFailureInjector.Phase.values()) {
            clear(); paymentId = UUID.randomUUID(); debtor = account(1_000); creditor = account(100); payment(paymentId);
            GrossInstantStrategy.GrossInstantCommand command = command(UUID.randomUUID(), UUID.randomUUID());
            GrossInstantStrategy strategy = strategy(actual -> {
                if (actual == phase) throw new IllegalStateException("injected " + phase);
            });
            assertThatThrownBy(() -> execute(strategy, command)).isInstanceOf(IllegalStateException.class);
            assertNoSuccessPartialState();
        }
    }

    @Test
    void concurrentIdenticalCommandsRetryTheWholeTransaction_andCreateOneMoneyFinalityAndOutboxEffect() throws Exception {
        GrossInstantStrategy strategy = strategy(ignored -> { });
        GrossInstantStrategy.GrossInstantCommand command = command(UUID.randomUUID(), UUID.randomUUID());

        List<ExecutionResult> results = concurrently(() -> executeResult(strategy, command),
                () -> executeResult(strategy, command));

        assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
        assertThat(results).allSatisfy(result -> assertThat(result.outcome()).isInstanceOf(GrossInstantStrategy.Settled.class));
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM ledger.reservations WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payment_events WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = '" + paymentId + "'"))
                .isEqualTo(1);
    }

    @Test
    void concurrentConflictingCommandsFailClosed_withoutSecondMoneyOrFinalityEffect() throws Exception {
        GrossInstantStrategy strategy = strategy(ignored -> { });
        GrossInstantStrategy.GrossInstantCommand first = command(UUID.randomUUID(), UUID.randomUUID());
        GrossInstantStrategy.GrossInstantCommand conflicting = command(UUID.randomUUID(), UUID.randomUUID());

        List<ExecutionResult> results = concurrently(() -> executeResult(strategy, first),
                () -> executeResult(strategy, conflicting));

        assertThat(results).filteredOn(result -> result.failure() == null).hasSize(1);
        assertThat(results).filteredOn(result -> result.failure() != null).hasSize(1);
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM settlement.settlement_attempts WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = '" + paymentId + "'"))
                .isEqualTo(1);
    }

    @Test
    void deterministicAccountLockOrderCompletesCrossedPostsWithoutDeadlock() throws Exception {
        UUID otherPayment = UUID.randomUUID();
        payment(otherPayment);
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE ledger.liquidity_accounts SET available_minor = 1000 WHERE id = '" + creditor + "'");
        }
        GrossInstantStrategy strategy = strategy(ignored -> { });
        GrossInstantStrategy.GrossInstantCommand leftToRight = command(UUID.randomUUID(), UUID.randomUUID());
        GrossInstantStrategy.GrossInstantCommand rightToLeft = new GrossInstantStrategy.GrossInstantCommand(TENANT,
                UUID.randomUUID(), otherPayment, creditor, debtor, 300, "EUR", UUID.randomUUID(), NOW,
                new byte[] {3, 3, 1, 2});

        List<ExecutionResult> results = concurrently(() -> executeResult(strategy, leftToRight),
                () -> executeResult(strategy, rightToLeft));

        assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE entry_type = 'POST'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records")).isEqualTo(2);
    }

    @Test
    void paymentRlsRejectsCrossTenantProjection_andRollsBackEarlierLedgerAndFinalityWork() throws Exception {
        UUID foreignPayment = UUID.randomUUID();
        UUID foreignTenant = UUID.randomUUID();
        payment(foreignPayment, foreignTenant);
        GrossInstantStrategy.GrossInstantCommand crossTenant = new GrossInstantStrategy.GrossInstantCommand(TENANT,
                UUID.randomUUID(), foreignPayment, debtor, creditor, 400, "EUR", UUID.randomUUID(), NOW,
                new byte[] {3, 3, 1, 3});

        assertThatThrownBy(() -> execute(strategy(ignored -> { }), crossTenant)).isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + foreignPayment + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + foreignPayment + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = '" + foreignPayment + "'"))
                .isZero();
        assertThat(value("SELECT status FROM payment.payments WHERE id = '" + foreignPayment + "'"))
                .isEqualTo("VALIDATED");
    }

    @Test
    void paymentFunctionInternalFailureRollsBackReservePostAndFinality() throws Exception {
        try {
            installProjectionFailureTrigger();
            GrossInstantStrategy.GrossInstantCommand command = command(UUID.randomUUID(), UUID.randomUUID());
            assertThatThrownBy(() -> execute(strategy(ignored -> { }), command)).isInstanceOf(RuntimeException.class);
            assertNoSuccessPartialState();
        } finally {
            dropProjectionFailureTrigger();
        }
    }

    @Test
    void changedFinalityEvidenceOnSameCommandFailsClosed_withoutSecondEffect() throws Exception {
        GrossInstantStrategy strategy = strategy(ignored -> { });
        GrossInstantStrategy.GrossInstantCommand command = command(UUID.randomUUID(), UUID.randomUUID());
        execute(strategy, command);
        GrossInstantStrategy.GrossInstantCommand conflictingEvidence = new GrossInstantStrategy.GrossInstantCommand(
                command.tenantId(), command.attemptId(), command.paymentId(), command.debtorAccountId(),
                command.creditorAccountId(), command.amountMinor(), command.currency(), command.commandId(),
                command.occurredAt(), new byte[] {9, 9, 9});

        assertThatThrownBy(() -> execute(strategy, conflictingEvidence)).isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + paymentId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = '" + paymentId + "'"))
                .isEqualTo(1);
    }

    private GrossInstantStrategy strategy(GrossInstantFailureInjector injector) {
        JdbcTemplate jdbc = new JdbcTemplate(((DataSourceTransactionManager) transactions.getTransactionManager()).getDataSource());
        return new GrossInstantStrategy(new JdbcGrossInstantLedgerPort(jdbc), new JdbcGrossInstantSettlementCommandPort(jdbc),
                new JdbcGrossInstantPaymentFinalityPort(jdbc), new GrossInstantCoordinatorTenantContext(jdbc), injector,
                transactions);
    }

    private GrossInstantStrategy.GrossInstantOutcome execute(GrossInstantStrategy strategy,
            GrossInstantStrategy.GrossInstantCommand command) {
        return strategy.execute(command);
    }

    private ExecutionResult executeResult(GrossInstantStrategy strategy, GrossInstantStrategy.GrossInstantCommand command) {
        try {
            return new ExecutionResult(execute(strategy, command), null);
        } catch (RuntimeException exception) {
            return new ExecutionResult(null, exception);
        }
    }

    private static List<ExecutionResult> concurrently(Callable<ExecutionResult> first, Callable<ExecutionResult> second)
            throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<ExecutionResult> left = executor.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            Future<ExecutionResult> right = executor.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        }
    }

    private GrossInstantStrategy.GrossInstantCommand command(UUID attempt, UUID commandId) {
        return new GrossInstantStrategy.GrossInstantCommand(TENANT, attempt, paymentId, debtor, creditor, 400, "EUR",
                commandId, NOW, new byte[] {3, 3, 1, 1});
    }

    private void assertNoSuccessPartialState() throws Exception {
        assertThat(count("SELECT count(*) FROM ledger.journal_entries WHERE payment_id = '" + paymentId + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM ledger.reservations WHERE payment_id = '" + paymentId + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM settlement.settlement_attempts WHERE payment_id = '" + paymentId + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + paymentId + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM payment.payment_events WHERE payment_id = '" + paymentId + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = '" + paymentId + "'"))
                .isZero();
        assertThat(value("SELECT status FROM payment.payments WHERE id = '" + paymentId + "'")).isEqualTo("VALIDATED");
    }

    private static void installAuditTriggers() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE public.gross_instant_command_audit (stage text, txid bigint, backend_pid integer)");
            statement.execute("""
                    CREATE FUNCTION public.capture_gross_instant_command_tx() RETURNS trigger
                    LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
                    BEGIN INSERT INTO public.gross_instant_command_audit VALUES (TG_ARGV[0], txid_current(), pg_backend_pid()); RETURN NEW; END;
                    $$
                    """);
            statement.execute("CREATE TRIGGER gross_instant_reserve_audit AFTER INSERT ON ledger.journal_entries FOR EACH ROW WHEN (NEW.entry_type = 'RESERVE') EXECUTE FUNCTION public.capture_gross_instant_command_tx('RESERVE')");
            statement.execute("CREATE TRIGGER gross_instant_post_audit AFTER INSERT ON ledger.journal_entries FOR EACH ROW WHEN (NEW.entry_type = 'POST') EXECUTE FUNCTION public.capture_gross_instant_command_tx('POST')");
            statement.execute("CREATE TRIGGER gross_instant_finality_audit AFTER INSERT ON settlement.settlement_finality_records FOR EACH ROW EXECUTE FUNCTION public.capture_gross_instant_command_tx('FINALITY')");
            statement.execute("CREATE TRIGGER gross_instant_projection_audit AFTER UPDATE OF finality_record_id ON payment.payments FOR EACH ROW WHEN (NEW.finality_record_id IS NOT NULL) EXECUTE FUNCTION public.capture_gross_instant_command_tx('PROJECTION')");
        }
    }

    private static void installProjectionFailureTrigger() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION payment.fail_gross_instant_projection() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'injected payment command-function failure'; END;
                    $$
                    """);
            statement.execute("""
                    CREATE TRIGGER gross_instant_projection_failure BEFORE UPDATE OF finality_record_id ON payment.payments
                    FOR EACH ROW EXECUTE FUNCTION payment.fail_gross_instant_projection()
                    """);
        }
    }

    private static void dropProjectionFailureTrigger() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS gross_instant_projection_failure ON payment.payments");
            statement.execute("DROP FUNCTION IF EXISTS payment.fail_gross_instant_projection()");
        }
    }

    private void clear() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payment_status_history, payment.payment_events, payment.outbox_events, payment.payments, settlement.settlement_attempt_events, settlement.settlement_finality_records, settlement.settlement_profile_snapshots, settlement.settlement_attempts, ledger.reservations, ledger.journal_lines, ledger.journal_entries, ledger.liquidity_accounts CASCADE");
            statement.execute("TRUNCATE public.gross_instant_command_audit");
        }
    }

    private void payment(UUID id) throws Exception {
        payment(id, TENANT);
    }

    private void payment(UUID id, UUID tenantId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, debtor_iban, creditor_iban, status)
                VALUES (?, ?, ?, ?, ?, 'VALIDATED')
                """)) {
            statement.setObject(1, id); statement.setObject(2, tenantId); statement.setBigDecimal(3, new BigDecimal("4.00"));
            statement.setString(4, "DE89370400440532013000"); statement.setString(5, "FR7630006000011234567890189");
            statement.executeUpdate();
        }
    }

    private UUID account(long available) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor)
                VALUES (?, ?, gen_random_uuid(), 'EUR', ?)
                """)) {
            statement.setObject(1, id); statement.setObject(2, TENANT); statement.setLong(3, available); statement.executeUpdate();
        }
        return id;
    }

    private static long count(String sql) throws Exception { return Long.parseLong(value(sql)); }
    private static String value(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue(); return result.getString(1);
        }
    }
    private static List<String> rows(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>(); while (result.next()) values.add(result.getString(1)); return values;
        }
    }
    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }

    private record ExecutionResult(GrossInstantStrategy.GrossInstantOutcome outcome, RuntimeException failure) { }
}
