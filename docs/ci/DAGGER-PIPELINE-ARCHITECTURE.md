# Dagger Pipeline Architecture

## Status

Phase D is locally runtime-proven on Dagger CLI/Engine `v0.21.4` with the selected rootful Podman API and the approved Engine PID limit of `16384`; see [the toolchain baseline](DAGGER-TOOLCHAIN-BASELINE.md).

One provider-neutral Go SDK module is rooted at `dagger.json`, with source in `dagger/`. The public no-argument gates are callable as `fast`, `integration`, `smoke`, `phase-d`, and `all`.

| Group | State | Membership |
|---|---|---|
| `fast` | runtime-proven | governance, Dagger module self-verification, backend architecture/compile checks and frontend codegen/lint/typecheck |
| `integration` | runtime-proven | socket-free backend regression, frontend build, PostgreSQL/Flyway/RLS/grants and Kafka probe |
| `smoke` | runtime-proven | backward-compatible D3A login/session/health Chromium slice |
| `smoke-payments` | runtime-proven | sequential JSON_DIRECT, maker-checker and Payment Detail lineage Chromium journeys with isolated stateful services |
| `phase-d` | aggregate-runtime-proven | `fast` + `integration` + `smoke-auth` + `smoke-payments` + resilience/artifact/cache assurance |
| `all` | aggregate-runtime-proven | non-duplicating public alias of `phase-d` |

The no-argument aggregates remain socket-free. The complete Testcontainers regression still requires the explicit typed Podman socket argument and is intentionally not hidden inside `phase-d` or `all`.

The module composes Go functions and the Dagger graph directly; it never invokes `dagger check`, `dagger call`, or `dagger run` from a function. Stateful payment services have per-journey instance identities, browser journeys are sequential, and every aggregate child has a finite context budget. Expected-failure leaves require both non-zero exit and an exact classification marker. Failure artifacts are redacted before export and automatically scanned for forbidden patterns and the exact synthetic secret values. Cache proof uses an observable cold vertex, a `CACHED` identical run, and source/config invalidation with independent earlier layers retained.

The final local proof used `dagger call phase-d --progress=plain` (exit 0), followed by explicit successful calls of `fast`, `integration`, `smoke`, and `all`. Workspace inputs use tight exclusions while retaining authoritative generated GraphQL source. Remote CI, `act`, deployment, publishing and release automation remain deferred.
