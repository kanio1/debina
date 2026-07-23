package pure

import (
	"fmt"
	"strings"
)

// BoundedUnavailableCommand is a finite readiness probe for an intentionally
// absent endpoint. It never prints environment, headers, credentials, cookies,
// or response bodies; callers use it only behind an expected-failure assertion.
func BoundedUnavailableCommand(url, classification string, attempts int) string {
	if attempts < 1 {
		panic("attempts must be positive")
	}
	return fmt.Sprintf(`set -eu
attempts=0
until curl --fail --silent --show-error --max-time 1 %s >/dev/null; do
  attempts=$((attempts + 1))
  if [ "$attempts" -ge %d ]; then
    echo %s >&2
    exit 1
  fi
  sleep 1
done
echo "unexpected readiness success" >&2
exit 0`, shellWord(url), attempts, shellWord(classification))
}

func shellWord(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "'\\\"'\\\"'") + "'"
}
