# SEPA Nexus — Iteration 0 Foundation Plan (Walking Skeleton)

**Nature.** The first buildable artifact of this project. Not a design document — a checklist-driven execution plan. Every task below ends in a checkbox and a verification command; nothing is "done" until its command passes. `[NO-PLAYWRIGHT]` **This plan deliberately installs no Playwright tests and writes no Playwright test files.** Playwright is Iteration 1+ work, once the first three real screens exist (per the project's own Adoption Plan, step 9). Verification here is backend (JUnit 5 + Testcontainers), frontend build/lint, and manual HTTP/SQL checks only.

**Goal of Iteration 0.** One thin, real, working vertical slice: an operator submits a payment through the actual UI → BFF → REST → Spring service → PostgreSQL row → outbox event → Kafka → consumer updates a read row → the UI shows it back. Every layer of the frozen architecture is touched once, for real, at minimum depth — proving the stack works together before any feature breadth is added.

**Explicitly out of scope for Iteration 0:** GraphQL read models (REST is enough to prove the vertical slice; GraphQL wires in Iteration 1 once a real read model exists), the full 12-role Keycloak realm (start with **4 roles**, per the Playwright vision document's own recommendation), the signature module, settlement/egress/reconciliation modules, 4EV/VoP/fraud-hold, all 9 screens (build 1, thin), and **all Playwright test code**.

---

## How to Use This Document

**For a human:** work top to bottom, epic by epic. Check a box only after its task's verification command actually passes on your machine — not before.

**For an AI coding agent (Claude Code or OpenAI Codex CLI):** this repository ships an `AGENTS.md` at the root (Story 0.2) that both tools read natively — Codex CLI as its primary instructions file, Claude Code as project context. It also ships a set of project-specific **Skills** in `.claude/skills/` (mirrored for Codex CLI) following the open **Agent Skills standard** (SKILL.md format: YAML frontmatter `name`/`description` + Markdown instructions), which both Claude Code and Codex CLI load identically — this is not a coincidence, it is a published, cross-vendor standard, so one skill file serves both tools. Each story below names which skill (if any) an agent should consult before starting. Work one **task** at a time, run its verification command, and only mark the checkbox and move on if it passes — do not batch multiple unverified tasks.

**Every task line follows this shape:**
```
- [ ] **Task name.** What to do. `verify: <exact command>` → expected result.
```

---

## Verified Stack Versions (as of this plan; re-check if starting later)

| Component | Version pinned | Note |
|---|---|---|
| JDK | 25 LTS | |
| Spring Boot | 4.1.x | Spring Framework 7.0.x under the hood |
| Spring Modulith | latest 2.x compatible with Boot 4.1 | |
| Spring Kafka | 4.1.x | |
| PostgreSQL | **18** (stable baseline) | 19 is lab/experimental only — never in this plan |
| Keycloak | **26.6.4** | pin exact patch; long CVE tail on older 26.x |
| Maven | latest 3.9.x (via `mvnw` wrapper) | not Gradle |
| Node.js | **24 LTS**, exact pin **24.18.0** | `[USER-DECISION 2026-07-13]`; replaces the previous Node.js 20 baseline |
| Next.js | **16.2.10 LTS or newer** `[SECURITY-PIN]` | May 2026 security release fixed 13 advisories incl. middleware/proxy **authorization bypass** — our BFF's entire security model depends on middleware-enforced auth, so this pin is not optional |
| React | 19.x (ships with Next.js 16) | |
| TypeScript | 7.0 (GA 8 July 2026) `[RISK, dated]` | day(s)-old GA as of this plan; `typescript-eslint`/`ts-morph`/custom transformers may lag until 7.1 — pin 7.0 but watch tooling compat in Story 0.1 |
| shadcn/ui | CLI v4, Base UI primitives (current default) | pin the CLI version used at scaffold time |
| Tailwind | v4 | |
| TanStack Table | v8/v9 (headless) | |

---

## EPIC 0 — Repository & Agent Foundation

**Purpose.** Before any framework code: a repo shape both a human and an AI coding agent can navigate unambiguously, and the Skill/AGENTS.md scaffolding that makes every later epic executable by Claude Code or Codex CLI without re-explaining context each time.

### Story 0.1 — Monorepo structure & tooling baseline

- [ ] **Create the monorepo skeleton.** Three top-level dirs: `backend/` (Maven, Spring Boot), `frontend/` (Next.js), `infra/` (docker-compose, Keycloak realm export, Flyway migrations shared reference). Add root `.gitignore` (Java+Node+IDE), `.editorconfig` (UTF-8, LF, 2-space YAML/JSON/TS, 4-space Java).
  `verify: ls backend frontend infra` → all three exist, each with a `README.md` stub stating its one-sentence purpose.
- [ ] **Pin Node via `.nvmrc`/`.node-version`.** Content: `24.18.0`. `[USER-DECISION 2026-07-13]` Node.js 24 LTS replaces the previous Node.js 20 baseline. `verify: test "$(tr -d '\r\n' < frontend/.node-version)" = "24.18.0"` → exact pin `24.18.0`.
- [ ] **Confirm TypeScript 7 tooling compatibility before relying on it.** `[RISK, dated]` Check whether `typescript-eslint` and any planned custom transformer already support TS 7.0 GA; if not, pin TypeScript to the latest 5.x LTS for Iteration 0 and revisit at 7.1.
  `verify: cd frontend && npm view typescript-eslint peerDependencies` → confirm `typescript@^7` is listed before adopting 7.0; otherwise document the fallback pin in `frontend/README.md`.

### Story 0.2 — `AGENTS.md` and project Skills for Claude Code / Codex CLI

> 🤖 **No skill needed for this story** — it *creates* the skills infrastructure other stories will reference.

- [ ] **Create root `AGENTS.md`.** Working agreements read natively by Codex CLI and as project context by Claude Code. Must state, verbatim, the no-Playwright rule for Iteration 0, the verification-before-checkbox rule, and the module-boundary rule.
  `verify: test -f AGENTS.md && grep -q "no Playwright" AGENTS.md` → file exists and contains the rule.

  Minimal required content:
  ```markdown
  # AGENTS.md — SEPA Nexus

  ## Working agreements
  - This is Iteration 0 (walking skeleton). Do NOT write, install config for, or scaffold Playwright tests — that is Iteration 1+ scope, once real screens exist.
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
  ```

- [ ] **Create `.claude/skills/` with five project skills.** Each a folder with `SKILL.md` (YAML frontmatter `name` + `description`, then Markdown instructions). Per the open Agent Skills standard, the same files work unmodified for Codex CLI.
  `verify: find .claude/skills -name SKILL.md | wc -l` → `5`.

  Skill 1 — `.claude/skills/spring-modulith-module/SKILL.md`:
  ```markdown
  ---
  name: spring-modulith-module
  description: Use when creating or modifying a Spring Modulith application module (a package under backend/src/main/java/.../modules/*) — controller/service/repository layering, module boundaries, and the DDD aggregate rule for this project.
  ---
  # Spring Modulith module conventions

  1. One top-level package per module (e.g. `modules.paymentlifecycle`). No other module may import an internal (`.internal`) subpackage.
  2. Layering inside a module: `web` (Controller, DTO only, zero business logic) → `service` (Service, owns the business rule AND the security decision together) → `repository` (thin Spring Data interface, schema-scoped, no cross-tenant queries — RLS already restricts the connection).
  3. Entities never use `@TenantId` (Hibernate) — tenant isolation is PostgreSQL RLS only, set via a GUC on the connection. See `postgres-rls-migration` skill.
  4. Every command handler runs in exactly one transaction (`@Transactional` at the service method, not the controller).
  5. Cross-module communication is by Spring Modulith domain events (`@ApplicationModuleListener`), never direct service-to-service calls across module packages.
  6. After any change: run `./mvnw -f backend test` — the Modulith `verify()` architecture test must pass.
  ```

  Skill 2 — `.claude/skills/postgres-rls-migration/SKILL.md`:
  ````markdown
  ---
  name: postgres-rls-migration
  description: Use when writing a Flyway migration that creates or alters a table needing tenant isolation, or when wiring the GUC session variable that PostgreSQL RLS depends on.
  ---
  # PostgreSQL RLS migration conventions

  1. Every tenant-scoped table gets `tenant_id uuid NOT NULL`, `ENABLE ROW LEVEL SECURITY`, and `FORCE ROW LEVEL SECURITY`.
  2. Policy pattern (copy exactly, substitute table name):
     ```sql
     ALTER TABLE payment.payments ENABLE ROW LEVEL SECURITY;
     ALTER TABLE payment.payments FORCE ROW LEVEL SECURITY;
     CREATE POLICY tenant_isolation ON payment.payments
       USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
     ```
  3. **Empty-GUC-zero-rows is mandatory**: `current_setting('app.tenant_id', true)` with `true` (missing_ok) returns NULL if unset, and `tenant_id = NULL` matches zero rows — never write a policy that falls back to "show everything" when the GUC is unset.
  4. The application DB role is never `BYPASSRLS` and never the migration/superuser role. Two distinct roles: a migration role (runs Flyway, owns DDL) and an app role (runs application queries, RLS-bound).
  5. After any migration: run `./mvnw -f backend test -Dtest=*RlsTest` — the empty-GUC and cross-tenant negative tests must pass.
  ````

  Skill 3 — `.claude/skills/keycloak-realm-config/SKILL.md`:
  ```markdown
  ---
  name: keycloak-realm-config
  description: Use when adding or modifying a Keycloak realm, client, role, or seeded user for local development — realm-export.json edits and the associated import verification.
  ---
  # Keycloak 26.6.4 realm conventions (Iteration 0 scope)

  1. Iteration 0 has exactly **4 realm roles**: `operator`, `payment_submitter`, `payment_approver`, `reference_data_admin` — not the full 12. Do not add more roles in Iteration 0 without updating this plan first.
  2. Two clients: `sepa-web` (confidential, used only by the Next.js BFF server, never the browser) and `sepa-api` (bearer-only resource server).
  3. Realm state lives in `infra/keycloak/realm-export.json`, committed to git — the docker-compose Keycloak service imports it on startup. Never configure the realm by hand in the admin console without exporting the change back to this file.
  4. Seed exactly one test user per role, password `dev-only-<role>`, clearly not for anything beyond local dev.
  5. After any change: `docker compose up -d keycloak` then `curl -f http://localhost:8080/health/ready` → HTTP 200, and confirm the realm imported via `curl http://localhost:8080/realms/sepa-nexus/.well-known/openid-configuration` → HTTP 200.
  ```

  Skill 4 — `.claude/skills/nextjs-bff-route/SKILL.md`:
  ```markdown
  ---
  name: nextjs-bff-route
  description: Use when adding a Next.js server route, middleware, or server action that talks to the backend REST API or handles the Keycloak session — BFF security conventions for this project.
  ---
  # Next.js BFF conventions (Iteration 0 scope)

  1. The browser never holds a Keycloak access/refresh token. The BFF holds the session server-side (HttpOnly, Secure, SameSite=Lax cookie); the browser only ever sees an opaque session cookie.
  2. Every state-changing route requires a CSRF token validated server-side, per the existing security design.
  3. Security headers (CSP, HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy) are set once, in `middleware.ts`, for every response — never per-route.
  4. `sepa-api` (backend) is never reachable directly from the browser — CORS on `sepa-api` allows zero browser origins; only the BFF's Node process calls it, server-to-server.
  5. Pin Next.js to `16.2.10` or newer — earlier 16.2.x has an unpatched middleware/proxy authorization-bypass advisory that directly undermines rule 1–3 above.
  6. After any change: `npm run build && npm run lint` in `frontend/`, then manually confirm security headers with `curl -sI http://localhost:3000/ | grep -i content-security-policy`.
  ```

  Skill 5 — `.claude/skills/shadcn-component-scaffold/SKILL.md`:
  ```markdown
  ---
  name: shadcn-component-scaffold
  description: Use when adding a UI component via the shadcn/ui CLI or composing a new screen — component-foundation and data-testid conventions for this project.
  ---
  # shadcn/ui + TanStack Table conventions (Iteration 0 scope)

  1. Components are vendored (copy-paste via the shadcn CLI), never installed as an npm dependency — we own the code so `data-testid` never breaks on a library upgrade.
  2. Every interactive element gets `data-testid="<workspace>.<entity>.<component>.<action-or-state>"` — e.g. `payments.list.submit-button`.
  3. Tables use TanStack Table (headless) rendering real `<table>`/`<th scope>` — never a `<div>` grid.
  4. No optimistic UI: a submitted form shows a pending state until the server confirms, never an immediate assumed-success row.
  5. After any change: `npm run build` in `frontend/`, then manually click through the flow once in a browser — no Playwright check at this stage.
  ```

- [ ] **Mirror skills for Codex CLI.** Either symlink or copy `.claude/skills/` to `.codex/skills/` so both tools see identical files (per the open Agent Skills standard, this is copy/symlink only — no format conversion needed).
  `verify: diff -r .claude/skills .codex/skills` → no differences (symlink or exact copy).

  > **2026-07-13 Codex CLI path update:** use the first safe writable supported location: root `.agents/skills`, user-scope `$HOME/.agents/skills`, or nested `tools/codex/.agents/skills`. In this environment root `.agents` is a system read-only tmpfs, so the verified fallback is `tools/codex/.agents/skills -> ../../../.claude/skills`; run `codex --cd tools/codex`. Verification: `test -e tools/codex/.agents/skills && diff -r .claude/skills tools/codex/.agents/skills`.

### Story 0.3 — Base Docker Compose (Postgres, Keycloak, Kafka)

> 🤖 Skill: none directly — this is infra scaffolding the other skills assume exists.

- [ ] **Write `infra/docker-compose.yml`** with three services: `postgres` (18, exposed 5432, named volume), `keycloak` (26.6.4, dev mode, imports `infra/keycloak/realm-export.json`, exposed 8080), `kafka` (KRaft mode, no separate Zookeeper, exposed 9092).
  `verify: docker compose -f infra/docker-compose.yml config` → valid config, no errors.
  ```yaml
  services:
    postgres:
      image: postgres:18
      environment:
        POSTGRES_DB: sepa_nexus
        POSTGRES_USER: sepa_migration
        POSTGRES_PASSWORD: dev-only-migration
      ports: ["5432:5432"]
      volumes: ["pgdata:/var/lib/postgresql/data"]
    keycloak:
      image: quay.io/keycloak/keycloak:26.6.4
      command: start-dev --import-realm
      environment:
        KC_BOOTSTRAP_ADMIN_USERNAME: admin
        KC_BOOTSTRAP_ADMIN_PASSWORD: dev-only-admin
      volumes: ["./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json"]
      ports: ["8080:8080"]
    kafka:
      image: apache/kafka:latest
      environment:
        KAFKA_PROCESS_ROLES: broker,controller
        KAFKA_NODE_ID: 1
        KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
        KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
        KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
        KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      ports: ["9092:9092"]
  volumes:
    pgdata: {}
  ```
- [ ] **Bring the stack up and confirm all three are healthy.**
  `verify: docker compose -f infra/docker-compose.yml up -d && docker compose -f infra/docker-compose.yml ps` → all three services `running`/`healthy`.

---

## EPIC 1 — PostgreSQL 18 Foundation

**Purpose.** The database truth for the thin vertical slice: one schema, one table, RLS proven with a real negative test, and the per-schema outbox pattern (ADR-N5) validated early since it is the riskiest architectural assumption to leave unverified.

> 🤖 Skill: `postgres-rls-migration` for every task in this epic.

### Story 1.1 — Flyway setup & two DB roles

- [ ] **Add Flyway to the backend Maven build** (`flyway-core` + `flyway-database-postgresql`), configured to run migrations from `backend/src/main/resources/db/migration`.
  `verify: ./mvnw -f backend flyway:info` → connects, shows zero migrations applied yet.
- [ ] **Create the two DB roles** in a bootstrap migration (`V1__roles.sql`): `sepa_migration` (owns DDL, used only by Flyway) and `sepa_app` (RLS-bound, used by the running application — **not** `BYPASSRLS`, **not** superuser).
  `verify: psql -c "\du" | grep -E "sepa_migration|sepa_app"` → both roles listed, `sepa_app` has no `Superuser`/`Bypass RLS` attribute.
  ```sql
  CREATE ROLE sepa_migration LOGIN PASSWORD 'dev-only-migration';
  CREATE ROLE sepa_app LOGIN PASSWORD 'dev-only-app' NOSUPERUSER NOBYPASSRLS;
  ```

### Story 1.2 — `payment` schema, `payments` table, RLS

- [ ] **Create the `payment` schema**, owned by `sepa_migration`, with `sepa_app` granted `USAGE` + `SELECT/INSERT/UPDATE` (never `DELETE` — payments are append/status-transition only).
  `verify: psql -c "\dn payment"` → schema exists, owner `sepa_migration`.
- [ ] **Create the thin `payments` table.** Only the columns the walking skeleton needs — no signature, no ISO lineage yet (that's Iteration 1+).
  `verify: psql -c "\d payment.payments"` → table exists with exactly these columns.
  ```sql
  CREATE TABLE payment.payments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    end_to_end_id text NOT NULL,
    amount numeric(18,2) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'EUR',
    debtor_iban text NOT NULL,
    creditor_iban text NOT NULL,
    status text NOT NULL DEFAULT 'RECEIVED'
      CHECK (status IN ('RECEIVED','VALIDATED','REJECTED','DISPATCHED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
  );
  CREATE UNIQUE INDEX payments_tenant_e2e_idx ON payment.payments(tenant_id, end_to_end_id);
  ```
- [ ] **Enable and force RLS, add the tenant policy** (per the `postgres-rls-migration` skill's exact pattern).
  `verify: psql -c "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname='payments'"` → both `t`.
- [ ] **Write the empty-GUC-zero-rows negative test** (JUnit + Testcontainers): connect as `sepa_app` **without** setting `app.tenant_id`, `SELECT * FROM payment.payments` → assert zero rows, even if rows exist for other tenants.
  `verify: ./mvnw -f backend test -Dtest=PaymentsRlsTest` → passes, including the empty-GUC case and a cross-tenant case (tenant A's GUC sees zero of tenant B's rows).

### Story 1.3 — Per-schema outbox/inbox tables (ADR-N5)

- [ ] **Create `payment.outbox_events`.** One row per domain event pending Kafka publication.
  `verify: psql -c "\d payment.outbox_events"` → table exists.
  ```sql
  CREATE TABLE payment.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    correlation_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
  );
  CREATE INDEX outbox_unpublished_idx ON payment.outbox_events(created_at) WHERE published_at IS NULL;
  ```
- [ ] **Create `payment.inbox_events`** for inbound dedup (unique on the source event id, so a redelivered Kafka message is a safe no-op).
  `verify: psql -c "\d payment.inbox_events"` → table exists with a unique constraint on `source_event_id`.
- [ ] **Negative test: a second writer role cannot write `payment.outbox_events`.** Create a throwaway `other_module_role`, attempt an `INSERT`, expect a permission-denied error — proving one-writer-per-schema holds even for the outbox table.
  `verify: ./mvnw -f backend test -Dtest=OutboxOwnershipTest` → passes.

---

## EPIC 2 — Keycloak 26.6.4 Realm (4 roles, Iteration 0 scope)

**Purpose.** A real OIDC identity provider wired end-to-end — not stubbed — because auth is exactly the kind of thing that's expensive to retrofit if the walking skeleton fakes it.

> 🤖 Skill: `keycloak-realm-config` for every task in this epic.

### Story 2.1 — Realm-as-code

- [ ] **Author `infra/keycloak/realm-export.json`** with realm `sepa-nexus`, four realm roles (`operator`, `payment_submitter`, `payment_approver`, `reference_data_admin`), and token claim mappers for `tenant_id` and `branch_id` (custom user attributes → JWT claims — the same GUC-feeding claims the RLS policy in Epic 1 depends on).
  `verify: docker compose up -d keycloak && curl -f http://localhost:8080/realms/sepa-nexus/.well-known/openid-configuration` → HTTP 200.
- [ ] **Add two clients**: `sepa-web` (confidential, `standardFlowEnabled: true`, redirect URI `http://localhost:3000/*`, used only by the BFF server) and `sepa-api` (bearer-only resource server, `publicClient: false`, no direct browser flow).
  `verify: curl -f http://localhost:8080/realms/sepa-nexus/.well-known/openid-configuration | jq '.token_endpoint'` → returns a valid URL; confirm both clients exist via `kcadm.sh get clients -r sepa-nexus` inside the container.
- [ ] **Seed four test users**, one per role, password `dev-only-<role>`.
  `verify: for u in operator submitter approver refdata; do curl -s -X POST http://localhost:8080/realms/sepa-nexus/protocol/openid-connect/token -d "client_id=sepa-web" -d "grant_type=password" -d "username=$u" -d "password=dev-only-$u" | jq -e '.access_token' > /dev/null; done` → all four succeed.

### Story 2.2 — Token shape verification

- [ ] **Confirm the JWT carries exactly the claims the backend needs**: `tenant_id`, `branch_id` (nullable), `realm_access.roles`, `sub`, `sid`. No extra sensitive data in the token (data-minimization, per the security review).
  `verify: TOKEN=$(curl -s ... | jq -r .access_token) && echo $TOKEN | cut -d. -f2 | base64 -d | jq 'keys'` → contains exactly the expected claim set, nothing beyond it.

---

## EPIC 3 — Spring Boot / Modulith Backend Skeleton

**Purpose.** The `payment-lifecycle` module, thin, with the Controller→Service→Repository discipline and RLS-GUC wiring proven for real — the pattern every later module copies.

> 🤖 Skill: `spring-modulith-module` for every task in this epic; `postgres-rls-migration` for Story 3.4 specifically.

### Story 3.1 — Maven project scaffold

- [ ] **Generate the Maven project** (JDK 25, `spring-boot-starter-parent` 4.1.x, `spring-modulith-starter-core`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`, `postgresql` driver, `flyway-core`).
  `verify: ./mvnw -f backend -q compile` → `BUILD SUCCESS`.
  ```xml
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
  </parent>
  <properties>
    <java.version>25</java.version>
  </properties>
  <dependencies>
    <dependency><groupId>org.springframework.modulith</groupId><artifactId>spring-modulith-starter-core</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>org.springframework.modulith</groupId><artifactId>spring-modulith-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>kafka</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
  </dependencies>
  ```
- [ ] **Add `application.yml`** with datasource pointed at `sepa_app` (never `sepa_migration`), Flyway pointed at `sepa_migration`, and the Keycloak issuer URI for the resource server.
  `verify: ./mvnw -f backend -q spring-boot:run &` then `curl -f http://localhost:8081/actuator/health` → `{"status":"UP"}`, then stop the process.
- [ ] **Create the module package skeleton**: `backend/src/main/java/.../modules/paymentlifecycle/{web,service,repository,domain}`.
  `verify: find backend/src/main/java -type d -name paymentlifecycle` → exists with the four subpackages.
- [ ] **Add the Spring Modulith architecture test** (`ApplicationModules.of(...).verify()`) as a JUnit test — this is the single most important test in this epic, since it enforces module boundaries for every module added after Iteration 0.
  `verify: ./mvnw -f backend test -Dtest=ModularityTest` → passes.

### Story 3.2 — `payment-lifecycle` module: Controller → Service → Repository

- [ ] **`PaymentEntity`** (JPA, `backend/.../paymentlifecycle/domain/PaymentEntity.java`) mapping `payment.payments` — **no `@TenantId`** (per the `postgres-rls-migration` skill: RLS is the sole enforcement layer).
  `verify: ./mvnw -f backend -q compile` → compiles clean.
  ```java
  @Entity
  @Table(name = "payments", schema = "payment")
  public class PaymentEntity {
      @Id @GeneratedValue private UUID id;
      private UUID tenantId;
      private String endToEndId;
      private BigDecimal amount;
      private String currency;
      private String debtorIban;
      private String creditorIban;
      @Enumerated(EnumType.STRING) private PaymentStatus status;
      private Instant createdAt;
      @Version private long version;
      // getters/setters
  }
  ```
- [ ] **`PaymentRepository`** (thin Spring Data interface, schema-scoped, no cross-tenant query methods).
  `verify: ./mvnw -f backend -q compile` → compiles clean.
  ```java
  public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
      Optional<PaymentEntity> findByTenantIdAndEndToEndId(UUID tenantId, String endToEndId);
  }
  ```
- [ ] **`SubmitPaymentRequest` DTO** (narrow, named — never a generic entity-binding `PUT`, per the security review's mass-assignment defense).
  `verify: ./mvnw -f backend -q compile` → compiles clean.
- [ ] **`PaymentService.submitPayment(...)`** — owns the business rule (idempotency check, status assignment) and, in later iterations, the security decision together, per the skill's rule 5. One `@Transactional` per command.
  `verify: ./mvnw -f backend test -Dtest=PaymentServiceTest` → passes (unit test with a mocked repository, asserting one row created, status `RECEIVED`).
  ```java
  @Service
  public class PaymentService {
      private final PaymentRepository repository;
      private final ApplicationEventPublisher events;

      @Transactional
      public PaymentEntity submitPayment(SubmitPaymentRequest request, UUID tenantId) {
          repository.findByTenantIdAndEndToEndId(tenantId, request.endToEndId())
              .ifPresent(existing -> { throw new DuplicatePaymentException(existing.getId()); });
          var payment = new PaymentEntity(/* ... */);
          var saved = repository.save(payment);
          events.publishEvent(new PaymentReceivedEvent(saved.getId(), tenantId, Instant.now()));
          return saved;
      }
  }
  ```
- [ ] **`PaymentController`** — parses/validates the HTTP request, calls exactly one service method, **no business logic**, per the skill's rule 2.
  `verify: ./mvnw -f backend test -Dtest=PaymentControllerTest` → passes (`@WebMvcTest`/`MockMvc`, POST returns 201 with a `Location` header).
  ```java
  @RestController
  @RequestMapping("/api/v1/payments")
  public class PaymentController {
      private final PaymentService service;

      @PostMapping
      public ResponseEntity<PaymentResponse> submit(
              @RequestBody @Valid SubmitPaymentRequest request,
              @AuthenticationPrincipal Jwt jwt) {
          var tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
          var saved = service.submitPayment(request, tenantId);
          return ResponseEntity.created(URI.create("/api/v1/payments/" + saved.getId()))
              .body(PaymentResponse.from(saved));
      }

      @GetMapping("/{id}")
      public ResponseEntity<PaymentResponse> get(@PathVariable UUID id) { /* ... */ }
  }
  ```
- [ ] **RFC 7807 error shape for all 4xx/5xx** on this controller (per the security review — establish this convention now, before more endpoints exist).
  `verify: ./mvnw -f backend test -Dtest=PaymentControllerErrorTest` → duplicate submission returns HTTP 409 with `Content-Type: application/problem+json` and a `correlationId` field.

### Story 3.3 — Spring Security Resource Server

- [ ] **Configure the Resource Server** to validate JWTs against the Keycloak realm from Epic 2, mapping `realm_access.roles` to Spring `GrantedAuthority`.
  `verify: ./mvnw -f backend test -Dtest=SecurityConfigTest` → an unauthenticated request to `POST /api/v1/payments` returns 401; a token with `payment_submitter` role returns 201.
- [ ] **Method security**: `@PreAuthorize("hasRole('payment_submitter')")` on `submitPayment`.
  `verify: ./mvnw -f backend test -Dtest=PaymentAuthorizationTest` → a token with only `operator` role gets 403 on submit.
- [ ] **Stub the custom `AuthorizationManager<MethodInvocation>`** referenced in the security review — not fully exercised until Iteration 1's approve/reject endpoints exist, but scaffold the bean now so the pattern is established.
  `verify: ./mvnw -f backend -q compile` → compiles clean; a placeholder unit test confirms the bean loads in the Spring context.

### Story 3.4 — RLS GUC-setting

> 🤖 Skill: `postgres-rls-migration`.

- [ ] **Write a Hibernate `StatementInspector` or a `@Transactional`-scoped connection interceptor** that runs `SET LOCAL app.tenant_id = '<value>'` at the start of every transaction, reading the value from the current JWT's `tenant_id` claim.
  `verify: ./mvnw -f backend test -Dtest=TenantGucIntegrationTest` → Testcontainers-backed test: two requests with different `tenant_id` claims each see only their own row.
- [ ] **Negative test: a request with no `tenant_id` claim at all sees zero rows**, not an error and not all rows (the empty-GUC-zero-rows rule surfaced at the application layer, not just the raw SQL layer).
  `verify: ./mvnw -f backend test -Dtest=MissingTenantClaimTest` → passes.

---

## EPIC 4 — Outbox/Inbox + Kafka (thin)

**Purpose.** Validate the event-driven backbone (ADR-N5) early — one topic, one event type, one producer, one consumer. Nothing more; depth comes in later iterations.

> 🤖 Skill: `spring-modulith-module`.

### Story 4.1 — Kafka topic & producer config

- [ ] **Add `spring-kafka` (4.1.x) to the backend POM.**
  `verify: ./mvnw -f backend -q compile` → compiles clean.
- [ ] **Define one topic**, `payment.lifecycle.events.v1`, 1 partition (Iteration 0 doesn't need more), via `NewTopic` bean or `docker exec kafka kafka-topics.sh --create`.
  `verify: docker exec <kafka-container> /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep payment.lifecycle.events.v1` → topic listed.

### Story 4.2 — Outbox dispatcher (scheduled poller)

- [ ] **Write a `@Scheduled` poller** (every 2s for Iteration 0 — a Debezium-style CDC relay is a later-iteration upgrade, not a walking-skeleton requirement) that reads unpublished `payment.outbox_events` rows, publishes to Kafka, and marks `published_at`.
  `verify: ./mvnw -f backend test -Dtest=OutboxDispatcherTest` → Testcontainers-backed: insert an outbox row directly, wait ≤5s, assert it was consumed from the real Kafka topic and `published_at` is set.
  ```java
  @Scheduled(fixedDelay = 2000)
  @Transactional
  public void dispatchPending() {
      var pending = outboxRepository.findUnpublished(50);
      for (var event : pending) {
          kafkaTemplate.send("payment.lifecycle.events.v1", event.getAggregateId().toString(), event.getPayload());
          event.markPublished(Instant.now());
      }
  }
  ```

### Story 4.3 — Inbox consumer skeleton

- [ ] **Write a `@KafkaListener`** consuming `payment.lifecycle.events.v1`, deduping via `payment.inbox_events` (unique on source event id — a redelivered message is a safe no-op), and updating a minimal read row.
  `verify: ./mvnw -f backend test -Dtest=InboxConsumerIdempotencyTest` → publish the same event twice; assert the read row is updated exactly once, and the second delivery is logged as a duplicate, not applied twice.

---

## EPIC 5 — Next.js BFF

**Purpose.** The one place the browser ever talks to — session-holding, CSRF-protected, security-headers-hardened, and proxying to `sepa-api` server-to-server only.

> 🤖 Skill: `nextjs-bff-route` for every task in this epic.

### Story 5.1 — Project scaffold

- [ ] **Scaffold with `create-next-app`, pinned to `16.2.10` or newer** `[SECURITY-PIN]` (the May 2026 security release fixed middleware/proxy authorization bypass — do not scaffold on an older 16.2.x). Note: recent `create-next-app` versions generate an `AGENTS.md` automatically — merge its output with the root `AGENTS.md` from Story 0.2 rather than keeping two.
  `verify: cd frontend && npx create-next-app@16.2.10 . --typescript --app --tailwind && npm run build` → build succeeds.
- [ ] **Confirm the patched version landed.**
  `verify: npm list next | grep 16.2.1` → version ≥ 16.2.10.

### Story 5.2 — OIDC Authorization Code + PKCE, HttpOnly session

- [ ] **Implement the Auth Code + PKCE flow** against the `sepa-web` Keycloak client from Epic 2: `/api/auth/login` redirects to Keycloak, `/api/auth/callback` exchanges the code, stores tokens **server-side only** (never sent to the browser), and sets an opaque `HttpOnly`, `Secure`, `SameSite=Lax` session cookie.
  `verify: curl -c cookies.txt -sI http://localhost:3000/api/auth/login | grep -i location` → redirects to the Keycloak authorization endpoint with a PKCE `code_challenge` parameter.
- [ ] **Session lookup middleware**: every request resolves the opaque cookie to the server-side token via a session store (in-memory Map is fine for Iteration 0; Redis is a later-iteration upgrade).
  `verify: after completing a real login flow in a browser, curl -b cookies.txt http://localhost:3000/api/session` → returns the authenticated user's claims, never a raw JWT.
- [ ] **Logout clears the session** server-side and redirects to Keycloak's end-session endpoint.
  `verify: curl -b cookies.txt -c cookies.txt http://localhost:3000/api/auth/logout && curl -b cookies.txt http://localhost:3000/api/session` → second call returns 401.

### Story 5.3 — Security headers & CSRF

- [ ] **`middleware.ts`** sets CSP, HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy on every response (per the `nextjs-bff-route` skill, rule 3).
  `verify: curl -sI http://localhost:3000/ | grep -iE "content-security-policy|strict-transport-security|x-frame-options"` → all three present.
- [ ] **CSRF token** issued on session creation, validated on every state-changing BFF route (`POST`/`PUT`/`DELETE`).
  `verify: curl -b cookies.txt -X POST http://localhost:3000/api/payments -d '{}' -H "Content-Type: application/json"` (no CSRF header) → 403; retry with the correct token → passes through to the backend.

### Story 5.4 — Server-side proxy to `sepa-api`

- [ ] **One server route**, `POST /api/payments`, that validates the session, attaches a server-obtained bearer token, and forwards to `sepa-api`'s `POST /api/v1/payments` — the browser never sees `sepa-api`'s URL or token.
  `verify: complete a real browser login, submit a payment via this route, confirm via `psql` that a row landed in `payment.payments`.

---

## EPIC 6 — React / shadcn Frontend Skeleton

**Purpose.** One thin, real screen — not a mockup — proving the component foundation (shadcn/ui + TanStack Table, vendored) and the `data-testid` convention before any Playwright test is written against them (that happens in Iteration 1).

> 🤖 Skill: `shadcn-component-scaffold` for every task in this epic.

### Story 6.1 — shadcn/ui + Tailwind v4 init

- [ ] **Run the shadcn CLI (v4) init**, pinning the exact CLI version used so a later `npx shadcn@latest` doesn't silently change vendored component code.
  `verify: cat frontend/components.json` → exists, records the shadcn config; `npm run build` → succeeds.
- [ ] **Vendor exactly the components this thin screen needs**: `table`, `button`, `input`, `form`, `card`, `sonner` (toast) — not the full kit yet.
  `verify: ls frontend/components/ui` → contains exactly those five (plus their direct primitive dependencies).

### Story 6.2 — TanStack Table setup

- [ ] **Add `@tanstack/react-table`**, build a minimal `PaymentsTable` component rendering a real `<table>` with `<th scope="col">` headers — no `<div>` grid.
  `verify: npm run build` → succeeds; manual check in browser dev tools confirms `<table>`/`<th scope>` markup.

### Story 6.3 — Minimal `AppShell`

- [ ] **Compose `AppShell`** from the shadcn `sidebar` + a header — thin for Iteration 0 (no role-filtered nav yet, that needs the full role set from Iteration 1).
  `verify: npm run build && npm run dev` then manually load `http://localhost:3000` → shell renders without console errors.

### Story 6.4 — Thin Payments screen (list + submit)

- [ ] **`app/payments/page.tsx`**: a form (end-to-end ID, amount, currency, debtor/creditor IBAN) posting to the BFF route from Story 5.4, and a `PaymentsTable` listing submitted payments (via a simple `GET /api/payments` BFF route added alongside it).
  `verify: npm run build` → succeeds.
- [ ] **`data-testid` convention wired on every interactive element** — `payments.list.table`, `payments.submit.form`, `payments.submit.submit-button`, `payments.submit.end-to-end-id-input`, etc. — per the `shadcn-component-scaffold` skill, rule 2. **No Playwright test is written against these in this plan** — the IDs exist so Iteration 1 can write one without re-touching this component.
  `verify: grep -c "data-testid" frontend/app/payments/page.tsx` → at least 5 occurrences.
- [ ] **No optimistic UI**: the submit button shows a pending state until the BFF confirms; the new row appears only after the response, never assumed.
  `verify: manual check — throttle network in browser dev tools, confirm the row does not appear until the response arrives.`
- [ ] **Manual end-to-end click-through** (human, not automated): log in as `payment_submitter`, submit one payment, see it appear in the table, confirm the row in `psql`.
  `verify: psql -c "SELECT end_to_end_id, status FROM payment.payments"` → the submitted payment is there with status `RECEIVED`.

---

## EPIC 7 — CI/CD Foundation

**Purpose.** The same verification commands from Epics 1–6 running unattended, locally reproducible via `nektos/act` before ever depending on GitHub's runners.

### Story 7.1 — Backend CI workflow

- [ ] **`.github/workflows/backend.yml`**: checkout → set up JDK 25 → `./mvnw -f backend test` (Testcontainers needs Docker-in-Docker or a `services:` Postgres+Kafka block — prefer Testcontainers' own Docker socket support on the GitHub-hosted runner, which has Docker preinstalled).
  `verify: act -W .github/workflows/backend.yml -j test` (via nektos/act, locally) → job succeeds.
- [ ] **Fail the build on the Modulith architecture test** (Story 3.1's `ModularityTest`) — this is the one test in the whole plan that, if skipped, silently un-does the module-boundary discipline everything else depends on.
  `verify: intentionally add a forbidden cross-module import, run the workflow, confirm it fails; then revert.`

### Story 7.2 — Frontend CI workflow

- [ ] **`.github/workflows/frontend.yml`**: checkout → set up Node 24 LTS, exact pin `24.18.0` → `npm ci` → `npm run lint` → `npm run build`. **No Playwright job in this workflow** — it is intentionally absent until Iteration 1 adds real Playwright tests.
  `verify: act -W .github/workflows/frontend.yml -j build` → job succeeds.

### Story 7.3 — Local CI parity via `nektos/act`

- [ ] **`.actrc`** pinning the same runner image family GitHub Actions uses, so `act` results match hosted CI closely enough to trust locally.
  `verify: act -l` → lists both workflows' jobs without configuration errors.

---

## EPIC 8 — Walking Skeleton Verification (no Playwright)

**Purpose.** Prove the whole vertical slice works together, end to end, using backend integration tests and manual checks only — this epic is the actual "walking skeleton" moment; everything before it was one layer at a time.

> 🤖 Skill: `spring-modulith-module` (for the integration test's module wiring).

### Story 8.1 — Full-chain Testcontainers integration test

- [ ] **One JUnit 5 test class**, `WalkingSkeletonIntegrationTest`, spinning up Postgres 18 + Kafka via Testcontainers (Keycloak can be a real running instance from `docker-compose` for this test, or a Testcontainers Keycloak image — either is acceptable for Iteration 0), that: (1) obtains a real token from Keycloak for `payment_submitter`, (2) calls `POST /api/v1/payments` directly against the backend (bypassing the BFF — this test proves the backend chain, not the BFF UI), (3) asserts the row exists in `payment.payments` with the right `tenant_id`, (4) asserts an `outbox_events` row was created and, within the poller's window, published to Kafka, (5) asserts a consumer-side read row was updated.
  `verify: ./mvnw -f backend test -Dtest=WalkingSkeletonIntegrationTest` → passes, and the log output shows all five assertions hit in order.
- [ ] **Negative case in the same test class**: a token with `operator` (not `payment_submitter`) attempting the same call gets 403, and **no** row, **no** outbox event, **no** Kafka message is produced — proving the security guard actually gates the whole chain, not just the HTTP layer.
  `verify: ./mvnw -f backend test -Dtest=WalkingSkeletonIntegrationTest#deniedRoleProducesNoSideEffects` → passes.

### Story 8.2 — Manual, human-run verification runbook

- [ ] **Full stack up from clean.** `docker compose -f infra/docker-compose.yml up -d && ./mvnw -f backend spring-boot:run & cd frontend && npm run dev`
  `verify: all three infra containers healthy, backend responds on :8081/actuator/health, frontend responds on :3000.`
- [ ] **Browser walk-through**: open `http://localhost:3000`, log in as `payment_submitter` (real Keycloak redirect, not a stub), submit one payment, see it in the table, log out, confirm `/api/session` returns 401 after logout.
  `verify: manual — every step above completes without a console error or a stack trace in any of the three logs (backend, frontend, Keycloak).`
- [ ] **Database spot-check.** `psql -c "SELECT p.status, o.published_at IS NOT NULL AS dispatched FROM payment.payments p JOIN payment.outbox_events o ON o.aggregate_id = p.id"`
  `verify: one row, status RECEIVED, dispatched = t.`

### Story 8.3 — Iteration 0 exit checklist

Before starting Iteration 1 (first three real screens + first real Playwright tests), every box below must be checked — this is the walking skeleton's own Definition of Done, distinct from any individual task's:

- [ ] All checkboxes in Epics 0–7 are checked.
- [ ] `WalkingSkeletonIntegrationTest` passes locally **and** in CI (`act -W .github/workflows/backend.yml`).
- [ ] The empty-GUC-zero-rows RLS test and the cross-tenant RLS test both pass.
- [ ] The Modulith `ModularityTest` passes and has been proven to actually fail on a deliberate boundary violation (Story 7.1).
- [ ] Zero Playwright test files exist anywhere in the repository.
  `verify: find . -path ./node_modules -prune -o -iname "*.spec.ts" -print -o -iname "playwright.config.ts" -print | wc -l` → `0`.
- [ ] `AGENTS.md` and all five `.claude/skills/*/SKILL.md` files exist and are available through the first safe writable Codex location (`.agents/skills`, `$HOME/.agents/skills`, or `tools/codex/.agents/skills`).
- [ ] Next.js is confirmed at `16.2.10` or newer (security pin).
- [ ] A fresh clone of the repo, with only `docker compose up` + the two `mvnw`/`npm` run commands, reproduces the full manual walk-through (Story 8.2) with no undocumented manual step.

---

## What Iteration 1 Inherits (do not re-litigate here)

Explicitly carried forward, not solved in Iteration 0: the remaining 8 Keycloak roles (personas already designed — see the frontend blueprint's persona work), GraphQL read models, the signature module, settlement/egress/reconciliation modules, 4EV/VoP/fraud-hold, all 9 screens beyond the one thin Payments slice, and — the first real Playwright test, written against the `data-testid`s this plan already laid down.

---

*End of Iteration 0 plan. `[NO-PLAYWRIGHT]` upheld throughout — verification is JUnit/Testcontainers/manual only. Every task carries its own verification command; the plan's own Definition of Done is Story 8.3.*
