package com.sepanexus.modules.paymentlifecycle.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sepanexus.modules.paymentlifecycle.service.DuplicatePaymentException;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService;
import com.sepanexus.security.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void returnsProblemDetailForDuplicate() throws Exception {
        when(paymentService.submitPayment(any())).thenThrow(new DuplicatePaymentException("E2E-1"));

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_submitter"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(PaymentControllerTest.validPaymentJson()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }
}
