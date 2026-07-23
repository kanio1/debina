package pure

import (
	"strings"
	"testing"
)

func TestBoundedUnavailableCommandIsFiniteAndRedacted(t *testing.T) {
	command := BoundedUnavailableCommand("http://missing:8081/actuator/health", "PHASE-D EXPECTED BACKEND_UNAVAILABLE", 3)
	for _, want := range []string{"--max-time 1", "-ge 3", "PHASE-D EXPECTED BACKEND_UNAVAILABLE", "exit 1"} {
		if !strings.Contains(command, want) {
			t.Fatalf("command omitted %q: %s", want, command)
		}
	}
	for _, forbidden := range []string{"-v", "--trace", "--include", "Authorization", "Cookie"} {
		if strings.Contains(command, forbidden) {
			t.Fatalf("unsafe diagnostic option %q", forbidden)
		}
	}
}

func TestBoundedUnavailableCommandRejectsNonPositiveAttempts(t *testing.T) {
	defer func() {
		if recover() == nil {
			t.Fatal("expected panic")
		}
	}()
	BoundedUnavailableCommand("http://missing", "EXPECTED", 0)
}
