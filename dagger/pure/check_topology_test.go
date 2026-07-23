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

	want := []string{"Acceptance"}
	if !reflect.DeepEqual(checks, want) {
		t.Fatalf("automatic checks = %v, want %v; child gates must remain callable functions without +check to prevent nested execution", checks, want)
	}
}

func TestAcceptanceOwnsExactlyThreeCanonicalClassifications(t *testing.T) {
	t.Parallel()

	sourcePath := filepath.Join("..", "checks.go")
	parsed, err := parser.ParseFile(token.NewFileSet(), sourcePath, nil, 0)
	if err != nil {
		t.Fatalf("parse %s: %v", sourcePath, err)
	}

	var actual []string
	for _, declaration := range parsed.Decls {
		function, ok := declaration.(*ast.FuncDecl)
		if !ok || function.Name.Name != "Acceptance" {
			continue
		}

		ast.Inspect(function.Body, func(node ast.Node) bool {
			composite, ok := node.(*ast.CompositeLit)
			if !ok {
				return true
			}
			array, ok := composite.Type.(*ast.ArrayType)
			if !ok {
				return true
			}
			element, ok := array.Elt.(*ast.Ident)
			if !ok || element.Name != "namedCheck" {
				return true
			}

			for _, item := range composite.Elts {
				entry, ok := item.(*ast.CompositeLit)
				if !ok {
					t.Fatalf("acceptance classification is not a namedCheck literal: %T", item)
				}

				var name, runner string
				for _, field := range entry.Elts {
					keyValue, ok := field.(*ast.KeyValueExpr)
					if !ok {
						continue
					}
					key, ok := keyValue.Key.(*ast.Ident)
					if !ok {
						continue
					}
					switch key.Name {
					case "Name":
						value, ok := keyValue.Value.(*ast.BasicLit)
						if ok {
							name = value.Value
						}
					case "Run":
						selector, ok := keyValue.Value.(*ast.SelectorExpr)
						if ok {
							runner = selector.Sel.Name
						}
					}
				}
				actual = append(actual, name+":"+runner)
			}
			return false
		})
	}

	expected := []string{
		`"fast":Fast`,
		`"integration":Integration`,
		`"smoke-suite":SmokeSuite`,
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("acceptance classifications = %v, want %v", actual, expected)
	}
}

func TestIntegrationOwnsExactlyFiveCanonicalLeaves(t *testing.T) {
	t.Parallel()

	sourcePath := filepath.Join("..", "checks.go")
	parsed, err := parser.ParseFile(token.NewFileSet(), sourcePath, nil, 0)
	if err != nil {
		t.Fatalf("parse %s: %v", sourcePath, err)
	}

	var actual []string
	for _, declaration := range parsed.Decls {
		function, ok := declaration.(*ast.FuncDecl)
		if !ok || function.Name.Name != "Integration" {
			continue
		}
		ast.Inspect(function.Body, func(node ast.Node) bool {
			call, ok := node.(*ast.CallExpr)
			if !ok || len(call.Args) != 2 {
				return true
			}
			callee, ok := call.Fun.(*ast.Ident)
			if !ok || callee.Name != "containerCheck" {
				return true
			}
			name, ok := call.Args[0].(*ast.BasicLit)
			if !ok {
				t.Fatalf("integration leaf name is not a literal: %T", call.Args[0])
			}
			vertex, ok := call.Args[1].(*ast.CallExpr)
			if !ok {
				t.Fatalf("integration leaf is not a container call: %T", call.Args[1])
			}
			selector, ok := vertex.Fun.(*ast.SelectorExpr)
			if !ok {
				t.Fatalf("integration leaf runner is not a selector: %T", vertex.Fun)
			}
			actual = append(actual, name.Value+":"+selector.Sel.Name)
			return false
		})
	}

	expected := []string{
		`"backend-integration":backendIntegration`,
		`"frontend-production-build":frontendBuild`,
		`"database-contract":databaseContract`,
		`"database-upgrade":databaseUpgrade`,
		`"kafka-contract":kafkaContract`,
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("integration leaves = %v, want %v", actual, expected)
	}
}

func TestDatabaseContractCreatesOneFreshPostgresService(t *testing.T) {
	t.Parallel()

	sourcePath := filepath.Join("..", "integration.go")
	parsed, err := parser.ParseFile(token.NewFileSet(), sourcePath, nil, 0)
	if err != nil {
		t.Fatalf("parse %s: %v", sourcePath, err)
	}

	serviceCalls := map[string][]string{}
	for _, declaration := range parsed.Decls {
		function, ok := declaration.(*ast.FuncDecl)
		if !ok || (function.Name.Name != "databaseContract" &&
			function.Name.Name != "databaseUpgrade") {
			continue
		}
		ast.Inspect(function.Body, func(node ast.Node) bool {
			call, ok := node.(*ast.CallExpr)
			if !ok || len(call.Args) == 0 {
				return true
			}
			selector, ok := call.Fun.(*ast.SelectorExpr)
			if !ok || selector.Sel.Name != "postgresService" {
				return true
			}
			instance, ok := call.Args[0].(*ast.BasicLit)
			if !ok {
				t.Fatalf("%s postgres service instance is not literal", function.Name.Name)
			}
			serviceCalls[function.Name.Name] =
				append(serviceCalls[function.Name.Name], instance.Value)
			return true
		})
	}

	expected := map[string][]string{
		"databaseContract": {`"database-contract"`},
		"databaseUpgrade":  {`"upgrade"`},
	}
	if !reflect.DeepEqual(serviceCalls, expected) {
		t.Fatalf("database service instances = %v, want %v", serviceCalls, expected)
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
