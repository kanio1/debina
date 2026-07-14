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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves the full walking-skeleton chain against a real, already-running Keycloak
 * (docker-compose, not a mock JWT): password grant -> real HTTP POST -> payment row ->
 * outbox row -> real Kafka publish -> inbox consumption -> read-side status update.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WalkingSkeletonIntegrationTest extends KafkaIntegrationSupport {

    private static final String KEYCLOAK_TOKEN_URL =
            "http://localhost:8080/realms/sepa-nexus/protocol/openid-connect/token";
    private static final UUID SUBMITTER_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void submittedPaymentFlowsThroughOutboxKafkaAndInbox() throws Exception {
        String endToEndId = "walking-skeleton-" + UUID.randomUUID();
        String token = accessToken("submitter", "dev-only-submitter");

        HttpResponse<String> response = submitPayment(token, endToEndId);

        assertThat(response.statusCode()).isEqualTo(201);

        eventually(() -> assertThat(paymentTenantId(endToEndId)).isEqualTo(SUBMITTER_TENANT_ID));
        eventually(() -> assertThat(outboxDispatched(endToEndId)).isTrue());
        eventually(() -> assertThat(paymentStatus(endToEndId)).isEqualTo("VALIDATED"));
    }

    @Test
    void deniedRoleProducesNoSideEffects() throws Exception {
        String endToEndId = "walking-skeleton-denied-" + UUID.randomUUID();
        String token = accessToken("operator", "dev-only-operator");

        HttpResponse<String> response = submitPayment(token, endToEndId);

        assertThat(response.statusCode()).isEqualTo(403);
        // Longer than OutboxDispatcher's 2s fixedDelay: proves no async side effect ever appears either.
        Thread.sleep(2500);
        assertThat(paymentExists(endToEndId)).isFalse();
        assertThat(outboxEventExists(endToEndId)).isFalse();
    }

    private HttpResponse<String> submitPayment(String token, String endToEndId) throws Exception {
        return HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/payments"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(paymentJson(endToEndId)))
                .build(), BodyHandlers.ofString());
    }

    private static String accessToken(String username, String password) throws Exception {
        String form = "grant_type=password&client_id=sepa-web&client_secret=dev-only-sepa-web-secret"
                + "&username=" + username + "&password=" + password + "&scope=openid";
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(form))
                .build(), BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Keycloak token request failed: " + response.statusCode() + " " + response.body());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = JSON.readValue(response.body(), Map.class);
        return (String) tokenResponse.get("access_token");
    }

    private static String paymentJson(String endToEndId) {
        return """
                {"endToEndId":"%s","amount":10.00,"currency":"EUR",
                 "debtorIban":"DE89370400440532013000","creditorIban":"FR7630006000011234567890189"}
                """.formatted(endToEndId);
    }

    private static UUID paymentTenantId(String endToEndId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT tenant_id FROM payment.payments WHERE end_to_end_id = ?")) {
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
                WHERE p.end_to_end_id = ?
                """)) {
            statement.setString(1, endToEndId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static String paymentStatus(String endToEndId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM payment.payments WHERE end_to_end_id = ?")) {
            statement.setString(1, endToEndId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static boolean paymentExists(String endToEndId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.payments WHERE end_to_end_id = ?")) {
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
                WHERE p.end_to_end_id = ?
                """)) {
            statement.setString(1, endToEndId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) > 0;
            }
        }
    }
}
