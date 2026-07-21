package com.sepanexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FullRoleModelRealmTest {

    private static final Set<String> EXPECTED_ROLES = Set.of(
            "operator", "payment_viewer", "payment_submitter", "payment_approver",
            "settlement_operator", "egress_operator", "reconciliation_operator", "case_operator",
            "reference_data_admin", "simulation_operator", "auditor", "security_admin");

    @BeforeAll
    static void importRealm() {
        KeycloakRealmTestSupport.start();
    }

    @Test
    void importsTheFrozenOrganizationRoleAndClientModel() throws Exception {
        var realm = KeycloakRealmTestSupport.adminGet("");
        assertThat(realm.path("organizationsEnabled").asBoolean()).isTrue();

        var roles = KeycloakRealmTestSupport.adminGet("roles").findValues("name").stream()
                .map(node -> node.asText())
                .collect(Collectors.toSet());
        assertThat(roles).containsAll(EXPECTED_ROLES);

        var clients = KeycloakRealmTestSupport.adminGet("clients");
        assertThat(clients).extracting(client -> client.path("clientId").asText())
                .contains("sepa-web", "sepa-api", "sepa-integration");
        assertThat(clients).filteredOn(client -> "sepa-web".equals(client.path("clientId").asText()))
                .allSatisfy(client -> assertThat(client.path("directAccessGrantsEnabled").asBoolean()).isFalse());

        var organization = KeycloakRealmTestSupport.adminGet("organizations").get(0);
        var organizationDetail = KeycloakRealmTestSupport.adminGet("organizations/" + organization.path("id").asText());
        assertThat(organizationDetail.path("alias").asText()).isEqualTo("demo-bank-org");
        assertThat(organizationDetail.path("attributes").path("tenant_id").get(0).asText())
                .isEqualTo("00000000-0000-0000-0000-000000000001");
    }

    @Test
    void mapsOrganizationAndBranchClaimsFromTheImportedRealm() throws Exception {
        KeycloakRealmTestSupport.createDirectGrantProbeClient();

        var claims = KeycloakRealmTestSupport.passwordGrantClaims("submitter", "dev-only-submitter");
        assertThat(claims.path("realm_access").path("roles")).extracting(node -> node.asText())
                .containsExactly("payment_submitter");
        assertThat(claims.path("sub").asText()).isNotBlank();
        assertThat(claims.path("branch_id").asText()).isEqualTo("00000000-0000-0000-0000-000000000101");
        assertThat(claims.path("organization").path("demo-bank-org").path("id").asText())
                .isEqualTo("00000000-0000-0000-0000-000000000010");
        assertThat(claims.path("organization").path("demo-bank-org").path("tenant_id").get(0).asText())
                .isEqualTo("00000000-0000-0000-0000-000000000001");
    }
}
