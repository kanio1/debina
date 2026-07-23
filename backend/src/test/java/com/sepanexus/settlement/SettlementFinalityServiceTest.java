package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.ledger.JdbcLedgerPort;
import com.sepanexus.ledger.LedgerConnectionFactory;
import com.sepanexus.ledger.LedgerPort;
import com.sepanexus.modules.PaymentFinalityPort;
import com.sepanexus.shared.ClockPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
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

/** PostgreSQL proof that only a real LedgerPort POST establishes synthetic laboratory finality. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class SettlementFinalityServiceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    private static JdbcLedgerPort ledger;
    private static RecordingProjection projection;
    private static SettlementFinalityService finality;
    private static final Instant NOW = Instant.parse("2026-07-20T10:15:30.123Z");

    @BeforeAll static void migrate() throws Exception {
        try (Connection c = admin(); Statement s = c.createStatement()) { s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'"); }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        ClockPort clock = () -> NOW;
        ledger = new JdbcLedgerPort(new LedgerConnectionFactory(POSTGRES.getJdbcUrl(), "ledger_role", "dev-only-ledger"), clock);
        projection = new RecordingProjection();
        finality = new SettlementFinalityService(
                new SettlementConnectionFactory(POSTGRES.getJdbcUrl(), "settlement_role", "dev-only-settlement"), projection);
    }

    @Test void ledgerPostCreatesOneImmutableAuthorityRecordAndProjectsItsAuthoritativeTimestamp() throws Exception {
        UUID tenant = UUID.randomUUID(); UUID payment = UUID.randomUUID(); UUID debtor = account(1_000); UUID creditor = account(100);
        UUID attempt = UUID.randomUUID();
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) ledger.reserve(attempt, payment, debtor, 400);
        LedgerPort.TerminalResult post = ledger.post(reserved.reservationId(), creditor, UUID.randomUUID());
        byte[] evidence = new byte[] {1, 2, 3};

        SettlementFinalityService.FinalityOutcome first = finality.recordLedgerPost(tenant, attempt, payment, post, evidence);
        SettlementFinalityService.FinalityOutcome replay = finality.recordLedgerPost(tenant, attempt, payment, post, evidence);

        assertThat(first.replayed()).isFalse();
        assertThat(replay).isEqualTo(new SettlementFinalityService.FinalityOutcome(first.recordId(), NOW, true));
        assertThat(string("SELECT finality_rule_code FROM settlement.settlement_finality_records WHERE id = '" + first.recordId() + "'"))
                .isEqualTo("ON_LEDGER_POST");
        assertThat(finalityAt(first.recordId())).isEqualTo(NOW);
        assertThat(projection.calls).isEqualTo(2);
        assertThatThrownBy(() -> finality.recordLedgerPost(tenant, attempt, payment, post, new byte[] {9}))
                .isInstanceOf(FinalityConflictException.class);
        assertThatThrownBy(() -> { try (Connection c = admin(); Statement s = c.createStatement()) { s.executeUpdate("UPDATE settlement.settlement_finality_records SET finality_at = now() WHERE id = '" + first.recordId() + "'"); } })
                .isInstanceOf(java.sql.SQLException.class);
    }

    @Test void releaseTransportLikeOrUnsupportedRulesCannotEstablishFinality() throws Exception {
        UUID payment = UUID.randomUUID(); UUID debtor = account(500);
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) ledger.reserve(UUID.randomUUID(), payment, debtor, 200);
        LedgerPort.TerminalResult release = ledger.release(reserved.reservationId(), UUID.randomUUID());
        assertThatThrownBy(() -> finality.recordLedgerPost(UUID.randomUUID(), UUID.randomUUID(), payment, release, new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ledger POST");
        FinalityRulePolicy policy = new FinalityRulePolicy();
        assertThat(policy.isCatalogued(FinalityRulePolicy.ON_INTERNAL_BOOK_POST, 1)).isTrue();
        assertThat(policy.isExecutableNow(FinalityRulePolicy.ON_INTERNAL_BOOK_POST)).isFalse();
        assertThat(policy.isCatalogued("ACSC", 1)).isFalse();
        assertThat(policy.isCatalogued("DISPATCHED", 1)).isFalse();
        assertThat(policy.isCatalogued("DELIVERED", 1)).isFalse();
        assertThat(policy.isCatalogued("RECEIPT", 1)).isFalse();
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + payment + "'"))
                .isZero();
    }

    @Test void concurrentIdenticalLedgerEvidenceCreatesOneAuthorityRecordAndOneReplay() throws Exception {
        UUID tenant = UUID.randomUUID(); UUID attempt = UUID.randomUUID(); UUID payment = UUID.randomUUID();
        UUID debtor = account(700); UUID creditor = account(100);
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) ledger.reserve(attempt, payment, debtor, 300);
        LedgerPort.TerminalResult post = ledger.post(reserved.reservationId(), creditor, UUID.randomUUID());
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SettlementFinalityService.FinalityOutcome> first = executor.submit(() -> { start.await(); return finality.recordLedgerPost(tenant, attempt, payment, post, new byte[] {7}); });
            Future<SettlementFinalityService.FinalityOutcome> second = executor.submit(() -> { start.await(); return finality.recordLedgerPost(tenant, attempt, payment, post, new byte[] {7}); });
            SettlementFinalityService.FinalityOutcome firstResult = first.get(15, TimeUnit.SECONDS);
            SettlementFinalityService.FinalityOutcome secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(firstResult.recordId()).isEqualTo(secondResult.recordId());
            assertThat(firstResult.replayed() ^ secondResult.replayed()).isTrue();
            assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + payment + "'"))
                    .isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void aDifferentAuthoritativeSourceForAnAlreadyFinalPaymentFailsClosed() throws Exception {
        UUID tenant = UUID.randomUUID(); UUID payment = UUID.randomUUID(); UUID debtor = account(1_000); UUID creditor = account(100);
        UUID firstAttempt = UUID.randomUUID();
        LedgerPort.Reserved firstReservation = (LedgerPort.Reserved) ledger.reserve(firstAttempt, payment, debtor, 200);
        LedgerPort.TerminalResult firstPost = ledger.post(firstReservation.reservationId(), creditor, UUID.randomUUID());
        finality.recordLedgerPost(tenant, firstAttempt, payment, firstPost, new byte[] {3});
        UUID secondAttempt = UUID.randomUUID();
        LedgerPort.Reserved secondReservation = (LedgerPort.Reserved) ledger.reserve(secondAttempt, payment, debtor, 200);
        LedgerPort.TerminalResult secondPost = ledger.post(secondReservation.reservationId(), creditor, UUID.randomUUID());

        assertThatThrownBy(() -> finality.recordLedgerPost(tenant, secondAttempt, payment, secondPost, new byte[] {4}))
                .isInstanceOf(FinalityConflictException.class);
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + payment + "'"))
                .isEqualTo(1);
    }

    @Test void retryAfterPaymentProjectionFailureReusesTheImmutableAuthorityRecord() throws Exception {
        UUID tenant = UUID.randomUUID(); UUID attempt = UUID.randomUUID(); UUID payment = UUID.randomUUID();
        UUID debtor = account(700); UUID creditor = account(100);
        LedgerPort.Reserved reserved = (LedgerPort.Reserved) ledger.reserve(attempt, payment, debtor, 300);
        LedgerPort.TerminalResult post = ledger.post(reserved.reservationId(), creditor, UUID.randomUUID());
        FailsOnceProjection failsOnce = new FailsOnceProjection();
        SettlementFinalityService service = new SettlementFinalityService(
                new SettlementConnectionFactory(POSTGRES.getJdbcUrl(), "settlement_role", "dev-only-settlement"), failsOnce);

        assertThatThrownBy(() -> service.recordLedgerPost(tenant, attempt, payment, post, new byte[] {8}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("projection fault");
        assertThat(count("SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = '" + payment + "'"))
                .isEqualTo(1);

        SettlementFinalityService.FinalityOutcome retry = service.recordLedgerPost(tenant, attempt, payment, post, new byte[] {8});
        assertThat(retry.replayed()).isTrue();
        assertThat(failsOnce.calls).isEqualTo(2);
    }

    private static UUID account(long available) throws Exception { UUID id = UUID.randomUUID(); try (Connection c = admin(); PreparedStatement s = c.prepareStatement("INSERT INTO ledger.liquidity_accounts (id, tenant_id, participant_id, currency, available_minor) VALUES (?, gen_random_uuid(), gen_random_uuid(), 'EUR', ?)")) { s.setObject(1,id); s.setLong(2,available); s.executeUpdate(); } return id; }
    private static int count(String sql) throws Exception { try (Connection c=admin(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { r.next(); return r.getInt(1); } }
    private static String string(String sql) throws Exception { try (Connection c=admin(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { r.next(); return r.getString(1); } }
    private static Instant finalityAt(UUID recordId) throws Exception { try (Connection c=admin(); PreparedStatement s=c.prepareStatement("SELECT finality_at FROM settlement.settlement_finality_records WHERE id = ?")) { s.setObject(1, recordId); try (ResultSet r=s.executeQuery()) { r.next(); return r.getObject(1, OffsetDateTime.class).toInstant(); } } }
    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static final class RecordingProjection implements PaymentFinalityPort { int calls; @Override public ProjectionResult project(UUID tenantId, UUID paymentId, UUID recordId, Instant finalityAt) { calls++; return ProjectionResult.PROJECTED; } }
    private static final class FailsOnceProjection implements PaymentFinalityPort { int calls; @Override public ProjectionResult project(UUID tenantId, UUID paymentId, UUID recordId, Instant finalityAt) { if (++calls == 1) throw new IllegalStateException("projection fault"); return ProjectionResult.PROJECTED; } }
}
