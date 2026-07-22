# HANDOFF

## Zadanie

Debina is a synthetic enterprise SEPA/ISO 20022 research platform. On `rebase/enterprise-evolution`, Phase D builds a local Dagger Go SDK verification platform; D2B has proven the supported typed-Socket Testcontainers bridge but awaits a portability decision before its complete Maven regression can pass.

## Zrobione

- D0, `dagger check fast` and the socket-free `dagger check integration` remain valid. D2A covers the pinned frontend build, 128 socket-free Maven tests, PostgreSQL/Flyway/RLS/grant leaves and the non-production Kafka probe.
- D2B adds `dagger/testcontainers.go`: `socket-transport-probe`, `runtime-reachability-probe`, `testcontainers-representative` and `testcontainers-regression`. The Dagger v0.21.4 CLI converted `--runtime-socket=/run/podman/podman.sock` into `Address.socket: Socket!`; `_ping` returned `OK` when mounted only in the disposable diagnostic container.
- The dedicated Maven container alone receives that typed socket at `/var/run/docker.sock`, with `DOCKER_HOST=unix:///var/run/docker.sock`. A socket-free Dagger probe reached `host.containers.internal` at `10.88.0.1`, so that Maven container also uses the evidence-backed `TESTCONTAINERS_HOST_OVERRIDE=host.containers.internal`.
- `PaymentsRlsTest` passed through the bridge (PostgreSQL 18, 60 Flyway migrations, 2 tests). The explicit full command ran 540 Maven tests; `docs/ci/DAGGER-IMPLEMENTATION.md`, `docs/ci/DAGGER-CHECK-MANIFEST.yaml`, `docs/ci/DAGGER-PODMAN-TROUBLESHOOTING.md` and `planning/programs/DEBINA-ENTERPRISE-REBASE-PHASE-D.md` record the exact D2B contract and outcomes.
- The user-owned `build/generated-spring-modulith/javadoc.json` remains unstaged and at SHA-256 `9c484e010bfa0a8719f78dd4ade744fe7e08a3a9fe7eaf0fb35ed1dd2ca0a015`.

## Utknęliśmy na

`DECISION-BLOCKED-CHECKPOINT`: the supported bridge worked across the full Testcontainers run, but `SettlementFinalityServiceTest:66` failed because it asserts that `finality_at::text` contains `12:15:30.123+02`. The pinned Dagger Maven container uses UTC and correctly returned `2026-07-20 10:15:30.123+00` for the same `Instant`; 539 other tests passed. Do not add a hidden `TZ` override or modify the backend test/production code without an approved portability decision.

## Plan na następny krok

Obtain the Phase D decision whether this existing test must assert a timezone-independent instant or whether a documented Maven execution-timezone contract is approved; then rerun `dagger call testcontainers-regression --runtime-socket=/run/podman/podman.sock` cold and warm.

## Pułapki, których nie wolno powtórzyć

- Do not revive the D2A claim that typed Socket transport is unsupported merely because `dag.Host()` lacks a socket accessor: Dagger v0.21.4 CLI arguments construct `Address.socket` for `*dagger.Socket` parameters.
- Do not give the runtime socket to `dagger check integration`, frontend, Flyway-native, Kafka-native, governance or any other container. Do not copy/proxy it, use Docker, create a compatibility socket, or change host groups, permissions, systemd, SELinux, containers.conf or PID limits.
- Do not make the runtime socket optional or report Testcontainers PASS while skipping it. Do not hide the observed timezone portability failure with a Dagger environment override.
- Do not stage, restore, regenerate or modify `build/generated-spring-modulith/javadoc.json`; do not touch Wave 12, Phase E, Playwright, production payment code, backend test sources, migrations, GraphQL, runtime compose, Keycloak, workflows, remote CI or `act`.
