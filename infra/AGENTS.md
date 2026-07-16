# AGENTS.md — infra

Local development infrastructure: `docker-compose.yml` (PostgreSQL 18, Keycloak 26.6.4 exact patch, Kafka in KRaft mode), Keycloak realm export (`infra/keycloak/realm-export.json`), AsyncAPI Kafka topic catalog (`infra/asyncapi/`). PostgreSQL 19 is lab/experimental only, never in the MVP plan.

## Container safety

- Use Podman Compose (`podman compose -f infra/docker-compose.yml ...`); Docker CLI in this environment emulates Podman.
- Never run `podman compose ... down -v`, `podman volume rm`, or `podman volume prune` / `podman system prune` — named volumes hold the local Postgres/Keycloak state across sessions and are not disposable. These are hard-denied in `.claude/settings.local.json`; do not attempt to route around that.
- `podman compose ... down` (without `-v`) is `ask`-gated — confirm with the user before running it.

## Compose vs. Testcontainers

Persistent local Compose infrastructure (this directory's `docker-compose.yml`) is intended for manual development, inspection, and long-lived local state.

Automated backend integration tests should prefer isolated Testcontainers instances rather than depending on the long-lived Compose database. Do not make tests depend on data left in `infra_postgres_1`. A test may additionally check a migration against the long-lived local Compose database as extra upgrade-path evidence, but that never replaces an isolated Testcontainers test.

## PostgreSQL

- Flyway migrations are append-only — never edit an already-applied migration; add a new one.
- Selective RLS: tenant/evidence tables get RLS (`ENABLE` + `FORCE ROW LEVEL SECURITY`, policy on `current_setting('app.tenant_id', true)::uuid`); queue/ledger tables use ownership grants instead, never RLS. See `postgres-rls-migration` skill.
- One-writer-per-schema is enforced at the grant level here, not just in application code — a module's DB role gets no privileges on another module's schema.
- Application code must never bypass RLS (no superuser connection from the app role) — the migrations under `backend/src/main/resources/db/migration/` are the authoritative current grant/policy state.

## Keycloak

Realm-as-code in `infra/keycloak/realm-export.json`; changes go through the `keycloak-realm-config` skill's import-verification flow, not manual `kcadm.sh` edits that aren't reflected back into the export file.

## Kafka

Topic catalog is generated from `sepa-nexus-message-flow-and-data-blueprint.md` §3.7 v2 — that table is the sole AsyncAPI source (ADR-N8); no other document restates or renames a topic.

## Database skill routing

- RLS / role isolation → `postgres-rls-migration`
- Flyway / DDL → `sepa-nexus-flyway-safe-change`
- payment identifiers / idempotency / ledger / correlation → `sepa-nexus-payments-data-integrity`
- PostgreSQL/Kafka integration tests → `sepa-nexus-database-testing`
- final DB change review → `sepa-nexus-database-review`
