package main

import (
	"context"
	"errors"
	"time"

	"dagger/debina-verification/internal/dagger"
	"dagger/debina-verification/pure"
)

// Fast runs high-signal checks that do not provision the full runtime.
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

// SmokeAuth is the explicit D3A authentication/session/health aggregate.
func (m *DebinaVerification) SmokeAuth(ctx context.Context) error {
	_, err := m.SmokeLoginSessionHealth(ctx)
	return err
}

// SmokePayments runs the three independent D3B browser journeys sequentially
// so their PostgreSQL, Kafka, Keycloak and Chromium graphs never overlap.
func (m *DebinaVerification) SmokePayments(ctx context.Context) error {
	return pure.RunChecksSequential(ctx, []namedCheck{
		{Name: "json-direct-submission", Timeout: 6 * time.Minute, Run: func(ctx context.Context) error {
			_, err := m.SmokeJsonDirectSubmission(ctx)
			return err
		}},
		{Name: "maker-checker-approval", Timeout: 7 * time.Minute, Run: func(ctx context.Context) error {
			_, err := m.SmokeMakerCheckerApproval(ctx)
			return err
		}},
		{Name: "payment-detail-lineage", Timeout: 6 * time.Minute, Run: func(ctx context.Context) error {
			_, err := m.SmokePaymentDetailLineage(ctx)
			return err
		}},
	})
}

func (m *DebinaVerification) phaseDAssurance(ctx context.Context) error {
	return pure.RunChecksSequential(ctx, []namedCheck{
		{Name: "child-non-zero", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResilienceChildNonZero(ctx)
			return err
		}},
		{Name: "bounded-timeout", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResilienceBoundedTimeout(ctx)
			return err
		}},
		{Name: "postgres-unavailable", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResiliencePostgresUnavailable(ctx)
			return err
		}},
		{Name: "kafka-unavailable", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResilienceKafkaUnavailable(ctx)
			return err
		}},
		{Name: "keycloak-unavailable", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResilienceKeycloakUnavailable(ctx)
			return err
		}},
		{Name: "backend-unavailable", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResilienceBackendUnavailable(ctx)
			return err
		}},
		{Name: "frontend-unavailable", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResilienceFrontendUnavailable(ctx)
			return err
		}},
		{Name: "browser-navigation-failure", Timeout: 2 * time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResilienceBrowserNavigationFailure(ctx)
			return err
		}},
		{Name: "failure-artifact-redaction", Timeout: 2 * time.Minute, Run: func(ctx context.Context) error {
			_, err := m.FailureArtifactRedaction(ctx)
			return err
		}},
		{Name: "cache-reuse", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.CacheReuse(ctx)
			return err
		}},
		{Name: "cache-invalidation", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.CacheInvalidation(ctx)
			return err
		}},
	})
}

// PhaseD is the complete no-host-socket aggregate. The typed-socket
// Testcontainers regression remains an explicit separate function.
// +check
func (m *DebinaVerification) PhaseD(ctx context.Context) error {
	return pure.RunChecksSequential(ctx, []namedCheck{
		{Name: "fast", Timeout: 10 * time.Minute, Run: m.Fast},
		{Name: "integration", Timeout: 15 * time.Minute, Run: m.Integration},
		{Name: "smoke-auth", Timeout: 6 * time.Minute, Run: m.SmokeAuth},
		{Name: "smoke-payments", Timeout: 20 * time.Minute, Run: m.SmokePayments},
		{Name: "assurance", Timeout: 10 * time.Minute, Run: m.phaseDAssurance},
	})
}

// All is the backward-compatible socket-free alias of PhaseD. It is callable,
// but deliberately not an automatic check: otherwise an unfiltered
// `dagger check` would run the complete PhaseD graph twice.
func (m *DebinaVerification) All(ctx context.Context) error {
	return m.PhaseD(ctx)
}

// AggregateUnexpectedFailureProbe proves that the same sequential aggregate
// compositor used by PhaseD propagates an unrelated child error instead of
// classifying it as an expected assurance failure.
func (m *DebinaVerification) AggregateUnexpectedFailureProbe(ctx context.Context) error {
	return pure.RunChecksSequential(ctx, []namedCheck{
		{Name: "expected-child", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ResilienceChildNonZero(ctx)
			return err
		}},
		{Name: "unexpected-child", Timeout: time.Minute, Run: func(context.Context) error {
			return errors.New("PHASE-D UNEXPECTED CHILD FAILURE")
		}},
	})
}
