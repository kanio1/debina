package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

@org.junit.jupiter.api.Tag("testcontainers")
class MissingTenantClaimTest extends TenantGucIntegrationTest {

    @Test
    @WithMockUser(roles = "payment_submitter")
    void missingTenantClaimSeesZeroRows() {
        assertThat(paymentService.visiblePayments(null)).isEmpty();
    }
}
