package com.sepanexus.modules.paymentlifecycle.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduler mechanics are deliberately small; state and audit semantics live in the SQL command. */
@Component
@ConditionalOnProperty(prefix = "sepa.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApprovalExpiryScheduler {
    private final ApprovalExpiryService expiry;
    public ApprovalExpiryScheduler(ApprovalExpiryService expiry) { this.expiry = expiry; }
    @Scheduled(fixedDelayString = "${sepa.scheduling.approval-expiry-fixed-delay-ms:60000}")
    public void expireDueApprovals() { expiry.expireDueApprovals(100); }
}
