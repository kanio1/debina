package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.evidenceaudit.CommandAuditPort;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

    @MockitoSpyBean
    private CommandAuditPort commandAuditPort;

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
