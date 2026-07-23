package pure

import (
	"regexp"
	"strings"
)

var sensitiveDiagnosticLine = regexp.MustCompile(`(?i)^\s*(authorization|cookie|set-cookie|access_token|refresh_token|id_token|client_secret|password|payment_payload)\s*[:=]`)

// RedactDiagnostic removes whole sensitive fields before an artifact can leave
// its failure container, then replaces any supplied synthetic secret that
// appeared in otherwise safe diagnostic text.
func RedactDiagnostic(input string, secrets []string) string {
	lines := strings.Split(input, "\n")
	safe := make([]string, 0, len(lines))
	for _, line := range lines {
		if sensitiveDiagnosticLine.MatchString(line) {
			continue
		}
		for _, secret := range secrets {
			if secret != "" {
				line = strings.ReplaceAll(line, secret, "[REDACTED]")
			}
		}
		safe = append(safe, line)
	}
	return strings.TrimSpace(strings.Join(safe, "\n"))
}
