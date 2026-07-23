package pure

import (
	"strings"
	"testing"
)

func TestRedactDiagnosticRemovesSensitiveFieldsAndSyntheticValues(t *testing.T) {
	const token = "phase-d-token-value"
	const password = "phase-d-password-value"
	raw := strings.Join([]string{
		"stage=browser-navigation",
		"classification=BROWSER_NAVIGATION_FAILED",
		"Authorization: Bearer " + token,
		"Cookie: sepa_session=" + token,
		"Set-Cookie: sepa_session=" + token,
		"access_token=" + token,
		"refresh_token=" + token,
		"id_token=" + token,
		"client_secret=" + password,
		"password=" + password,
		"payment_payload={synthetic-sensitive-payload}",
		"safe_error=page.goto failed for graph-local target",
		"bounded_excerpt=synthetic value " + token + " removed",
	}, "\n")

	redacted := RedactDiagnostic(raw, []string{token, password})
	for _, forbidden := range []string{
		"Authorization:", "Bearer ", "access_token", "refresh_token", "id_token",
		"client_secret", "password", "Cookie:", "Set-Cookie:", token, password,
		"synthetic-sensitive-payload",
	} {
		if strings.Contains(redacted, forbidden) {
			t.Fatalf("redacted diagnostic contains %q: %s", forbidden, redacted)
		}
	}
	for _, required := range []string{
		"stage=browser-navigation",
		"classification=BROWSER_NAVIGATION_FAILED",
		"safe_error=page.goto failed for graph-local target",
		"bounded_excerpt=synthetic value [REDACTED] removed",
	} {
		if !strings.Contains(redacted, required) {
			t.Fatalf("redacted diagnostic omitted %q: %s", required, redacted)
		}
	}
}
