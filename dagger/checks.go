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
		containerCheck("frontend-fast", m.frontendFast()),
	}
	return runChecks(ctx, checks)
}

// Integration proves exactly five disjoint Dagger-native integration leaves.
// The backend leaf owns the durable fast JUnit classification; typed-socket
// Testcontainers remain outside this socket-free graph.
func (m *DebinaVerification) Integration(ctx context.Context) error {
	checks := []namedCheck{
		containerCheck("backend-integration", m.backendIntegration()),
		containerCheck("frontend-production-build", m.frontendBuild()),
		containerCheck("database-contract", m.databaseContract()),
		containerCheck("database-upgrade", m.databaseUpgrade()),
		containerCheck("kafka-contract", m.kafkaContract()),
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

// SmokeSuite is the complete ADR-N16 browser-smoke gate. D3A and the three D3B
// journeys remain separately callable, but this classification owns all six
// capped journeys and executes their runtime graphs sequentially.
func (m *DebinaVerification) SmokeSuite(ctx context.Context) error {
	return pure.RunChecksSequential(ctx, []namedCheck{
		{Name: "authentication-session-health", Timeout: 6 * time.Minute, Run: m.SmokeAuth},
		{Name: "payment-journeys", Timeout: 20 * time.Minute, Run: m.SmokePayments},
	})
}

// Smoke preserves the pre-contract callable name.
//
// Deprecated: use SmokeSuite.
func (m *DebinaVerification) Smoke(ctx context.Context) error {
	return m.SmokeSuite(ctx)
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

// PipelineAssurance proves failure propagation, finite timeouts, diagnostic
// redaction, cache semantics and service-binding behavior. These are tests of
// the verification platform itself, not product integration or browser smoke.
func (m *DebinaVerification) PipelineAssurance(ctx context.Context) error {
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
		{Name: "cache-output-determinism", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.CacheOutputDeterminism(ctx)
			return err
		}},
		{Name: "cache-output-input-sensitivity", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.CacheOutputInputSensitivity(ctx)
			return err
		}},
		{Name: "service-binding-dns", Timeout: time.Minute, Run: func(ctx context.Context) error {
			_, err := m.ServiceBindingDNS(ctx)
			return err
		}},
	})
}

// Acceptance is the complete no-host-socket aggregate and the sole automatic
// check. Its three child gates are disjoint classifications and remain callable.
// The typed-socket Testcontainers regression remains an explicit separate gate.
// +check
func (m *DebinaVerification) Acceptance(ctx context.Context) error {
	if err := pure.RunChecks(ctx, []namedCheck{
		{Name: "fast", Timeout: 10 * time.Minute, Run: m.Fast},
		{Name: "integration", Timeout: 15 * time.Minute, Run: m.Integration},
	}); err != nil {
		return err
	}
	return pure.RunChecksSequential(ctx, []namedCheck{
		{Name: "smoke-suite", Timeout: 26 * time.Minute, Run: m.SmokeSuite},
	})
}

// PhaseD preserves the established callable Phase D entry point.
//
// Deprecated: use Acceptance.
func (m *DebinaVerification) PhaseD(ctx context.Context) error {
	return m.Acceptance(ctx)
}

// All preserves the legacy socket-free alias.
//
// Deprecated: use Acceptance.
func (m *DebinaVerification) All(ctx context.Context) error {
	return m.Acceptance(ctx)
}

// AllSocketFree preserves the pre-contract explicit socket-free alias.
//
// Deprecated: use Acceptance.
func (m *DebinaVerification) AllSocketFree(ctx context.Context) error {
	return m.Acceptance(ctx)
}

// FullLocal composes the socket-free acceptance graph with exactly the backend
// testcontainers classification. The host runtime socket remains a required
// typed argument and is mounted only into the Testcontainers leaf.
func (m *DebinaVerification) FullLocal(ctx context.Context, runtimeSocket *dagger.Socket) error {
	return pure.RunChecksSequential(ctx, []namedCheck{
		{Name: "acceptance", Timeout: 45 * time.Minute, Run: m.Acceptance},
		{Name: "backend-testcontainers", Timeout: 30 * time.Minute, Run: func(ctx context.Context) error {
			_, err := m.BackendTestcontainers(ctx, runtimeSocket)
			return err
		}},
	})
}

// AggregateUnexpectedFailureProbe proves that the same sequential aggregate
// compositor used by Acceptance propagates an unrelated child error instead of
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
