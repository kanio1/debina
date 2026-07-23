package main

import (
	"context"

	"dagger/debina-verification/internal/dagger"
	"dagger/debina-verification/pure"
)

const (
	backendServiceAlias          = "backend"
	frontendServiceAlias         = "frontend"
	keycloakServiceAlias         = "keycloak"
	keycloakPostgresServiceAlias = "keycloak-postgres"
	flywayCompletionPath         = "/tmp/d3a-flyway-complete"
	credentialCompletionPath     = "/tmp/d3a-sepa-app-credential-complete"
)

// Smoke runs the ADR-N16 D3A login/session/health vertical slice.
// +check
func (m *DebinaVerification) Smoke(ctx context.Context) error {
	_, err := m.SmokeLoginSessionHealth(ctx)
	return err
}

// SmokeLoginSessionHealth returns the finite Chromium execution for the
// D3A Keycloak authorization-code/PKCE, BFF session and authenticated-shell proof.
func (m *DebinaVerification) SmokeLoginSessionHealth(ctx context.Context) (string, error) {
	return m.d3aSmokeRunner("pnpm run test:smoke:d3a").Stdout(ctx)
}

// SmokePostgresReadiness proves the ephemeral database and repository Flyway
// migrations before any application process is started.
func (m *DebinaVerification) SmokePostgresReadiness(ctx context.Context) (string, error) {
	credentials := newPhaseDCredentials()
	service := m.postgresService("smoke-postgres-readiness", credentials)
	return m.smokeMigrations(service, credentials).Stdout(ctx)
}

func (m *DebinaVerification) smokeMigrations(service *dagger.Service, credentials phaseDCredentials) *dagger.Container {
	// The marker must be written by the same finite process that performs the
	// migrations. A separate WithNewFile can be evaluated without executing its
	// predecessor, which would allow a dependent service to start before V1
	// creates the application roles.
	command := "set -eu; " + flywayCommand("", "flyway:migrate", "flyway:validate") + " && printf '%s\\n' flyway-migrate-and-validate-complete > " + flywayCompletionPath
	return m.flywayClient(service).
		WithSecretVariable("FLYWAY_PASSWORD", credentials.migrationPassword).
		WithExec([]string{"sh", "-ec", command})
}

func (m *DebinaVerification) smokeMigrationMarker(service *dagger.Service, credentials phaseDCredentials) *dagger.File {
	return m.smokeMigrations(service, credentials).File(flywayCompletionPath)
}

func (m *DebinaVerification) smokeAppCredentialContract(postgres *dagger.Service, migrationMarker *dagger.File, password *dagger.Secret) *dagger.Container {
	return postgresClient(postgres).
		WithFile("/tmp/d3a-flyway-complete", migrationMarker).
		WithEnvVariable("PGUSER", "sepa_app").
		WithSecretVariable("PGPASSWORD", password).
		WithExec([]string{"sh", "-ec", "set -eu; identity=$(psql -v ON_ERROR_STOP=1 -Atqc 'SELECT current_user || chr(124) || current_database()'); test \"$identity\" = 'sepa_app|sepa_nexus'; printf '%s\\n' 'D3A sepa_app credential contract verified' | tee " + credentialCompletionPath})
}

func (m *DebinaVerification) smokeAppCredentialMarker(postgres *dagger.Service, migrationMarker *dagger.File, password *dagger.Secret) *dagger.File {
	return m.smokeAppCredentialContract(postgres, migrationMarker, password).File(credentialCompletionPath)
}

// SmokeKeycloakReadiness is a short-lived client of the imported Keycloak
// service. Call its stdout from the CLI; the server itself is never awaited.
func (m *DebinaVerification) SmokeKeycloakReadiness() *dagger.Container {
	credentials := newPhaseDCredentials()
	overlay := m.d3aRealmOverlayArtifacts()
	return m.keycloakReadiness(m.keycloakServiceWithOverlay(overlay, credentials), overlay.File("verified.marker"))
}

