# Debina Enterprise Rebase — Phase D

**Status:** `IN-PROGRESS` (2026-07-22). **Baseline:** `676d09e19393830936f5bf419b40f4a5eaa2a4c3`. **Branch:** `rebase/enterprise-evolution`.

## Completed evidence

- The required baseline was verified. The user-owned `build/generated-spring-modulith/javadoc.json` remains unaltered at bootstrap SHA-256 `9c484e010bfa0a8719f78dd4ade744fe7e08a3a9fe7eaf0fb35ed1dd2ca0a015`.
- Repository inputs and ADR-N16's actual smoke capability inventory were inspected and recorded. The program records D/E/F on this long-lived branch while historical completed-phase branch references remain untouched.
- D0 now passes: Dagger CLI/Engine `v0.21.4`, Go `1.26.5`, Podman `5.8.4` rootful, socket access without `sudo`, privileged Engine PID limit `16384`, and a deterministic Alpine Dagger probe. The former environment blocker is superseded without Phase D changing host configuration.
- One CLI-generated Go SDK module now exists (`dagger.json` → `dagger/`), pins Engine `v0.21.4`, and exposes native `fast`.
- `dagger check fast` passed. It runs the authoritative governance runner (PASS with the established 296 traceability and 69 planning legacy warnings), non-mutating module self-verification, JDK 25 Maven compilation plus six selected architecture/source-boundary tests, and the pinned Node `24.18.0`/pnpm `10.33.0` codegen-drift, lint and typecheck leaf.
- D2A completed all socket-free native integration leaves through `dagger check integration`: frontend production build; 128 current non-Testcontainers backend tests; ephemeral PostgreSQL 18 readiness; Flyway fresh migrate/validate; the approved V54 → V60 upgrade/validate; real-role RLS/grant probes; and a pinned `apache/kafka:4.1.1` non-production create/produce/consume probe. No host ports, volumes, runtime socket mounts, Docker, or host configuration changes were used.
- D2B supersedes the former socket-accessor interpretation. The v0.21.4 CLI accepted `/run/podman/podman.sock` as `--runtime-socket`, converted it to the generated `Address.socket: Socket!` argument, and `Container.WithUnixSocket` mounted it only into the disposable diagnostic and dedicated Maven/Testcontainers containers at `/var/run/docker.sock`. `_ping` returned `OK`. No socket copy, proxy, Docker compatibility socket, unrelated mount, or host change was used.
- The first Maven/Testcontainers attempt created Ryuk but could not reach its published `localhost` port. A socket-free Dagger probe resolved `host.containers.internal` to `10.88.0.1` and connected to the active Ryuk port; the dedicated Maven container consequently sets only `DOCKER_HOST=unix:///var/run/docker.sock` and the evidence-backed `TESTCONTAINERS_HOST_OVERRIDE=host.containers.internal`.
- `PaymentsRlsTest` then passed through the bridge (PostgreSQL 18; 60 Flyway migrations; 2 tests). The complete explicit `testcontainers-regression` ran 540 tests and proved repeated PostgreSQL/Kafka Testcontainers reachability, but exited 1 at `SettlementFinalityServiceTest:66`: it hard-codes the Warsaw `+02` text rendering of a `timestamptz`; the pinned UTC Maven container correctly returned `2026-07-20 10:15:30.123+00`. This is a repository test-portability/contract defect, not a socket or production-finality data defect. No test or production source was changed.

## Remaining Phase D work

- Obtain a Phase D portability decision for the existing `SettlementFinalityServiceTest` timezone-dependent assertion: make the assertion portable or explicitly approve a documented execution-timezone contract. Do not hide it with an undeclared Dagger `TZ` override. After that decision, rerun the explicit full Testcontainers regression cold and warm.
- Materialize only the approved ADR-N16 Playwright journeys if the existing capability matrix remains implementable.
- Add redacted diagnostics, pure Go tests, cache/adversarial evidence, complete command proofs and the final Phase D record/runbook updates.

No Phase E work is authorized. Wave 12 at `5ebebb0` remains untouched. No production payment code, backend tests, migrations, GraphQL schema, runtime compose, realm source, workflow, remote CI or `act` has been changed.
