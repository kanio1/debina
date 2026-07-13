# SEPA Nexus — Keycloak 26.6.4 Security Architecture Blueprint

**Nature.** A security *architecture decision* document — decisions, not options — for the line **Keycloak 26.6.4 → Next.js/React BFF → Spring Boot / Spring Security backend → REST / GraphQL (read-only) / optional gRPC → PostgreSQL 18 (PG19 lab)**. It supersedes the earlier `sepa-nexus-keycloak-security-blueprint.md` on version-specific and protocol decisions; the role model and claim→GUC shape are consistent with it and with the ownership/frontend blueprints. `[NO-CODE]` — no application code, no production DDL. Does not reopen ADR-N1…N8.
**Frozen inputs:** Next.js BFF token model, no browser token storage, GraphQL read-only, REST commands through BFF, Kafka internal-only, selective RLS (tenant/evidence yes; queue/ledger by grants), Keycloak as external IAM, one Spring Modulith deployable.

---

## 1. Executive Security Verdict

`[FREEZE]` **Four-layer defense in depth, no single point of trust:** Keycloak 26.6.4 (identity + org/branch/roles) → Next.js BFF (server-side session, HttpOnly cookie, **zero browser token**) → Spring Security Resource Server (JWT validation, method security on every command and read) → PostgreSQL (selective RLS + ownership grants, so a bug in any upper layer still cannot cross a tenant boundary). `[ADOPT]` Keycloak carries **only identity and coarse authorization** (who, which org/branch, which roles) — it is **never** the payment business-authorization engine; finality, settlement legality, and case-timing rules live in the domain, not in tokens. `[FREEZE]` **PostgreSQL 18 is the baseline; PostgreSQL 19 is lab/security-profile only** — PG19 is Beta 1 as of June 2026 with GA targeted Sept/Oct 2026, and the project explicitly warns against production use, so it cannot be the security baseline. MVP ships the realm, three clients (BFF confidential, resource-server, M2M), Organizations claims → GUC → two-level RLS, eleven roles, method security, and the negative-test wall; DPoP/FAPI-2 on the machine client and passkeys are `[P1]` — real, supported, but not MVP-blocking.

---

## 2. Source Verification Notes

Verified against official sources (July 2026):
- **Keycloak 26.6.4 is the current supported release** (June 2026); 26.5 reached EOL 2026-04-08 — so the platform must target the 26.6.x stream, patched forward. `[ADOPT]`
- **Organizations** is a supported realm feature (B2B/B2B2C baseline) — the tenant primitive. `[ADOPT]`
- **FAPI 2.0 Security Profile + Message Signing are Final and supported**; Keycloak ships built-in client profiles `fapi-2-security-profile`, `fapi-2-dpop-security-profile`, `fapi-2-message-signing` passing the FAPI conformance suite. `[ADOPT for P1]`
- **DPoP is fully supported since 26.4** (not preview) — sender-constrained tokens on all bearer endpoints, option to bind only refresh tokens for public clients. `[ADOPT for P1]`
- **Passkeys/WebAuthn** integrated natively in login forms (conditional + modal UI). `[ADOPT for P1]`
- **FGAP v2** (fine-grained admin permissions) is available — used for the admin plane; note several 26.6.x CVEs touched FGAP/Organizations/OIDC, reinforcing "patch to the newest 26.6.x." `[ADOPT]`
- **`secure-client-uris` executor now enforces HTTPS**, clock skew defaults to 10s per FAPI 2.0. `[ADOPT]`
- **Spring Security** secures GraphQL via `@PreAuthorize`/`@Secured` on data-fetching methods with context propagation to the fetch level, plus query depth/complexity instrumentation; Resource Server validates JWT with issuer + audience checks and a custom authorities converter. `[ADOPT]`
- **PostgreSQL 19 = Beta 1 (2026-06-04), GA ~Sept/Oct 2026, "do not run in production"**; PG18 is the current stable stream (18.x). Decision below. `[FREEZE]`

```text
PG18 baseline + PG19 lab/security profile
```

Rationale for the lab profile specifically: PG19 brings online checksum enable/disable, `REPACK CONCURRENTLY`, MERGE/SPLIT PARTITION, JIT-off-by-default — useful to *study*, and one PG19-era security fix class (leaky-operator statistics bypassing RLS, CVE-2017-7484/2019-10130 lineage) is exactly the kind of RLS-hardening lesson the lab wants — but none of it justifies a beta baseline under a payments-security story.

---

## 3. Target Security Architecture