func (m *DebinaVerification) keycloakReadiness(service *dagger.Service, marker *dagger.File) *dagger.Container {
	return dag.Container().From("curlimages/curl:8.16.0").
		WithServiceBinding(keycloakServiceAlias, service).
		WithFile("/tmp/verified.marker", marker).
		WithExec([]string{"sh", "-ec", "test \"$(cat /tmp/verified.marker)\" = \"" + pure.OverlaySuccessMarker + "\"; cat /tmp/verified.marker; " + boundedReadinessCommand("http://keycloak:8080/realms/sepa-nexus/.well-known/openid-configuration", "Keycloak")})
}

func boundedReadinessCommand(url, name string) string {
	return "set -eu; attempts=0; until curl --fail --silent --show-error --max-time 5 " + url + " >/dev/null; do attempts=$((attempts + 1)); if [ \"$attempts\" -ge 60 ]; then echo \"" + name + " readiness timed out: " + url + "\" >&2; exit 1; fi; sleep 1; done; echo \"" + name + " ready via " + url + "\""
}

func (m *DebinaVerification) keycloakPostgresService(credentials phaseDCredentials) *dagger.Service {
	return dag.Container().
		From(postgresImage).
		WithEnvVariable("POSTGRES_DB", "keycloak").
		WithEnvVariable("POSTGRES_USER", "keycloak").
		WithSecretVariable("POSTGRES_PASSWORD", credentials.keycloakDBPassword).
		WithExposedPort(5432).
		AsService()
}

func (m *DebinaVerification) keycloakService() *dagger.Service {
	credentials := newPhaseDCredentials()
	return m.keycloakServiceWithOverlay(m.d3aRealmOverlayArtifacts(), credentials)
}

func (m *DebinaVerification) keycloakServiceWithOverlay(overlay *dagger.Directory, credentials phaseDCredentials) *dagger.Service {
	return dag.Container().
		From(keycloakImage).
		// The pinned image inherits 9000/tcp and 8443/tcp. start-dev in this
		// smoke runtime listens only on HTTP 8080, so those inherited health
		// checks would prevent a bound diagnostic client from running.
		WithoutExposedPort(9000).
		WithoutExposedPort(8443).
		WithServiceBinding(keycloakPostgresServiceAlias, m.keycloakPostgresService(credentials)).
		WithFile("/opt/keycloak/data/import/realm-export.json", overlay.File("realm-export.json"), dagger.ContainerWithFileOpts{Owner: "1000:1000", Permissions: 0640}).
		WithEnvVariable("KC_BOOTSTRAP_ADMIN_USERNAME", "admin").
		WithSecretVariable("KC_BOOTSTRAP_ADMIN_PASSWORD", credentials.keycloakAdminPassword).
		WithEnvVariable("KC_DB", "postgres").
		WithEnvVariable("KC_DB_URL_HOST", keycloakPostgresServiceAlias).
		WithEnvVariable("KC_DB_URL_DATABASE", "keycloak").
		WithEnvVariable("KC_DB_USERNAME", "keycloak").
		WithSecretVariable("KC_DB_PASSWORD", credentials.keycloakDBPassword).
		WithExposedPort(8080, dagger.ContainerWithExposedPortOpts{Description: "Keycloak HTTP for D3A smoke"}).
		AsService(dagger.ContainerAsServiceOpts{
			Args:          []string{"start-dev", "--import-realm"},
			UseEntrypoint: true,
		})
}

// d3aRealmOverlay derives a local-only import artifact. It never changes the
// canonical realm source and fails closed if the expected client shape drifts.
func (m *DebinaVerification) d3aRealmOverlayArtifacts() *dagger.Directory {
	return dag.Container().
		From(goImage).
		WithDirectory("/src", m.source().Directory("dagger")).
		WithFile("/input/realm-export.json", m.source().File("infra/keycloak/realm-export.json")).
		WithWorkdir("/src").
		WithExec([]string{"go", "run", "./cmd/realm-overlay", "--input", "/input/realm-export.json", "--output", "/tmp/d3a-realm-overlay/realm-export.json", "--marker", "/tmp/d3a-realm-overlay/verified.marker", "--callback", pure.D3AFrontendCallback, "--origin", pure.D3AFrontendOrigin}).
		Directory("/tmp/d3a-realm-overlay")
}

