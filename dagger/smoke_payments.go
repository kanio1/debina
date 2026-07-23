package main

import (
	"context"

	"dagger/debina-verification/internal/dagger"
	"dagger/debina-verification/pure"
)

// SmokeJsonDirectSubmission proves the existing JSON_DIRECT browser-to-BFF-to-
// backend path and its durable PostgreSQL/Kafka evidence using a fresh runtime.
// It is intentionally not a +check until all D3B leaves are implemented.
func (m *DebinaVerification) SmokeJsonDirectSubmission(ctx context.Context) (string, error) {
	runtime := m.paymentSmokeRuntime("json-direct")
	browser := m.paymentPlaywright(runtime, "pnpm exec playwright test e2e/d3b-json-direct-submission.spec.ts --project=chromium --workers=1")
	paymentID := browser.File(d3BPaymentIDPath)
	postgresEvidence := m.jsonDirectPostgresEvidence(runtime, paymentID)
	return m.jsonDirectKafkaEvidence(runtime, paymentID, postgresEvidence).Stdout(ctx)
}

// SmokeMakerCheckerApproval proves the existing approval project-policy with
// independent maker and checker browser contexts and a scoped reference-data fixture.
func (m *DebinaVerification) SmokeMakerCheckerApproval(ctx context.Context) (string, error) {
	runtime := m.paymentSmokeRuntime("maker-checker")
	approvalRule := m.seedApprovalRule(runtime)
	browser := m.paymentPlaywright(
		runtime,
		"test \"$(cat /tmp/phase-d-approval-rule)\" = 'PHASE-D approval matrix fixture verified' && pnpm exec playwright test e2e/d3b-maker-checker-approval.spec.ts --project=chromium --workers=1",
		paymentPlaywrightInput{path: d3BApprovalRulePath, file: approvalRule},
	)
	paymentID := browser.File(d3BPaymentIDPath)
	postgresEvidence := m.makerCheckerPostgresEvidence(runtime, paymentID)
	return m.makerCheckerKafkaEvidence(runtime, paymentID, postgresEvidence).Stdout(ctx)
}

// SmokePaymentDetailLineage proves the implemented payment detail identifiers,
// status and timeline, then correlates the same payment in source-owned stores.
func (m *DebinaVerification) SmokePaymentDetailLineage(ctx context.Context) (string, error) {
	runtime := m.paymentSmokeRuntime("detail-lineage")
	browser := m.paymentPlaywright(runtime, "pnpm exec playwright test e2e/d3b-payment-detail-lineage.spec.ts --project=chromium --workers=1")
	paymentID := browser.File(d3BPaymentIDPath)
	postgresEvidence := m.paymentDetailPostgresEvidence(runtime, paymentID)
	return m.paymentDetailKafkaEvidence(runtime, paymentID, postgresEvidence).Stdout(ctx)
}

type paymentPlaywrightInput struct {
	path string
	file *dagger.File
}

func (m *DebinaVerification) paymentPlaywright(runtime *paymentSmokeRuntime, command string, inputs ...paymentPlaywrightInput) *dagger.Container {
	browser := dag.Container().
		From(playwrightImage).
		WithMountedCache("/pnpm/store", dag.CacheVolume("debina-pnpm-node24.18.0-pnpm10.33.0")).
		WithEnvVariable("PNPM_HOME", "/pnpm").
		WithEnvVariable("PNPM_STORE_DIR", "/pnpm/store").
		WithDirectory("/workspace", m.source()).
		WithWorkdir("/workspace/frontend").
		WithServiceBinding(backendServiceAlias, runtime.backend).
		WithServiceBinding(keycloakServiceAlias, runtime.keycloak).
		WithServiceBinding(frontendServiceAlias, runtime.frontend).
		WithEnvVariable("SMOKE_BASE_URL", "http://frontend:3000").
		WithSecretVariable("SMOKE_SUBMITTER_USERNAME", runtime.credentials.submitterUsername).
		WithSecretVariable("SMOKE_SUBMITTER_PASSWORD", runtime.credentials.submitterPassword).
		WithSecretVariable("SMOKE_APPROVER_USERNAME", runtime.credentials.approverUsername).
		WithSecretVariable("SMOKE_APPROVER_PASSWORD", runtime.credentials.approverPassword).
		WithEnvVariable("PLAYWRIGHT_BROWSERS_PATH", "/ms-playwright").
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"})
	for _, input := range inputs {
		browser = browser.WithFile(input.path, input.file)
	}
	return browser.WithExec([]string{"sh", "-ec", pure.D3AReadinessCommand([]string{
		"http://keycloak:8080/realms/sepa-nexus/.well-known/openid-configuration",
		"http://backend:8081/actuator/health",
		"http://frontend:3000",
	}, command)})
}
