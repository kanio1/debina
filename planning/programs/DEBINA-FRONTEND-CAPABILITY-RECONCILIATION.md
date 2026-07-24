# Debina frontend capability reconciliation

Status: `AI_DRAFT`, `NOT_REVIEWED`
Baseline: `d5be545`, 2026-07-24
Scope: Phase E planning only. No screen, BFF route, Keycloak role, or API was changed.

The current frontend is Next.js 16.2.10 / React 19.2.7 / TypeScript 6.0.3 on
Node 24.18.0 with pnpm declared as 10.33.0 (local Corepack resolves 10.33.4).
REST owns commands; query-only GraphQL supplies operational reads. The BFF owns
session/token handling, not payment semantics.

| Capability | Backend | REST/GraphQL | Kafka | BFF | UI | Smoke | Epic/story | Source state | Decision |
|---|---|---|---|---|---|---|---|---|---|
| Authentication/session | Keycloak JWT and role checks | REST secured | N/A | login/callback/logout/session routes | session-aware shell | D3A | EPIC-02, 05, 74 | project policy | IMPLEMENTED_AND_PROVEN |
| Workspace/role mapping | role/branch authorization | REST/GraphQL visibility | N/A | session claims | payment and approval workspaces | partial | EPIC-23, 24, 74 | project policy | PARTIALLY_IMPLEMENTED |
| Payment submission (JSON) | command + idempotency | REST | outbox | payments POST | no dedicated form in inspected payment pages | D3A | 19.1, 24.1 | project policy | IMPLEMENTED_NOT_PLANNING_RECONCILED |
| Signed file upload | signed pain endpoint exists | `POST /api/v1/iso/pain001` | payment outbox | no route | no upload/progress/result UI | none | 19.2, 19.4, proposed E1-06 | source/channel blocked | READY_FOR_SLICE_PLANNING |
| Payment list | visibility read model | REST | N/A | payments route | accessible table/states | D3A | 24.2A | project policy | IMPLEMENTED_AND_PROVEN |
| Payment detail | payment + visibility | REST | N/A | payment detail route | detail screen/states | D3A | 24.2A | project policy | IMPLEMENTED_AND_PROVEN |
| ISO identifiers | ISO source-owned read | GraphQL | ISO outbox | GraphQL allowlist proxy | ISO panel | D3B | 26.3, 26.4, 24.2A | message profile incomplete | IMPLEMENTED_NOT_PLANNING_RECONCILED |
| Timeline | payment history | REST | N/A | timeline route | timeline | D3A | 20.x, 24.2A | project policy | IMPLEMENTED_AND_PROVEN |
| Approval queue | approval policy and commands | GraphQL query + REST commands | N/A | GraphQL + decision routes | queue and detail actions | D3B | EPIC-76 | project policy | IMPLEMENTED_AND_PROVEN |
| Audit/evidence | append-only audit + ISO evidence reads | GraphQL query | audit events not exposed as UI owner | allowlisted GraphQL | evidence drawer | D3B | EPIC-77, 24.8A-C | retention legal review | IMPLEMENTED_NOT_PLANNING_RECONCILED |
| Route/rail state | project routing primitives | no admitted read contract | planned | none | none | none | EPIC-51–56 | participant sources missing | CAPABILITY_BLOCKED |
| File/group status | no file/group aggregate admitted | none | none | none | none | none | EPIC-73 | partial-file decision blocked | BACKEND_CAPABILITY_MISSING |
| Settlement/finality | settlement primitives and finality evidence | no user-journey read model | internal events | none | none | none | EPIC-33–42 | rail profile missing | CAPABILITY_BLOCKED |
| Egress/delivery | egress primitives | no admitted operational read | egress outbox/inbox | none | none | none | EPIC-43–50 | participant sources missing | CAPABILITY_BLOCKED |
| Reconciliation | planned primitives/backlog | none | planned | none | none | none | EPIC-57–64 | report/profile sources missing | BACKEND_CAPABILITY_MISSING |
| Return/recall | correlation primitives only | none | planned | none | none | none | EPIC-30, 42, 69 | EPC messages known; profile/flow review needed | READY_FOR_SLICE_PLANNING |
| Case/investigation | backlog only | none | planned | none | none | none | EPIC-65–72 | use-case/source review required | BACKEND_CAPABILITY_MISSING |
| Reporting | no Phase E report train | none | none | none | none | none | future | EBA/legal applicability review | OUT_OF_SCOPE |

## E1 frontend vertical slice

The proposed `E1-06` story is one vertical outcome, not separate backend/BFF/UI
stories:

- authenticated submitter selects one XML file and detached signature metadata;
- BFF enforces session, role, origin/CSRF, content type and a reviewed byte limit;
- BFF streams/forwards to the existing REST command without logging or parsing XML;
- UI shows upload progress and deterministic validation/provenance outcome;
- accepted result links to existing payment detail, ISO identifiers and evidence metadata;
- raw XML, account/name/address data, signature bytes and private certificate material are
  absent from telemetry and ordinary UI;
- loading, empty, error, unauthorized, invalid-signature, validation-rejected,
  duplicate/idempotent and accepted states are accessible by keyboard and announced
  appropriately.

GraphQL remains query-only and source-owned. A new upload mutation or frontend-owned
payment model is explicitly out of scope.

## UI quality gate

The `impeccable` review found no product context file (`NO_PRODUCT_MD`); existing
payments list/detail visual language and accessibility patterns therefore form the E1
baseline. This does not block a scoped upload journey. Before implementation:

- Product review supplies copy for signature, validation and source-sensitive outcomes.
- Security review approves which evidence metadata is visible per role.
- Accessibility review covers focus order, progress announcements, table/error semantics
  and non-colour status communication.
- QA allocates only one Playwright signed-upload smoke; validation breadth stays below UI.

## Planning reconciliation

`24.2A`, `24.8A`, `26.3` and `78.1–78.3` carry legacy planning warnings despite
implemented runtime evidence. Phase E proposals classify them as
`IMPLEMENTED_NOT_PLANNING_RECONCILED`; no status is changed until human review and an
executable verify are recorded under `semantic_enforcement: ENFORCED`.
