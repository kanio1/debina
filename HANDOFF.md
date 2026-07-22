# HANDOFF

## Zadanie

Debina is a synthetic enterprise SEPA/ISO 20022 payment-processing research platform. Phase D is authorized to create one provider-neutral, local Dagger Go SDK verification platform, but it is currently at a tooling checkpoint rather than implementation completion.

## Zrobione

- Verified `rebase/enterprise-evolution` at baseline `676d09e19393830936f5bf419b40f4a5eaa2a4c3`; updated `planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md` so D/E/F continue on this long-lived branch while preserving historical branch records.
- Recorded the Dagger/Go/Podman gate in `docs/ci/DAGGER-TOOLCHAIN-BASELINE.{md,yaml}`, the exact ADR-N16 smoke inventory in `docs/ci/SMOKE-CAPABILITY-MATRIX.{md,yaml}`, Phase D status in `planning/programs/DEBINA-ENTERPRISE-REBASE-PHASE-D.md`, and the local-only workflow in `.claude/skills/dagger-go-pipeline/SKILL.md`.
- Added checkpoint implementation/check-manifest/image/Flyway/runbook/troubleshooting documents under `docs/ci/`; skills validation and the enterprise governance runner pass with the established 296 traceability and 69 planning legacy warnings.

## Utknęliśmy na

`dagger` and `go` are absent. Podman 5.8.4 is installed but rootless; Dagger's official Podman guidance requires rootful execution and a safe Engine PID limit. No Dagger module, bindings, Dagger engine probe, Testcontainers-in-Dagger spike, Flyway/RLS/Kafka graph, Playwright smoke, cache timing or adversarial proof has run. The pre-existing user-owned `build/generated-spring-modulith/javadoc.json` remains modified but unchanged from bootstrap SHA-256 `9c484e010bfa0a8719f78dd4ade744fe7e08a3a9fe7eaf0fb35ed1dd2ca0a015` and must not be staged or altered.

## Plan na następny krok

After the workstation owner installs pinned Dagger `v0.21.4`, a compatible Go toolchain and satisfies the official Podman prerequisite, run `dagger version && dagger core version && dagger check --help`, then execute the deterministic no-repository-content Dagger engine probe before generating the module.

## Pułapki, których nie wolno powtórzyć

- Do not guess a Dagger module/config/binding format or silently install Dagger/Go; use the installed stable CLI to generate it.
- Do not weaken Podman security, use Docker, add a Docker symlink, use sudo, change PID limits or start a privileged runner without explicit user authorization.
- Do not resume, switch to, merge, cherry-pick, rebase or modify `wip/wave-12-signature-verdict-evidence` / `5ebebb0` before Phase F.
- Do not stage, restore, regenerate or include `build/generated-spring-modulith/javadoc.json`; remote CI, `.github/workflows`, `act`, deployment and payment-feature work remain out of scope.
