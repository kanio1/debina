package pure

import (
	"encoding/json"
	"fmt"
	"reflect"
)

const D3AFrontendCallback = "http://frontend:3000/api/auth/callback"
const D3AFrontendOrigin = "http://frontend:3000"
const OverlaySuccessMarker = "D3A realm overlay structurally verified: exact callback and origin added; all other structure unchanged"

// DeriveRealmOverlay adds only the approved sepa-web entries and verifies the
// parsed realm structure before returning a deterministic derived artifact.
func DeriveRealmOverlay(input []byte, callback, origin string) ([]byte, error) {
	if callback != D3AFrontendCallback || origin != D3AFrontendOrigin {
		return nil, fmt.Errorf("invalid approved overlay values")
	}
	var canonical map[string]any
	if err := json.Unmarshal(input, &canonical); err != nil {
		return nil, fmt.Errorf("decode canonical realm: %w", err)
	}
	derived, err := cloneMap(canonical)
	if err != nil {
		return nil, err
	}
	_, client, err := sepaWebClient(derived)
	if err != nil {
		return nil, err
	}
	for _, change := range []struct {
		key, value string
	}{{"redirectUris", callback}, {"webOrigins", origin}} {
		values, err := stringArray(client[change.key], "sepa-web "+change.key)
		if err != nil {
			return nil, err
		}
		if occurrences(values, change.value) > 1 {
			return nil, fmt.Errorf("duplicate approved %s", change.key)
		}
		if occurrences(values, change.value) == 0 {
			client[change.key] = append(values, change.value)
		}
	}
	if err := verifyRealmOverlay(canonical, derived, callback, origin); err != nil {
		return nil, err
	}
	output, err := json.Marshal(derived)
	if err != nil {
		return nil, fmt.Errorf("encode derived realm: %w", err)
	}
	return output, nil
}

func cloneMap(value map[string]any) (map[string]any, error) {
	raw, err := json.Marshal(value)
	if err != nil {
		return nil, fmt.Errorf("clone realm: %w", err)
	}
	var clone map[string]any
	if err := json.Unmarshal(raw, &clone); err != nil {
		return nil, fmt.Errorf("decode cloned realm: %w", err)
	}
	return clone, nil
}

func verifyRealmOverlay(canonical, derived map[string]any, callback, origin string) error {
	canonicalClients, canonicalWeb, err := sepaWebClient(canonical)
	if err != nil {
		return fmt.Errorf("canonical realm: %w", err)
	}
	derivedClients, derivedWeb, err := sepaWebClient(derived)
	if err != nil {
		return fmt.Errorf("derived realm: %w", err)
	}
	if err := sameKeys(canonical, derived, "realm"); err != nil {
		return err
	}
	for key, value := range canonical {
		if key != "clients" && !reflect.DeepEqual(value, derived[key]) {
			return fmt.Errorf("unauthorized realm property change: %s", key)
		}
	}
	if len(canonicalClients) != len(derivedClients) {
		return fmt.Errorf("client count changed")
	}
	for i := range canonicalClients {
		canonicalClient, ok := canonicalClients[i].(map[string]any)
		if !ok {
			return fmt.Errorf("canonical client %d is not an object", i)
		}
		derivedClient, ok := derivedClients[i].(map[string]any)
		if !ok {
			return fmt.Errorf("derived client %d is not an object", i)
		}
		canonicalID, ok := canonicalClient["clientId"].(string)
		if !ok {
			return fmt.Errorf("canonical client %d has invalid clientId", i)
		}
		derivedID, ok := derivedClient["clientId"].(string)
		if !ok || canonicalID != derivedID {
			return fmt.Errorf("client order or identity changed")
		}
		if canonicalID != "sepa-web" && !reflect.DeepEqual(canonicalClient, derivedClient) {
			return fmt.Errorf("unauthorized client change: %s", canonicalID)
		}
	}
	if err := sameKeys(canonicalWeb, derivedWeb, "sepa-web"); err != nil {
		return err
	}
	for key, value := range canonicalWeb {
		if key != "redirectUris" && key != "webOrigins" && !reflect.DeepEqual(value, derivedWeb[key]) {
			return fmt.Errorf("unauthorized sepa-web property change: %s", key)
		}
	}
	if err := verifyAdded(canonicalWeb["redirectUris"], derivedWeb["redirectUris"], callback, "redirect URI"); err != nil {
		return err
	}
	if err := verifyAdded(canonicalWeb["webOrigins"], derivedWeb["webOrigins"], origin, "web origin"); err != nil {
		return err
	}
	return nil
}

func sepaWebClient(realm map[string]any) ([]any, map[string]any, error) {
	clients, ok := realm["clients"].([]any)
	if !ok {
		return nil, nil, fmt.Errorf("clients must be an array")
	}
	var found map[string]any
	count := 0
	for i, entry := range clients {
		client, ok := entry.(map[string]any)
		if !ok {
			return nil, nil, fmt.Errorf("client %d is not an object", i)
		}
		id, ok := client["clientId"].(string)
		if !ok {
			return nil, nil, fmt.Errorf("client %d has invalid clientId", i)
		}
		if id == "sepa-web" {
			found = client
			count++
		}
	}
	if count != 1 {
		return nil, nil, fmt.Errorf("expected exactly one sepa-web client")
	}
	return clients, found, nil
}

func sameKeys(canonical, derived map[string]any, scope string) error {
	for key := range canonical {
		if _, ok := derived[key]; !ok {
			return fmt.Errorf("%s property removed: %s", scope, key)
		}
	}
	for key := range derived {
		if _, ok := canonical[key]; !ok {
			return fmt.Errorf("%s property added: %s", scope, key)
		}
	}
	return nil
}

func verifyAdded(before, after any, approved, label string) error {
	canonical, err := stringArray(before, "canonical "+label+"s")
	if err != nil {
		return err
	}
	derived, err := stringArray(after, "derived "+label+"s")
	if err != nil {
		return err
	}
	if occurrences(canonical, approved) > 1 {
		return fmt.Errorf("duplicate approved %s", label)
	}
	expected := append([]any{}, canonical...)
	if occurrences(canonical, approved) == 0 {
		expected = append(expected, approved)
	}
	if !reflect.DeepEqual(expected, derived) {
		return fmt.Errorf("unauthorized %s array change", label)
	}
	if occurrences(derived, approved) != 1 {
		return fmt.Errorf("approved %s count invalid", label)
	}
	return nil
}

func stringArray(value any, label string) ([]any, error) {
	values, ok := value.([]any)
	if !ok {
		return nil, fmt.Errorf("%s must be an array", label)
	}
	for _, value := range values {
		if _, ok := value.(string); !ok {
			return nil, fmt.Errorf("%s must contain only strings", label)
		}
	}
	return values, nil
}

func occurrences(values []any, sought string) int {
	count := 0
	for _, value := range values {
		if value == sought {
			count++
		}
	}
	return count
}
