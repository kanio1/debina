package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.evidenceaudit.CommandAuditPort;
import com.sepanexus.evidenceaudit.ActorType;
import com.sepanexus.evidenceaudit.CommandAuditEntry;
import com.sepanexus.evidenceaudit.CommandAuditOutcome;
import com.sepanexus.evidenceaudit.DeniedCommandAuditPort;
import com.sepanexus.shared.ClockPort;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** PostgreSQL 18 proof for the frozen approval prefix gate (EPIC-76 Story 76.2). */
@SpringBootTest(classes = SepaNexusApplication.class)
class ApprovalSubmissionIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    private static boolean initialized;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ApprovalQueueReadModel approvalQueueReadModel;

    @Autowired
    private ApprovalDecisionService approvalDecisionService;

    @Autowired
    private ApprovalExpiryService approvalExpiryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private CommandAuditPort commandAuditPort;

    @MockitoSpyBean
    private DeniedCommandAuditPort deniedCommandAuditPort;

    @MockitoSpyBean
    private ClockPort clock;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE audit.audit_log, payment.payment_approvals, payment.payments, payment.outbox_events, "
                    + "payment.payment_status_history, payment.payment_events, ingress.idempotency_keys, "
                    + "ingress.raw_inbound_messages, iso.payment_iso_identifiers, iso.message_lineage, "
                    + "iso.iso_messages, reference_data.approval_matrix_rules CASCADE");
        }
    }

    @Test
    @WithMockUser(roles = "payment_submitter")
    void broadApprovalRuleKeepsPaymentBeforeFsmAndReplayDoesNotReleaseAnEvent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID ruleId = addBroadRule(tenantId, true, false);
        SubmitPaymentCommand command = command(tenantId, branchId, "maker-subject", UUID.randomUUID().toString());

        var first = paymentService.submitPayment(command);
        var replay = paymentService.submitPayment(command);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(paymentService.approvalStatus(tenantId, branchId, first.getId()).name()).isEqualTo("PENDING_APPROVAL");
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", first.getId())).isZero();
        assertThat(count("SELECT count(*) FROM payment.payment_status_history WHERE payment_id = ?", first.getId())).isZero();
        assertThat(count("SELECT count(*) FROM iso.message_lineage WHERE payment_id = ?", first.getId())).isEqualTo(1);
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT p.status, a.status, a.maker_user_id, a.matrix_rule_id,
                       a.expires_at - a.submitted_for_approval_at AS expiry
                FROM payment.payments p JOIN payment.payment_approvals a ON a.payment_id = p.id
                WHERE p.id = ?
                """)) {
            statement.setObject(1, first.getId());
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isNull();
                assertThat(result.getString("maker_user_id")).isEqualTo("maker-subject");
                assertThat(result.getObject("matrix_rule_id")).isEqualTo(ruleId);
                assertThat(result.getString("expiry")).isEqualTo("1 day");
            }
        }
    }

    @Test
    @WithMockUser(roles = "payment_submitter")
    void noMatchingRulePreservesReceivedFlowAndWritesExactlyOneApprovalAndOutboxEvent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        var payment = paymentService.submitPayment(command(tenantId, branchId, "maker-subject", UUID.randomUUID().toString()));

        assertThat(payment.getStatus().name()).isEqualTo("RECEIVED");
        assertThat(paymentService.approvalStatus(tenantId, branchId, payment.getId()).name()).isEqualTo("NOT_REQUIRED");
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payment_status_history WHERE payment_id = ?", payment.getId())).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "payment_submitter")
    void ambiguousOrUnavailableRulesFailClosedBeforePaymentCreation() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        addBroadRule(tenantId, true, false);
        addBroadRule(tenantId, false, false);

        assertThatThrownBy(() -> paymentService.submitPayment(command(tenantId, branchId, "maker", UUID.randomUUID().toString())))
                .isInstanceOf(ApprovalMatrixPolicyException.class);
        assertThat(count("SELECT count(*) FROM payment.payments")).isZero();
        assertThat(count("SELECT count(*) FROM payment.outbox_events")).isZero();
    }

    @Test
    @WithMockUser(roles = "payment_approver")
    void queueIsRlsScopedCursorPaginatedAndHonestAboutUnprocessedExpiry() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID ruleId = addBroadRule(tenantId, true, false);
        UUID first = insertPending(tenantId, branchId, ruleId, Instant.parse("2026-07-01T08:00:00Z"),
                Instant.parse("2026-07-02T08:00:00Z"));
        UUID second = insertPending(tenantId, branchId, ruleId, Instant.parse("2026-07-03T08:00:00Z"),
                Instant.parse("2099-07-04T08:00:00Z"));
        UUID third = insertPending(tenantId, branchId, ruleId, Instant.parse("2026-07-05T08:00:00Z"),
                Instant.parse("2099-07-06T08:00:00Z"));

        ApprovalQueueReadModel.QueuePage firstPage = approvalQueueReadModel.pending(tenantId, branchId, 2, null);
        ApprovalQueueReadModel.QueuePage secondPage = approvalQueueReadModel.pending(tenantId, branchId, 2,
                firstPage.nextCursor());

        assertThat(firstPage.items()).extracting(ApprovalQueueReadModel.QueueItem::approvalId)
                .containsExactly(first, second);
        assertThat(firstPage.items().getFirst().expired()).isTrue();
        assertThat(firstPage.nextCursor()).isNotNull();
        assertThat(secondPage.items()).extracting(ApprovalQueueReadModel.QueueItem::approvalId).containsExactly(third);
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(approvalQueueReadModel.pending(tenantId, UUID.randomUUID(), 2, null).items()).isEmpty();
        assertThat(approvalQueueReadModel.pending(UUID.randomUUID(), branchId, 2, null).items()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "payment_approver")
    void approveAndRejectAreAuditedIdempotentAndOnlyApproveReleasesTheExistingEvent() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID approvePayment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now(), Instant.now().plusSeconds(3600)));
        ApprovalDecisionCommand approve = new ApprovalDecisionCommand(tenant, branch, approvePayment, "checker-a",
                "sid-a", UUID.randomUUID(), "approve-key", null, ApprovalDecisionCommand.Decision.APPROVE);
        withApproverJwt(tenant, branch, "checker-a", () -> {
            var first = approvalDecisionService.decide(approve);
            var replay = approvalDecisionService.decide(approve);
            assertThat(first).isEqualTo(replay);
            assertThat(first.approvalStatus().name()).isEqualTo("APPROVED");
        });
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", approvePayment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ?", approvePayment)).isEqualTo(1);

        UUID rejectPayment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now(), Instant.now().plusSeconds(3600)));
        withApproverJwt(tenant, branch, "checker-b", () -> {
            var reject = approvalDecisionService.decide(new ApprovalDecisionCommand(tenant, branch, rejectPayment, "checker-b",
                    "sid-b", UUID.randomUUID(), "reject-key", "not acceptable", ApprovalDecisionCommand.Decision.REJECT));
            assertThat(reject.approvalStatus().name()).isEqualTo("REJECTED");
        });
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", rejectPayment)).isZero();
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ?", rejectPayment)).isEqualTo(1);
    }

    @Test
    void deniedApproverAttemptIsRecordedWithoutRevealingOrMutatingTheTarget() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now(), Instant.now().plusSeconds(3600)));
        var command = new ApprovalDecisionCommand(tenant, branch, payment, "submitter", "sid-denied", UUID.randomUUID(),
                "denied-key", null, ApprovalDecisionCommand.Decision.APPROVE);

        withJwt(tenant, branch, "submitter", "payment_submitter", () ->
                assertThatThrownBy(() -> approvalDecisionService.decide(command))
                        .isInstanceOf(org.springframework.security.authorization.AuthorizationDeniedException.class));

        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ? AND outcome = 'DENIED'", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM ingress.idempotency_keys")).isZero();
    }

    @Test
    void unavailableDeniedAuditFailsClosedWithoutAuthorizingOrMutating() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now(), Instant.now().plusSeconds(3600)));
        org.mockito.Mockito.doThrow(new IllegalStateException("controlled denial audit failure"))
                .when(deniedCommandAuditPort).appendDenied(org.mockito.ArgumentMatchers.any());

        var command = new ApprovalDecisionCommand(tenant, branch, payment, "submitter", "sid-denied", UUID.randomUUID(),
                "denied-audit-failure", null, ApprovalDecisionCommand.Decision.APPROVE);
        withJwt(tenant, branch, "submitter", "payment_submitter", () -> assertThatThrownBy(() -> decisions(command))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("controlled denial audit failure"));

        assertThat(count("SELECT count(*) FROM payment.payment_approvals WHERE payment_id = ? AND status = 'PENDING_APPROVAL'", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM ingress.idempotency_keys")).isZero();
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ?", payment)).isZero();
    }

    @Test
    void makerAndForeignBranchApproversAreDeniedBeforeAnyDecisionMutation() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now(), Instant.now().plusSeconds(3600)));

        var maker = new ApprovalDecisionCommand(tenant, branch, payment, "maker-queue", "sid-maker", UUID.randomUUID(),
                "maker-key", null, ApprovalDecisionCommand.Decision.APPROVE);
        withJwt(tenant, branch, "maker-queue", "payment_approver", () -> assertThatThrownBy(() ->
                approvalDecisionService.decide(maker)).isInstanceOf(org.springframework.security.authorization.AuthorizationDeniedException.class));

        UUID foreignBranch = UUID.randomUUID();
        var foreign = new ApprovalDecisionCommand(tenant, foreignBranch, payment, "checker-foreign", "sid-foreign",
                UUID.randomUUID(), "foreign-key", null, ApprovalDecisionCommand.Decision.APPROVE);
        withJwt(tenant, foreignBranch, "checker-foreign", "payment_approver", () -> assertThatThrownBy(() ->
                approvalDecisionService.decide(foreign)).isInstanceOf(org.springframework.security.authorization.AuthorizationDeniedException.class));

        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ? AND outcome = 'DENIED'", payment)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM ingress.idempotency_keys")).isZero();
    }

    @Test
    void auditAppendFailureRollsBackApprovalOutboxAndIdempotency() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now(), Instant.now().plusSeconds(3600)));
        var command = new ApprovalDecisionCommand(tenant, branch, payment, "checker", "sid-failure", UUID.randomUUID(),
                "audit-failure-key", null, ApprovalDecisionCommand.Decision.APPROVE);
        org.mockito.Mockito.doThrow(new IllegalStateException("controlled audit failure"))
                .when(commandAuditPort).append(org.mockito.ArgumentMatchers.any());

        withApproverJwt(tenant, branch, "checker", () -> assertThatThrownBy(() -> approvalDecisionService.decide(command))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("controlled audit failure"));

        assertThat(count("SELECT count(*) FROM payment.payment_approvals WHERE payment_id = ? AND status = 'PENDING_APPROVAL'", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM ingress.idempotency_keys")).isZero();
    }

    @Test
    void auditAppendFailureRollsBackRejectAndIdempotency() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now(), Instant.now().plusSeconds(3600)));
        var command = new ApprovalDecisionCommand(tenant, branch, payment, "checker", "sid-reject-failure", UUID.randomUUID(),
                "reject-audit-failure-key", "reject must roll back", ApprovalDecisionCommand.Decision.REJECT);
        org.mockito.Mockito.doThrow(new IllegalStateException("controlled reject audit failure"))
                .when(commandAuditPort).append(org.mockito.ArgumentMatchers.any());

        withApproverJwt(tenant, branch, "checker", () -> assertThatThrownBy(() -> approvalDecisionService.decide(command))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("controlled reject audit failure"));

        assertThat(count("SELECT count(*) FROM payment.payment_approvals WHERE payment_id = ? AND status = 'PENDING_APPROVAL'", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM ingress.idempotency_keys")).isZero();
    }

    @Test
    void successfulAuditAppendUsesThePhysicalCommandTransactionAndRollsBackWithIt() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID auditId = new TransactionTemplate(transactionManager).execute(status -> {
            jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenant.toString());
            jdbcTemplate.queryForObject("SELECT set_config('app.branch_id', ?, true)", String.class, branch.toString());
            Long transactionId = jdbcTemplate.queryForObject("SELECT txid_current()", Long.class);
            UUID written = commandAuditPort.append(new CommandAuditEntry(tenant, branch, ActorType.HUMAN, "checker-tx",
                    "payment_approver", "sid-tx", UUID.randomUUID(), "PAYMENT_APPROVAL_APPROVED", "PAYMENT_APPROVAL",
                    UUID.randomUUID(), UUID.randomUUID(), null, null, java.util.Map.of("status", "PENDING_APPROVAL"),
                    java.util.Map.of("status", "APPROVED"), CommandAuditOutcome.SUCCESS, UUID.randomUUID(), Instant.now()));
            Long insertedTransactionId = jdbcTemplate.queryForObject(
                    "SELECT xmin::text::bigint FROM audit.audit_log WHERE audit_entry_id = ?", Long.class, written);
            assertThat(insertedTransactionId).isEqualTo(transactionId);
            status.setRollbackOnly();
            return written;
        });
        assertThat(auditId).isNotNull();
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE audit_entry_id = ?", auditId)).isZero();
    }

    @Test
    void twoDistinctCheckersRaceAndOnlyOneAuditedApprovalCommits() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now(), Instant.now().plusSeconds(3600)));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executors = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = executors.submit(() -> raceDecision(start, tenant, branch, payment, "checker-race-a"));
            Future<Throwable> second = executors.submit(() -> raceDecision(start, tenant, branch, payment, "checker-race-b"));
            start.countDown();

            Throwable firstResult = first.get();
            Throwable secondResult = second.get();
            assertThat(firstResult == null ? 1 : 0).isEqualTo(1 - (secondResult == null ? 1 : 0));
            assertThat(firstResult == null ? secondResult : firstResult).isInstanceOf(ApprovalDecisionConflictException.class);
        } finally {
            executors.shutdownNow();
        }

        assertThat(count("SELECT count(*) FROM payment.payment_approvals WHERE payment_id = ? AND status = 'APPROVED'", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payment_approvals WHERE payment_id = ? AND checker_user_id IN ('checker-race-a', 'checker-race-b')", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ? AND outcome = 'SUCCESS'", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ingress.idempotency_keys")).isEqualTo(1);
    }

    @Test
    void approveVersusExpiryLeavesOneTerminalAuditAndAtMostOneReceivedEvent() throws Exception {
        approvalExpiryRace(ApprovalDecisionCommand.Decision.APPROVE);
    }

    @Test
    void rejectVersusExpiryLeavesOneTerminalAuditAndNoReceivedEvent() throws Exception {
        approvalExpiryRace(ApprovalDecisionCommand.Decision.REJECT);
    }

    @Test
    void expiryUsesTheNarrowSystemRoleAndIsReplaySafeWithoutStartingTheFsm() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now().minusSeconds(90_000),
                Instant.now().minusSeconds(1)));

        assertThat(approvalExpiryService.expireDueApprovals(10)).isEqualTo(1);
        assertThat(approvalExpiryService.expireDueApprovals(10)).isZero();
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT a.status, p.status, l.actor_type, l.actor_id, l.authorized_role
                FROM payment.payment_approvals a JOIN payment.payments p ON p.id = a.payment_id
                JOIN audit.audit_log l ON l.payment_id = p.id
                WHERE p.id = ?
                """)) {
            statement.setObject(1, payment);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("EXPIRED");
                assertThat(result.getString(2)).isNull();
                assertThat(result.getString(3)).isEqualTo("SYSTEM");
                assertThat(result.getString(4)).isEqualTo("system_approval_expiry");
                assertThat(result.getString(5)).isEqualTo("system_approval_expiry");
            }
        }
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ?", payment)).isEqualTo(1);
    }

    @Test
    void expiryAuditAppendFailureRollsBackTheExpiryTransition() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, Instant.now().minusSeconds(90_000),
                Instant.now().minusSeconds(1)));
        String appendFunction = "audit.append_command_audit(uuid,uuid,text,text,text,text,uuid,text,text,uuid,uuid,uuid,text,jsonb,jsonb,text,uuid,timestamptz)";
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("REVOKE EXECUTE ON FUNCTION " + appendFunction + " FROM payment_approval_expiry_function_owner");
        }
        try {
            assertThatThrownBy(() -> approvalExpiryService.expireDueApprovals(10)).isInstanceOf(RuntimeException.class);
        } finally {
            try (Connection connection = admin(); Statement statement = connection.createStatement()) {
                statement.execute("GRANT EXECUTE ON FUNCTION " + appendFunction + " TO payment_approval_expiry_function_owner");
            }
        }
        assertThat(count("SELECT count(*) FROM payment.payment_approvals WHERE payment_id = ? AND status = 'PENDING_APPROVAL'", payment)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ?", payment)).isZero();
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isZero();
    }

    private static SubmitPaymentCommand command(UUID tenantId, UUID branchId, String maker, String idempotencyKey) {
        return new SubmitPaymentCommand(tenantId, branchId, "E2E-APPROVAL", new BigDecimal("10.00"), "EUR",
                "DE89370400440532013000", "FR7630006000011234567890189", maker, idempotencyKey);
    }

    private static UUID addBroadRule(UUID tenantId, boolean requiresApproval, boolean requiresStepUp) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role",
                "dev-only-reference-data")) {
            try (PreparedStatement guc = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                guc.setString(1, tenantId.toString());
                guc.execute();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO reference_data.approval_matrix_rules
                        (tenant_id, requires_approval, requires_step_up, valid_from)
                    VALUES (?, ?, ?, CURRENT_DATE) RETURNING id
                    """)) {
                insert.setObject(1, tenantId);
                insert.setBoolean(2, requiresApproval);
                insert.setBoolean(3, requiresStepUp);
                try (ResultSet result = insert.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    return (UUID) result.getObject(1);
                }
            }
        }
    }

    private static UUID insertPending(UUID tenantId, UUID branchId, UUID ruleId, Instant submittedAt, Instant expiresAt)
            throws Exception {
        try (Connection connection = admin(); PreparedStatement payment = connection.prepareStatement("""
                INSERT INTO payment.payments (tenant_id, branch_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (?, ?, 10.00, 'EUR', 'DEBTOR', 'CREDITOR', NULL) RETURNING id
                """)) {
            payment.setObject(1, tenantId);
            payment.setObject(2, branchId);
            try (ResultSet result = payment.executeQuery()) {
                assertThat(result.next()).isTrue();
                UUID paymentId = (UUID) result.getObject(1);
                try (PreparedStatement approval = connection.prepareStatement("""
                        INSERT INTO payment.payment_approvals
                            (payment_id, status, maker_user_id, matrix_rule_id, submitted_for_approval_at, expires_at)
                        VALUES (?, 'PENDING_APPROVAL', 'maker-queue', ?, ?, ?) RETURNING id
                        """)) {
                    approval.setObject(1, paymentId);
                    approval.setObject(2, ruleId);
                    approval.setTimestamp(3, java.sql.Timestamp.from(submittedAt));
                    approval.setTimestamp(4, java.sql.Timestamp.from(expiresAt));
                    try (ResultSet approvalResult = approval.executeQuery()) {
                        assertThat(approvalResult.next()).isTrue();
                        return (UUID) approvalResult.getObject(1);
                    }
                }
            }
        }
    }

    private static int count(String sql, Object... parameters) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static UUID paymentForApproval(UUID approvalId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(
                "SELECT payment_id FROM payment.payment_approvals WHERE id = ?")) {
            statement.setObject(1, approvalId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return (UUID) result.getObject(1);
            }
        }
    }

    private static void withApproverJwt(UUID tenant, UUID branch, String subject, Runnable assertion) {
        withJwt(tenant, branch, subject, "payment_approver", assertion);
    }

    private Throwable raceDecision(CountDownLatch start, UUID tenant, UUID branch, UUID payment, String checker) {
        try {
            start.await();
            withApproverJwt(tenant, branch, checker, () -> approvalDecisionService.decide(new ApprovalDecisionCommand(
                    tenant, branch, payment, checker, "sid-" + checker, UUID.randomUUID(), "race-" + checker,
                    null, ApprovalDecisionCommand.Decision.APPROVE)));
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void decisions(ApprovalDecisionCommand command) {
        approvalDecisionService.decide(command);
    }

    private void approvalExpiryRace(ApprovalDecisionCommand.Decision decision) throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID rule = addBroadRule(tenant, true, false);
        Instant expiry = Instant.parse("2040-01-01T00:00:00Z");
        UUID payment = paymentForApproval(insertPending(tenant, branch, rule, expiry.minusSeconds(60), expiry));
        org.mockito.Mockito.doAnswer(invocation -> Thread.currentThread().getName().contains("expiry-race")
                ? expiry : expiry.minusMillis(1)).when(clock).now();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executors = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> interactive = executors.submit(() -> {
                Thread.currentThread().setName("interactive-decision-race");
                try {
                    start.await();
                    withApproverJwt(tenant, branch, "checker-expiry-race", () -> approvalDecisionService.decide(
                            new ApprovalDecisionCommand(tenant, branch, payment, "checker-expiry-race", "sid-race",
                                    UUID.randomUUID(), "expiry-race-" + decision, decision == ApprovalDecisionCommand.Decision.REJECT
                                            ? "reject in race" : null, decision)));
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            });
            Future<Throwable> expiryWorker = executors.submit(() -> {
                Thread.currentThread().setName("expiry-race");
                try {
                    start.await();
                    approvalExpiryService.expireDueApprovals(10);
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            });
            start.countDown();

            assertThat(expiryWorker.get()).isNull();
            Throwable interactiveFailure = interactive.get();
            assertThat(interactiveFailure == null || interactiveFailure instanceof ApprovalDecisionConflictException).isTrue();
        } finally {
            executors.shutdownNow();
        }

        String terminal = decision == ApprovalDecisionCommand.Decision.APPROVE ? "APPROVED" : "REJECTED";
        assertThat(count("SELECT count(*) FROM payment.payment_approvals WHERE payment_id = ? AND status IN (?, 'EXPIRED')", payment, terminal)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ? AND outcome = 'SUCCESS'", payment)).isEqualTo(1);
        int events = count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment);
        if (decision == ApprovalDecisionCommand.Decision.APPROVE) assertThat(events).isLessThanOrEqualTo(1);
        else assertThat(events).isZero();
    }

    private static void withJwt(UUID tenant, UUID branch, String subject, String role, Runnable assertion) {
        var jwt = new org.springframework.security.oauth2.jwt.Jwt("test-token", Instant.now(), Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "none"), java.util.Map.of("sub", subject, "tenant_id", tenant.toString(),
                        "branch_id", branch.toString(), "sid", "test-session"));
        var authentication = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previous = context.getAuthentication();
        context.setAuthentication(authentication);
        try { assertion.run(); } finally { context.setAuthentication(previous); }
    }

    static synchronized void initializeDatabase() {
        if (initialized) {
            return;
        }
        POSTGRES.start();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception);
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        initialized = true;
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
