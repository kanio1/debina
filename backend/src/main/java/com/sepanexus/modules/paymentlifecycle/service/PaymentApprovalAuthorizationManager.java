package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.evidenceaudit.DeniedCommandAuditEntry;
import com.sepanexus.evidenceaudit.DeniedCommandAuditPort;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentApprovalRepository;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Source-required before-method object authorization for approval decisions. The read executes
 * under the caller's tenant/branch GUC before the service body is entered; it neither mutates
 * domain state nor treats UUID entropy as authorization.
 */
@Component
public class PaymentApprovalAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    private final TransactionTemplate transactionTemplate;
    private final TenantGucConfigurer tenantGuc;
    private final PaymentRepository payments;
    private final PaymentApprovalRepository approvals;
    private final DeniedCommandAuditPort deniedAudit;

    public PaymentApprovalAuthorizationManager(TransactionTemplate transactionTemplate, TenantGucConfigurer tenantGuc,
            PaymentRepository payments, PaymentApprovalRepository approvals, DeniedCommandAuditPort deniedAudit) {
        this.transactionTemplate = transactionTemplate;
        this.tenantGuc = tenantGuc;
        this.payments = payments;
        this.approvals = approvals;
        this.deniedAudit = deniedAudit;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
        if (!(invocation.getArguments()[0] instanceof ApprovalDecisionCommand command)) return new AuthorizationDecision(false);
        Authentication principal = authentication.get();
        if (!(principal instanceof JwtAuthenticationToken jwt)) return new AuthorizationDecision(false);
        if (!hasApproverRole(principal)) return deny(command, jwt, "ROLE_DENIED");
        String tenantClaim = jwt.getToken().getClaimAsString("tenant_id");
        String branchClaim = jwt.getToken().getClaimAsString("branch_id");
        if (tenantClaim == null || !tenantClaim.equals(command.tenantId().toString()) || !jwt.getToken().getSubject().equals(command.checkerUserId())) {
            return deny(command, jwt, "CONTEXT_MISMATCH");
        }
        if ((branchClaim == null) != (command.branchId() == null) || (branchClaim != null && !branchClaim.equals(command.branchId().toString()))) {
            return deny(command, jwt, "CONTEXT_MISMATCH");
        }
        boolean allowed = Boolean.TRUE.equals(transactionTemplate.execute(status -> visibleNonSelfPending(command)));
        return allowed ? new AuthorizationDecision(true) : deny(command, jwt, "OBJECT_ACCESS_DENIED");
    }

    private boolean visibleNonSelfPending(ApprovalDecisionCommand command) {
        tenantGuc.apply(command.tenantId(), command.branchId());
        return payments.findById(command.paymentId())
                .flatMap(payment -> approvals.findByPaymentId(payment.getId()))
                // State legality remains in the transactional command service.  The object guard
                // deliberately permits a same-checker idempotent replay after its terminal state.
                .map(approval -> !approval.getMakerUserId().equals(command.checkerUserId()))
                .orElse(false);
    }

    private AuthorizationDecision deny(ApprovalDecisionCommand command, JwtAuthenticationToken jwt, String reason) {
        try {
            UUID tenantId = UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
            String branch = jwt.getToken().getClaimAsString("branch_id");
            deniedAudit.appendDenied(new DeniedCommandAuditEntry(tenantId, branch == null ? null : UUID.fromString(branch),
                    jwt.getToken().getSubject(), hasApproverRole(jwt) ? "payment_approver" : "no_authorizing_role",
                    jwt.getToken().getClaimAsString("sid"), command.correlationId(), "PAYMENT_APPROVAL_" + command.decision(),
                    "PAYMENT", command.paymentId(), command.paymentId(), reason, Instant.now()));
            return new AuthorizationDecision(false);
        } catch (IllegalArgumentException malformedTrustedClaim) {
            return new AuthorizationDecision(false);
        }
    }

    private static boolean hasApproverRole(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_payment_approver"));
    }
}
