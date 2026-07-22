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
)

// Smoke runs only the ADR-N16 D3A vertical slice: runtime health, Keycloak
// authorization-code/PKCE login, and BFF session exposure.
// +check
func (m *DebinaVerification) Smoke(ctx context.Context) error {
	_, err := m.d3aSmokeRunner().Sync(ctx)
	return err
}

// SmokePostgresReadiness proves the ephemeral database and repository Flyway
// migrations before any application process is started.
func (m *DebinaVerification) SmokePostgresReadiness(ctx context.Context) (string, error) {
	service := m.postgresService("smoke-postgres-readiness")
	return m.smokeMigrations(service).Stdout(ctx)
}

func (m *DebinaVerification) smokeMigrations(service *dagger.Service) *dagger.Container {
	args := append(flywayArguments(""), "flyway:migrate", "flyway:validate")
	return m.flywayClient(service).
		WithExec(args).
		WithNewFile("/tmp/d3a-flyway-complete", "flyway-migrate-and-validate-complete\n")
}

func (m *DebinaVerification) smokeMigrationMarker(service *dagger.Service) *dagger.File {
	return m.smokeMigrations(service).File("/tmp/d3a-flyway-complete")
}

// SmokeKeycloakReadiness is a short-lived client of the imported Keycloak
// service. Call its stdout from the CLI; the server itself is never awaited.
func (m *DebinaVerification) SmokeKeycloakReadiness() *dagger.Container {
	return m.keycloakReadiness(m.keycloakService())
}

func (m *DebinaVerification) keycloakReadiness(service *dagger.Service) *dagger.Container {
	return dag.Container().From("curlimages/curl:8.16.0").
		WithServiceBinding(keycloakServiceAlias, service).
		WithExec([]string{"sh", "-ec", boundedReadinessCommand("http://keycloak:8080/realms/sepa-nexus/.well-known/openid-configuration", "Keycloak")})
}

func boundedReadinessCommand(url, name string) string {
	return "set -eu; attempts=0; until curl --fail --silent --show-error --max-time 5 " + url + " >/dev/null; do attempts=$((attempts + 1)); if [ \"$attempts\" -ge 60 ]; then echo \"" + name + " readiness timed out: " + url + "\" >&2; exit 1; fi; sleep 1; done; echo \"" + name + " ready via " + url + "\""
}

func (m *DebinaVerification) keycloakPostgresService() *dagger.Service {
	return dag.Container().
		From(postgresImage).
		WithEnvVariable("POSTGRES_DB", "keycloak").
		WithEnvVariable("POSTGRES_USER", "keycloak").
		WithSecretVariable("POSTGRES_PASSWORD", dag.SetSecret("d3a-keycloak-db-password", "dev-only-keycloak-db")).
		WithExposedPort(5432).
		AsService()
}

func (m *DebinaVerification) keycloakService() *dagger.Service {
	return dag.Container().
		From(keycloakImage).
		// The pinned image inherits 9000/tcp and 8443/tcp. start-dev in this
		// smoke runtime listens only on HTTP 8080, so those inherited health
		// checks would prevent a bound diagnostic client from running.
		WithoutExposedPort(9000).
		WithoutExposedPort(8443).
		WithServiceBinding(keycloakPostgresServiceAlias, m.keycloakPostgresService()).
		WithFile("/opt/keycloak/data/import/realm-export.json", m.source().File("infra/keycloak/realm-export.json")).
		WithEnvVariable("KC_BOOTSTRAP_ADMIN_USERNAME", "admin").
		WithSecretVariable("KC_BOOTSTRAP_ADMIN_PASSWORD", dag.SetSecret("d3a-keycloak-admin-password", "dev-only-admin")).
		WithEnvVariable("KC_DB", "postgres").
		WithEnvVariable("KC_DB_URL_HOST", keycloakPostgresServiceAlias).
		WithEnvVariable("KC_DB_URL_DATABASE", "keycloak").
		WithEnvVariable("KC_DB_USERNAME", "keycloak").
		WithSecretVariable("KC_DB_PASSWORD", dag.SetSecret("d3a-keycloak-db-password", "dev-only-keycloak-db")).
		WithExposedPort(8080, dagger.ContainerWithExposedPortOpts{Description: "Keycloak HTTP for D3A smoke"}).
		AsService(dagger.ContainerAsServiceOpts{
			Args:          []string{"start-dev", "--import-realm"},
			UseEntrypoint: true,
		})
}

func (m *DebinaVerification) smokeBackendService(postgres, kafka, keycloak *dagger.Service, migrationMarker *dagger.File) *dagger.Service {
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25")).
		WithDirectory("/workspace", m.source()).
		WithFile("/workspace/.d3a-flyway-complete", migrationMarker).
		WithWorkdir("/workspace").
		WithServiceBinding(postgresServiceAlias, postgres).
		WithServiceBinding(kafkaServiceAlias, kafka).
		WithServiceBinding(keycloakServiceAlias, keycloak).
		WithEnvVariable("SEPA_APP_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
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
	postgres := m.postgresService("smoke-backend-readiness")
	keycloak := m.keycloakService()
	backend := m.smokeBackendService(postgres, m.kafkaService(), keycloak, m.smokeMigrationMarker(postgres))
	return dag.Container().From("curlimages/curl:8.16.0").
		WithServiceBinding(backendServiceAlias, backend).
		WithExec([]string{"sh", "-ec", boundedReadinessCommand("http://backend:8081/actuator/health", "Backend")})
}

func (m *DebinaVerification) smokeFrontendService(backend, keycloak *dagger.Service) *dagger.Service {
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
		WithSecretVariable("KEYCLOAK_CLIENT_SECRET", dag.SetSecret("d3a-keycloak-client-secret", "dev-only-sepa-web-secret")).
		WithEnvVariable("BFF_BASE_URL", "http://localhost:3000").
		WithEnvVariable("BACKEND_API_BASE_URL", "http://backend:8081").
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"}).
		WithExec([]string{"pnpm", "run", "build"}).
		WithExec([]string{"pnpm", "run", "start", "--", "--hostname", "0.0.0.0"}).
		WithExposedPort(3000).
		AsService()
}

func (m *DebinaVerification) d3aSmokeRunner() *dagger.Container {
	postgres := m.postgresService("smoke")
	kafka := m.kafkaService()
	keycloak := m.keycloakService()
	backend := m.smokeBackendService(postgres, kafka, keycloak, m.smokeMigrationMarker(postgres))
	frontend := m.smokeFrontendService(backend, keycloak)
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
		WithEnvVariable("SMOKE_BASE_URL", "http://localhost:3000").
		WithEnvVariable("SMOKE_BACKEND_HEALTH_URL", "http://backend:8081/actuator/health").
		WithSecretVariable("SMOKE_SUBMITTER_PASSWORD", dag.SetSecret("d3a-submitter-password", "dev-only-submitter")).
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"}).
		WithExec([]string{"sh", "-ec", pure.D3AReadinessCommand([]string{
			"http://keycloak:8080/realms/sepa-nexus/.well-known/openid-configuration",
			"http://backend:8081/actuator/health",
			"http://frontend:3000",
		})})
}
