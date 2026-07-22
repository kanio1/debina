# HANDOFF

## Zadanie

Debina is a synthetic enterprise SEPA/ISO 20022 research platform. Phase D implements a provider-neutral local Dagger Go SDK verification platform on `rebase/enterprise-evolution`; D2A has completed its independent Dagger-native integration foundation while Testcontainers compatibility is environment-blocked.

## Zrobione

- D0 and `fast` remain valid at `9081d938a109afcbcce41743c3f1e19bcc03e6c8`: Dagger CLI/Engine `v0.21.4`, Go `1.26.5`, rootful Podman `5.8.4`, privileged Engine PID limit `16384`, and the user-owned `build/generated-spring-modulith/javadoc.json` SHA-256 `9c484e010bfa0a8719f78dd4ade744fe7e08a3a9fe7eaf0fb35ed1dd2ca0a015`.
- D2A adds `dagger check integration` in `dagger/`. It proves the pinned frontend production build; 128 explicitly selected current Maven tests with Testcontainers-dependent classes excluded; PostgreSQL 18 readiness; Flyway fresh migrate/validate; `docs/ci/FLYWAY-UPGRADE-BASELINE.md` V54 → V60 migrate/validate; real-role RLS/grant checks; and `apache/kafka:4.1.1` ephemeral create/produce/consume of `debina.phase-d.non-production-probe`.
- The native graph passed: `dagger check integration --progress=plain` exit 0 in 17.1s. `dagger check fast --progress=plain` exit 0, with the final warm rerun fully cached at 0.0s. Pure Go success/failure-propagation tests live in `dagger/pure/` so they do not require an SDK session.
- `docs/ci/DAGGER-{IMPLEMENTATION,CHECK-MANIFEST,IMAGE-BASELINE}.yaml` and `planning/programs/DEBINA-ENTERPRISE-REBASE-PHASE-D.md` record D2A evidence and the exact environmental boundary.

## Utknęliśmy na

`ENVIRONMENT-BLOCKED-CHECKPOINT`: existing Testcontainers-backed Maven classes cannot run inside a Dagger v0.21.4 execution container. Testcontainers 2.0.5 reports `/var/run/docker.sock` absent; the generated v0.21.4 bindings expose `Container.WithUnixSocket` only with a Dagger `Socket` input and expose no host-socket accessor. Official Dagger service bindings are isolated TCP services, not Unix socket import. A rootful Podman bridge would require the prohibited unsupported socket mount/proxy/Docker compatibility socket. This is not a production defect.

## Plan na następny krok

Start by reviewing the committed D2A checkpoint and do not attempt D2B unless a Dagger v0.21.4-supported, minimum-privilege mechanism to provide the required Testcontainers API is newly evidenced; otherwise preserve the environment-blocked checkpoint.

## Pułapki, których nie wolno powtórzyć

- Do not upgrade Dagger, use Docker, create a compatibility socket, copy/mount/proxy `/run/podman/podman.sock`, or alter groups, socket permissions, systemd, SELinux, containers.conf or PID limits.
- Do not claim Testcontainers-backed Maven regression, smoke, unfiltered `dagger check`, Playwright, remote CI, `act`, deployment, or D2B passed. `integration` deliberately covers only the native socket-free leaves.
- Do not stage, restore, regenerate, or modify `build/generated-spring-modulith/javadoc.json`; it must remain user-owned and at the recorded hash. Do not touch Wave 12, production payment code, backend tests, migrations, GraphQL schema, runtime compose, Keycloak realm, or workflows.
