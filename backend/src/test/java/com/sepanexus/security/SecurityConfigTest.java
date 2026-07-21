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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * {@code PaymentService} is fully mocked ({@link MockitoBean}), so no repository/query behaviour
 * is under test here — the {@code @DynamicPropertySource} Testcontainers Postgres below exists
 * only because {@code @SpringBootTest} boots the full application context, and Hibernate's
 * JPA auto-configuration needs a reachable database to detect its dialect at startup. Per
 * {@code infra/AGENTS.md} ("do not make tests depend on data left in infra_postgres_1"), this
 * must be an isolated container, never the long-lived Compose Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
    }

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
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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

    @Test
    void normalizesTheSingleOrganizationTenantAttributeWithoutSelectingAnAmbiguousOrganization() {
        Jwt organizationJwt = Jwt.withTokenValue("organization-token").header("alg", "none")
                .claim("organization", Map.of("demo-bank-org", Map.of(
                        "id", "00000000-0000-0000-0000-000000000010",
                        "tenant_id", List.of("00000000-0000-0000-0000-000000000001"))))
                .build();

        JwtAuthenticationToken normalized = (JwtAuthenticationToken) new SecurityConfig()
                .jwtAuthenticationConverter().convert(organizationJwt);
        org.assertj.core.api.Assertions.assertThat(normalized.getToken().getClaimAsString("tenant_id"))
                .isEqualTo("00000000-0000-0000-0000-000000000001");
        org.assertj.core.api.Assertions.assertThat(normalized.getToken().getClaimAsString("organization_id"))
                .isEqualTo("00000000-0000-0000-0000-000000000010");

        Jwt ambiguousJwt = Jwt.withTokenValue("ambiguous-token").header("alg", "none")
                .claim("organization", Map.of(
                        "demo-bank-org", Map.of("tenant_id", List.of("00000000-0000-0000-0000-000000000001")),
                        "another-org", Map.of("tenant_id", List.of("00000000-0000-0000-0000-000000000002"))))
                .build();
        JwtAuthenticationToken ambiguous = (JwtAuthenticationToken) new SecurityConfig()
                .jwtAuthenticationConverter().convert(ambiguousJwt);
        org.assertj.core.api.Assertions.assertThat(ambiguous.getToken().hasClaim("tenant_id")).isFalse();
    }

    private static String paymentJson() {
        return """
                {"endToEndId":"E2E-1","amount":10.00,"currency":"EUR",
                 "debtorIban":"DE89370400440532013000","creditorIban":"FR7630006000011234567890189"}
                """;
    }
}
