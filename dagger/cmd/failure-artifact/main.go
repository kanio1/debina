package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"dagger/debina-verification/pure"
)

type artifact struct {
	Stage           string `json:"stage"`
	Classification  string `json:"classification"`
	Timestamp       string `json:"timestamp"`
	TestName        string `json:"testName"`
	ArtifactType    string `json:"artifactType"`
	CorrelationID   string `json:"correlationId"`
	SafeError       string `json:"safeError"`
	BoundedLog      string `json:"boundedLogExcerpt"`
	ExpectedFailure bool   `json:"expectedFailure"`
}

func main() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "usage: failure-artifact INPUT OUTPUT")
		os.Exit(2)
	}
	raw, err := os.ReadFile(os.Args[1])
	if err != nil {
		panic(err)
	}
	redacted := pure.RedactDiagnostic(string(raw), []string{
		os.Getenv("PHASE_D_SYNTHETIC_ACCESS_TOKEN"),
		os.Getenv("PHASE_D_SYNTHETIC_REFRESH_TOKEN"),
		os.Getenv("PHASE_D_SYNTHETIC_PASSWORD"),
	})
	record := artifact{
		Stage:           "browser-navigation",
		Classification:  "BROWSER_NAVIGATION_FAILED",
		Timestamp:       time.Now().UTC().Format(time.RFC3339),
		TestName:        "D6 controlled graph-local navigation failure",
		ArtifactType:    "redacted-json-summary",
		CorrelationID:   "phase-d-navigation-local",
		SafeError:       "page.goto failed for an unavailable graph-local target",
		BoundedLog:      redacted,
		ExpectedFailure: true,
	}
	data, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		panic(err)
	}
	if err := os.MkdirAll(filepath.Dir(os.Args[2]), 0o755); err != nil {
		panic(err)
	}
	if err := os.WriteFile(os.Args[2], append(data, '\n'), 0o644); err != nil {
		panic(err)
	}
}
