package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;

/**
 * Proves the full walking-skeleton chain with a test-context JWT decoder: authenticated HTTP POST
 * -> payment row -> source-backed outbox fact -> real Kafka publication -> inbox consumption.
 * Testcontainers supplies PostgreSQL and Kafka; this test does not depend on local Keycloak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.junit.jupiter.api.Tag("testcontainers")
class WalkingSkeletonIntegrationTest extends KafkaIntegrationSupport {

    private static final UUID SUBMITTER_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private OutboxDispatcher dispatcher;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void tokensAreDecodedByTheTestContext() {
        when(jwtDecoder.decode("submitter-token")).thenReturn(jwt("payment_submitter"));
        when(jwtDecoder.decode("operator-token")).thenReturn(jwt("operator"));
    }

    @Test
    void submittedPaymentFlowsThroughOutboxKafkaAndInbox() throws Exception {
        String endToEndId = "walking-skeleton-" + UUID.randomUUID();
        HttpResponse<String> response = submitPayment("submitter-token", endToEndId);

        assertThat(response.statusCode()).isEqualTo(201);

        eventually(() -> assertThat(paymentTenantId(endToEndId)).isEqualTo(SUBMITTER_TENANT_ID));
        dispatcher.dispatch();
        eventually(() -> assertThat(outboxDispatched(endToEndId)).isTrue());
        eventually(() -> assertThat(paymentStatus(endToEndId)).isEqualTo("RECEIVED"));
    }

    @Test
    void deniedRoleProducesNoSideEffects() throws Exception {
        String endToEndId = "walking-skeleton-denied-" + UUID.randomUUID();
        HttpResponse<String> response = submitPayment("operator-token", endToEndId);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(paymentExists(endToEndId)).isFalse();
        assertThat(outboxEventExists(endToEndId)).isFalse();
    }

    private HttpResponse<String> submitPayment(String token, String endToEndId) throws Exception {
        return HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/payments"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(BodyPublishers.ofString(paymentJson(endToEndId)))
                .build(), BodyHandlers.ofString());
    }

    private static Jwt jwt(String role) {
        return Jwt.withTokenValue("test-token-" + role)
                .header("alg", "none")
                .claim("sub", "walking-skeleton-" + role)
                .claim("tenant_id", SUBMITTER_TENANT_ID.toString())
                .claim("realm_access", Map.of("roles", java.util.List.of(role)))
                .build();
    }

    private static String paymentJson(String endToEndId) {
        return """
                {"endToEndId":"%s","amount":10.00,"currency":"EUR",
                 "debtorIban":"DE89370400440532013000","creditorIban":"FR7630006000011234567890189"}
                """.formatted(endToEndId);
    }

    private static UUID paymentTenantId(String endToEndId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT p.tenant_id FROM payment.payments p
                JOIN iso.payment_iso_identifiers pii ON pii.payment_id = p.id
                WHERE pii.end_to_end_id = ?
                """)) {
            statement.setString(1, endToEndId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return (UUID) result.getObject(1);
            }
        }
    }

    private static boolean outboxDispatched(String endToEndId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT o.published_at IS NOT NULL FROM payment.outbox_events o
                JOIN payment.payments p ON p.id = o.aggregate_id
                JOIN iso.payment_iso_identifiers pii ON pii.payment_id = p.id
                WHERE pii.end_to_end_id = ?
                """)) {
            statement.setString(1, endToEndId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static String paymentStatus(String endToEndId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT p.status FROM payment.payments p
                JOIN iso.payment_iso_identifiers pii ON pii.payment_id = p.id
                WHERE pii.end_to_end_id = ?
                """)) {
            statement.setString(1, endToEndId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static boolean paymentExists(String endToEndId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = ?")) {
            statement.setString(1, endToEndId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) > 0;
            }
        }
    }

    private static boolean outboxEventExists(String endToEndId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM payment.outbox_events o
                JOIN payment.payments p ON p.id = o.aggregate_id
                JOIN iso.payment_iso_identifiers pii ON pii.payment_id = p.id
                WHERE pii.end_to_end_id = ?
                """)) {
            statement.setString(1, endToEndId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) > 0;
            }
        }
    }
}
