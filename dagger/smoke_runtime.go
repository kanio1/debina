package main

import "dagger/debina-verification/internal/dagger"

// paymentSmokeRuntime owns one complete, fresh, socket-free runtime graph for
// one D3B journey. Every consumer below is bound to these exact service values;
// it never publishes host ports or retains mutable service state in a cache.
type paymentSmokeRuntime struct {
	postgres        *dagger.Service
	kafka           *dagger.Service
	keycloak        *dagger.Service
	backend         *dagger.Service
	frontend        *dagger.Service
	migrationMarker *dagger.File
	credentials     phaseDCredentials
}

func (m *DebinaVerification) paymentSmokeRuntime(instance string) *paymentSmokeRuntime {
	credentials := newPhaseDCredentials()
	postgres := m.postgresService("payment-"+instance, credentials)
	kafka := m.kafkaService("payment-" + instance)
	overlay := m.d3aRealmOverlayArtifacts()
	keycloak := m.keycloakServiceWithOverlay("payment-"+instance, overlay, credentials)
	migrationMarker := m.smokeMigrationMarker(postgres, credentials)
	backend := m.paymentSmokeBackendService(postgres, kafka, keycloak, migrationMarker, credentials)
	frontend := m.smokeFrontendService(backend, keycloak, credentials)
	return &paymentSmokeRuntime{
		postgres:        postgres,
		kafka:           kafka,
		keycloak:        keycloak,
		backend:         backend,
		frontend:        frontend,
		migrationMarker: migrationMarker,
		credentials:     credentials,
	}
}

// paymentSmokeBackendService differs from D3A only in the already-supported
// D3B relay configuration. It provides every configured datasource credential
// as a Dagger secret rather than falling back to an ordinary environment value.
func (m *DebinaVerification) paymentSmokeBackendService(postgres, kafka, keycloak *dagger.Service, migrationMarker *dagger.File, credentials phaseDCredentials) *dagger.Service {
	credentialMarker := m.smokeAppCredentialMarker(postgres, migrationMarker, credentials.appPassword)
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25"), sharedCache).
		WithDirectory("/workspace", m.backendWorkspace()).
		WithFile("/workspace/.phase-d-flyway-complete", migrationMarker).
		WithFile("/workspace/.phase-d-sepa-app-credential-complete", credentialMarker).
		WithWorkdir("/workspace").
		WithServiceBinding(postgresServiceAlias, postgres).
		WithServiceBinding(kafkaServiceAlias, kafka).
		WithServiceBinding(keycloakServiceAlias, keycloak).
		WithEnvVariable("SEPA_APP_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_APP_DB_USER", "sepa_app").
		WithSecretVariable("SEPA_APP_DB_PASSWORD", credentials.appPassword).
		WithEnvVariable("SEPA_MIGRATION_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_MIGRATION_DB_USER", "sepa_migration").
		WithSecretVariable("SEPA_MIGRATION_DB_PASSWORD", credentials.migrationPassword).
		WithEnvVariable("OUTBOX_RELAY_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("OUTBOX_RELAY_DB_USER", "outbox_dispatcher_role").
		WithSecretVariable("OUTBOX_RELAY_DB_PASSWORD", credentials.outboxPassword).
		WithEnvVariable("SEPA_SIGNATURE_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_SIGNATURE_DB_USER", "signature_role").
		WithSecretVariable("SEPA_SIGNATURE_DB_PASSWORD", credentials.signaturePassword).
		WithEnvVariable("SEPA_LEDGER_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_LEDGER_DB_USER", "ledger_role").
		WithSecretVariable("SEPA_LEDGER_DB_PASSWORD", credentials.ledgerPassword).
		WithEnvVariable("SEPA_SETTLEMENT_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("SEPA_SETTLEMENT_DB_USER", "settlement_role").
		WithSecretVariable("SEPA_SETTLEMENT_DB_PASSWORD", credentials.settlementPassword).
		WithEnvVariable("GROSS_INSTANT_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("GROSS_INSTANT_DB_USER", "gross_instant_executor_role").
		WithSecretVariable("GROSS_INSTANT_DB_PASSWORD", credentials.grossInstantPassword).
		WithEnvVariable("APPROVAL_EXPIRY_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("APPROVAL_EXPIRY_DB_USER", "approval_expiry_role").
		WithSecretVariable("APPROVAL_EXPIRY_DB_PASSWORD", credentials.approvalExpiryPassword).
		WithEnvVariable("AUDIT_AUDITOR_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("AUDIT_AUDITOR_DB_USER", "audit_auditor_role").
		WithSecretVariable("AUDIT_AUDITOR_DB_PASSWORD", credentials.auditAuditorPassword).
		WithEnvVariable("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092").
		WithEnvVariable("KEYCLOAK_ISSUER_URI", "http://keycloak:8080/realms/sepa-nexus").
		WithEnvVariable("SEPA_SCHEDULING_ENABLED", "true").
		WithEnvVariable("SEPA_SCHEDULING_RELAY_FIXED_DELAY_MS", "250").
		WithExposedPort(8081).
		AsService(dagger.ContainerAsServiceOpts{Args: []string{"./mvnw", "-f", "backend", "spring-boot:run"}})
}
