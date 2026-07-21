# Debina Audit Explorer & Evidence Drawer — Wave 10

## Baseline and source map

- Actual baseline: `d1dcc65f215e9d1f8e490e9648ce09af01ac24ad` (`main`, clean).
- Wave 9 record: `planning/programs/DEBINA-READ-ONLY-GRAPHQL-APPROVAL-WORKSPACE-WAVE-9.md` at blob `$(git hash-object planning/programs/DEBINA-READ-ONLY-GRAPHQL-APPROVAL-WORKSPACE-WAVE-9.md)` (hash resolved before commit in the final record).
- Source anchors inspected: `sepa-nexus-final-3-screens-ui-spec.md` §7; `sepa-nexus-keycloak-26-security-architecture-blueprint.md` §§9–13; `sepa-nexus-react-nextjs-frontend-blueprint.md` §§3a, 9; `ADR-N3`, `ADR-N5`, `ADR-N9`, `ADR-N14`, `ADR-W7-01`, `ADR-W8-01`, `ADR-W9`.
- Implementation anchors inspected: `CommandAuditPort`, `JdbcCommandAuditPort`, audit V55–V60 migrations, `ApprovalGraphQlController`, GraphQL SDL/security tests, `frontend/src/app/api/graphql/route.ts`, GraphQL codegen, payment detail, approval queue, and role-workspace map.

## Readiness and scope

- `77.3` is `READY`: immutable `audit.audit_log`, ordinary tenant/branch RLS, a distinct auditor role/policy, and source-required fields already exist.
- `78.4` is conditionally `READY` after 77.3. The Wave 9 GraphQL adapter and fixed BFF allowlist are existing source-compatible transport seams.
- Story 24.8's “evidence-audit has no implementation” statement is stale after Wave 8. It must split into `24.8A` audit/command-history drawer, `24.8B` auditor audit-search workspace, `24.8C` source-owned message evidence, `24.8D` Playwright acceptance (iteration-blocked), and `24.8E` bundle export (decision-blocked).
- `24.8C` is `SOURCE-BLOCKED`: existing signature/ingress/ISO implementations do not currently provide a source-owned typed evidence read port. No raw message, hash, signature, parsed-message, or trace UI will be invented.

## Queue

1. 77.3 — typed scoped audit query and PostgreSQL proof.
2. 78.4 — secured query-only GraphQL and BFF operation allowlist.
3. 24.8A — payment-detail audit drawer.
4. 24.8B — auditor audit-search workspace.

## RED evidence

- `AuditQueryArchitectureTest` added before the query port; it must fail with `ClassNotFoundException` until the public boundary exists.

## Evidence log

### Recovery incident — resolved

- Failed command: `curl -fsS http://localhost:8080/realms/sepa-nexus/.well-known/openid-configuration`; exit code `7`.
- First meaningful failure: connection to Keycloak port `8080` was refused.
- Classification: `RUNTIME_CONFIGURATION_DEFECT`.
- Root cause: the compose request did not revive the existing stopped `infra_keycloak-postgres_1`, so Keycloak could not start.
- Fix: explicitly started the existing `keycloak-postgres` and `keycloak` services; no persistent configuration, seed data or project volume was changed.
- Focused proof: Keycloak 26.6.4 discovery succeeded, then real maker, approver, operator and auditor sessions completed the BFF paths below.

### Recovery incident — resolved

- Failed command: isolated-runtime Spring backend start; exit code `1`.
- First meaningful failure: the pristine PostgreSQL 18 database contained only `sepa_migration`, while Spring attempted the `sepa_app` datasource before the app role/schema existed.
- Classification: `RUNTIME_CONFIGURATION_DEFECT`.
- Root cause: a clean Wave 10 PostgreSQL container requires the full Flyway history before the running backend can obtain its application datasource.
- Fix: ran the repository Flyway migrate command against the isolated container, applying V1–V60; the backend then started normally.
- Focused proof: the backend served secured GraphQL audit reads on `:8081` and the BFF served the fixed operations on `:3000`.

