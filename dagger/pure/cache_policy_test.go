package pure

import (
	"go/ast"
	"go/parser"
	"go/token"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

func TestSmokeSignedPain001IsRegisteredOutsideAutomaticChecks(t *testing.T) {
	t.Parallel()

	source, err := os.ReadFile(filepath.Join("..", "smoke_signed_pain001.go"))
	if err != nil {
		t.Fatalf("read smoke_signed_pain001.go: %v", err)
	}
	text := string(source)
	if !strings.Contains(text, "func (m *DebinaVerification) SmokeSignedPain001") {
		t.Fatal("SmokeSignedPain001 must remain a public DebinaVerification callable")
	}
	if !strings.Contains(text, "paymentSmokeRuntime(instance)") {
		t.Fatal("SmokeSignedPain001 must reuse paymentSmokeRuntime")
	}
	if !strings.Contains(text, "signed-pain001-%s") {
		t.Fatal("proofNonce must alter the runtime instance suffix")
	}
	if strings.Contains(text, "E1_SMOKE_PROOF_NONCE") {
		t.Fatal("unused E1_SMOKE_PROOF_NONCE env must not be reintroduced; instance suffix owns isolation")
	}
	if strings.Contains(text, "// +check") {
		t.Fatal("SmokeSignedPain001 must not become an automatic +check root")
	}

	checksPath := filepath.Join("..", "checks.go")
	checks, err := os.ReadFile(checksPath)
	if err != nil {
		t.Fatalf("read checks.go: %v", err)
	}
	if strings.Contains(string(checks), "SmokeSignedPain001") {
		t.Fatal("SmokeSignedPain001 must stay outside SmokeSuite/Acceptance composition")
	}
}

func TestProofNonceDoesNotAppearInDependencyCacheNames(t *testing.T) {
	t.Parallel()

	entries, err := os.ReadDir("..")
	if err != nil {
		t.Fatalf("read module: %v", err)
	}
	nonceLike := regexp.MustCompile(`CacheVolume\("[^"]*(nonce|proof-|timestamp|[0-9a-f]{40})[^"]*"\)`)
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".go") || strings.HasSuffix(entry.Name(), "_test.go") {
			continue
		}
		path := filepath.Join("..", entry.Name())
		body, err := os.ReadFile(path)
		if err != nil {
			t.Fatalf("read %s: %v", path, err)
		}
		if entry.Name() == "cache_volume.go" {
			// Stress fixture intentionally uses a hash-namespaced SHARED volume
			// outside the ordinary dependency-cache allowlist.
			continue
		}
		if nonceLike.Find(body) != nil {
			t.Fatalf("%s: dependency CacheVolume name must not embed nonce/SHA/timestamp: %s", entry.Name(), nonceLike.Find(body))
		}
	}
}

func TestNamedCacheMountsStayOnAllowlist(t *testing.T) {
	t.Parallel()

	files := token.NewFileSet()
	entries, err := os.ReadDir("..")
	if err != nil {
		t.Fatalf("read module: %v", err)
	}

	allowedVolumes := map[string]struct{}{}
	for _, name := range ApprovedNamedCacheVolumes {
		allowedVolumes[name] = struct{}{}
	}
	allowedPaths := map[string]struct{}{}
	for _, name := range ApprovedCacheMountPaths {
		allowedPaths[name] = struct{}{}
	}

	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".go") || strings.HasSuffix(entry.Name(), "_test.go") {
			continue
		}
		if entry.Name() == "cache_volume.go" {
			continue
		}
		path := filepath.Join("..", entry.Name())
		parsed, err := parser.ParseFile(files, path, nil, 0)
		if err != nil {
			t.Fatalf("parse %s: %v", path, err)
		}
		ast.Inspect(parsed, func(node ast.Node) bool {
			call, ok := node.(*ast.CallExpr)
			if !ok {
				return true
			}
			selector, ok := call.Fun.(*ast.SelectorExpr)
			if !ok {
				return true
			}
			switch selector.Sel.Name {
			case "CacheVolume":
				if len(call.Args) != 1 {
					return true
				}
				lit, ok := call.Args[0].(*ast.BasicLit)
				if !ok || lit.Kind != token.STRING {
					t.Fatalf("%s: CacheVolume argument must be a string literal for policy checks", entry.Name())
				}
				name := strings.Trim(lit.Value, `"`)
				if _, ok := allowedVolumes[name]; !ok {
					t.Fatalf("%s: unapproved CacheVolume %q", entry.Name(), name)
				}
			case "WithMountedCache":
				if len(call.Args) < 1 {
					return true
				}
				lit, ok := call.Args[0].(*ast.BasicLit)
				if !ok || lit.Kind != token.STRING {
					return true
				}
				mount := strings.Trim(lit.Value, `"`)
				if _, ok := allowedPaths[mount]; !ok {
					t.Fatalf("%s: unapproved cache mount path %q", entry.Name(), mount)
				}
				lower := strings.ToLower(mount)
				for _, fragment := range ForbiddenCacheMountPathFragments {
					if strings.Contains(lower, fragment) {
						t.Fatalf("%s: forbidden cache mount fragment %q in %q", entry.Name(), fragment, mount)
					}
				}
			}
			return true
		})
	}
}

func TestSourceExcludesCoverBuildAndRuntimeNoise(t *testing.T) {
	t.Parallel()

	body, err := os.ReadFile(filepath.Join("..", "versions.go"))
	if err != nil {
		t.Fatalf("read versions.go: %v", err)
	}
	text := string(body)
	for _, required := range RequiredSourceExcludes {
		if !strings.Contains(text, `"`+required+`"`) {
			t.Fatalf("sourceExcludes missing %q", required)
		}
	}
}

func TestNoProductionFunctionShellsIntoDaggerCLI(t *testing.T) {
	t.Parallel()

	entries, err := os.ReadDir("..")
	if err != nil {
		t.Fatalf("read module: %v", err)
	}
	forbidden := []string{"dagger call", "dagger check", "dagger functions", "dagger develop"}
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".go") || strings.HasSuffix(entry.Name(), "_test.go") {
			continue
		}
		body, err := os.ReadFile(filepath.Join("..", entry.Name()))
		if err != nil {
			t.Fatalf("read %s: %v", entry.Name(), err)
		}
		text := string(body)
		for _, needle := range forbidden {
			if strings.Contains(text, needle) {
				t.Fatalf("%s must not shell into %q", entry.Name(), needle)
			}
		}
	}
}

func TestPaymentSmokeRuntimeReusesSharedConstructors(t *testing.T) {
	t.Parallel()

	body, err := os.ReadFile(filepath.Join("..", "smoke_runtime.go"))
	if err != nil {
		t.Fatalf("read smoke_runtime.go: %v", err)
	}
	text := string(body)
	for _, required := range []string{
		"postgresService(",
		"kafkaService(",
		"keycloakServiceWithOverlay(",
		"paymentSmokeBackendService(",
		"smokeFrontendService(",
	} {
		if !strings.Contains(text, required) {
			t.Fatalf("paymentSmokeRuntime missing shared constructor use: %s", required)
		}
	}
}
