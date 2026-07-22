# Dagger Pipeline Architecture

## Status

Phase D is in implementation. D0 passed on Dagger CLI/Engine `v0.21.4` with the selected rootful Podman API and the approved Engine PID limit of `16384`; see [the toolchain baseline](DAGGER-TOOLCHAIN-BASELINE.md).

One provider-neutral Go SDK module is rooted at `dagger.json`, with source in `dagger/`. Its first native no-argument check, `fast`, is implemented and proven with `dagger check fast`.

| Group | State | Membership |
|---|---|---|
| `fast` | partially implemented | governance runner and Dagger module self-verification; backend/frontend leaves pending inventory completion |
| `integration` | pending | full Maven regression, Testcontainers compatibility, frontend build, fresh/upgrade Flyway, RLS/grant probes and Kafka readiness |
| `smoke` | pending | ephemeral PostgreSQL, Kafka, Keycloak, backend, Next.js BFF and the capped six ADR-N16 Chromium journeys |

`dagger check` becomes the canonical complete check only after all three groups exist and is executed successfully. `all` remains absent until the installed CLI proves a native non-duplicating alias.

The module composes Go functions and the Dagger graph directly; it never invokes `dagger check`, `dagger call`, or `dagger run` from a function. Workspace inputs use tight exclusions while retaining authoritative generated GraphQL source. Remote CI, `act`, deployment, publishing and release automation remain deferred.
