#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  printf 'usage: %s TRACE...\n' "$0" >&2
  exit 2
fi

repository_root="$(git rev-parse --show-toplevel)"
(
  cd "${repository_root}/dagger"
  go run ./cmd/log-health "$@"
)
