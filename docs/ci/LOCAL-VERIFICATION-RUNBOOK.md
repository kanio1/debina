# Local verification runbook

## Current checkpoint

Phase D cannot yet be run: see [the toolchain baseline](DAGGER-TOOLCHAIN-BASELINE.md). Do not install tools or reconfigure Podman through this runbook automatically.

## After the owner completes the prerequisites

1. Run `dagger version`, `dagger core version`, `dagger check --help`, `go version`, and `podman info`.
2. Run the deterministic no-repository-content Dagger engine probe.
3. Generate the single Go module using that exact stable CLI and inspect its changeset before applying it.
4. Run the recorded Dagger-native check list, beginning with `dagger check -l` and `dagger check fast`.
5. Export only redacted failure artifacts through the module's documented artifact function.

No remote provider, GitHub Actions, `act`, deployment or Wave 12 action is part of this runbook.
