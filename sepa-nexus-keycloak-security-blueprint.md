# SEPA Nexus — Keycloak / Security Blueprint

`[SUPERSEDED, partial]` **On version and protocol specifics only** (Keycloak version pin, DPoP/FAPI-2 profile detail, PostgreSQL 18-vs-19 baseline framing), this document is superseded by `sepa-nexus-keycloak-26-security-architecture-blueprint.md` — read that document for anything version- or protocol-dated. **This document remains the source of truth** for the realm/role/claim/GUC narrative shape (tenant→branch two-level tenancy, the eleven-role model, admin-command mapping), which the -26- document itself builds on and does not restate.

**Scope.** Concrete realm / Organizations / claims / claim→GUC / BFF / RLS / admin-authorization design — closing decision-gate blocker B5 (security half) and R-14. Consistent with ADR-N3 (BFF), the main blueprint §3.5/§4.7 (selective RLS + service roles), §7.2 admin-command inventory, and the ownership integration role model. `[NO-CODE]` — realm/table/claim summaries only, no exported JSON, no production DDL, no code. Does not reopen ADR-N1…N8.
**One-line purpose.** One Keycloak realm maps an operator's Organization/branch/roles into a JWT; the Next.js BFF turns that JWT into a server session; the backend turns the session into PostgreSQL GUCs; selective RLS turns GUCs into row visibility. Every admin command is role-gated and audited.

---

## 1. Executive Verdict

`[FREEZE]` **BFF, per ADR-N3**: tokens live in a Next.js server session (HttpOnly cookie), never in the browser. `[ADOPT]` **Two-level tenancy** (tenant → branch) via Keycloak **Organizations** claims → `app.tenant_id`/`app.branch_id` GUCs → **selective** RLS (tenant/evidence tables only; queues and ledger protected by ownership grants, not RLS — §4.7). `[ADOPT]` Eleven roles, one per operator job, mapped to the §7.2 admin-command inventory via **FGAP v2** for the admin plane. `[MVP]` covers the realm, three clients, Organizations claims, the role set, claim→GUC, and the two-token/empty-GUC tests; `[P1]` adds FAPI-2 on the integration client, passkeys, and the full FGAP admin-permission model. This is production-*shaped* Keycloak usage on a synthetic realm — real patterns, no real bank identity.

---

## 2. Security Model Overview

```text
Operator browser
  → Next.js BFF (confidential client, Authorization Code + PKCE, server-side token exchange)
       → HttpOnly Secure session cookie (no token in browser — ADR-N3)
  → BFF attaches bearer server-side → backend REST (commands) / GraphQL (reads, read-only)
       → backend security context extracts claims (tenant_id, branch_id, roles, sub, sid)
       → per-request: set_config('app.tenant_id'|'app.branch_id'|'app.user_id', ...)
            → PostgreSQL selective RLS (tenant/evidence tables) + ownership grants (queues/ledger)
  → SSE live feeds proxied through the BFF (ADR-N4) — never a direct browser→backend stream
```

