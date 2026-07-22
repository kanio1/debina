package pure

import (
	"strings"
	"testing"
)

func TestD3AReadinessCommandGatesBrowserOnEveryService(t *testing.T) {
	urls := []string{"http://keycloak:8080/ready", "http://backend:8081/actuator/health", "http://frontend:3000"}
	command := D3AReadinessCommand(urls)

	for _, url := range urls {
		if !strings.Contains(command, url) {
			t.Fatalf("readiness command omitted %q", url)
		}
	}
	if !strings.Contains(command, "exit 1") || !strings.Contains(command, "pnpm run test:smoke:d3a") {
		t.Fatal("readiness failure must stop before the browser command")
	}
}