```text
Operator browser (no token, ever)
  → Next.js BFF  [sepa-web: confidential, Auth Code + PKCE, server-side exchange]
       → HttpOnly/Secure/SameSite=Lax session cookie; CSRF token for state-changing calls
  → BFF attaches bearer server-side
       → Spring Security Resource Server [sepa-api]
            → validate: signature (JWKS), issuer, audience=sepa-api, exp, nbf (10s skew)
            → authorities converter: realm roles → ROLE_*, org/branch/sub → principal attributes
            → method security: @PreAuthorize on every REST command + every GraphQL data-fetcher
            → per request: set_config('app.tenant_id'|'app.branch_id'|'app.user_id', ...)
                 → PostgreSQL: selective RLS (tenant/evidence) + ownership grants (queue/ledger)
  → SSE proxied through BFF (ADR-N4)     |  Kafka internal-only, never browser-reachable
Machine-to-machine [sepa-integration]: client credentials (+FAPI-2/DPoP in P1) → same Resource Server
```

`[SECURITY-RISK, mitigated]` The two classic failure modes — token in the browser, and "authenticated == authorized for this tenant's rows" — are both closed structurally: BFF removes the first; RLS-on-GUC removes the second even if method security is misconfigured.

---

## 4. Keycloak 26.6.4 Realm Model

| Object | Decision | Why | MVP/P1/P2 |
|---|---|---|---|
| Realm `sepa-nexus` | `[FREEZE]` one realm | one platform, one trust domain; multi-realm is needless blast radius | `[MVP]` |
| Client `sepa-web` | `[FREEZE]` confidential BFF | the only token-holder; Auth Code + PKCE, server-side exchange (ADR-N3) | `[MVP]` |
| Client `sepa-api` | `[FREEZE]` bearer-only resource server | validates tokens; never initiates login | `[MVP]` |
| Client `sepa-integration` | `[ADOPT]` confidential M2M (client credentials) | PSP/CSM synthetic submission; FAPI-2/DPoP profile in P1 | `[MVP]` client / `[P1]` FAPI-2 |
| Client `sepa-ops-cli` (optional) | `[DEFER P1]` confidential, device/client-credentials | operator CLI for lab automation; not MVP | `[P1]` |
| Organization `demo-bank-org` | `[FREEZE]` one demo Organization = one tenant | Organizations is the supported tenant primitive | `[MVP]` |
| Tenant attribute `tenant_id` | `[FREEZE]` stable UUID on the Organization | → `app.tenant_id` GUC | `[MVP]` |
| Branch attribute `branch_id` | `[FREEZE]` user attribute | → `app.branch_id` GUC (optional narrowing) | `[MVP]` |
| User groups | `[ADOPT]` groups map to role bundles per job | assignment ergonomics, not an authz layer of their own | `[MVP]` |
| Realm roles vs client roles | `[FREEZE]` **realm roles** for the 11 operator jobs | one flat, auditable role namespace; client roles reserved for `sepa-api`-internal scopes if ever needed | `[MVP]` |
| Client scope `sepa-guc` | `[FREEZE]` carries `tenant_id`/`branch_id`/`organization_id` | one scope owns the GUC-feeding claims | `[MVP]` |
| Token mappers | `[ADOPT]` Organization → `tenant_id`/`organization_id`; user attr → `branch_id`; realm roles → `roles`; `sub`/`sid` native | deterministic claim shape for the converter | `[MVP]` |
| Client policies / profiles | `[ADOPT]` bind `sepa-integration` to `fapi-2-security-profile` (or `fapi-2-dpop-security-profile`) | supported FAPI-2 Final profiles | `[P1]` |
| Brute-force protection | `[FREEZE]` enabled (permanent-lockout off, temporary on) | credential-stuffing baseline | `[MVP]` |
| Passkeys/WebAuthn | `[ADOPT]` passwordless policy, passkeys enabled | native in 26.6; phishing-resistant operator login | `[P1]` |
| Admin event + login event logging | `[FREEZE]` enabled, retained | audit + forensics | `[MVP]` |

Hardening `[FREEZE]`: SAML disabled; exact/`https`-only redirect URIs (26.6 `secure-client-uris` enforces HTTPS); pin to the newest 26.6.x (the CVE list across 26.6.0–26.6.4 is long — redirect-URI bypass, refresh-token reuse on restart, introspection audience leak, WebAuthn bypasses — all fixed forward, so "latest 26.6.x" is a security requirement, not a preference); separate Keycloak database; short access-token TTL + rotating refresh with `revokeRefreshToken`; admin console not exposed to operators.

---

## 5. Client Security Model

| Client | Type | Flow | Secret? | PKCE | DPoP | Audience | MVP/P1/P2 |
|---|---|---|---|---|---|---|---|
| `sepa-web` | confidential (BFF) | Authorization Code | yes (server-side) | **yes** | no (BFF holds tokens server-side; DPoP adds little when the browser never has a token) | `sepa-api` | `[MVP]` |
| `sepa-api` | bearer-only | — (validates) | n/a | n/a | accepts DPoP-bound tokens if presented (P1) | self | `[MVP]` |
| `sepa-integration` | confidential (M2M) | Client Credentials | yes + rotation | n/a | **yes (P1)** — sender-constrained per FAPI-2 | `sepa-api` | `[MVP]` client / `[P1]` DPoP |
| `sepa-ops-cli` | confidential | Client Credentials / Device | yes | where applicable | optional | `sepa-api` | `[P1]` |

