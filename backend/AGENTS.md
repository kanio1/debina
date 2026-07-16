# AGENTS.md — backend

Spring Boot 4.1 / Spring Framework 7.0 / Spring Modulith 2.x monolith, JDK 25, Maven via `./mvnw` (never Gradle). Implements the CPC-SP payment platform — see root `AGENTS.md` for the frozen architecture invariants this code must satisfy.

## Commands

- After any Java change: `./mvnw -f backend test`.
- Run Maven directly — never prepend `export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"`; the Podman socket is already wired into the environment.
- Targeted test: `./mvnw -f backend test -Dtest=<ClassName>`.
- Full backend regression is required before marking any backend task `done` — do not batch multiple unverified tasks.

## Module boundaries

- One Spring Modulith application module per bounded context (package under `src/main/java/.../modules/*`); a module writes only to its own PostgreSQL schema. Cross-module calls only through a public port / `@ApplicationModuleListener`, never direct repository access into another module's schema. Enforced three ways: Spring Modulith `allowedDependencies`, ArchUnit, SQL grants — see `spring-modulith-module` skill.
- Controllers hold no business logic or security decisions. Services own the business rule **and** the authorization decision together. Repositories are thin and schema-scoped.
- Tenant isolation is RLS-only — never `@TenantId`, never a Hibernate-level tenant filter, never a superuser connection from the app role. RLS policies and the `app.tenant_id` GUC convention: see `postgres-rls-migration` skill and the Flyway migrations under `src/main/resources/db/migration/`.
- `signature` verification runs before ISO XML parsing (verify-before-parse is an enforced ordering rule, not just documentation) and never writes to any other module's schema.
- Money only ever moves through `ledger` via `LedgerPort` — `settlement` never writes `ledger.journal_*` directly.

## Testing

JUnit 5 + Testcontainers for integration tests. `act` + Testcontainers does not work in this environment (Docker-in-Docker is unavailable) — a Testcontainers failure surfaced only under `act` is an environment limitation, not a regression; confirm against `./mvnw -f backend test` directly before concluding otherwise.

## What's out of scope here

Playwright/E2E is a `frontend/` concern gated by capability, not by directory — see `planning/AGENTS.md`. Do not scaffold Playwright under `backend/`.
