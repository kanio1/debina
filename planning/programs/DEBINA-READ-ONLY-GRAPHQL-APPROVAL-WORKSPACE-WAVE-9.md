# Debina Read-only GraphQL Approval Workspace — Wave 9

## Baseline and source record

- Actual baseline: `ade6218e822d26dfd348947e684a04cc0da099d3` on `main`; clean before Wave 9.
- Wave 8 reconciled from local commits `73501af` through `ade6218`; approval persistence, commands,
  queue model, audit and expiry are complete historical capability, not re-counted.
- `.agents/skills -> ../.claude/skills` remains the canonical discovery bridge.

| Binding source | Relevant sections | Blob |
|---|---|---|
| `README.md` | frozen GraphQL read-only, BFF, one-writer and five-axis rules | `d575e449903c727af7e4f7dfad05ff27be4a6d73` |
| `sepa-nexus-message-flow-and-data-blueprint.md` | §§3.6.5, 4.7, 6.6, 7.2, §8 | `f8667131109858da5ff7f3d3a92d74d31a1df900` |
| `sepa-nexus-blueprint-ownership-integration.md` | §3.6 read ownership and architecture gates | `fc88d643a0e5c24e73f3f5cb32a2056ab646428b` |
| `sepa-nexus-keycloak-26-security-architecture-blueprint.md` | §§7, 9-13 | `4f7250d967d3bd2369ce70f5889df6965e78e35c` |
| `sepa-nexus-react-nextjs-frontend-blueprint.md` | §§3a-3b, 9-10, 12-19 | `93473408711c6a5b474680e090de3b6b1615a138` |

## Planning-owner result and decision

- No pre-existing story built the GraphQL runtime: EPIC-16 enforces a runtime after it exists,
  EPIC-23/23.1B generates types after SDL, and EPIC-76/76.6 owns UI after a read contract.
- New source-derived owner: `EPIC-78`; ADR-W9-01 accepts a thin technical adapter depending only
  on public, source-owned query ports/DTOs. Reporting ownership and repository-direct resolvers
  are rejected.
- Primary chain: 78.1 foundation → 16.1/16.2 ownership/read-only enforcement → 78.2 approval
  queries → 23.1B codegen + 78.3 BFF → 76.6 workspace UI. Reserve: 77.3/78.4 audit query.

## RED evidence

- `GraphQLReadOnlyStructureTest` failed as required before implementation: no
  `graphql/schema.graphqls` resource existed. Log:
  `/tmp/DEBINA-READ-ONLY-GRAPHQL-APPROVAL-WORKSPACE-WAVE-9/red-graphql-structure.log`.

## Security and contract constraints

- Query only: no Mutation or Subscription; DTOs only; no command service/repository/native SQL in
  adapter; no GraphQL arguments may establish tenant/branch.
- Approval reads require `payment_approver`; owner port sets current tenant/branch GUC and relies
  on RLS. Commands remain existing audited REST BFF calls and server-confirmed refresh is mandatory.
- Candidate contract: `approvalQueue(first, after)` and `approval(paymentId)`, with the existing
  `(submitted_at, approval_id)` cursor order and bounded page size.

## Current checkpoint

Planning owner and red structural proof are present. The first runtime slice is GREEN:

- Spring Boot's managed `spring-boot-starter-graphql` exposes fixed `POST /graphql` from a Query-only
  SDL. `ApprovalGraphQlRuntimeTest` proves a bearer-authenticated `payment_approver` gets a DTO
  response and missing bearer receives 401 against isolated PostgreSQL 18.
- `GraphQLReadOnlyStructureTest` parses the schema and proves Query exists while Mutation and
  Subscription roots do not. `GraphQLReadModelOwnershipTest` plus `ModularityTest` prove the
  transport depends on the public `modules.ApprovalQueueQuery`, not repositories or approval commands.
