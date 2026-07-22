# Dagger implementation

## Current state

No module exists at this checkpoint. The Dagger CLI and Go toolchain are absent, so creating `dagger.json`, Go source or generated bindings would be a guessed implementation. The installation and Podman gates are in [the toolchain baseline](DAGGER-TOOLCHAIN-BASELINE.md).

## Post-gate implementation contract

Initialize exactly one repository-local Go SDK module with the installed stable CLI, commit the bindings it generates, and declare the matching Engine version. Keep orchestration, service construction, version constants, result/redaction logic and pure decision logic separate. Its native checks will be `fast`, `integration`, and `smoke`; `dagger check` will be canonical complete execution.

The module must use workspace exclusions, pinned tools/images, Dagger cache volumes and secret types. It must return redacted, exportable failure artifacts and never invoke the Dagger CLI from its Go functions.
