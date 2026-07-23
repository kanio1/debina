package pure

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestFrontendProductionBuildExcludesRuntimeEnvironment(t *testing.T) {
	t.Parallel()

	frontendSource := readSource(t, filepath.Join("..", "frontend.go"))
	for _, runtimeName := range []string{
		"KEYCLOAK_ISSUER",
		"KEYCLOAK_CLIENT_ID",
		"KEYCLOAK_CLIENT_SECRET",
		"BFF_BASE_URL",
		"BACKEND_API_BASE_URL",
	} {
		if strings.Contains(frontendSource, runtimeName) {
			t.Fatalf("frontend production build contains runtime input %s", runtimeName)
		}
	}

	smokeSource := readSource(t, filepath.Join("..", "smoke.go"))
	buildOffset := strings.Index(smokeSource, "return m.frontendProductionBuild().")
	if buildOffset < 0 {
		t.Fatal("smoke frontend does not reuse frontendProductionBuild")
	}
	for _, runtimeBinding := range []string{
		`WithEnvVariable("KEYCLOAK_ISSUER"`,
		`WithEnvVariable("KEYCLOAK_CLIENT_ID"`,
		`WithSecretVariable("KEYCLOAK_CLIENT_SECRET"`,
		`WithEnvVariable("BFF_BASE_URL"`,
		`WithEnvVariable("BACKEND_API_BASE_URL"`,
	} {
		offset := strings.Index(smokeSource, runtimeBinding)
		if offset < buildOffset {
			t.Fatalf("runtime binding %s is missing or precedes the production build", runtimeBinding)
		}
	}
}

func TestFrontendHasNoBuildTimePublicEnvironmentInputs(t *testing.T) {
	t.Parallel()

	frontendRoot := filepath.Join("..", "..", "frontend")
	err := filepath.WalkDir(frontendRoot, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() {
			switch entry.Name() {
			case "node_modules", ".next":
				return filepath.SkipDir
			default:
				return nil
			}
		}
		switch filepath.Ext(path) {
		case ".ts", ".tsx", ".js", ".mjs":
			source, readErr := os.ReadFile(path)
			if readErr != nil {
				return readErr
			}
			if strings.Contains(string(source), "NEXT_PUBLIC_") {
				t.Fatalf("build-time public environment input found in %s", path)
			}
		}
		return nil
	})
	if err != nil {
		t.Fatalf("inspect frontend environment inputs: %v", err)
	}
}

func readSource(t *testing.T, path string) string {
	t.Helper()
	source, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return string(source)
}
