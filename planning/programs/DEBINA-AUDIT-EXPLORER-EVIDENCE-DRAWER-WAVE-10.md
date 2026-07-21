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

In progress. No capability is claimed complete until focused Testcontainers, GraphQL/BFF, runtime and regression evidence are recorded.
