package com.sepanexus.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.security.authorization.AuthorizationManager<org.aopalliance.intercept.MethodInvocation>
            paymentLifecycleAuthorizationManager;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void requiresAuthenticationAndAcceptsPaymentSubmitterRole() throws Exception {
        mockMvc.perform(post("/api/v1/payments").contentType("application/json").content(paymentJson()))
                .andExpect(status().isUnauthorized());

        PaymentEntity payment = org.mockito.Mockito.mock(PaymentEntity.class);
        when(payment.getId()).thenReturn(UUID.randomUUID());
        when(paymentService.submitPayment(any())).thenReturn(payment);
        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_submitter"))
                        .contentType("application/json").content(paymentJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void mapsNestedKeycloakRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .claim("sub", "subject")
                .claim("realm_access", Map.of("roles", List.of("payment_submitter")))
                .build();

        org.assertj.core.api.Assertions.assertThat(new SecurityConfig().jwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactly("ROLE_payment_submitter");
        org.assertj.core.api.Assertions.assertThat(paymentLifecycleAuthorizationManager).isNotNull();
    }

    private static String paymentJson() {
        return """
                {"endToEndId":"E2E-1","amount":10.00,"currency":"EUR",
                 "debtorIban":"DE89370400440532013000","creditorIban":"FR7630006000011234567890189"}
                """;
    }
}
