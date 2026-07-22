package pure

import "strings"

// D3AReadinessCommand creates the bounded service-readiness gate used before
// the browser smoke. A failed dependency is terminal: the Playwright command
// is not run after the timeout.
func D3AReadinessCommand(urls []string) string {
	return `set -eu
for url in ` + strings.Join(urls, " ") + `; do
  attempts=0
  until curl --fail --silent --show-error "$url" >/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 120 ]; then
      echo "D3A readiness timed out for $url" >&2
      exit 1
    fi
    sleep 1
  done
done
pnpm run test:smoke:d3a`
}