`[FREEZE]` decisions: `sepa-web` = confidential BFF; **browser never stores tokens**; Auth Code + PKCE; access/refresh **server-side only** in HttpOnly/Secure/SameSite cookies. `[REJECT]` SPA public client for MVP. `[DEFER P1]` DPoP: for the **browser** path it buys little (the token never reaches the browser under BFF), so it is **not** MVP; for **`sepa-integration`** (M2M) it is the right P1 hardening — sender-constrained tokens via the FAPI-2 DPoP profile. Client-secret rotation on `sepa-integration` is `[MVP]` (a scheduled rotation, aided by Keycloak client-secret-rotation policy); federated client authentication (26.6, secrets-free via external issuer/K8s SA) is a `[P2]` educational track.

---

## 6. Backend Spring Security Model

`[ADOPT]` Spring Security **OAuth2 Resource Server** with JWT (not opaque — self-contained validation avoids an introspection round-trip per request; introspection stays available as a P2 lab to teach the tradeoff). Validate **signature (JWKS), issuer, audience=`sepa-api`, exp, nbf** (10s skew per FAPI-2). A **custom authorities converter** turns realm roles into `ROLE_*` authorities and exposes `tenant_id`/`branch_id`/`sub`/`sid` as principal attributes for the GUC filter. **No user credentials ever reach the backend** — it sees only validated tokens.

| Endpoint Type | Security Model | Required Claims | Authorization Rule | Test |
|---|---|---|---|---|
| REST commands | Resource Server + `@PreAuthorize(hasRole(...))` | `roles`, `tenant_id`, `sub` | role per command (§11); tenant/branch GUC set before DB work | forbidden-command → 403 + audit |
| GraphQL read endpoint | Resource Server + `@PreAuthorize` on data-fetchers | `roles`, `tenant_id` | authenticated + role per sensitive fetch; read-only | cross-tenant read denied; unauth → 401 |
| gRPC (optional, P2) | mTLS **and** JWT with `audience`+claims | `roles`, `tenant_id` | explicit audience + claim check; never unauthenticated | no-audience → reject |
| Actuator / health | split: liveness/readiness public; the rest role-gated | none (probes) / `security_admin` (details) | probes anonymous; `/actuator/**` else restricted | detail endpoint unauth → 403 |
| File upload | Resource Server + `@PreAuthorize` + size/type gate | `roles`, `tenant_id` | `payment_submitter`; hardened parse downstream | oversized/невalid → rejected pre-parse |
| Simulation | Resource Server + `@PreAuthorize` | `roles`, `tenant_id` | `simulation_operator` only | other role → 403 |
| Object-decision commands `[ADD, security-review]` (approve/reject/release/escalate/vop-override) | Resource Server + custom `AuthorizationManager<MethodInvocation>` | `roles`, `tenant_id`, `sub` | resolves the target `payment`/`fraud_hold` argument **before** the service method body runs; checks tenant/branch ownership **and** `checker_user_id != maker_user_id` as one object-level decision, not two separate checks bolted on | forbidden-object → 403 + audit; BOLA object-manipulation test (§13) |

`[AUTHZ-RISK, mitigated]` Method security is enforced on **both** REST commands and GraphQL data-fetchers — GraphQL being read-only does not make it public.

`[CHANGE, security-review]` **Object-level checks use `AuthorizationManager`, not SpEL string expressions.** Spring Security's `@PreAuthorize("hasRole(...)")` is right for role-only checks (the six rows above it), but the object-decision commands need to inspect the *resolved domain argument* (which payment, whose approval) — Spring Security 6/7's `AuthorizationManager<T>` interface (superseding the older `AccessDecisionManager`/`AccessDecisionVoter` architecture) is built exactly for this: a bean receives the method invocation, can inspect its arguments, and returns one `AuthorizationDecision` covering both the tenant-ownership check and the maker≠checker check together, evaluated once, before the method runs — not a role check in `@PreAuthorize` plus a second, easy-to-forget check written by hand inside the service method.

---

## 6a. Step-Up Authentication `[ADD, persona-driven][MVP, minimal]`

`[FREEZE]` **Token-freshness check, not an adaptive SCA engine.** Four commands require it: payment/batch approval above the matrix's step-up threshold, VoP-override, fraud-hold release, and approval-matrix/limit-policy configuration — all four are exactly the "high-risk action" list the maker-checker and risk controls above depend on for their guarantees to mean anything.

**Mechanism, minimal and Keycloak-native:**
```text
1. Standard login → normal token, auth_time = T0.
2. High-risk command attempted → backend checks: (now - auth_time) < step_up_window (e.g. 5 min)?
     fresh enough → proceed
     stale        → 401 + WWW-Authenticate hint → BFF redirects to Keycloak with acr_values=step-up (OIDC standard param, real Keycloak feature)
3. Keycloak forces re-auth (password or passkey re-entry, TOTP if configured) → issues a new token with a fresh auth_time and acr claim reflecting the step-up
4. BFF retries the original command with the fresh token → auth_time check passes → proceed, audited as a step-up-gated decision (§12)
```