- Green log: `/tmp/DEBINA-READ-ONLY-GRAPHQL-APPROVAL-WORKSPACE-WAVE-9/graphql-runtime-attempt-2.log`
- Mutation proof: temporarily adding `type Mutation { forbidden: Boolean }` made
  `GraphQLReadOnlyStructureTest` fail on the parsed Mutation root; the schema was restored and
  the test passed. Logs: `mutation-schema-red.log`, `mutation-schema-restored-green.log`.

Remaining in 78.1: bounded depth/complexity, production introspection restriction and their
non-vacuous tests. Then add real RLS/cursor/detail GraphQL integration proof before frontend work.

## Hardening checkpoint

- `MaxQueryDepthInstrumentation(10)` and `MaxQueryComplexityInstrumentation(100)` are installed
  in the GraphQL source builder. `application-prod.yml` disables SDL introspection.
- `ApprovalGraphQlRuntimeTest` proves aliases cannot bypass complexity and deep introspection cannot
  bypass depth; `ProductionGraphQlIntrospectionTest` proves an authenticated caller receives a
  GraphQL error for `__schema` under the production profile. Green log:
  `/tmp/DEBINA-READ-ONLY-GRAPHQL-APPROVAL-WORKSPACE-WAVE-9/graphql-hardening-attempt-2.log`.

## Payment query integration checkpoint

- `ApprovalSubmissionIntegrationTest` now invokes `/graphql` against the actual payment-owned JDBC
  read model and PostgreSQL 18 RLS. It proves deterministic first/second cursor pages and that a
  foreign-tenant `approval(paymentId)` returns null rather than the target. The first run exposed
  and fixed the cursor decoder's over-escaped separator. Green log:
  `/tmp/DEBINA-READ-ONLY-GRAPHQL-APPROVAL-WORKSPACE-WAVE-9/graphql-rls-cursor-attempt-2.log`.

## Frontend delivery checkpoint

- `frontend/codegen.yml` generates query types directly from the versioned backend SDL; its
  `ApprovalQueue` and `Approval` documents are limited to the approved decision fields. Two
  consecutive runs produced the same generated-file SHA
  `2ca0e1390941ee16b5927116836d363b4634423bf89935c32f4e96996ebe3912`.
- The fixed-destination `/api/graphql` BFF route accepts only the two named read operations,
  validates a bounded body, attaches the server-session bearer and correlation ID, and never
  returns a token or accepts a browser-provided backend URL. Approve/reject use separate fixed
  REST BFF routes with the established CSRF and idempotency conventions.
- Payments & Files now composes a `payment_approver`-gated approval queue. It presents loading,
  empty, unauthorized and error states separately; links payment detail; preserves cursor loading;
  blocks self-approval; confirms approve; requires a rejection comment; and never alters an item
  until the REST response succeeds and the GraphQL queue is refetched. Frontend lint, typecheck
  and production build are GREEN (one pre-existing TanStack React Compiler lint warning only).

## Detail and BFF hardening checkpoint

- The Spring schema inspection now reports no unmapped fields: explicit source-DTO mappings expose
  the nullable `decisionComment` and `decidedAt` detail fields without exposing an entity. The
  focused runtime test covers the detail contract as well as a submitter-role denial.
- The GraphQL BFF checks both declared and actual UTF-8 body size before parsing JSON, so a missing
  or false `Content-Length` cannot bypass its 16 KiB request bound.

## Runtime environment checkpoint

- A fresh isolated PostgreSQL 18 container migrated V1 through V60 successfully using the existing
  Maven Flyway workflow. Runtime configuration now declares every module migration classpath so a
  packaged application can discover the same source tree rather than only the root V1 migration.
- Real Keycloak 26.6.4 → Next.js PKCE established an HttpOnly `sepa_session` plus CSRF cookie for
  `approver`; its BFF `ApprovalQueue` call returned an empty server-backed connection. The BFF now
  requests `openid sepa-guc`, the actual realm-published scope, rather than the absent `profile`
  scope that Keycloak rejected with `invalid_scope`.
