package main

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"dagger/debina-verification/pure"
)

const helperRealm = `{"clients":[{"clientId":"sepa-web","redirectUris":[],"webOrigins":[]}]}`

func TestRunCreatesNestedOutputAndMarkerAfterVerifiedRealm(t *testing.T) {
	directory := t.TempDir()
	input := filepath.Join(directory, "input.json")
	output := filepath.Join(directory, "nested", "realm-export.json")
	marker := filepath.Join(directory, "other", "verified.marker")
	if err := os.WriteFile(input, []byte(helperRealm), 0600); err != nil {
		t.Fatal(err)
	}
	var stdout bytes.Buffer
	if err := run([]string{"--input", input, "--output", output, "--marker", marker, "--callback", pure.D3AFrontendCallback, "--origin", pure.D3AFrontendOrigin}, &stdout); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(output); err != nil {
		t.Fatalf("verified realm was not created: %v", err)
	}
	content, err := os.ReadFile(marker)
	if err != nil {
		t.Fatalf("marker was not created: %v", err)
	}
	if string(content) != pure.OverlaySuccessMarker+"\n" || stdout.String() != pure.OverlaySuccessMarker+"\n" {
		t.Fatal("safe success marker was not emitted after output creation")
	}
}

func TestRunFailsWithoutRequiredPaths(t *testing.T) {
	if err := run(nil, &bytes.Buffer{}); err == nil {
		t.Fatal("expected missing path rejection")
	}
}

func TestRunDoesNotCreateMarkerWhenOutputWriteFails(t *testing.T) {
	directory := t.TempDir()
	input := filepath.Join(directory, "input.json")
	blocker := filepath.Join(directory, "blocker")
	marker := filepath.Join(directory, "marker", "verified.marker")
	if err := os.WriteFile(input, []byte(helperRealm), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(blocker, []byte("not-a-directory"), 0600); err != nil {
		t.Fatal(err)
	}
	err := run([]string{"--input", input, "--output", filepath.Join(blocker, "realm.json"), "--marker", marker, "--callback", pure.D3AFrontendCallback, "--origin", pure.D3AFrontendOrigin}, &bytes.Buffer{})
	if err == nil {
		t.Fatal("expected output directory failure")
	}
	if !strings.Contains(err.Error(), "not a directory") {
		t.Fatalf("unexpected safe failure: %v", err)
	}
	if _, err := os.Stat(marker); !os.IsNotExist(err) {
		t.Fatal("marker exists despite failed realm output")
	}
}
