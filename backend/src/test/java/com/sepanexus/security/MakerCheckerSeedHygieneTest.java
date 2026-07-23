package com.sepanexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("testcontainers")
class MakerCheckerSeedHygieneTest {

    @BeforeAll
    static void importRealm() {
        KeycloakRealmTestSupport.start();
    }

    @Test
    void neverSeedsSubmitterAndApproverOnTheSameOrganizationMember() throws Exception {
        var organization = KeycloakRealmTestSupport.adminGet("organizations").get(0);
        var members = KeycloakRealmTestSupport.adminGet("organizations/" + organization.path("id").asText() + "/members?max=100");
        Set<String> submitters = new HashSet<>();
        Set<String> approvers = new HashSet<>();

        for (var member : members) {
            String userId = member.path("id").asText();
            var roles = KeycloakRealmTestSupport.adminGet("users/" + userId + "/role-mappings/realm");
            for (var role : roles) {
                if ("payment_submitter".equals(role.path("name").asText())) submitters.add(userId);
                if ("payment_approver".equals(role.path("name").asText())) approvers.add(userId);
            }
        }

        assertThat(submitters).hasSize(1);
        assertThat(approvers).hasSize(1);
        assertThat(submitters).doesNotContainAnyElementsOf(approvers);
    }
}
