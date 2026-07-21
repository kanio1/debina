# Dagger Pipeline Architecture

Future pipeline implementation is one local, provider-neutral Go SDK Dagger module. Developers and Codex invoke it directly as `dagger check fast`, `dagger check integration`, `dagger check smoke`, and `dagger check all`. `fast` owns governance/semantic/fitness checks, focused backend tests, frontend codegen/lint/typecheck; `integration` adds PostgreSQL 18, Kafka, Flyway fresh/upgrade, RLS/grants, backend regression and frontend build; `smoke` adds Keycloak, backend/BFF and minimal approved Playwright smoke; `all` composes them.

Dagger owns local verification orchestration and executable pipeline logic. Remote CI, scheduling, credential brokering, artifact publication and release automation are deferred. No remote provider is selected. No Dagger module, remote workflow, `act` invocation or configuration is implemented in Phase B.
