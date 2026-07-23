#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
artifact_directory="${1:-$(mktemp -d /tmp/debina-dagger-cache-proof.XXXXXX)}"
proof_nonce="cache-proof-$(date -u +%Y%m%dT%H%M%SZ)-$$"

mkdir -p "${artifact_directory}"

run_probe() {
  local source_input="$1"
  local output_file="$2"

  (
    cd "${repository_root}"
    dagger call cache-probe \
      --source-input "${source_input}" \
      --configuration baseline \
      --lock=frozen \
      --progress=plain
  ) >"${output_file}" 2>&1
}

cold_trace="${artifact_directory}/cold.log"
warm_trace="${artifact_directory}/warm.log"
changed_trace="${artifact_directory}/changed-input.log"

run_probe "${proof_nonce}" "${cold_trace}"
run_probe "${proof_nonce}" "${warm_trace}"
run_probe "${proof_nonce}-changed" "${changed_trace}"

cold_digest="$(sed -n 's/.*PHASE-D CACHE-PROBE \([0-9a-f]\{64\}\).*/\1/p' "${cold_trace}" | tail -1)"
warm_digest="$(sed -n 's/.*PHASE-D CACHE-PROBE \([0-9a-f]\{64\}\).*/\1/p' "${warm_trace}" | tail -1)"
changed_digest="$(sed -n 's/.*PHASE-D CACHE-PROBE \([0-9a-f]\{64\}\).*/\1/p' "${changed_trace}" | tail -1)"

test -n "${cold_digest}"
test "${cold_digest}" = "${warm_digest}"
test "${cold_digest}" != "${changed_digest}"

rg -q 'DebinaVerification\.cacheProbe DONE' "${cold_trace}"
rg -q 'Container\.withExec DONE' "${cold_trace}"
rg -q 'DebinaVerification\.cacheProbe CACHED' "${warm_trace}"
rg -q 'Container\.withExec DONE' "${changed_trace}"
rg -q '(Directory\.file|Container\.withFile) CACHED' "${changed_trace}"

summary="${artifact_directory}/summary.txt"
{
  printf 'OUTPUT-DETERMINISM-PROVEN digest=%s\n' "${cold_digest}"
  printf 'CACHE-REUSE-TRACE-PROVEN trace=%s\n' "${warm_trace}"
  printf 'SELECTIVE-INVALIDATION-TRACE-PROVEN changed_digest=%s trace=%s\n' "${changed_digest}" "${changed_trace}"
  printf 'COLD-TRACE=%s\n' "${cold_trace}"
  printf 'RELEVANT-VERTICES\n'
  rg 'DebinaVerification\.cacheProbe|Container\.withExec|Directory\.file|Container\.withFile|Container\.from' \
    "${cold_trace}" "${warm_trace}" "${changed_trace}"
} >"${summary}"

printf 'Dagger cache proof: %s\n' "${summary}"