Four enforcement layers, defense in depth: **Keycloak** (who you are, what org/branch/roles) → **BFF** (session, no browser token) → **method security** (role/permission on the command) → **PostgreSQL** (RLS + grants, so a bug in an upper layer still cannot leak another tenant's rows).

---

## 3. Keycloak Realm Model

| Object | Name | Purpose | MVP/P1/P2 |
|---|---|---|---|
| Realm | `sepa-nexus` | one realm for the whole platform | `[MVP]` |
| Client | `sepa-web` | Next.js BFF — confidential, Authorization Code + PKCE, server-side exchange (ADR-N3) | `[MVP]` |
| Client | `sepa-api` | backend resource server — bearer-only, validates tokens | `[MVP]` |
| Client | `sepa-integration` | machine-to-machine PSP/CSM submission — client credentials; FAPI-2 profile in `[P1]` | `[MVP]` (client) / `[P1]` (FAPI-2) |
| Organization | `demo-bank-org` | one demo Organization = one tenant boundary (Keycloak Organizations) | `[MVP]` |
| Tenant attribute | `tenant_id` | stable UUID for the Organization → `app.tenant_id` | `[MVP]` |
| Branch attribute | `branch_id` | sub-unit within the Organization → `app.branch_id` | `[MVP]` |
| Roles | core 11 (see §6) | realm roles mapped to operator jobs | `[MVP]` |
| Client scope | `sepa-guc` | carries `tenant_id`/`branch_id`/`organization_id` claims into the token | `[MVP]` |
| Authentication | password + TOTP (MVP); passkeys/WebAuthn (`[P1]`) | login | `[MVP]`/`[P1]` |

Hardening `[ADOPT]`: SAML disabled; exact redirect URIs only; Keycloak 26.6.x pinned; short access-token TTL + rotating refresh; separate Keycloak database (Iteration 0); admin console not exposed to operators.

---

## 4. Organizations, Tenants and Branches

`[ADOPT]` Keycloak **Organizations** is the tenant primitive. Model:
- **Organization** `demo-bank-org` ⇒ one **tenant** (`tenant_id` UUID). Multi-org is the natural P1 growth (KIR-style multiple participants) but MVP seeds exactly one.
- **Branch** is an attribute on the user (`branch_id`) — a sub-unit inside the tenant (e.g. a regional operations desk). A user with a `branch_id` sees only that branch's rows; a user without one (tenant-wide operator) sees the whole tenant.
- **Claim propagation**: Organization membership → `organization_id`/`tenant_id` claim; user attribute → `branch_id` claim. Both flow through the `sepa-guc` client scope.

`[FREEZE]` Two-level RLS: `tenant_id` is mandatory on tenant tables; `branch_id` is optional — a null `app.branch_id` GUC means "all branches of this tenant," a set one narrows to that branch. This is exactly the policy shape already in main blueprint §4.7.

---

## 5. Clients

| Client | Type | Flow | Token audience | Notes |
|---|---|---|---|---|
| `sepa-web` | confidential | Authorization Code + PKCE, server-side exchange | `sepa-api` | the BFF; only place that exchanges a code for tokens; holds no browser token |
| `sepa-api` | bearer-only | validates tokens | — | backend resource server; never initiates login |
| `sepa-integration` | confidential (M2M) | client credentials | `sepa-api` | PSP/CSM submission; `[P1]` FAPI-2 profile (sender-constrained, DPoP/mTLS) |

---

## 6. Roles and Permissions

Eleven roles, one per operator job. `[ADOPT]` These are the authoritative role names — the frontend role-to-workspace matrix and the §7.2 admin-command inventory both reference this table.

| Role | Purpose | Allowed Commands | Allowed Workspaces | MVP/P1/P2 |
|---|---|---|---|---|
| `operator` | general ops read + triage | assign exception (P1) | Ops Control Room, all read | `[MVP]` |
| `payment_viewer` | read payments/files/lineage | none | Payments & Files (read) | `[MVP]` |
| `payment_submitter` | submit synthetic payments/files | submit payment, submit file | Payments & Files | `[MVP]` |
| `settlement_operator` | run/close cycles | close settlement cycle | Settlement & Liquidity | `[MVP]` |
| `egress_operator` | manage delivery | resend/cancel outbound artifact | Egress & Delivery | `[MVP]` |
| `reconciliation_operator` | triage exceptions | assign/resolve/false-positive exception | Reconciliation & Cases | `[MVP]` (assign) / `[P1]` (resolve) |
| `case_operator` | R-message decisions | resolve recall/case, escalate, close | Reconciliation & Cases | `[P1]` |
| `reference_data_admin` | maintain catalogs | reference-data CRUD | Reference Data / Admin | `[MVP]` (thin) / `[P1]` (full) |
| `security_admin` | realm-adjacent admin, keys | manage keys (key registry), role assignment (via Keycloak) | Admin | `[P1]` |
| `simulation_operator` | drive the lab | launch/replay simulation | Simulation Lab | `[MVP]` |
| `auditor` | cross-tenant read-only | none (read only, incl. evidence) | Evidence/Audit (read, cross-tenant) | `[MVP]` |

`[ADOPT]` `manual ISO correlation/replay` is an `ops_senior` capability (a permission granted to `operator` at a senior level in FGAP, `[P1]`), not a 12th role — avoids role sprawl.

---

## 7. Claims and Token Model

| Claim | Source | Used By | Purpose |
|---|---|---|---|
| `tenant_id` | Organization attribute | backend → `app.tenant_id` GUC | tenant RLS |
| `branch_id` | user attribute | backend → `app.branch_id` GUC | branch RLS (optional) |
| `organization_id` | Organization | reporting/audit scoping | display + cross-ref |
| `roles` | realm roles | method security + frontend visibility | authorization |
| `permissions` | FGAP v2 (P1) | fine-grained admin-command gating | admin plane |
| `sub` (subject) | Keycloak user id | `app.user_id` GUC + audit actor | audit attribution |
| `sid` (session_id) | Keycloak session | BFF session correlation + audit | session tracing |

`[FREEZE]` Tokens are validated by `sepa-api`; the browser never sees them (ADR-N3). `permissions` is `[P1]` (FGAP v2); MVP gates on `roles` alone, which is sufficient for the eleven-role model.

---

## 8. Next.js BFF Integration

`[FREEZE]` Per ADR-N3:
- `sepa-web` is a **confidential** client; the **Next.js server** performs the Authorization Code + PKCE exchange.
- The result is a **server-side session**, referenced by an **HttpOnly, Secure, SameSite** cookie. No access/refresh token is ever exposed to browser JavaScript or browser storage.
- Every browser call hits a Next.js route handler; the handler attaches the bearer **server-side** and forwards to `sepa-api` (REST command or GraphQL read).
- Refresh happens server-side; the browser only holds the opaque session cookie.
- SSE (ADR-N4) is opened **to the Next.js server**, which proxies the backend stream — no direct browser→backend streaming connection, no token in the stream URL.

DPoP-bound SPA is the documented `[P2]` alternative only; it is never the MVP default.

---

## 9. PostgreSQL GUC and RLS Integration

```text
BFF session (server-side)
  → backend security context (claims: tenant_id, branch_id, sub, roles)
  → per-request, per-connection:
       set_config('app.tenant_id', <tenant_id>, true)
       set_config('app.branch_id', <branch_id or NULL>, true)
       set_config('app.user_id',  <sub>, true)
  → PostgreSQL policies evaluate against these GUCs
```

`[FREEZE]` **Selective** RLS (main blueprint §4.7), not universal:

| Table category | RLS? | Mechanism |
|---|---|---|
| Tenant-facing operational (`payments`, `raw_inbound_messages`, `inbound_files`, `outbound_messages`) | **Yes** | `tenant_id` + optional `branch_id` policy on the GUCs |
| Evidence/audit (`evidence_records`, `audit_log`) | **Yes** | tenant policy + cross-tenant read for `auditor` service context |
| Queue / dispatcher (`<schema>.outbox_events`/`inbox_events`, batch tables) | **No** | ownership grants; `outbox_dispatcher_role` narrow grant, no domain writes (ADR-N5) |
| Ledger core (`journal_entries`, `journal_lines`, `liquidity_accounts`) | **No** | table ownership; `ledger_role` sole writer; service views for cross-tenant reporting |

`[FREEZE]` **Empty-GUC session sees zero rows** on RLS tables — the founding negative test. A request without a set `app.tenant_id` is not "sees everything," it is "sees nothing."

---

## 10. Service Roles and Background Workers

`[ADOPT]` Background workers (`OutboxDispatcher`, `CycleScheduler`, `ReconciliationRunner`, `EgressDispatcher`, Spring Batch) open DB work via `SystemSessionInitializer`. Which mechanism depends on the table (§3.5):
- On **RLS-protected tenant tables**: set `app.role = 'system_<name>'` GUC; a narrow `p_system_*` policy grants exactly the rows that worker needs (e.g. a relay reading unpublished outbox-linked rows).
- On **queue/ledger tables**: no RLS; the worker's DB role has the explicit grant or it does not. `outbox_dispatcher_role` gets `SELECT/UPDATE(published_at)` across every `<schema>.outbox_events` and **no** domain-table grant (ADR-N5).
- **No background worker may run against an RLS table with a default (empty-GUC) session** — enforced by the empty-GUC-zero-rows test plus a grant-matrix test.

---

## 11. Admin Command Authorization Matrix

`[ADOPT]` Every command from main blueprint §7.2, gated + audited. RLS context = which GUCs must be set for the command's DB work.

| Command | Required Role | Audit Required | RLS Context | MVP/P1/P2 |
|---|---|---|---|---|
| Submit payment / file | `payment_submitter` (or `sepa-integration` client) | yes | tenant (+branch) | `[MVP]` |
| Launch / replay simulation | `simulation_operator` | yes | tenant (sim scope) | `[MVP]` |
| Close settlement cycle | `settlement_operator` | yes | tenant | `[MVP]` (Iter 4) |
| Resend / cancel outbound artifact | `egress_operator` | yes | tenant | `[MVP]` (resend) / `[P1]` (cancel) |
| Assign / resolve / false-positive reconciliation exception | `reconciliation_operator` | yes | tenant | `[MVP]` (assign) / `[P1]` (resolve/FP) |
| Resolve recall / escalate / close case | `case_operator` (resolve), `case_supervisor` perm (escalate) | yes | tenant | `[P1]` |
| Manual ISO correlation / replay | `operator` + `ops_senior` permission (FGAP) | yes | tenant | `[P1]` |
| Reference-data CRUD | `reference_data_admin` | yes | platform/tenant catalog | `[MVP]` (thin) / `[P1]` (full) |
| Manage signature keys | `security_admin` | yes | platform | `[P1]` |

`[FREEZE]` All commands are **REST**, role-gated, audited same-transaction with actor (`sub`) + role. **Never GraphQL mutations** — GraphQL is read-only (main blueprint §6.6).

---

## 12. Frontend Role Visibility Model

`[ADOPT]` The frontend reads `roles` from the BFF session (server-side) and renders a **single role→workspace map** — nav items, action buttons, and whole workspaces are hidden when the role lacks access. Hiding is UX, not security: the real gate is the backend (method security + RLS). Rule: **no action button renders without a role mapping** (and the frontend blueprint's Playwright role tests assert exactly this). The role names here are the source of truth the frontend blueprint's §9/§3E matrix must match — no frontend-only roles.

---

## 13. Security Non-Goals

`[REJECT]`/non-goal for MVP: real bank identity or KYC; production PKI/CA/HSM (the `signature` module is synthetic); real sanctions/AML gating; SCIM user provisioning; multi-realm federation; social login; step-up auth beyond TOTP (passkeys are `[P1]`); fine-grained ABAC beyond tenant/branch/role (+FGAP permissions in P1); token binding beyond FAPI-2 on the integration client (P1). None of these adds an MVP lesson; each is a named later track.

---

## 14. Tests

`[PLAYWRIGHT]` for the BFF/no-token assertions; Testcontainers for the DB-level ones. Wired as Iteration 0/1 CI gates.

- **two-token tenant isolation** — token A (tenant 1) cannot read tenant 2's payments; token B (tenant 2) cannot read tenant 1's.
- **branch isolation** — a token with `branch_id` set sees only that branch's rows within its tenant.
- **empty-GUC-zero-rows** — a DB session with no `app.tenant_id` sees zero rows on every RLS table.
- **background worker service role** — a worker with `app.role='system_<n>'` sees exactly its intended rows, nothing more; `outbox_dispatcher_role` has zero domain-table write.
- **forbidden admin command** — a role without the required role/permission gets 403; the attempt is audited.
- **BFF session cookie** — login yields an HttpOnly Secure cookie; the session works across requests.
- **no browser token exposure** — assert no access/refresh token in any browser-readable storage, cookie flag, or response body reachable by JS (ADR-N3).
- **reference-data admin role** — only `reference_data_admin` can CRUD catalogs; others read-only.
- **auditor read-only** — `auditor` reads across tenants (evidence/audit) but has zero write/command capability.

---

## 15. MVP / P1 / P2 Scope

| Capability | Scope |
|---|---|
| Realm, 3 clients, one Organization/tenant/branch, 11 roles | `[MVP]` |
| Organizations claims → `tenant_id`/`branch_id` GUCs → selective two-level RLS | `[MVP]` |
| BFF (ADR-N3), empty-GUC-zero-rows, two-token tests | `[MVP]` |
| Admin-command role gating (roles-based) + same-TX audit | `[MVP]` |
| FGAP v2 fine-grained `permissions` for the admin plane | `[P1]` |
| FAPI-2 profile on `sepa-integration`; passkeys/WebAuthn | `[P1]` |
| Multi-Organization (multiple participants), audit hash-chaining | `[P1]` |
| DPoP-SPA alternative, SCIM, federation, step-up beyond TOTP | `[P2]` |

---

## 16. Iteration 0 / Iteration 1 Impact

- **Iteration 0:** realm seed import (`sepa-nexus`, `demo-bank-org`, one tenant/branch, 11 roles, three clients, `sepa-guc` scope with claim mappers); separate Keycloak DB in the compose stack; the claim→GUC filter + `SystemSessionInitializer` scaffolding; selective-RLS policy scaffolding on the first tenant tables; the two-token + empty-GUC + grant-matrix tests as blocking CI gates. **No FGAP, no FAPI-2 yet** — just the realm, claims, GUCs, and the negative tests.
- **Iteration 1:** the BFF login flow goes live end-to-end (operator logs in, gets a session, sees the payment spine); `payment_submitter`/`payment_viewer`/`operator` roles gate the first commands and reads; `app.user_id` flows into the first audit rows. Branch-level RLS is exercised by the two-token test even though the demo seeds one branch.

---

*End of Keycloak/security blueprint. `[NO-CODE]` — realm/claim/policy summaries only; realm export and RLS DDL land in Iteration 0 per EPIC-SEC-KC. Consistent with ADR-N3, main blueprint §3.5/§4.7/§7.2, and the ownership integration role model. Synthetic realm — no real bank identity or compliance claim.*
