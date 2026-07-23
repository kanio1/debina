#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
artifact_directory="${1:-$(mktemp -d /tmp/debina-dagger-architecture.XXXXXX)}"
proof_nonce="architecture-$(date -u +%Y%m%dT%H%M%SZ)-$$"
negative_lock_root=""

cleanup() {
  if [[ "${negative_lock_root}" == /tmp/debina-dagger-frozen-lock.* ]] &&
      [ -d "${negative_lock_root}" ]; then
    rm -rf -- "${negative_lock_root}"
  fi
}
trap cleanup EXIT

mkdir -p "${artifact_directory}"
export DAGGER_NO_NAG=1
export NO_COLOR=1

cd "${repository_root}"

(
  cd dagger
  go test ./...
  go vet ./...
  test -z "$(gofmt -l -- *.go pure/*.go cmd/*/*.go)"
)
git diff --check

mapfile -t automatic_check_files < <(rg -l '^\s*// \+check$' dagger --glob '*.go')
test "${#automatic_check_files[@]}" -eq 1
test "${automatic_check_files[0]}" = "dagger/checks.go"
rg -q -U '// \+check\nfunc \(m \*DebinaVerification\) Acceptance' dagger/checks.go
test -z "$(rg 'fastBackendTests|backendTestsWithoutTestcontainers|-Dtest=' dagger/backend.go || true)"
test -z "$(rg '\bdagger (call|check|develop)\b' dagger --glob '*.go' --glob '!*.gen.go' || true)"

generated_bindings=(
  dagger/dagger.gen.go
  dagger/internal/dagger/dagger.gen.go
)
sha256sum "${generated_bindings[@]}" >"${artifact_directory}/bindings.before.sha256"
dagger develop -y >"${artifact_directory}/dagger-develop.log" 2>&1
sha256sum "${generated_bindings[@]}" >"${artifact_directory}/bindings.after-develop.sha256"
cmp "${artifact_directory}/bindings.before.sha256" \
  "${artifact_directory}/bindings.after-develop.sha256"
dagger check --generate >"${artifact_directory}/dagger-check-generate.log" 2>&1
sha256sum "${generated_bindings[@]}" >"${artifact_directory}/bindings.after-check-generate.sha256"
cmp "${artifact_directory}/bindings.before.sha256" \
  "${artifact_directory}/bindings.after-check-generate.sha256"

dagger check -l >"${artifact_directory}/checks.list" 2>&1
test "$(rg -c 'debina-verification:acceptance' "${artifact_directory}/checks.list")" -eq 1
test "$(rg -c 'debina-verification:' "${artifact_directory}/checks.list")" -eq 1

dagger functions >"${artifact_directory}/functions.list" 2>&1
canonical_functions=(
  fast
  integration
  smoke-auth
  smoke-payments
  smoke-suite
  acceptance
  pipeline-assurance
  backend-testcontainers
  backend-regression-all
  full-local
)
deprecated_aliases=(
  smoke
  phase-d
  all-socket-free
  all
  testcontainers-regression
  cache-reuse
  cache-invalidation
)
for function_name in "${canonical_functions[@]}" "${deprecated_aliases[@]}"; do
  rg -q "^${function_name}[[:space:]]" "${artifact_directory}/functions.list"
done

dagger call image-lock-probe \
  --proof-nonce "${proof_nonce}" \
  --lock=frozen \
  --progress=plain >"${artifact_directory}/frozen-lock.log" 2>&1
rg -q 'DebinaVerification\.imageLockProbe DONE' \
  "${artifact_directory}/frozen-lock.log"

negative_lock_root="$(mktemp -d /tmp/debina-dagger-frozen-lock.XXXXXX)"
cp -a dagger.json .dagger dagger "${negative_lock_root}/"
sed -i '\|docker.io/curlimages/curl:8.16.0|d' "${negative_lock_root}/.dagger/lock"
if (
  cd "${negative_lock_root}/dagger"
  go test ./pure -run TestEveryContainerImageLookupIsDeclaredAndLockProbed
) >"${artifact_directory}/frozen-lock-missing.log" 2>&1; then
  printf 'lock completeness regression unexpectedly accepted a missing image entry\n' >&2
  exit 1
fi
rg -q 'has no frozen lock entry' "${artifact_directory}/frozen-lock-missing.log"

dagger check \
  --lock=frozen \
  --progress=plain >"${artifact_directory}/acceptance.log" 2>&1
dagger call pipeline-assurance \
  --lock=frozen \
  --progress=plain >"${artifact_directory}/pipeline-assurance.log" 2>&1

tools/ci/verify-dagger-log-health.sh \
  "${artifact_directory}/acceptance.log" \
  >"${artifact_directory}/acceptance-log-health.txt"
tools/ci/verify-dagger-log-health.sh \
  "${artifact_directory}/pipeline-assurance.log" \
  >"${artifact_directory}/pipeline-assurance-log-health.txt"
rg -q 'DAGGER-LOG-HEALTH-PROVEN' "${artifact_directory}/acceptance-log-health.txt"
rg -q 'DAGGER-LOG-HEALTH-PROVEN' \
  "${artifact_directory}/pipeline-assurance-log-health.txt"

tools/ci/verify-dagger-cache.sh "${artifact_directory}/cache" \
  >"${artifact_directory}/cache-runner.txt"
rg -q 'OUTPUT-DETERMINISM-PROVEN' "${artifact_directory}/cache/summary.txt"
rg -q 'CACHE-REUSE-TRACE-PROVEN' "${artifact_directory}/cache/summary.txt"
rg -q 'SELECTIVE-INVALIDATION-TRACE-PROVEN' \
  "${artifact_directory}/cache/summary.txt"

dagger call cache-volume-stress \
  --namespace "${proof_nonce}" \
  --reset=true \
  --lock=frozen \
  --progress=plain >"${artifact_directory}/cache-volume.log" 2>&1
rg -q 'SHARED-CACHE-CONCURRENT-WRITERS-PROVEN' \
  "${artifact_directory}/cache-volume.log"

if dagger call aggregate-unexpected-failure-probe \
  --lock=frozen \
  --progress=plain >"${artifact_directory}/unexpected-failure.log" 2>&1; then
  printf 'unexpected-child propagation probe unexpectedly returned zero\n' >&2
  exit 1
fi
rg -q 'unexpected-child: PHASE-D UNEXPECTED CHILD FAILURE' \
  "${artifact_directory}/unexpected-failure.log"

{
  printf 'DAGGER-ARCHITECTURE-VERIFICATION-PROVEN\n'
  printf 'ARTIFACT_DIRECTORY=%s\n' "${artifact_directory}"
  printf 'AUTOMATIC_CHECK=acceptance\n'
  printf 'GENERATOR-IDEMPOTENCE-PROVEN\n'
  printf 'FROZEN-LOCK-COMPLETE-AND-MISSING-FAIL-CLOSED\n'
  printf 'ACCEPTANCE-LOG-HEALTH-PROVEN\n'
  printf 'PIPELINE-ASSURANCE-LOG-HEALTH-PROVEN\n'
  printf 'CACHE-TRACE-PROVEN\n'
  printf 'CACHE-VOLUME-CONCURRENCY-PROVEN\n'
  printf 'UNEXPECTED-FAILURE-PROPAGATION-PROVEN\n'
} | tee "${artifact_directory}/summary.txt"
