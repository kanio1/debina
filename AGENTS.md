# AGENTS.md — SEPA Nexus

## Working agreements
- This is Iteration 0 (walking skeleton). Do NOT write, install config for, or scaffold Playwright tests — that is Iteration 1+ scope, once real screens exist. Iteration 0 has an explicit no Playwright rule.
- Every task in `sepa-nexus-iteration-0-foundation-plan.md` has a `verify:` command. Do not mark a task done, or move to the next task, until that exact command passes.
- Backend: run `./mvnw -f backend test` after any Java change.
- Frontend: run `npm run build && npm run lint` in `frontend/` after any change.
- Never bypass PostgreSQL RLS from application code (no `@TenantId`, no superuser connection from the app role) — see `infra/postgres/README.md`.
- Controllers contain no business logic; services own both the business rule and the security decision; repositories are thin and schema-scoped. See the `spring-modulith-module` skill.
- Prefer `pnpm` if already present in `frontend/`, otherwise `npm`; do not mix lockfiles.

## Repository map
- `backend/` — Spring Boot 4.1 / Spring Modulith, JDK 25, Maven (`./mvnw`)
- `frontend/` — Next.js 16.2.10+ BFF + React 19 UI
- `infra/` — docker-compose, Keycloak realm export, Flyway migrations reference