### Recovery incident — resolved

- Failed command: maker payment submission through the live BFF; exit code `0` at HTTP level, with a `500` application response.
- First meaningful failure: `ApprovalMatrixPolicyException: An active approval matrix rule uses an unsupported selector`.
- Classification: `RUNTIME_CONFIGURATION_DEFECT`.
- Root cause: the isolated runtime fixture used `min_amount=0`, which is not a supported selector for the active approval rule.
- Fix: changed only the isolated runtime fixture to use null selectors. Production seed data was not altered.
- Focused proof: maker submission became `PENDING`, approver action became `APPROVED`, and the immutable audit row was returned through the operator and auditor BFF operations.

### Focused proof

- `./mvnw -f backend test -Dtest=CommandAuditQueryIntegrationTest,AuditQueryArchitectureTest,ApprovalGraphQlRuntimeTest,GraphQLReadModelOwnershipTest,GraphQLReadOnlyStructureTest,ProductionGraphQlIntrospectionTest,ApprovalSubmissionIntegrationTest,ModularityTest,OwnershipArchRulesTest` → **39 tests, 0 failures, 0 errors, 0 skipped**.
- The focused audit Testcontainers suite proves ordinary tenant reads, branch narrowing, empty/foreign tenant and branch denial, auditor cross-tenant read, auditor write denial, filter combinations, opaque cursor validation, equal-timestamp order, no duplicate pages and page-size cap.
- A temporary SDL `Mutation` was introduced solely to prove the guard: `GraphQLReadOnlyStructureTest` failed (`1/2`), the exact temporary change was removed, and the rerun passed (`2/2`). No mutation residue remains.
- Frontend GraphQL codegen was run twice with no second-run diff. Lint, typecheck and production build passed; lint retains the pre-existing TanStack compatibility warning in `payments-table.tsx`.
- Final backend regressions after the URL-state hydration repair: `backend-regression-final-1.log` and `backend-regression-final-2.log` each report **533 tests, 0 failures, 0 errors, 0 skipped, exit 0**.

### Runtime proof

- Stack: real Keycloak **26.6.4**, Next.js BFF, Spring backend, Kafka and an isolated Wave 10 PostgreSQL **18** container migrated through V1–V60.
- Maker submitted an approval-required payment; approver approved it. The permitted operator opened the payment audit drawer through the BFF `PaymentAuditTrail` operation and received command, actor, role, outcome, timestamp, correlation ID and approved before/after disclosure.
- Auditor authentication made `/evidence` visible and the fixed BFF `AuditEntries` operation returned the audit record. A payment/command URL filter constrained the server query. Two cursor pages returned distinct audit-entry IDs with no duplicates.
- The drawer renders the correlation ID as selectable text and keeps `audit.correlation-id.copy-button`; its safe clipboard action announces success or failure through `audit.correlation-id.copy-status` (`aria-live="polite"`). Missing IDs render `—`, with no synthetic value. The project has no approved component-test runner, so this is structural proof plus the real runtime drawer smoke.
- Non-auditor `/evidence` navigation redirected away; the same user received GraphQL `Forbidden` for `AuditEntries`. Anonymous direct backend GraphQL returned `401`. An unknown BFF operation returned `400`. Cookie-jar inspection found no access or refresh token; the BFF keeps its opaque HttpOnly session cookie only.
- A single live realm cannot mint a second tenant without changing seed data. Real Keycloak proves role/BFF behavior; PostgreSQL 18 Testcontainers and signed security fixtures provide the tenant/branch and auditor cross-tenant isolation proof.

### Retained logs

`/tmp/DEBINA-AUDIT-EXPLORER-EVIDENCE-DRAWER-WAVE-10/` retains backend/frontend logs, session and operation responses, payment audit-trail response, cursor-page responses, denial and unknown-operation responses, and the final full-regression logs.
