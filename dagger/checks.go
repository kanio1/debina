package main

import (
	"context"

	"dagger/debina-verification/internal/dagger"
	"dagger/debina-verification/pure"
)

// Fast runs high-signal checks that do not provision the full runtime.
// +check
func (m *DebinaVerification) Fast(ctx context.Context) error {
	checks := []namedCheck{
		containerCheck("governance", m.governance()),
		containerCheck("module", m.moduleSelfTest()),
		containerCheck("backend-fast", m.backendFast()),
		containerCheck("frontend-fast", m.frontendFast()),
	}
	return runChecks(ctx, checks)
}

// Integration proves the Dagger-native integration leaves. Existing
// Testcontainers tests are intentionally excluded until a supported bridge to
// the selected Podman runtime is available.
// +check
func (m *DebinaVerification) Integration(ctx context.Context) error {
	checks := []namedCheck{
		containerCheck("backend-regression-without-testcontainers", m.backendWithoutTestcontainers()),
		containerCheck("frontend-production-build", m.frontendBuild()),
		containerCheck("postgres-readiness", m.postgresReadiness()),
		containerCheck("flyway-fresh-database", m.flywayFresh()),
		containerCheck("flyway-upgrade-from-v54", m.flywayUpgrade()),
		containerCheck("postgres-rls-and-grants", m.rlsAndGrantProbes()),
		containerCheck("kafka-readiness-and-non-production-probe", m.kafkaProbe()),
	}
	return runChecks(ctx, checks)
}

type namedCheck = pure.NamedCheck

func containerCheck(name string, container *dagger.Container) namedCheck {
	return namedCheck{Name: name, Run: func(ctx context.Context) error {
		_, err := container.Sync(ctx)
		return err
	}}
}

func runChecks(ctx context.Context, checks []namedCheck) error {
	return pure.RunChecks(ctx, checks)
}
