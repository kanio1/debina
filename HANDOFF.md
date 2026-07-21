# HANDOFF

## Zadanie

Wave 10 completed the immutable audit-log read path through `AuditQueryPort`, Query-only GraphQL and fixed BFF operations, the Payment Detail Evidence Drawer, and the auditor `/evidence` workspace.

## Zrobione

- Recovery start SHA: `7e61ecb85e9eb1e5a31cfcaf18d60863c6e4fa52`; existing Wave 10 commits and interrupted UI/planning work were preserved.
- Correlation ID copy now uses a guarded Clipboard API call, selectable text, `aria-live="polite"` success/failure feedback, stable test IDs and an honest `—` for a missing value. The evidence workspace uses `useSearchParams`/router URL state, avoiding direct `window` access and hydration mismatch.
- Focused backend gate passed: **39 tests, 0 failures/errors/skips**. Temporary GraphQL Mutation injection made the read-only structural test RED, was removed, and the GREEN rerun passed.
- Real Keycloak 26.6.4 + Next BFF + Spring + isolated PostgreSQL 18 proof completed: maker submit, approver approval, permitted operator drawer, auditor workspace/filter/cursor, normal-user denial, unknown-operation rejection and direct-backend 401.
- Final frontend gate: codegen twice deterministic, codegen check/typecheck/build PASS; lint PASS with only the known TanStack warning. Final backend regressions: **two consecutive runs, each 533 tests, 0 failures/errors/skips**.
- Story 24.8 is split: 24.8A/24.8B done; 24.8C SOURCE-BLOCKED, 24.8D ITERATION-BLOCKED, 24.8E DECISION-BLOCKED. Governance, story inventory, capability graph and skills validators PASS.

## Utknęliśmy na

No technical blocker. Remaining roadmap blockers are intentional: source-owned message evidence (24.8C), Playwright sequence (24.8D), evidence export PII/format decision (24.8E), and the separately tracked Control Room scope.

## Plan na następny krok

Start only the next analytically READY capability; do not reopen Wave 10 or relabel the blocked 24.8C/D/E capabilities as delivered.

## Pułapki, których nie wolno powtórzyć

- A clean isolated Wave 10 PostgreSQL runtime needs Flyway V1–V60 before Spring starts its `sepa_app` datasource. Do not change the persistent compose database.
- Compose may leave the existing Keycloak PostgreSQL service stopped; inspect container state before classifying connection failures as transient.
- The BFF session store is intentionally in-memory; restarting Next requires a new Keycloak login.
- Audit GraphQL uses `variables.auditFilter` with `commandType`, not `filter`/`command`; cursors use JSON `null` for the first page.
- Preserve the Query-only/allowlist boundary: no mutation, subscription, direct browser backend call, raw evidence payload or audit write surface.
