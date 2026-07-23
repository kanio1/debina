package main

import (
	"context"
	"fmt"
	"strings"

	"dagger/debina-verification/internal/dagger"
	"dagger/debina-verification/pure"
)

const expectedFailureMarkerPrefix = "PHASE-D EXPECTED "

// ResilienceChildNonZero proves that a non-zero finite child is observed by
// the Dagger SDK and is never converted into a false success.
func (m *DebinaVerification) ResilienceChildNonZero(ctx context.Context) (string, error) {
	child := dag.Container().
		From(alpineImage).
		WithExec(
			[]string{"sh", "-ec", "echo 'PHASE-D EXPECTED CHILD_EXIT_NON_ZERO' >&2; exit 23"},
			dagger.ContainerWithExecOpts{Expect: dagger.ReturnTypeFailure},
		)
	return expectedFailure(ctx, "CHILD_EXIT_NON_ZERO", child)
}

// ResilienceBoundedTimeout proves that a missing dependency ends within a
// fixed finite budget and is surfaced as an expected readiness failure.
func (m *DebinaVerification) ResilienceBoundedTimeout(ctx context.Context) (string, error) {
	child := dag.Container().
		From(curlImage).
		WithExec(
			[]string{"sh", "-ec", pure.BoundedUnavailableCommand("http://phase-d-missing:8081/actuator/health", "PHASE-D EXPECTED READINESS_TIMEOUT", 3)},
			dagger.ContainerWithExecOpts{Expect: dagger.ReturnTypeFailure},
		)
	return expectedFailure(ctx, "READINESS_TIMEOUT", child)
}

// ResilienceBackendUnavailable exercises the same bounded redacted client
// behavior used by a frontend BFF dependency probe, without starting a browser
// or opening a host port. The absent alias is intentional and graph-local.
func (m *DebinaVerification) ResilienceBackendUnavailable(ctx context.Context) (string, error) {
	return m.expectedHTTPUnavailable(ctx, "BACKEND_UNAVAILABLE", "http://phase-d-backend-unavailable:8081/actuator/health")
}

// ResilienceKeycloakUnavailable proves a finite issuer discovery failure using
// the real discovery path but an intentionally absent, graph-local authority.
func (m *DebinaVerification) ResilienceKeycloakUnavailable(ctx context.Context) (string, error) {
	return m.expectedHTTPUnavailable(ctx, "KEYCLOAK_UNAVAILABLE", "http://phase-d-keycloak-unavailable:8080/realms/sepa-nexus/.well-known/openid-configuration")
}

// ResilienceFrontendUnavailable proves the frontend root cannot be mistaken
// for readiness when no frontend service is present.
func (m *DebinaVerification) ResilienceFrontendUnavailable(ctx context.Context) (string, error) {
	return m.expectedHTTPUnavailable(ctx, "FRONTEND_UNAVAILABLE", "http://phase-d-frontend-unavailable:3000/")
}

// ResiliencePostgresUnavailable exercises the PostgreSQL protocol client
// against an absent alias; it is distinct from an HTTP readiness assertion.
func (m *DebinaVerification) ResiliencePostgresUnavailable(ctx context.Context) (string, error) {
	child := dag.Container().
		From(postgresImage).
		WithExec([]string{"sh", "-ec", `
set -eu
attempts=0
until pg_isready -h phase-d-postgres-unavailable -p 5432 -t 1 >/dev/null 2>&1; do
  attempts=$((attempts + 1))
  if [ "$attempts" -ge 3 ]; then
    echo 'PHASE-D EXPECTED POSTGRES_UNAVAILABLE' >&2
    exit 1
  fi
  sleep 1
done
echo 'unexpected PostgreSQL readiness success' >&2
exit 0`}, dagger.ContainerWithExecOpts{Expect: dagger.ReturnTypeFailure})
	return expectedFailure(ctx, "POSTGRES_UNAVAILABLE", child)
}

