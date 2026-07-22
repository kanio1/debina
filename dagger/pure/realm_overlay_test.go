package pure

import (
	"encoding/json"
	"reflect"
	"strings"
	"testing"
)

const realmFixture = `{"realm":"sepa-nexus","enabled":true,"clients":[{"clientId":"sepa-web","secret":"redacted","redirectUris":["http://localhost:3000/*"],"webOrigins":["http://localhost:3000"],"standardFlowEnabled":true},{"clientId":"other","redirectUris":[],"webOrigins":[]}]}`

func TestDeriveRealmOverlayAddsOnlyApprovedEntriesAndIsIdempotent(t *testing.T) {
	first, err := DeriveRealmOverlay([]byte(realmFixture), D3AFrontendCallback, D3AFrontendOrigin)
	if err != nil {
		t.Fatal(err)
	}
	second, err := DeriveRealmOverlay(first, D3AFrontendCallback, D3AFrontendOrigin)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(parseRealm(t, first), parseRealm(t, second)) {
		t.Fatal("overlay was not structurally idempotent")
	}
	canonical := parseRealm(t, []byte(realmFixture))
	derived := parseRealm(t, first)
	if err := verifyRealmOverlay(canonical, derived, D3AFrontendCallback, D3AFrontendOrigin); err != nil {
		t.Fatal(err)
	}
}

func TestDeriveRealmOverlayRejectsInvalidInputs(t *testing.T) {
	tests := []struct {
		name   string
		input  string
		call   string
		origin string
	}{
		{"invalid callback", realmFixture, "http://bad/callback", D3AFrontendOrigin},
		{"invalid origin", realmFixture, D3AFrontendCallback, "http://bad"},
		{"malformed json", `{`, D3AFrontendCallback, D3AFrontendOrigin},
		{"missing clients", `{}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"malformed clients", `{"clients":{}}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"malformed client", `{"clients":["bad"]}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"missing sepa-web", `{"clients":[{"clientId":"other"}]}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"duplicate sepa-web", `{"clients":[{"clientId":"sepa-web","redirectUris":[],"webOrigins":[]},{"clientId":"sepa-web","redirectUris":[],"webOrigins":[]}]}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"bad redirect array", `{"clients":[{"clientId":"sepa-web","redirectUris":{},"webOrigins":[]}]}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"bad origin array", `{"clients":[{"clientId":"sepa-web","redirectUris":[],"webOrigins":{}}]}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"non string redirect", `{"clients":[{"clientId":"sepa-web","redirectUris":[1],"webOrigins":[]}]}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"non string origin", `{"clients":[{"clientId":"sepa-web","redirectUris":[],"webOrigins":[1]}]}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"duplicate callback", `{"clients":[{"clientId":"sepa-web","redirectUris":["http://frontend:3000/api/auth/callback","http://frontend:3000/api/auth/callback"],"webOrigins":[]}]}`, D3AFrontendCallback, D3AFrontendOrigin},
		{"duplicate origin", `{"clients":[{"clientId":"sepa-web","redirectUris":[],"webOrigins":["http://frontend:3000","http://frontend:3000"]}]}`, D3AFrontendCallback, D3AFrontendOrigin},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := DeriveRealmOverlay([]byte(test.input), test.call, test.origin)
			if err == nil {
				t.Fatal("expected fail-closed rejection")
			}
			if strings.Contains(err.Error(), "redacted") {
				t.Fatal("error exposed fixture secret")
			}
		})
	}
}

func TestVerifyRealmOverlayRejectsUnauthorizedStructuralChanges(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(map[string]any, map[string]any)
	}{
		{"top level addition", func(_, derived map[string]any) { derived["unexpected"] = true }},
		{"top level removal", func(_, derived map[string]any) { delete(derived, "enabled") }},
		{"top level modification", func(_, derived map[string]any) { derived["enabled"] = false }},
		{"another client mutation", func(_, derived map[string]any) { clients(derived)[1].(map[string]any)["changed"] = true }},
		{"changed client order", func(_, derived map[string]any) {
			values := clients(derived)
			values[0], values[1] = values[1], values[0]
		}},
		{"sepa property addition", func(_, derived map[string]any) { sepa(derived)["unexpected"] = true }},
		{"sepa property removal", func(_, derived map[string]any) { delete(sepa(derived), "standardFlowEnabled") }},
		{"sepa property modification", func(_, derived map[string]any) { sepa(derived)["standardFlowEnabled"] = false }},
		{"additional redirect", func(_, derived map[string]any) {
			sepa(derived)["redirectUris"] = append(sepa(derived)["redirectUris"].([]any), "http://bad/callback")
		}},
		{"additional origin", func(_, derived map[string]any) {
			sepa(derived)["webOrigins"] = append(sepa(derived)["webOrigins"].([]any), "http://bad")
		}},
		{"wildcard redirect", func(_, derived map[string]any) {
			sepa(derived)["redirectUris"] = append(sepa(derived)["redirectUris"].([]any), "http://frontend:3000/*")
		}},
		{"wildcard origin", func(_, derived map[string]any) {
			sepa(derived)["webOrigins"] = append(sepa(derived)["webOrigins"].([]any), "http://frontend:*")
		}},
		{"removed canonical redirect", func(_, derived map[string]any) { sepa(derived)["redirectUris"] = []any{D3AFrontendCallback} }},
		{"reordered canonical redirect", func(_, derived map[string]any) {
			sepa(derived)["redirectUris"] = []any{D3AFrontendCallback, "http://localhost:3000/*"}
		}},
		{"removed canonical origin", func(_, derived map[string]any) { sepa(derived)["webOrigins"] = []any{D3AFrontendOrigin} }},
		{"reordered canonical origin", func(_, derived map[string]any) {
			sepa(derived)["webOrigins"] = []any{D3AFrontendOrigin, "http://localhost:3000"}
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			canonical := parseRealm(t, []byte(realmFixture))
			derived := parseRealm(t, mustOverlay(t, []byte(realmFixture)))
			test.mutate(canonical, derived)
			err := verifyRealmOverlay(canonical, derived, D3AFrontendCallback, D3AFrontendOrigin)
			if err == nil {
				t.Fatal("expected structural rejection")
			}
			if strings.Contains(err.Error(), "redacted") {
				t.Fatal("error exposed fixture secret")
			}
		})
	}
}

func parseRealm(t *testing.T, input []byte) map[string]any {
	t.Helper()
	var result map[string]any
	if err := json.Unmarshal(input, &result); err != nil {
		t.Fatal(err)
	}
	return result
}

func mustOverlay(t *testing.T, input []byte) []byte {
	t.Helper()
	output, err := DeriveRealmOverlay(input, D3AFrontendCallback, D3AFrontendOrigin)
	if err != nil {
		t.Fatal(err)
	}
	return output
}

func clients(realm map[string]any) []any       { return realm["clients"].([]any) }
func sepa(realm map[string]any) map[string]any { return clients(realm)[0].(map[string]any) }
