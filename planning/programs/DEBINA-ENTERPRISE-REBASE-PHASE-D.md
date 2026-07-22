# Debina Enterprise Rebase — Phase D

**Status:** `ENVIRONMENT-BLOCKED-CHECKPOINT` (2026-07-22). **Baseline:** `676d09e19393830936f5bf419b40f4a5eaa2a4c3`. **Branch:** `rebase/enterprise-evolution`.

## Completed independent work

- Verified the required baseline and retained the existing user-owned `build/generated-spring-modulith/javadoc.json`; its bootstrap SHA-256 is `9c484e010bfa0a8719f78dd4ade744fe7e08a3a9fe7eaf0fb35ed1dd2ca0a015`.
- Inspected the governance runner, Maven Wrapper/JDK 25 backend, Node `24.18.0`/pnpm `10.33.0` frontend, PostgreSQL/Flyway migrations, Kafka/Keycloak compose inputs, Testcontainers inventory, actual BFF/UI routes, Keycloak roles and UC-SCT-001, UC-SCT-APPROVAL-001 and UC-AUDIT-001.
- Recorded the toolchain gate in `docs/ci/DAGGER-TOOLCHAIN-BASELINE.{md,yaml}` and the capped ADR-N16 smoke inventory in `docs/ci/SMOKE-CAPABILITY-MATRIX.{md,yaml}`. A later recheck proved Dagger CLI/Engine `v0.21.4` and Go `1.26.5` work only through rootless Podman.
- Updated the program so D/E/F continue on this long-lived branch; historical completed-phase branch references remain untouched.
- Ran `bash tools/agent-config/validate-enterprise-governance.sh`: PASS, with the established 296 legacy traceability and 69 legacy planning warnings.

## Blocker

The user supplied rootful socket at `/run/podman/podman.sock` is not accessible: `podman --remote --url unix:///run/podman/podman.sock ps` fails `connect: permission denied`. Dagger falls back to rootless Podman, where its observed privileged Engine has PID limit `2048`. Dagger's official Podman guidance requires rootful execution and warns about this limit. No host runtime/security setting was changed.

## Deferred Phase D evidence

No Dagger check, Engine probe, Testcontainers-in-Dagger spike, Flyway fresh/upgrade probe, RLS/grant probe, Kafka service graph, frontend Dagger build, Playwright execution, cache timing, adversarial proof or generated-binding verification has run. Consequently there are no module/image pins, command surface, artifacts, timings or Phase E entry clearance yet.

## Retry criteria

The workstation owner must authorize this user to access the rootful Podman socket and set an approved safe Engine PID limit, then explicitly confirm those security-sensitive changes. Then begin with the minimal engine probe, initialize the one Go module through the installed CLI, and execute the Phase D implementation/validation sequence. Wave 12 at `5ebebb0` remains untouched; no production payment code, migration, GraphQL schema, runtime compose, realm source, workflow, remote CI or `act` was changed.
