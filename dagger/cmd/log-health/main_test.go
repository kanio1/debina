package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestScanAcceptsExpectedFailureAndStructuralError(t *testing.T) {
	path := writeTrace(t, "12 : Container.withExec ERROR [0.1s]\n12 : [0.1s] | PHASE-D EXPECTED CHILD_EXIT_NON_ZERO\nWARN Resolved [MissingRequestHeaderException: expected validation]\n")
	if err := scan(path); err != nil {
		t.Fatalf("expected allowlisted trace to pass: %v", err)
	}
}

func TestScanRejectsRuntimeNullPointerException(t *testing.T) {
	path := writeTrace(t, "12 : [0.1s] | java.lang.NullPointerException: mapper failed\n")
	if err := scan(path); err == nil {
		t.Fatal("expected NullPointerException to fail log-health scan")
	}
}

func TestScanRejectsRuntimeError(t *testing.T) {
	path := writeTrace(t, "12 : [0.1s] | 2026-07-23 ERROR application startup failed\n")
	if err := scan(path); err == nil {
		t.Fatal("expected runtime ERROR to fail log-health scan")
	}
}

func writeTrace(t *testing.T, contents string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "trace.log")
	if err := os.WriteFile(path, []byte(contents), 0o600); err != nil {
		t.Fatalf("write trace: %v", err)
	}
	return path
}
