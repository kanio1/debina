package com.sepanexus.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.modules.ApprovalQueueQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** HTTP/runtime proof for the fixed authenticated GraphQL endpoint; data ownership is tested separately. */
@SpringBootTest(classes = SepaNexusApplication.class)
@AutoConfigureMockMvc
class ApprovalGraphQlRuntimeTest {
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

    @Autowired MockMvc mockMvc;
    @MockitoBean ApprovalQueueQuery approvalQueue;

    @Test
    void authenticatedApproverReceivesPaymentOwnedQueueDto() throws Exception {
        UUID tenant = UUID.randomUUID(); UUID branch = UUID.randomUUID(); UUID approval = UUID.randomUUID(); UUID payment = UUID.randomUUID();
        when(approvalQueue.pending(any(), any(), any(), any())).thenReturn(new ApprovalQueueQuery.QueuePage(List.of(
                new ApprovalQueueQuery.QueueItem(approval, payment, "PENDING_APPROVAL", "maker", Instant.parse("2026-07-21T10:00:00Z"),
                        Instant.parse("2026-07-22T10:00:00Z"), UUID.randomUUID(), new BigDecimal("10.00"), "EUR",
                        "DE89370400440532013000", "FR7630006000011234567890189", false)), null));

        mockMvc.perform(post("/graphql").contentType("application/json")
                        .content("{\"query\":\"query { approvalQueue(first: 10) { items { paymentId approvalStatus expiredButUnprocessed } } }\"}")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenant.toString()).claim("branch_id", branch.toString()))
                                .authorities(() -> "ROLE_payment_approver")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalQueue.items[0].paymentId").value(payment.toString()))
                .andExpect(jsonPath("$.data.approvalQueue.items[0].approvalStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.approvalQueue.items[0].expiredButUnprocessed").value(false));
    }

    @Test
    void missingBearerIsRejectedBeforeGraphqlExecution() throws Exception {
        mockMvc.perform(post("/graphql").contentType("application/json").content("{\"query\":\"{ __typename }\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aliasesCannotBypassQueryComplexityLimit() throws Exception {
        StringBuilder query = new StringBuilder("query {");
        for (int index = 0; index <= GraphQlSecurityConfiguration.MAX_COMPLEXITY; index++) {
            query.append("q").append(index).append(": approvalQueue(first: 1) { nextCursor }");
        }
        query.append('}');

        mockMvc.perform(post("/graphql").contentType("application/json")
                        .content("{\"query\":\"" + query + "\"}")
                        .with(approverJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void deeplyNestedIntrospectionCannotBypassQueryDepthLimit() throws Exception {
        String nested = "{ __schema { types { fields { type { ofType { ofType { ofType { ofType { ofType { ofType { ofType { ofType { ofType { name } } } } } } } } } } } } } }";
        mockMvc.perform(post("/graphql").contentType("application/json")
                        .content("{\"query\":\"" + nested + "\"}")
                        .with(approverJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor approverJwt() {
        return jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                .authorities(() -> "ROLE_payment_approver");
    }
}
