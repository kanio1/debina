---
name: dagger-go-pipeline
description: Use when creating, changing, diagnosing or running Debina's local Dagger Go SDK verification platform; it gates on the installed CLI/Engine, Podman safety and the Phase D local-only scope.
---
# Dagger Go pipeline

## Purpose and triggers

Use for `dagger check`, Dagger Go module code, generated bindings, local service graphs, Dagger caches, Dagger diagnostics, or Phase D verification documentation. Do not use for GitHub Actions, `act`, remote CI, deployment/release automation, host-runtime reconfiguration, payment features or migration changes that do not affect composed verification.

When Technical Architect `PIPELINE_IMPACT` is not `NONE`, this skill is required.

## Read first

Read `AGENTS.md`, `HANDOFF.md`, `docs/ci/DAGGER-IMPLEMENTATION.md`, `docs/ci/DAGGER-CHECK-MANIFEST.yaml`, `docs/ci/DAGGER-TOOLCHAIN-BASELINE.yaml`, `docs/ci/DAGGER-IMAGE-BASELINE.yaml`, ADR-N16 and the relevant local `AGENTS.md`. Inspect the actual repository commands before composing them.

## Compatibility and Podman gates

Before creating or changing module files, run `dagger version`, `dagger core version`, `dagger check --help`, `dagger functions --help`, `go version` and `podman info`. Never guess a Dagger generation or create hand-written bindings. When tooling is absent, record `TOOLING-BLOCKED-CHECKPOINT`; do not install tools silently.

Let Dagger auto-detect the available OCI runtime, then prove a deterministic no-repository-content container. Do not install Docker, add a `docker` symlink, enable rootful Podman, change groups/services/PID limits, use sudo, or create a privileged runner without explicit user authorization. Record official prerequisites and return `ENVIRONMENT-BLOCKED-CHECKPOINT` when they are unmet.

## Graph and cache efficiency gate

### Input boundaries

- Prefer the smallest meaningful `Directory` or `File`.
- Keep backend, frontend, Dagger and governance inputs separate.
- Use tight include/exclude filters; manifests and lockfiles are dependency-layer inputs; source overlays come after.
- Exclude generated/build output unless it is authoritative input.
- Do not mount the entire repository when a package or manifest subset suffices.
- Pass secrets only to the exact step that needs them.
- A README or unrelated documentation edit must not invalidate Maven, pnpm, Playwright or Go work unless intentionally listed as input.

### Graph design

- Typed Dagger functions and typed arguments; compose graph values directly.
- Never shell from one function into the Dagger CLI.
- Reuse existing containers, directories and shared service constructors.
- Materialise only at final output boundaries.
- Do not expose heavy graphs as overlapping automatic `+check` roots.
- Do not duplicate the same heavy child inside composed parents.

### Result cache versus named cache volumes

**Dagger result/layer cache** follows graph inputs and may skip command execution entirely. A cache hit is **not** a second runtime proof.

**Named cache volumes** accelerate mutable tool data only:

- Maven local repository (`debina-maven-jdk25` → `/root/.m2/repository`)
- pnpm store (`debina-pnpm-node24.18.0-pnpm10.33.0` → `/pnpm/store`)
- Go module cache (`debina-dagger-go-1.26.5` → `/go/pkg/mod`)
- Playwright browser download cache only when justified

**Forbidden named caches:** PostgreSQL data; Kafka state; Keycloak state; application DB volumes; credentials; cookies/tokens; raw payment/XML evidence; test reports as correctness input; pass/fail outcomes; `.env.local`; host runtime sockets; generated random identities used as proof.

The pipeline must remain correct when named caches are empty.

### Cache naming

Stable names based only on compatibility dimensions (tool/runtime major or pinned version, package-manager generation, architecture when required). Do **not** put commit SHA, timestamp, branch name or test nonce in ordinary dependency cache names. A `proofNonce` may alter a runtime instance / result-cache input for an explicit fresh execution, but must not create an unbounded new dependency cache volume.

### Determinism and invalidation

Prove when relevant: identical meaningful inputs can reuse cache; a source change invalidates only affected leaves; a lockfile change invalidates dependency work; unrelated documentation does not invalidate code work; cache absence does not break correctness; fresh runtime proof uses an explicit input such as `proofNonce`; reports distinguish result-cache hit, named-cache reuse and actual command execution. Do not add timestamps or randomness to every function merely to force execution.

### Services and volumes

Databases, Kafka and Keycloak remain ephemeral Dagger services. No persistent state volume to make tests pass. Readiness checks must use the same service instance later consumed. Bound lifecycle; no host ports unless the execution model requires them; no hidden Compose dependency for Dagger-native proofs; no privileged/rootful runtime change without explicit authorization.

## Build workflow

Use one Go module generated by the installed stable CLI. Use first-class no-argument checks where supported: `fast`, `integration`, `smoke`; unfiltered `dagger check` is canonical. Expose `all` only after proving it is a non-duplicating native alias.

## Check selection and security

The governance script remains authoritative; Dagger only orchestrates it. Use the manifest for exact backend selection. Preserve the Maven Wrapper, Node `24.18.0`, pnpm `10.33.0`, committed generated GraphQL output and BFF-only token boundary. Inject ephemeral values and Dagger secrets without logging tokens, cookies, passwords or client/database secrets.

The ADR-N16 smoke scope is exactly six journeys. E1 `smoke-signed-pain-001` is separately callable and outside that cap. Use actual routes and accessible locators; do not add a payment feature or alter production UI merely for a test.

## Go quality

Generated bindings only via the installed compatible CLI. Idiomatic package boundaries; small pure helpers for unit-tested graph policy; `gofmt`; `go vet`; explicit error handling; no panic for expected pipeline failures; `context.Context` where applicable; no orphan goroutines; helpful test failures; table-driven tests where clearer; interfaces only at real consumer boundaries; document exported Dagger functions and important types.

## Verification and anti-patterns

Run Dagger unit tests for the changed surface and `git diff --check`. When
topology, cache policy, check manifests or public callables change, also run
`tools/ci/verify-dagger-architecture.sh` (or the smallest focused subset that
covers the changed contract). Never claim an unrun check passed, never use
remote CI/`act`, never copy a compose graph into host orchestration, and never
touch Wave 12.

## CORRECT / WRONG

CORRECT: explicit `proofNonce` forces a fresh isolated runtime proof

WRONG: a repeated result-cache hit is described as a second test execution

## References

- [Implementation](../../../docs/ci/DAGGER-IMPLEMENTATION.md)
- [Check manifest](../../../docs/ci/DAGGER-CHECK-MANIFEST.yaml)
- [Toolchain baseline](../../../docs/ci/DAGGER-TOOLCHAIN-BASELINE.yaml)
- Path-scoped rule: `.cursor/rules/40-dagger-ci-pipeline.mdc`
