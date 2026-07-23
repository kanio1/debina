package pure

import (
	"go/ast"
	"go/parser"
	"go/token"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strconv"
	"strings"
	"testing"
)

func TestEveryContainerImageLookupIsDeclaredAndLockProbed(t *testing.T) {
	t.Parallel()

	files := token.NewFileSet()
	mainFile, err := parser.ParseFile(files, filepath.Join("..", "main.go"), nil, 0)
	if err != nil {
		t.Fatalf("parse main.go: %v", err)
	}

	declared := map[string]string{}
	var probed []string
	for _, declaration := range mainFile.Decls {
		general, ok := declaration.(*ast.GenDecl)
		if !ok {
			continue
		}
		for _, specification := range general.Specs {
			value, ok := specification.(*ast.ValueSpec)
			if !ok {
				continue
			}
			for index, name := range value.Names {
				if general.Tok == token.CONST && strings.HasSuffix(name.Name, "Image") && index < len(value.Values) {
					literal, ok := value.Values[index].(*ast.BasicLit)
					if !ok || literal.Kind != token.STRING {
						t.Fatalf("%s must be a string literal", name.Name)
					}
					image, err := strconv.Unquote(literal.Value)
					if err != nil {
						t.Fatalf("unquote %s: %v", name.Name, err)
					}
					declared[name.Name] = image
				}
				if name.Name == "runtimeImages" && len(value.Values) == 1 {
					list, ok := value.Values[0].(*ast.CompositeLit)
					if !ok {
						t.Fatal("runtimeImages must be a composite literal")
					}
					for _, element := range list.Elts {
						identifier, ok := element.(*ast.Ident)
						if !ok {
							t.Fatal("runtimeImages entries must name declared image constants")
						}
						probed = append(probed, identifier.Name)
					}
				}
			}
		}
	}

	var declaredNames []string
	for name := range declared {
		declaredNames = append(declaredNames, name)
	}
	sort.Strings(declaredNames)
	sort.Strings(probed)
	if !reflect.DeepEqual(probed, declaredNames) {
		t.Fatalf("runtimeImages = %v, declared image constants = %v", probed, declaredNames)
	}

	lockPath := filepath.Join("..", "..", ".dagger", "lock")
	lockBytes, err := os.ReadFile(lockPath)
	if err != nil {
		t.Fatalf("read %s: %v", lockPath, err)
	}
	lock := string(lockBytes)
	for name, image := range declared {
		if !strings.Contains(lock, image+`"`) {
			t.Errorf("%s image %q has no frozen lock entry", name, image)
		}
	}

	entries, err := os.ReadDir("..")
	if err != nil {
		t.Fatalf("read Dagger module source: %v", err)
	}
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".go") || strings.HasSuffix(entry.Name(), "_test.go") || entry.Name() == "image_lock.go" {
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
			if !ok || selector.Sel.Name != "From" || len(call.Args) != 1 {
				return true
			}
			identifier, ok := call.Args[0].(*ast.Ident)
			if !ok {
				t.Errorf("%s contains non-declared Container.From lookup", path)
				return true
			}
			if _, ok := declared[identifier.Name]; !ok {
				t.Errorf("%s uses Container.From(%s), which is absent from runtimeImages", path, identifier.Name)
			}
			return true
		})
	}
}