`[ADOPT]` Enforced by a `@RequireStepUp(withinMinutes = 5)` method-security annotation (custom, thin wrapper over an `auth_time` claim check) on the four command handlers — not a new authentication system, a five-line guard reusing claims Keycloak already issues. `[REJECT]` a general-purpose adaptive/risk-based SCA engine (device fingerprinting, behavioral biometrics, ML risk scoring) — real products do this, it is genuinely `[P2]`-or-never for an educational MVP; the lesson here is "how do you gate one sensitive action behind fresh auth," not "how do you build a fraud-adaptive auth engine."

`[TESTABILITY]` This is a deliberately rich Playwright surface: session freshly authenticated → high-risk command succeeds; session authenticated >5 min ago → command 401s → redirect → re-auth → retry succeeds — a real, multi-step, observable flow, not a boolean flag.

---

## 7. REST / GraphQL / Protobuf Security

GraphQL is read-only but **not** a free-for-all.

| GraphQL Area | Risk | Control | Test |
|---|---|---|---|
| Endpoint auth | anonymous data harvest | Resource Server requires a valid token on the single `/graphql` endpoint | unauth query → 401 |
| Method-level authz | sensitive field exposed to wrong role | `@PreAuthorize` on the data-fetcher (e.g. evidence, cross-tenant reporting) | viewer cannot fetch auditor-only field |
| Cross-tenant read | tenant A reads tenant B | RLS on the underlying tenant tables (GUC-scoped) — the query physically cannot see other tenants' rows | two-token cross-tenant read → empty |
| Query depth / complexity | recursive/expensive query DoS | max-depth + complexity instrumentation (e.g. depth ≤ 10), reject over-limit | deep query → rejected |
| Introspection | schema disclosure | disable introspection in prod profile; allow in dev | prod introspection → disabled |
| Persisted queries | arbitrary query surface | `[P1]` allow-list persisted queries for the frontend; ad-hoc queries dev-only | non-persisted in prod → rejected (P1) |
| Raw evidence exposure | PII/evidence without role | evidence fetchers `@PreAuthorize(auditor/authorized role)` | non-auditor evidence read → 403 |
| Mutations | write via GraphQL | **no mutation resolvers exist** — writes are REST only | schema has zero mutations |

`[REJECT]` GraphQL mutations (ADR-aligned). `[ADOPT]` **gRPC only if** the routing-extraction P2 exercise ever happens, and then **only** with mTLS **and** JWT audience+claim validation — never claim-less gRPC.

`[ADD, security-review]` **REST error response shape**, undecided until now — `[FREEZE]` [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807) (`application/problem+json`: `type`, `title`, `status`, `detail`, `instance`, plus a `correlationId` extension member) for every 4xx/5xx REST response. Not decorative — a stable, typed error shape is exactly what Playwright negative-path assertions need to check *why* a command failed, not just that it returned non-2xx, and it is the same shape whether the failure is validation (422), idempotency conflict (409), forbidden (403), or object-not-found-or-not-yours (404 — see below).

`[ADD, security-review]` **Sensitive business flows get their own rate limit, separate from general API throttling** — this is OWASP API Security Top 10:2023's "Unrestricted Access to Sensitive Business Flows" category, and the 4EV/VoP/fraud-hold surface (main blueprint §2.2b/§2.2c) is exactly that kind of flow: `approve`/`reject`/`vop-override`/`fraud-hold-release` are rate-limited **per-user**, independent of the general per-IP/per-client API rate limit, so a compromised or careless approver session can't be used to rubber-stamp an unusual volume of decisions in a short window. `[MVP]`, deterministic thresholds (not adaptive/ML) — consistent with §6a's "no adaptive engine" stance.

---

## 8. Next.js BFF Security

