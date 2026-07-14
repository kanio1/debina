package com.sepanexus.modules.paymentlifecycle.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
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
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void createsPaymentAndReturnsLocation() throws Exception {
        PaymentEntity payment = org.mockito.Mockito.mock(PaymentEntity.class);
        UUID paymentId = UUID.randomUUID();
        when(payment.getId()).thenReturn(paymentId);
        when(paymentService.submitPayment(any())).thenReturn(payment);

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_submitter"))
                        .contentType("application/json")
                        .content(validPaymentJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/payments/" + paymentId));
    }

    static String validPaymentJson() {
        return """
                {"endToEndId":"E2E-1","amount":10.00,"currency":"EUR",
                 "debtorIban":"DE89370400440532013000","creditorIban":"FR7630006000011234567890189"}
                """;
    }
}
