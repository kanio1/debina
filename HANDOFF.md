# HANDOFF

## Zadanie

Debina is a synthetic enterprise SEPA/ISO 20022 research platform. Phase D is implementing one provider-neutral, local Dagger Go SDK verification platform on `rebase/enterprise-evolution`; Phase E and Wave 12 remain out of scope.

## Zrobione

- D0 was independently reverified and the obsolete environment checkpoint was superseded in `docs/ci/DAGGER-TOOLCHAIN-BASELINE.{md,yaml}`: Dagger CLI/Engine `v0.21.4`, Go `1.26.5`, Podman `5.8.4` rootful, rootful API access without `sudo`, privileged Engine PID limit `16384`, and the deterministic Alpine Dagger probe all pass.
- Created and committed one CLI-generated module at root `dagger.json` with Go source in `dagger/`: `c417a6a feat(dagger): initialize local Go verification module`.
- Added and committed native `fast` composition in `06f4109 feat(dagger): add fast backend and frontend checks`. `dagger check -l` lists `fast`; `dagger check fast` passes. It runs the authoritative governance runner (the established 296 traceability and 69 planning legacy warnings), Go module test/vet/format-drift validation, JDK 25 Maven compilation plus six exact architecture/source-boundary tests, and Node `24.18.0`/pnpm `10.33.0` frozen install, direct GraphQL codegen drift comparison, lint and typecheck. A warm rerun was cached and took one second.
- The rootful Engine remains `privileged=true` with `pids_limit=16384`. The user-owned `build/generated-spring-modulith/javadoc.json` remains at bootstrap SHA-256 `9c484e010bfa0a8719f78dd4ade744fe7e08a3a9fe7eaf0fb35ed1dd2ca0a015`; it is the only current worktree modification and must never be staged.

## Utknęliśmy na

The required Testcontainers-in-Dagger compatibility spike is not yet complete. A Dagger execution container was probed with `dagger core container from --address alpine:3.22 ...`; it reports both `/run/podman/podman.sock` and `/var/run/docker.sock` as `not-visible`, and has no `DOCKER_HOST`/`CONTAINER_HOST`. The generated v0.21.4 bindings expose `Container.WithUnixSocket` but no `Host` socket accessor. Do not treat this preliminary finding as the final environment checkpoint yet: Phase D policy requires completing Dagger-native Flyway/database checks and frontend build first. No host setting was changed.

## Plan na następny krok

Inspect the v0.21.4 generated Dagger schema and official installed CLI capabilities for a supported, minimum-privilege Testcontainers socket/service bridge; if none exists, implement the independent Dagger-native PostgreSQL/Flyway/RLS/Kafka and frontend-build leaves before recording the exact `ENVIRONMENT-BLOCKED-CHECKPOINT`.

## Pułapki, których nie wolno powtórzyć

- Do not upgrade Dagger from `v0.21.4`, use Docker, change groups/socket permissions/systemd/SELinux/containers.conf/PID limits, use `sudo`, or mount a host socket by an unsupported mechanism. The rootful API is intentionally authorized only for this trusted session.
- Do not claim `integration`, `smoke`, unfiltered `dagger check`, `all`, Testcontainers, Flyway, Kafka or Playwright succeeded: only `fast` is implemented and proven.
- Do not stage, restore, regenerate or include `build/generated-spring-modulith/javadoc.json`; preserve its bootstrap hash. Do not switch/merge/cherry-pick/rebase or copy from Wave 12 (`wip/wave-12-signature-verdict-evidence` / `5ebebb0`), push, use `act`, select remote CI, or alter production payment code, backend tests, migrations, GraphQL schema, compose or the Keycloak realm.
