package main

import (
	"context"

	"dagger/debina-verification/internal/dagger"
)

const (
	syntheticAccessToken  = "phase-d-synthetic-access-token-7c3a"
	syntheticRefreshToken = "phase-d-synthetic-refresh-token-9e51"
	syntheticPassword     = "phase-d-synthetic-password-b2f4"
)

// FailureArtifacts returns only a redacted structured summary. The raw
// diagnostic and its synthetic secrets remain inside the producer container.
func (m *DebinaVerification) FailureArtifacts() *dagger.Directory {
	accessToken := dag.SetSecret("phase-d-artifact-access-token", syntheticAccessToken)
	refreshToken := dag.SetSecret("phase-d-artifact-refresh-token", syntheticRefreshToken)
	password := dag.SetSecret("phase-d-artifact-password", syntheticPassword)
	return dag.Container().
		From(goImage).
		WithDirectory("/src", m.source().Directory("dagger")).
		WithWorkdir("/src").
		WithSecretVariable("PHASE_D_SYNTHETIC_ACCESS_TOKEN", accessToken).
		WithSecretVariable("PHASE_D_SYNTHETIC_REFRESH_TOKEN", refreshToken).
		WithSecretVariable("PHASE_D_SYNTHETIC_PASSWORD", password).
		WithExec([]string{"sh", "-ec", `
set -eu
{
  printf '%s\n' 'stage=browser-navigation'
  printf '%s\n' 'classification=BROWSER_NAVIGATION_FAILED'
  printf 'Authorization: Bearer %s\n' "$PHASE_D_SYNTHETIC_ACCESS_TOKEN"
  printf 'Cookie: sepa_session=%s\n' "$PHASE_D_SYNTHETIC_ACCESS_TOKEN"
  printf 'Set-Cookie: sepa_session=%s\n' "$PHASE_D_SYNTHETIC_ACCESS_TOKEN"
  printf 'access_token=%s\n' "$PHASE_D_SYNTHETIC_ACCESS_TOKEN"
  printf 'refresh_token=%s\n' "$PHASE_D_SYNTHETIC_REFRESH_TOKEN"
  printf 'id_token=%s\n' "$PHASE_D_SYNTHETIC_ACCESS_TOKEN"
  printf 'client_secret=%s\n' "$PHASE_D_SYNTHETIC_PASSWORD"
  printf 'password=%s\n' "$PHASE_D_SYNTHETIC_PASSWORD"
  printf '%s\n' 'payment_payload={synthetic-sensitive-payload}'
  printf '%s\n' 'safe_error=page.goto failed for graph-local target'
  printf 'bounded_excerpt=synthetic value %s removed\n' "$PHASE_D_SYNTHETIC_ACCESS_TOKEN"
} > /tmp/raw-diagnostic
go run ./cmd/failure-artifact /tmp/raw-diagnostic /out/failure-summary.json
`}).
		Directory("/out")
}

// FailureArtifactRedaction automatically verifies both the forbidden patterns
// and the exact synthetic secret values while retaining safe diagnostic facts.
func (m *DebinaVerification) FailureArtifactRedaction(ctx context.Context) (string, error) {
	accessToken := dag.SetSecret("phase-d-artifact-scan-access-token", syntheticAccessToken)
	refreshToken := dag.SetSecret("phase-d-artifact-scan-refresh-token", syntheticRefreshToken)
	password := dag.SetSecret("phase-d-artifact-scan-password", syntheticPassword)
	return dag.Container().
		From(alpineImage).
		WithDirectory("/artifacts", m.FailureArtifacts()).
		WithSecretVariable("PHASE_D_SYNTHETIC_ACCESS_TOKEN", accessToken).
		WithSecretVariable("PHASE_D_SYNTHETIC_REFRESH_TOKEN", refreshToken).
		WithSecretVariable("PHASE_D_SYNTHETIC_PASSWORD", password).
		WithExec([]string{"sh", "-ec", `
set -eu
artifact=/artifacts/failure-summary.json
test -s "$artifact"
for pattern in 'Authorization:' 'Bearer ' access_token refresh_token id_token client_secret password 'Cookie:' 'Set-Cookie:'; do
  if grep -Fi "$pattern" "$artifact" >/dev/null; then
    echo 'PHASE-D SECRET_EXPOSURE: forbidden diagnostic pattern' >&2
    exit 1
  fi
done
for secret in "$PHASE_D_SYNTHETIC_ACCESS_TOKEN" "$PHASE_D_SYNTHETIC_REFRESH_TOKEN" "$PHASE_D_SYNTHETIC_PASSWORD"; do
  if grep -F "$secret" "$artifact" >/dev/null; then
    echo 'PHASE-D SECRET_EXPOSURE: synthetic secret value' >&2
    exit 1
  fi
done
grep -F '"stage": "browser-navigation"' "$artifact" >/dev/null
grep -F '"classification": "BROWSER_NAVIGATION_FAILED"' "$artifact" >/dev/null
grep -F '"expectedFailure": true' "$artifact" >/dev/null
grep -F '"correlationId": "phase-d-navigation-local"' "$artifact" >/dev/null
echo 'PHASE-D FAILURE-ARTIFACT REDACTION VERIFIED'
`}).
		Stdout(ctx)
}
