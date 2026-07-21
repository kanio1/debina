package com.sepanexus.security;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real Keycloak 26.6.4 import boundary used by EPIC-74 realm-as-code proofs. */
final class KeycloakRealmTestSupport {

    static final String REALM = "sepa-nexus";
    static final ObjectMapper JSON = new ObjectMapper();

    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:26.6.4"))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "realm-test-admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "realm-test-admin-secret")
            .withCopyFileToContainer(MountableFile.forHostPath(
                    Path.of("..", "infra", "keycloak", "realm-export.json").toAbsolutePath()),
                    "/opt/keycloak/data/import/realm-export.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private KeycloakRealmTestSupport() {}

    static synchronized void start() {
        if (!KEYCLOAK.isRunning()) {
            KEYCLOAK.start();
        }
    }

    static JsonNode adminGet(String path) throws Exception {
        return JSON.readTree(send(HttpRequest.newBuilder(adminUri(path))
                .header("Authorization", "Bearer " + adminToken())
                .GET()
                .build()).body());
    }

    static void adminPut(String path, JsonNode body) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(adminUri(path))
                .header("Authorization", "Bearer " + adminToken())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build());
        if (response.statusCode() != 204) {
            throw new AssertionError("Keycloak admin PUT failed: " + response.statusCode() + " " + response.body());
        }
    }

    static void createDirectGrantProbeClient() throws Exception {
        var client = JSON.createObjectNode();
        client.put("clientId", "realm-runtime-probe");
        client.put("enabled", true);
        client.put("protocol", "openid-connect");
        client.put("publicClient", true);
        client.put("directAccessGrantsEnabled", true);
        client.put("standardFlowEnabled", false);
        HttpResponse<String> create = send(HttpRequest.newBuilder(adminUri("clients"))
                .header("Authorization", "Bearer " + adminToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(client)))
                .build());
        if (create.statusCode() != 201) {
            throw new AssertionError("Keycloak probe-client creation failed: " + create.statusCode() + " " + create.body());
        }

        String clientId = adminGet("clients?clientId=realm-runtime-probe").get(0).path("id").asText();
        String scopeId = adminGet("client-scopes").findValues("name").isEmpty()
                ? null : adminGet("client-scopes").findParents("name").stream()
                .filter(scope -> "sepa-guc".equals(scope.path("name").asText()))
                .findFirst().orElseThrow().path("id").asText();
        adminPut("clients/" + clientId + "/default-client-scopes/" + scopeId, JSON.nullNode());
    }

    static JsonNode passwordGrantClaims(String username, String password) throws Exception {
        String form = "grant_type=password&client_id=realm-runtime-probe&username=" + encode(username)
                + "&password=" + encode(password) + "&scope=openid";
        HttpResponse<String> response = send(HttpRequest.newBuilder(realmUri("protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());
        if (response.statusCode() != 200) {
            throw new AssertionError("Keycloak password grant failed: " + response.statusCode() + " " + response.body());
        }
        String token = JSON.readTree(response.body()).path("access_token").asText();
        return JSON.readTree(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8));
    }

    private static String adminToken() throws Exception {
        String form = "grant_type=password&client_id=admin-cli&username=realm-test-admin&password=realm-test-admin-secret";
        HttpResponse<String> response = send(HttpRequest.newBuilder(baseUri("realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());
        if (response.statusCode() != 200) {
            throw new AssertionError("Keycloak admin token failed: " + response.statusCode() + " " + response.body());
        }
        return JSON.readTree(response.body()).path("access_token").asText();
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static URI adminUri(String path) { return baseUri("admin/realms/" + REALM + "/" + path); }

    private static URI realmUri(String path) { return baseUri("realms/" + REALM + "/" + path); }

    private static URI baseUri(String path) {
        return URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/" + path);
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