func (m *DebinaVerification) smokeBackendService(postgres, kafka, keycloak *dagger.Service, migrationMarker *dagger.File, credentials phaseDCredentials) *dagger.Service {
	credentialMarker := m.smokeAppCredentialMarker(postgres, migrationMarker, credentials.appPassword)
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25")).
		WithDirectory("/workspace", m.source()).
		WithFile("/workspace/.d3a-flyway-complete", migrationMarker).
		WithFile("/workspace/.d3a-sepa-app-credential-complete", credentialMarker).
		WithWorkdir("/workspace").
		WithServiceBinding(postgresServiceAlias, postgres).
		WithServiceBinding(kafkaServiceAlias, kafka).
		WithServiceBinding(keycloakServiceAlias, keycloak).
		WithEnvVariable("SEPA_APP_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_APP_DB_USER", "sepa_app").
		WithSecretVariable("SEPA_APP_DB_PASSWORD", credentials.appPassword).
		WithEnvVariable("SEPA_MIGRATION_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("OUTBOX_RELAY_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_SIGNATURE_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_LEDGER_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_SETTLEMENT_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("GROSS_INSTANT_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("APPROVAL_EXPIRY_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("AUDIT_AUDITOR_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092").
		WithEnvVariable("KEYCLOAK_ISSUER_URI", "http://keycloak:8080/realms/sepa-nexus").
		WithEnvVariable("SEPA_SCHEDULING_ENABLED", "false").
		WithExposedPort(8081).
		AsService(dagger.ContainerAsServiceOpts{Args: []string{"./mvnw", "-f", "backend", "spring-boot:run"}})
}

// SmokeBackendReadiness uses the real application health endpoint from an
// alias-bound, short-lived client. Kafka is included because Spring Kafka is
// configured during application startup.
func (m *DebinaVerification) SmokeBackendReadiness() *dagger.Container {
	credentials := newPhaseDCredentials()
	postgres := m.postgresService("smoke-backend-readiness", credentials)
	keycloak := m.keycloakServiceWithOverlay(m.d3aRealmOverlayArtifacts(), credentials)
	migrationMarker := m.smokeMigrationMarker(postgres, credentials)
	backend := m.smokeBackendService(postgres, m.kafkaService(), keycloak, migrationMarker, credentials)
	return dag.Container().From("curlimages/curl:8.16.0").
		WithServiceBinding(backendServiceAlias, backend).
		WithExec([]string{"sh", "-ec", boundedReadinessCommand("http://backend:8081/actuator/health", "Backend")})
}

// SmokeBackendCredentialReadiness proves the post-Flyway application-role
// login that the backend will use. It is finite and never starts Spring.
func (m *DebinaVerification) SmokeBackendCredentialReadiness(ctx context.Context) (string, error) {
	credentials := newPhaseDCredentials()
	postgres := m.postgresService("smoke-backend-credential-readiness", credentials)
	return m.smokeAppCredentialContract(postgres, m.smokeMigrationMarker(postgres, credentials), credentials.appPassword).Stdout(ctx)
}

func (m *DebinaVerification) smokeFrontendService(backend, keycloak *dagger.Service, credentials phaseDCredentials) *dagger.Service {
	return dag.Container().
		From(nodeImage).
		WithMountedCache("/pnpm/store", dag.CacheVolume("debina-pnpm-node24.18.0-pnpm10.33.0")).
		WithEnvVariable("PNPM_HOME", "/pnpm").
		WithEnvVariable("PNPM_STORE_DIR", "/pnpm/store").
		WithDirectory("/workspace", m.source()).
		WithWorkdir("/workspace/frontend").
		WithServiceBinding(backendServiceAlias, backend).
		WithServiceBinding(keycloakServiceAlias, keycloak).
		WithEnvVariable("KEYCLOAK_ISSUER", "http://keycloak:8080/realms/sepa-nexus").
		WithEnvVariable("KEYCLOAK_CLIENT_ID", "sepa-web").
		WithSecretVariable("KEYCLOAK_CLIENT_SECRET", credentials.webClientSecret).
		WithEnvVariable("BFF_BASE_URL", "http://frontend:3000").
		WithEnvVariable("BACKEND_API_BASE_URL", "http://backend:8081").
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"}).
		WithExec([]string{"pnpm", "run", "build"}).
		WithExposedPort(3000).
		AsService(dagger.ContainerAsServiceOpts{Args: []string{"pnpm", "exec", "next", "start", "--hostname", "0.0.0.0", "--port", "3000"}})
}

// SmokeFrontendReadiness builds the pinned production frontend and probes its
// public shell through the Dagger service alias. It does not start a browser.
func (m *DebinaVerification) SmokeFrontendReadiness() *dagger.Container {
	credentials := newPhaseDCredentials()
	postgres := m.postgresService("smoke-frontend-readiness", credentials)
	keycloak := m.keycloakServiceWithOverlay(m.d3aRealmOverlayArtifacts(), credentials)
	backend := m.smokeBackendService(postgres, m.kafkaService(), keycloak, m.smokeMigrationMarker(postgres, credentials), credentials)
	frontend := m.smokeFrontendService(backend, keycloak, credentials)
	return dag.Container().From("curlimages/curl:8.16.0").
		WithServiceBinding(frontendServiceAlias, frontend).
		WithExec([]string{"sh", "-ec", frontendReadinessCommand})
}

const frontendReadinessCommand = `set -eu
attempts=0
until [ "$attempts" -ge 60 ]; do
  attempts=$((attempts + 1))
  status="$(curl --silent --show-error --max-time 5 --output /tmp/frontend-body --dump-header /tmp/frontend-headers --write-out '%{http_code}' http://frontend:3000/ || true)"
  location="$(tr -d '\r' < /tmp/frontend-headers 2>/dev/null | awk 'tolower($1) == "location:" { print $2; exit }')"
  if [ "$status" = 307 ] && [ "$location" = "/payments" ]; then
    echo "Frontend ready: HTTP 307 Location /payments via frontend:3000/"
    exit 0
  fi
  sleep 1
done
echo "Frontend readiness timed out: alias=frontend port=3000 path=/ status=$status location=$location" >&2
exit 1`

func (m *DebinaVerification) d3aSmokeRunner(browserCommand string) *dagger.Container {
	credentials := newPhaseDCredentials()
	postgres := m.postgresService("smoke", credentials)
	kafka := m.kafkaService()
	keycloak := m.keycloakServiceWithOverlay(m.d3aRealmOverlayArtifacts(), credentials)
	backend := m.smokeBackendService(postgres, kafka, keycloak, m.smokeMigrationMarker(postgres, credentials), credentials)
	frontend := m.smokeFrontendService(backend, keycloak, credentials)
	return dag.Container().
		From(playwrightImage).
		WithMountedCache("/pnpm/store", dag.CacheVolume("debina-pnpm-node24.18.0-pnpm10.33.0")).
		WithEnvVariable("PNPM_HOME", "/pnpm").
		WithEnvVariable("PNPM_STORE_DIR", "/pnpm/store").
		WithDirectory("/workspace", m.source()).
		WithWorkdir("/workspace/frontend").
		WithServiceBinding(backendServiceAlias, backend).
		WithServiceBinding(keycloakServiceAlias, keycloak).
		WithServiceBinding(frontendServiceAlias, frontend).
		WithEnvVariable("SMOKE_BASE_URL", "http://frontend:3000").
		WithSecretVariable("SMOKE_SUBMITTER_USERNAME", credentials.submitterUsername).
		WithSecretVariable("SMOKE_SUBMITTER_PASSWORD", credentials.submitterPassword).
		WithEnvVariable("PLAYWRIGHT_BROWSERS_PATH", "/ms-playwright").
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"}).
		WithExec([]string{"sh", "-ec", pure.D3AReadinessCommand([]string{
			"http://keycloak:8080/realms/sepa-nexus/.well-known/openid-configuration",
			"http://backend:8081/actuator/health",
			"http://frontend:3000",
		}, browserCommand)})
}
