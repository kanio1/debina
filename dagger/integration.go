package main

import (
	"strings"

	"dagger/debina-verification/internal/dagger"
)

const (
	postgresServiceAlias             = "postgres"
	kafkaServiceAlias                = "kafka"
	databaseReadinessCompletionPath  = "/tmp/database-readiness-complete"
	databaseMigrationCompletionPath  = "/tmp/database-migration-complete"
	databaseCredentialCompletionPath = "/tmp/database-credential-complete"
)

// postgresService is intentionally ephemeral and has no host port or volume.
func (m *DebinaVerification) postgresService(instance string, credentials phaseDCredentials) *dagger.Service {
	return dag.Container().
		From(postgresImage).
		WithEnvVariable("POSTGRES_DB", "sepa_nexus").
		WithEnvVariable("POSTGRES_USER", "sepa_migration").
		WithSecretVariable("POSTGRES_PASSWORD", credentials.migrationPassword).
		// Distinguish independently-created service instances in one Dagger session.
		WithLabel("dev.debina.phase-d.database-instance", instance).
		WithExposedPort(5432).
		AsService()
}

func postgresClient(service *dagger.Service) *dagger.Container {
	return dag.Container().
		From(postgresImage).
		WithServiceBinding(postgresServiceAlias, service).
		WithEnvVariable("PGHOST", postgresServiceAlias).
		WithEnvVariable("PGPORT", "5432").
		WithEnvVariable("PGDATABASE", "sepa_nexus")
}

func (m *DebinaVerification) flywayClient(service *dagger.Service) *dagger.Container {
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25"), sharedCache).
		WithDirectory("/workspace", m.backendWorkspace()).
		WithWorkdir("/workspace").
		WithServiceBinding(postgresServiceAlias, service)
}

func flywayCommand(target string, goals ...string) string {
	args := []string{
		"./mvnw", "-f", "backend",
		"-Dflyway.url=jdbc:postgresql://postgres:5432/sepa_nexus",
		"-Dflyway.user=sepa_migration",
		`-Dflyway.password="$FLYWAY_PASSWORD"`,
		"-Dflyway.locations=filesystem:backend/src/main/resources/db/migration",
	}
	if target != "" {
		args = append(args, "-Dflyway.target="+target)
	}
	return strings.Join(append(args, goals...), " ")
}

func (m *DebinaVerification) databaseUpgrade() *dagger.Container {
	credentials := newPhaseDCredentials()
	return m.flywayClient(m.postgresService("upgrade", credentials)).
		WithSecretVariable("FLYWAY_PASSWORD", credentials.migrationPassword).
		WithExec([]string{"sh", "-ec", flywayCommand("54", "flyway:migrate", "flyway:validate")}).
		WithExec([]string{"sh", "-ec", flywayCommand("", "flyway:migrate", "flyway:validate") + " && printf '%s\n' 'DATABASE-UPGRADE-CONTRACT-VERIFIED'"})
}

