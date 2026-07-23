# Local verification runbook

## Current checkpoint

Phase D is runnable on the pinned local toolchain described by
[the toolchain baseline](DAGGER-TOOLCHAIN-BASELINE.md). Do not install, upgrade
or reconfigure Dagger or Podman through this runbook automatically.

## Verification sequence

1. Run `dagger version`, `dagger core version`, `dagger check --help`, `go version`, and `podman info`.
2. Run the deterministic no-repository-content Dagger engine probe.
3. Run `dagger develop -y`, then prove `go test ./...` in `dagger/` and `dagger check --generate`.
4. Run `dagger check -l`; it must list exactly `acceptance`.
5. Run the focused callable gates as needed: `fast`, `integration`,
   `smoke-auth`, `smoke-payments`, `smoke-suite` and the independent
   `pipeline-assurance`.
6. Run `tools/ci/verify-dagger-architecture.sh`; it owns the canonical frozen
   `dagger check`, assurance, both log-health scans, generator, lock, cache,
   cache-volume and unexpected-failure proofs.
7. Run `backend-testcontainers` and `full-local` only with the explicitly
   supplied typed Podman socket argument.
8. Use `backend-regression-all` only as the full-suite coverage oracle; it is
   not a child of `full-local`.
9. Export only redacted failure artifacts through the module's documented
   artifact function.

No remote provider, GitHub Actions, `act`, deployment or Wave 12 action is part of this runbook.