| Concern | Decision | Why | Test |
|---|---|---|---|
| Token location | `[FREEZE]` server-side session only | no browser token = no XSS token theft | no-token-in-browser test |
| Session cookie | `[FREEZE]` HttpOnly + Secure + SameSite=Lax | Lax allows top-level nav, blocks CSRF on cross-site POST | cookie-flags test |
| Token exchange | `[FREEZE]` server-side code exchange (`sepa-web`) | confidential client secret never in browser | — |
| CSRF | `[FREEZE]` CSRF token (double-submit / synchronizer) on all state-changing BFF routes | SameSite is defense-in-depth, not sole control | CSRF-missing → 403 |
| Route protection | `[ADOPT]` server-side session check per route group | unauthenticated → redirect to login | protected route unauth → redirect |
| Role-based nav | `[ADOPT]` render from session roles; hide unauthorized nav/actions | UX layer; backend is the real gate | role-gated nav test |
| REST commands | `[FREEZE]` browser → BFF route → bearer attached server-side → `sepa-api` | one token-handling location | — |
| GraphQL reads | `[FREEZE]` browser → BFF → `sepa-api` `/graphql` | same session, read-only | — |
| SSE | `[FREEZE]` proxied through BFF (ADR-N4) | no direct browser↔backend stream, no token in URL | SSE-via-BFF test |
| Logout / expiry | `[ADOPT]` BFF clears session + Keycloak back-channel logout; refresh server-side | clean session lifecycle; rotated refresh honored | logout invalidates session |
| Security headers `[ADD, security-review]` | `[ADOPT]` `Content-Security-Policy` (no inline script/style without nonce), `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` (or `frame-ancestors 'none'`), `Referrer-Policy: same-origin` — set once, at the Next.js middleware layer, every response | OWASP Top 10:2025 lists Security Misconfiguration at #2; a console that renders raw/parsed payloads (Evidence) and file uploads is exactly the surface CSP protects | headers-present test; CSP violation report endpoint (P1) |
| CORS `[ADD, security-review]` | `[FREEZE]` no cross-origin browser access to `sepa-api` at all — the browser only ever talks to the same-origin Next.js BFF; `sepa-api` itself allows **zero** browser origins (server-to-server only, from the BFF's Node process) | the BFF architecture (ADR-N3) already makes CORS close to a non-issue — stating it explicitly prevents a future "just add a CORS allowlist" shortcut that would reopen the exact browser-to-backend path ADR-N3 closed | cross-origin fetch to `sepa-api` from a test page → blocked |

---

## 9. PostgreSQL RLS / GUC / Grants Model

```text
Keycloak token / BFF session
  → Spring Security principal (tenant_id, branch_id, sub, roles)
  → per request/connection:
       set_config('app.tenant_id', <uuid>, true)
       set_config('app.branch_id', <uuid or NULL>, true)
       set_config('app.user_id',  <sub>, true)
  → PostgreSQL policies evaluate on these GUCs
```

| Table Type | Isolation Method | Why | Test |
|---|---|---|---|
| Tenant-facing operational (`payments`, `raw_inbound_messages`, `inbound_files`, `outbound_messages`) | **RLS** on `tenant_id` (+ optional `branch_id`) GUC | row-level tenant/branch isolation, enforced by the DB | two-token isolation; branch isolation |
| Evidence / audit (`evidence_records`, `audit_log`) | **RLS** + a narrow cross-tenant read policy for `auditor` service context | evidence needs tenant scoping *and* an auditor override | auditor cross-tenant read allowed; others denied |
| Queue / dispatcher (`<schema>.outbox_events`/`inbox_events`, batch) | **No RLS** — ownership grants | queues are not tenant-scoped rows; `outbox_dispatcher_role` narrow grant, no domain write (ADR-N5) | dispatcher-no-domain-write; cross-outbox-write-denied |
| Ledger core (`journal_entries`, `journal_lines`, `liquidity_accounts`) | **No RLS** — table ownership; `ledger_role` sole writer; service views for reporting | money correctness is ownership-enforced, not row-filtered | settlement-role-has-no-ledger-grant |

`[RLS-RISK, mitigated]` `[FREEZE]` **empty-GUC session sees zero rows** on every RLS table — a request without `app.tenant_id` is "sees nothing," never "sees everything." `[FREEZE]` background workers: RLS tables via `app.role='system_<n>'` + narrow `p_system_*` policy; queue/ledger via explicit grants; no worker runs on an RLS table with a default session. `[ADD, PG18 hardening]` apply the leaky-operator/statistics RLS-bypass fixes (present in current 18.x) — the class of bug where planner statistics leak RLS-hidden values is exactly what the PG19 lab profile studies but the PG18 baseline must already have patched.

`[ADD, persona-driven]` Optional branch narrowing exists because not every persona works at the same scope: the Operator and Exception Analyst are typically branch-scoped (their queue is local); the Supervisor and Auditor are typically tenant-wide (they cover for others or must see everything to answer "prove it"). `app.branch_id` being nullable is not an unused escape hatch — it is the mechanism that makes both of those real, different jobs correct under the same RLS policy.

`[REJECT, security-review][FREEZE]` **JPA/Hibernate performs no tenant filtering of its own — the ORM is deliberately tenant-blind.** Hibernate 6.4+ ships `@TenantId` (discriminator-based, entity-level automatic `tenant_id` population/filtering) — a real, current, tempting mechanism, and explicitly **not adopted** here. Reason: `@TenantId`'s own documentation states its `WHERE` filtering does not apply to native SQL queries or certain `@OneToMany`-fetched associations — an escape hatch by design, not a bug, but exactly the kind of gap OWASP's Broken-Access-Control category warns about, and a second enforcement layer that could silently disagree with the first. **PostgreSQL RLS is the sole enforcement layer**; entities are mapped without a tenant-filtering annotation, and every query — JPQL, native, or GraphQL-backed — passes through the same GUC-scoped connection regardless of how Hibernate issued it. One enforcement layer, not two that could drift apart.

---

## 10. Roles and Permission Matrix

`[FREEZE]` Eleven realm roles — the single source of truth the frontend role→workspace matrix and the admin-command matrix both reference (identical to the frontend/Keycloak blueprints; no role drift). `[ADD]` Each role has a persona behind it (frontend blueprint §3a) — the columns below are the permission shape of a job a specific person actually does daily; read the persona first if a row looks arbitrary.

`[CHANGE, persona-driven]` **Twelve realm roles as of this pass**, not eleven — `payment_approver` is added below. This is a deliberate exception to "avoid role sprawl" (§10, `ops_senior` note): `ops_senior` layers an elevated *capability* onto the *same* principal (`operator`), while a maker-checker gate structurally requires the checker to be a **different** principal than the maker for the same payment — that separation cannot be expressed as a permission on the submitter's own role without defeating the control's purpose. One new role, not two: `payment_approver` decides, `payment_submitter` prepares — the roles were already distinct in job (frontend blueprint §3a), just missing on the permission side.

| Role | REST Commands | GraphQL Reads | UI Workspaces | Admin Rights | MVP/P1/P2 |
|---|---|---|---|---|---|
| `operator` | assign exception (P1) | broad read (tenant-scoped) | Ops Control Room, most read | none | `[MVP]` |
| `payment_viewer` | — | payment/file/lineage reads | Payments & Files (read) | none | `[MVP]` |
| `payment_submitter` | submit payment/file | payment reads | Payments & Files | none | `[MVP]` |
| `payment_approver` `[ADD, persona-driven]` | approve/reject payment or batch; VoP override (+step-up); release/reject/escalate fraud hold | payment + approval-queue reads (own tenant/branch only) | Payments & Files (approval queue) | none | `[MVP]` |
| `settlement_operator` | close cycle | settlement/ledger reads | Settlement & Liquidity | none | `[MVP]` |
| `egress_operator` | resend/cancel artifact | egress reads | Egress & Delivery | none | `[MVP]` |
| `reconciliation_operator` | assign/resolve/false-positive exception | recon reads | Reconciliation & Cases | none | `[MVP]` assign / `[P1]` resolve |
| `case_operator` | resolve recall/case, escalate, close | case reads | Reconciliation & Cases | none | `[P1]` |
| `reference_data_admin` | reference-data CRUD; approval-matrix/limit-policy config (+step-up) `[CHANGE, persona-driven]` | catalog reads | Reference Data / Admin | catalog admin | `[MVP]` thin / `[P1]` full |
| `simulation_operator` | launch/replay simulation | simulation reads | Simulation Lab | none | `[MVP]` |
| `auditor` | — | cross-tenant evidence/audit reads | Evidence/Audit (read) | none (read-only) | `[MVP]` drawer / `[P1]` workspace |
| `security_admin` | manage signature keys (P1); **onboard Organization/branch, reassign role (P1)** `[CHANGE]` | admin reads | Admin | FGAP-scoped admin | `[P1]` |

`[ADOPT]` `ops_senior` (manual ISO correlation/replay) is an **FGAP-v2 permission** layered on `operator`, not a 12th role — avoids role sprawl. `[REJECT]` one super-admin role for everything. `[FREEZE, persona-driven]` **`payment_submitter` and `payment_approver` are never held by the same user for the same tenant/branch in the seeded MVP realm** — enforced by realm data hygiene at seed time, not by a runtime Keycloak rule (a runtime cross-role exclusion policy is real FGAP-v2 territory but is `[P2]`-scale ceremony for what the backend's `checker_user_id != maker_user_id` guard (main blueprint §2.2b) already makes structurally impossible regardless of role assignment).

`[CHANGE, persona-driven]` The `security_admin` row previously listed only "manage signature keys" — the Tenant/Security Configuration Owner persona (frontend blueprint §3a) surfaced that Organization/branch onboarding, though architecturally defined above (§4, Organizations as the tenant primitive), was never assigned to any role's command list. It belongs here, not to a new role: one person owns both keys and tenant/branch structure in this MVP's scale. Both stay `[P1]` — they gate nothing in Iteration 0, where a single seeded Organization/branch is enough.

---

## 11. Admin Command Authorization

`[FREEZE]` Every command is REST, role-gated, audited same-transaction with actor (`sub`) + role + `tenant_id`; **never a GraphQL mutation**. FGAP v2 provides fine-grained `permissions` for the admin plane in P1.

| Command | Required Role | Audit | RLS Context | MVP/P1/P2 |
|---|---|---|---|---|
| Submit payment / file | `payment_submitter` or `sepa-integration` | yes | tenant (+branch) | `[MVP]` |
| Launch / replay simulation | `simulation_operator` | yes | tenant | `[MVP]` |
| Close settlement cycle | `settlement_operator` | yes | tenant | `[MVP]` (Iter 4) |
| Resend / cancel outbound artifact | `egress_operator` | yes | tenant | `[MVP]` resend / `[P1]` cancel |
| Assign / resolve / false-positive exception | `reconciliation_operator` | yes | tenant | `[MVP]` assign / `[P1]` resolve |
| Resolve recall / escalate / close case | `case_operator` (+ supervisor perm) | yes | tenant | `[P1]` |
| Manual ISO correlation / replay | `operator` + `ops_senior` (FGAP) | yes | tenant | `[P1]` |
| Reference-data CRUD | `reference_data_admin` | yes | platform/tenant catalog | `[MVP]` thin / `[P1]` full |
| Manage signature keys | `security_admin` | yes | platform | `[P1]` |
| Approve / reject payment or batch `[ADD, persona-driven]` | `payment_approver` | yes | tenant (+branch), `checker≠maker` structural guard | `[MVP]` |
| Release / reject / escalate fraud hold `[ADD, persona-driven]` | `reconciliation_operator` + `ops_senior` | yes | tenant | `[MVP]`, thin |
| Override VoP mismatch `[ADD, persona-driven]` | `payment_approver` / `ops_senior` | yes, same-TX | tenant | `[MVP]`, thin — **requires step-up** (§6a) |
| Update approval matrix / limit policy `[ADD, persona-driven]` | `reference_data_admin` | yes | platform/tenant | `[MVP]` — **requires step-up** (§6a) |

---

## 12. Security Event / Audit Model

`[FREEZE]` Two audit planes, both retained: **Keycloak** login + admin events (authentication, role changes, client changes, brute-force lockouts) and the **application** audit (`audit_log`, every command, same-transaction with the command's DB write, actor/role/tenant/`correlationId`). `[ADOPT]` correlation: the token `sid` and the platform `correlationId` are both logged so an operator action can be traced from Keycloak session → BFF request → backend command → DB row → audit entry. `[P1]` audit hash-chaining for tamper-evidence. **No command without an audit row** — asserted by test, not convention.

`[ADD, persona-driven]` **Decision commands carry more than the standard row.** Approve/reject/release/escalate/vop-override all write, in the same transaction: `decision_comment` (required on reject/escalate, optional on approve), `before`/`after` state snapshot for the decided object, and — because these are exactly the "prove it" moments the Auditor persona (frontend blueprint §3a) lives on — the audit row for a decision command is queryable by `correlation_id` **and** by `payment_id`/`batch_id`, not only by actor, so a full maker→checker→auditor chain reconstructs from either end.

---

## 13. Tests

| Test | Layer | Tool | MVP/P1/P2 |
|---|---|---|---|
| Tenant isolation (two-token) | DB/RLS | Testcontainers | `[MVP]` |
| Branch isolation | DB/RLS | Testcontainers | `[MVP]` |
| Wrong-audience token rejected | Resource Server | Spring test | `[MVP]` |
| Expired token rejected | Resource Server | Spring test | `[MVP]` |
| Missing role rejected (401/403) | method security | Spring test | `[MVP]` |
| Forbidden command rejected + audited | REST + audit | Spring + DB | `[MVP]` |
| GraphQL cross-tenant read denied | GraphQL + RLS | Spring + Testcontainers | `[MVP]` |
| GraphQL mutation impossible | schema | Spring test | `[MVP]` |
| GraphQL depth/complexity limit | GraphQL | Spring test | `[MVP]` |
| BFF no-token-in-browser | frontend | Playwright | `[MVP]` |
| CSRF on state-changing BFF route | frontend/BFF | Playwright | `[MVP]` |
| RLS empty-GUC → zero rows | DB | Testcontainers | `[MVP]` |
| Service-role background worker scope | DB | Testcontainers | `[MVP]` |
| Audit entry per command | audit | Spring + DB | `[MVP]` |
| Role-based UI (nav/actions) | frontend | Playwright | `[MVP]` |
| DPoP sender-constrained token (integration) | Resource Server | Spring test | `[P1]` |
| Passkey login | Keycloak | Playwright | `[P1]` |
| Client-secret rotation | Keycloak | integration | `[P1]` |
| Maker≠checker structurally enforced `[ADD, persona-driven]` | DB + REST | Testcontainers + Playwright | `[MVP]` |
| Approval-queue BOLA (object-ID manipulation across tenant/branch) `[ADD, persona-driven]` | REST + RLS | Playwright + Testcontainers | `[MVP]` |
| Step-up required for high-risk command; stale-session redirect-and-retry `[ADD, persona-driven]` | Resource Server + Keycloak | Playwright | `[MVP]` |
| VoP override requires role + step-up + same-TX audit `[ADD, persona-driven]` | REST + audit | Spring + DB + Playwright | `[MVP]`, thin |

`[ADD, persona-driven]` The table above proves each **role's** boundary holds; it doesn't prove a **persona's actual workflow** survives a handoff (Exception Analyst → Case Owner escalation; Administrator's catalog change reaching everyone). Those cross-persona scenarios are specified once, against the frontend blueprint's §3a/§3b personas, in the Playwright vision document's persona-driven scenario set — not duplicated here.

---

## 14. MVP / P1 / P2 Scope

`[MVP]`: realm + `sepa-web`/`sepa-api`/`sepa-integration`; one Organization/tenant/branch; **twelve** roles (`[CHANGE, persona-driven]` — was eleven, `payment_approver` added); `sepa-guc` claims → GUC → selective two-level RLS; BFF (Auth Code + PKCE, HttpOnly session, CSRF); Resource Server (issuer/audience/exp/nbf + authorities converter); method security on REST + GraphQL; GraphQL depth/complexity + introspection-off-in-prod; empty-GUC/two-token/branch/audit negative-test wall; brute-force + event logging; PG18 baseline; newest-26.6.x pin; **4EV/maker-checker (main blueprint §2.2b), step-up authentication (§6a, minimal), VoP thin check (main blueprint §2.2c), fraud/risk hold (thin), limits/velocity — all `[ADD, persona-driven]`, all MVP**.
`[P1]`: FAPI-2 + DPoP on `sepa-integration`; passkeys/WebAuthn; FGAP v2 fine-grained admin permissions (incl. any future runtime cross-role exclusion policy for maker/checker, §10); persisted GraphQL queries; client-secret rotation automation; audit hash-chaining; `sepa-ops-cli`; auditor workspace; VoP's anomaly-scoring/corridor-stats depth (thin check stays MVP).
`[P2]`: federated client authentication (secrets-free); opaque-token/introspection lab; gRPC-with-mTLS+JWT (only with routing extraction); PG19 lab/security profile (online checksums, REPACK, RLS-leak study); DPoP-SPA alternative teaching track; MCP/CIMD authorization-server experiment; adaptive/risk-based SCA engine (rejected as a general mechanism, §6a); DORA-scale resilience tooling (`[REFERENCE ONLY]`).

---

## 15. Rejected Options

| Rejected Option | Why | Safer Alternative |
|---|---|---|
| SPA token storage as MVP | browser token = XSS theft, refresh in JS | `[FREEZE]` BFF, server-side session |
| GraphQL mutations | write surface bypassing REST audit/role model | REST commands, role-gated + audited |
| Public browser access to backend tokens | no token containment | BFF holds tokens server-side |
| One admin role for everything | blast radius; no least-privilege | 11 scoped roles + FGAP permissions |
| RLS on queue/ledger tables | wrong tool; perf + false realism; queues aren't tenant rows | ownership grants; sole-writer roles |
| Service workers bypassing authorization | ambient authority, untestable | `system_<n>` GUC + narrow policy / explicit grants |
| Direct DB access from frontend | destroys every boundary at once | BFF → Resource Server → DB only |
| Keycloak as payment business-authorization engine | finality/settlement/case legality are domain rules, not token claims | Keycloak = identity+coarse authz; domain owns business rules |
| Custom auth instead of OIDC | reinvents a solved, audited problem, badly | Keycloak OIDC + Spring Resource Server |
| gRPC security without audience/mTLS/claims | unauthenticated internal RPC = lateral movement | mTLS + JWT audience+claims, only if gRPC is extracted (P2) |
| PG19 as production baseline | Beta 1 (June 2026), GA ~Sept/Oct 2026, "do not run in production" | `[FREEZE]` PG18 baseline + PG19 lab profile |
| DPoP on the browser/BFF path for MVP | token never reaches the browser under BFF — marginal MVP value | DPoP on `sepa-integration` M2M in P1 |

---

## 16. Final Architecture Decision

`[FREEZE]` **Keycloak 26.6.4 (newest 26.6.x, patched forward) as external IAM with Organizations-based two-level tenancy; Next.js BFF as the sole token model (zero browser token); Spring Security Resource Server enforcing JWT + method security on every REST command and every read-only GraphQL data-fetcher; PostgreSQL 18 baseline with selective RLS (tenant/evidence) and ownership grants (queue/ledger), PG19 as lab/security profile only; DPoP/FAPI-2/passkeys as P1 hardening; and an explicit rejection of every option that moves a token into the browser, opens a GraphQL write path, collapses roles, or treats the IAM as the payment business-authorization engine.** This is the most secure, current, and enterprise-real posture buildable for a synthetic SEPA lab — real patterns, no real-bank claim, every control backed by a negative test.

---

*End of Keycloak 26.6.4 security architecture blueprint. `[NO-CODE]` — decisions, matrices, and test inventory only; realm export, Spring config, and RLS DDL land in Iteration 0 per EPIC-SEC-KC. Grounded in verified July-2026 sources (Keycloak 26.6.4 current; FAPI-2/DPoP/passkeys supported; PG19 Beta 1, not GA). Consistent with ADR-N1…N8, the ownership/frontend/signature blueprints, and main blueprint §3.5/§4.7/§7.2. Synthetic realm — no real bank identity or compliance claim.*