func (m *DebinaVerification) databaseContract() *dagger.Container {
	credentials := newPhaseDCredentials()
	service := m.postgresService("database-contract", credentials)
	readiness := postgresClient(service).
		WithEnvVariable("PGUSER", "sepa_migration").
		WithSecretVariable("PGPASSWORD", credentials.migrationPassword).
		WithExec([]string{"sh", "-ec", "pg_isready -t 30 && printf '%s\n' ready > " + databaseReadinessCompletionPath}).
		File(databaseReadinessCompletionPath)
	migrated := m.flywayClient(service).
		WithFile(databaseReadinessCompletionPath, readiness).
		WithSecretVariable("FLYWAY_PASSWORD", credentials.migrationPassword).
		WithExec([]string{"sh", "-ec", "test \"$(cat " + databaseReadinessCompletionPath + ")\" = ready && " + flywayCommand("", "flyway:migrate", "flyway:validate") + " && printf '%s\n' migrated > " + databaseMigrationCompletionPath}).
		File(databaseMigrationCompletionPath)
	credential := postgresClient(service).
		WithFile(databaseMigrationCompletionPath, migrated).
		WithEnvVariable("PGUSER", "sepa_app").
		WithSecretVariable("PGPASSWORD", credentials.appPassword).
		WithExec([]string{"sh", "-ec", "test \"$(cat " + databaseMigrationCompletionPath + ")\" = migrated; test \"$(psql -v ON_ERROR_STOP=1 -Atqc 'SELECT current_user')\" = sepa_app; printf '%s\n' credential > " + databaseCredentialCompletionPath}).
		File(databaseCredentialCompletionPath)
	return postgresClient(service).
		WithFile(databaseReadinessCompletionPath, readiness).
		WithFile(databaseMigrationCompletionPath, migrated).
		WithFile(databaseCredentialCompletionPath, credential).
		WithEnvVariable("PGUSER", "sepa_migration").
		WithSecretVariable("PGPASSWORD", credentials.migrationPassword).
		WithSecretVariable("SEPA_APP_PASSWORD", credentials.appPassword).
		WithExec([]string{"sh", "-ec", `
set -eu
test "$(cat ` + databaseReadinessCompletionPath + `)" = ready
test "$(cat ` + databaseMigrationCompletionPath + `)" = migrated
test "$(cat ` + databaseCredentialCompletionPath + `)" = credential
assert_true() {
  actual="$(psql -v ON_ERROR_STOP=1 -Atqc "$1")"
  test "$actual" = t
}
assert_true "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid = 'payment.payments'::regclass"
assert_true "SELECT NOT rolbypassrls AND rolcanlogin FROM pg_roles WHERE rolname = 'sepa_app'"
assert_true "SELECT has_table_privilege('sepa_app', 'payment.payments', 'SELECT,INSERT,UPDATE')"
assert_true "SELECT NOT has_table_privilege('sepa_app', 'ledger.journal_entries', 'INSERT')"
assert_true "SELECT has_table_privilege('outbox_dispatcher_role', 'payment.outbox_events', 'SELECT,UPDATE')"
assert_true "SELECT NOT has_table_privilege('outbox_dispatcher_role', 'payment.payments', 'INSERT')"
PGUSER=sepa_app PGPASSWORD="$SEPA_APP_PASSWORD" psql -v ON_ERROR_STOP=1 -Atqc "SELECT count(*) FROM payment.payments" | grep -x 0
printf '%s\n' 'DATABASE-CONTRACT-VERIFIED'
`})
}

func (m *DebinaVerification) kafkaService(instance string) *dagger.Service {
	return dag.Container().
		From(kafkaImage).
		WithLabel("dev.debina.phase-d.kafka-instance", instance).
		WithEnvVariable("KAFKA_PROCESS_ROLES", "broker,controller").
		WithEnvVariable("KAFKA_NODE_ID", "1").
		WithEnvVariable("KAFKA_LISTENERS", "PLAINTEXT://:9092,CONTROLLER://:9093").
		WithEnvVariable("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:9092").
		WithEnvVariable("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093").
		WithEnvVariable("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER").
		WithEnvVariable("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1").
		WithEnvVariable("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1").
		WithEnvVariable("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1").
		WithExposedPort(9092).
		AsService()
}

func (m *DebinaVerification) kafkaContract() *dagger.Container {
	service := m.kafkaService("kafka-contract")
	return dag.Container().
		From(kafkaImage).
		WithServiceBinding(kafkaServiceAlias, service).
		WithExec([]string{"sh", "-ec", `
set -eu
topic=debina.phase-d.non-production-probe
/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic "$topic" --partitions 1 --replication-factor 1
printf 'phase-d-probe\n' | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic "$topic"
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic "$topic" --from-beginning --max-messages 1 --timeout-ms 30000 | grep -x phase-d-probe
printf '%s\n' 'KAFKA-CONTRACT-VERIFIED'
`})
}
