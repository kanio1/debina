package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
class PaymentAuthorizationTest {

    @Autowired
    private PaymentService paymentService;

    @Test
    @WithMockUser(roles = "operator")
    void rejectsOperatorForPaymentSubmission() {
        assertThatThrownBy(() -> paymentService.submitPayment(command()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static SubmitPaymentCommand command() {
        return new SubmitPaymentCommand(UUID.randomUUID(), null, "E2E-1", new BigDecimal("1.00"), "EUR", "DEBTOR", "CREDITOR",
                UUID.randomUUID().toString());
    }
}