// ResilienceKafkaUnavailable uses Kafka's own bootstrap probe with an absent
// alias and a bounded shell timeout. No broker, host port, or runtime socket is
// introduced by this controlled-fault leaf.
func (m *DebinaVerification) ResilienceKafkaUnavailable(ctx context.Context) (string, error) {
	child := dag.Container().
		From(kafkaImage).
		WithExec([]string{"sh", "-ec", `
set -eu
attempts=0
until timeout 1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server phase-d-kafka-unavailable:9092 --list >/dev/null 2>&1; do
  attempts=$((attempts + 1))
  if [ "$attempts" -ge 3 ]; then
    echo 'PHASE-D EXPECTED KAFKA_UNAVAILABLE' >&2
    exit 1
  fi
  sleep 1
done
echo 'unexpected Kafka readiness success' >&2
exit 0`}, dagger.ContainerWithExecOpts{Expect: dagger.ReturnTypeFailure})
	return expectedFailure(ctx, "KAFKA_UNAVAILABLE", child)
}

// ResilienceBrowserNavigationFailure proves a real Playwright page.goto
// failure against an absent graph-local alias. The wrapper emits the expected
// marker only after Playwright exits non-zero and its bounded log contains a
// navigation-layer error.
func (m *DebinaVerification) ResilienceBrowserNavigationFailure(ctx context.Context) (string, error) {
	child := dag.Container().
		From(playwrightImage).
		WithMountedCache("/pnpm/store", dag.CacheVolume("debina-pnpm-node24.18.0-pnpm10.33.0")).
		WithEnvVariable("PNPM_HOME", "/pnpm").
		WithEnvVariable("PNPM_STORE_DIR", "/pnpm/store").
		WithDirectory("/workspace/frontend", m.source().Directory("frontend")).
		WithWorkdir("/workspace/frontend").
		WithEnvVariable("PLAYWRIGHT_BROWSERS_PATH", "/ms-playwright").
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"}).
		WithExec([]string{"sh", "-ec", `
set -eu
if timeout 30 pnpm exec playwright test e2e/d6-browser-navigation-failure.spec.ts --project=chromium --workers=1 > /tmp/navigation.log 2>&1; then
  echo 'browser navigation unexpectedly succeeded' >&2
  exit 1
fi
if ! grep -F 'page.goto:' /tmp/navigation.log >/dev/null; then
  echo 'controlled Playwright failure was not a navigation failure' >&2
  exit 1
fi
if ! grep -E 'ERR_NAME_NOT_RESOLVED|ERR_CONNECTION_REFUSED|ERR_CONNECTION_CLOSED' /tmp/navigation.log >/dev/null; then
  echo 'controlled Playwright failure had an unexpected network classification' >&2
  exit 1
fi
echo 'PHASE-D EXPECTED BROWSER_NAVIGATION_FAILED' >&2
exit 23
`}, dagger.ContainerWithExecOpts{Expect: dagger.ReturnTypeFailure})
	return expectedFailure(ctx, "BROWSER_NAVIGATION_FAILED", child)
}

func (m *DebinaVerification) expectedHTTPUnavailable(ctx context.Context, classification, url string) (string, error) {
	child := dag.Container().
		From(curlImage).
		WithExec(
			[]string{"sh", "-ec", pure.BoundedUnavailableCommand(url, expectedFailureMarkerPrefix+classification, 3)},
			dagger.ContainerWithExecOpts{Expect: dagger.ReturnTypeFailure},
		)
	return expectedFailure(ctx, classification, child)
}

func expectedFailure(ctx context.Context, classification string, child *dagger.Container) (string, error) {
	exitCode, err := child.ExitCode(ctx)
	marker := expectedFailureMarkerPrefix + classification
	if err != nil {
		return "", fmt.Errorf("%s evaluation failed: %w", classification, err)
	}
	if exitCode == 0 {
		return "", fmt.Errorf("%s false success: controlled child exited 0", classification)
	}
	stderr, err := child.Stderr(ctx)
	if err != nil {
		return "", fmt.Errorf("%s stderr evaluation failed: %w", classification, err)
	}
	if !strings.Contains(stderr, marker) {
		return "", fmt.Errorf("%s wrong failure classification: expected marker %q", classification, marker)
	}
	return marker, nil
}
