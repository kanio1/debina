package pure

import (
	"go/ast"
	"go/parser"
	"go/token"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"
)

func TestAutomaticCheckTopologyHasOneCanonicalRoot(t *testing.T) {
	t.Parallel()

	entries, err := os.ReadDir("..")
	if err != nil {
		t.Fatalf("read Dagger module source: %v", err)
	}

	var checks []string
	files := token.NewFileSet()
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".go") || strings.HasSuffix(entry.Name(), "_test.go") {
			continue
		}
		path := filepath.Join("..", entry.Name())
		parsed, err := parser.ParseFile(files, path, nil, parser.ParseComments)
		if err != nil {
			t.Fatalf("parse %s: %v", path, err)
		}
		for _, declaration := range parsed.Decls {
			function, ok := declaration.(*ast.FuncDecl)
			if !ok || function.Recv == nil || function.Doc == nil {
				continue
			}
			if hasCheckDirective(function.Doc) {
				checks = append(checks, function.Name.Name)
			}
		}
	}
	sort.Strings(checks)

	want := []string{"PhaseD"}
	if !reflect.DeepEqual(checks, want) {
		t.Fatalf("automatic checks = %v, want %v; child gates must remain callable functions without +check to prevent nested execution", checks, want)
	}
}

func hasCheckDirective(group *ast.CommentGroup) bool {
	for _, comment := range group.List {
		for _, line := range strings.Split(comment.Text, "\n") {
			line = strings.TrimSpace(line)
			line = strings.TrimSpace(strings.TrimPrefix(line, "//"))
			line = strings.TrimSpace(strings.TrimPrefix(line, "*"))
			if line == "+check" {
				return true
			}
		}
	}
	return false
}
