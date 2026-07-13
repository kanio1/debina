# SEPA Nexus — Frontend Compatibility Review

**Nature.** A brutal consistency review of the whole SEPA Nexus frontend specification set — checked against the BFF/security/backend/data/test decisions — **before** `sepa-nexus-iteration-0-foundation-plan.md` is written. This is a review, not a redesign: no new screens, no rewritten specs, no code. Scope is frontend ↔ backend/security/data/test integration. `[NO-CODE]`
**Documents reviewed:** the frontend blueprint, the three nine-screen UI specs, the component-foundation blueprint, both Keycloak/security blueprints, the message-flow/data blueprint, the ownership-integration blueprint, and ADR-N1…N8.
**Verification:** current versions checked against official sources (July 2026), inline below.

---

## 1. Version & Compatibility Check

| Area | Expected Version / Line | Risk | Verdict |
|---|---|---|---|
| React | React 19 (server components, Actions) | none — current, matches Next.js App Router | `[ADOPT]` current |
| Next.js | App Router, current major (16.x line) | none — BFF/server-session model is idiomatic | `[ADOPT]` current |
| TypeScript | 6.x strict | none — matches project stack; codegen targets it | `[ADOPT]` current |
| Tailwind | v4 (CSS-variable tokens, OKLCh) | none — shadcn defaults to v4 | `[ADOPT]` current |
| shadcn/ui | Base UI default (July 2026), CLI v4, vendored | low — Base-UI-default is recent; pin the CLI version at vendor time | `[ADOPT]`, pin at vendor |
| TanStack Table | v8 stable (v9 available, lower memory) | low — v8 is safe; v9 optional later for large grids | `[ADOPT]` v8; v9 `[P2]` |
| Playwright | **1.61** (June 15 2026, current stable; 1.61.1 patch) | none — no breaking changes since 1.60; **`page.localStorage`/`sessionStorage` API and WebAuthn virtual authenticator directly serve our no-token and passkey tests** | `[ADOPT]` 1.61.x |
| Keycloak | **26.6.4** (current supported; 26.5 EOL 2026-04-08) | medium — long 26.6.x CVE list → **pin newest 26.6.x**, patch forward | `[ADOPT]`, pin latest patch |
| Spring Boot / Spring Security | Spring Boot 4.1 / Security 6.x, OAuth2 Resource Server | none — `@PreAuthorize` on REST + GraphQL fetchers is idiomatic | `[ADOPT]` current |
| GraphQL | Spring for GraphQL, read-only, method security | none — context propagation to fetchers is supported | `[ADOPT]` current |
| PostgreSQL | **PG18 baseline + PG19 lab** | **high if PG19 were baseline** — PG19 is Beta 1 (2026-06-04), GA ~Sept/Oct 2026, "do not run in production" | `[ADOPT]` PG18 baseline; `[REJECT]` PG19 baseline |

`[COMPATIBILITY-RISK]` The only real version risks are (a) **PG19 must not be baseline** — already frozen correctly as PG18-baseline in the security/data blueprints; and (b) **Keycloak must track the newest 26.6.x** — the 26.6.0→26.6.4 CVE run (redirect-URI bypass, refresh-token reuse on restart, introspection audience leak, WebAuthn bypasses) makes "latest patch" a security requirement, already stated in the Keycloak-26 blueprint. Both are already handled; this review confirms, doesn't change.

---

## 2. Frontend Architecture Consistency

| Check | Result | Verdict |
|---|---|---|
| BFF model | frontend blueprint §12, first-spec §3, foundation §11a all cite ADR-N3; Next.js confidential client, server-side code exchange | ✅ consistent |
| App Router / server-side session | HttpOnly session cookie; server components fetch through BFF | ✅ consistent |
| No browser token storage | stated in every spec's shared rules; foundation adds no-token Playwright test | ✅ consistent |
| REST commands through BFF | every command in all 3 specs routes browser → BFF route handler → bearer server-side → `sepa-api` | ✅ consistent |
| GraphQL read-only | main blueprint §6.6 + every spec; `[REJECT]` mutations everywhere | ✅ consistent |
| SSE through BFF | ADR-N4; proxied through Next.js server; no direct browser↔backend stream | ✅ consistent |
| No GraphQL mutations | asserted as a Playwright ban in all specs | ✅ consistent |
| No direct browser-to-backend tokens | BFF is the only token holder | ✅ consistent |

`[ADOPT]` Frontend architecture is internally consistent and consistent with ADR-N3/N4. No finding.

---

## 3. Security Integration Review

| Check | Result | Verdict |
|---|---|---|
| Keycloak claims → frontend | frontend reads `roles` from BFF session (server-side); `tenant_id`/`branch_id` never reach the browser (become GUCs backend-side) | ✅ consistent |
| Roles (the 11) | identical set across Keycloak §6, both security blueprints, frontend role→workspace matrix, and all 3 specs — verified no role drift | ✅ consistent |
| Tenant/branch isolation | enforced by RLS on GUCs, not by the frontend; UI never filters tenants client-side | ✅ correct boundary |
| CSRF | Keycloak-26 blueprint §8 mandates CSRF token on state-changing BFF routes | ✅ present |
| SameSite/HttpOnly cookies | HttpOnly + Secure + SameSite=Lax specified | ✅ present |
| Backend authorization | `@PreAuthorize` on every REST command + GraphQL fetcher (Keycloak-26 §6/§7) | ✅ present |
| GraphQL authorization | method security on data-fetchers + depth/complexity limits + introspection-off-in-prod | ✅ present |
| Admin command authorization | every command maps to a role in the §11 matrix; frontend command placement (§10 of specs) ⊆ that matrix | ✅ consistent |
| Audit trail | every command audited same-transaction; frontend surfaces an audit toast; Evidence/Audit screen reads it | ✅ consistent |
| Service roles / background workers | backend concern; frontend never assumes worker identity | ✅ correct boundary |

`[SECURITY-RISK — minor, SR-SEC-1]` The frontend specs say "hiding is UX, backend is the real gate," which is correct, but **no spec explicitly states that a role-gated command, if somehow invoked by a manipulated client, is still rejected by the backend `@PreAuthorize`** — it's implied, not asserted as a test. `[SHOULD-FIX]` add a "forbidden command returns 403 even if the UI is bypassed" test to the shared Playwright/API set (the Keycloak-26 blueprint §13 already has "forbidden admin command" — just cross-reference it from the frontend specs). Not a blocker.

---

## 4. Backend / Data Compatibility

| Screen data need | Backend read model / command | Exists / Planned? | Verdict |
|---|---|---|---|
| Control Room counts + alerts | reporting projections + SSE stream | planned (main §6.6, §7.1/§7.2) | ✅ planned |
| Payments list / detail / timeline | `payments`, `paymentTimeline`, `payment_status_history`, `iso.payment_iso_identifiers` | exist (main §6.6) | ✅ |
| Files list / detail | `inboundFile`/`inboundFiles` | **added in the paper pass** (main §7.2) | ✅ (was a gap, now closed) |
| ISO lineage (incl. JSON_DIRECT) | `messageLineage`, `payment_iso_identifiers` with JSON_DIRECT row | exist (ADR-N7) | ✅ |
| Routing tab | `routeExplanation` + candidates | exist (main §4.10) | ✅ |
| Settlement cycle/attempt/finality | settlement read models + finality view | exist (main §4.11) | ✅ |
| Ledger impact (read-only) | ledger views | exist (main §4.5) | ✅ read-only enforced |
| Egress artifacts / delivery / receipts | `egressDeliveries`, transport read models | exist (main §6.x) | ✅ |
| Reconciliation queue / runs / exceptions | `exception_queue`, run read models | exist (main §4.12) | ✅ |
| Case timeline / decision / outbound | case read models | exist (main §4.14) | ✅ (case is `[P1]`) |
| Simulation runs / seed / generated events | `simulationRun` + trace | exist (main §4) | ✅ |
| Reference-data catalogs | reference-data read models + validation/mapping/render profiles | exist (main §4.13/§4.13a) | ✅ |
| Evidence / audit | `messageEvidence`, `audit_log`, `payload_hashes` | exist (main §6.6, signature blueprint §9) | ✅ |
| operatorWorklist (Control Room P1) | composite projection | **added in the paper pass** (main §7.2), `[P1]` | ✅ planned P1 |

**Command ownership:** every command in the specs maps to an owning module + REST endpoint in main §7.2 (submit→ingress, close cycle→settlement, resend/cancel→egress, assign/resolve/escalate→reconciliation/case, launch/replay→simulation, CRUD→reference-data, manual correlation→iso-adapter). ✅ no orphan command.

**GraphQL needs no mutation:** every write in every spec is a REST command; GraphQL is read-only throughout. ✅

**RLS/GUC/grants support the roles and filters:** tenant/branch filters map to `app.tenant_id`/`app.branch_id` GUCs; auditor cross-tenant read has its policy; queue/ledger use grants not RLS. ✅

**SSE/feed events exist or are planned:** Control Room live tiles, settlement cycle board, egress counters, reconciliation queue, simulation run-status all map to a `reporting`-owned SSE endpoint (ADR-N4) fed by existing topics (§3.7 v2). ✅

**URL filters have backend data:** every URL-driven table filter (status, date, participant, severity, mismatch type, tenant/branch) maps to a queryable field on an existing read model. ✅

`[FRONTEND-RISK — minor, SR-DATA-1]` The Control Room's `operatorWorklist` composite is `[P1]` (needs the case module), but the Control Room screen is `[MVP]`. The first-spec already resolves this ("thin Control Room in MVP; worklist is P1"), so it's consistent — **but the specs should state once, explicitly, that MVP Control Room shows per-queue counts, not the unified worklist.** `[SHOULD-FIX]`, not a blocker.

---

## 5. UI/UX Consistency

| Check | Result | Verdict |
|---|---|---|
| 7 workspaces (+Evidence hybrid) | consistent across blueprint §7 and the 3 specs; the prompt's "9 workspaces" = 7 workspaces + Evidence drawer + auditor Evidence workspace | ✅ consistent (naming: 7 top-level + Evidence hybrid) |
| Screen consolidation (18→7) | blueprint §6; specs honor it (tabs/drawers, not new pages) | ✅ consistent |
| Labels | English throughout; status labels consistent (Business/Settlement/Egress/Reconciliation) | ✅ consistent |
| Layout patterns | header + role-nav + main + drawer + footer, identical across specs | ✅ consistent |
| Tabs / drawers | object detail = tabs; evidence = drawer; consistent | ✅ consistent |
| Error/loading/empty/unauthorized | now a component family (foundation §11e); empty ≠ unauthorized enforced in all specs | ✅ consistent (post-patch) |
| No optimistic UI | global rule in every spec's shared rules | ✅ consistent |
| Status separation | four labelled chips (Business/Settlement/Egress/Reconciliation) canonical | ✅ consistent |
| Egress delivery ≠ finality | next-spec §6 carries no finality label on Egress at all | ✅ consistent |
| Evidence progressive disclosure | final-spec §7 + foundation Sheet/Collapsible; raw collapsed + role-gated | ✅ consistent |

`[ADOPT]` UI/UX is consistent post-patch. **One naming clarification** `[SHOULD-FIX]`: the prompt (and some readers) will count "9 workspaces"; the specs deliver **7 top-level workspaces + a global Evidence drawer + a thin auditor Evidence/Audit workspace**. State this equivalence once in the frontend blueprint §7 so "9 screens / 7 workspaces / Evidence hybrid" is not read as a contradiction.

---

## 6. Component Foundation Review

| Check | Result | Verdict |
|---|---|---|
| shadcn/ui + TanStack Table + Tailwind | frozen in foundation blueprint; all 4 docs patched to reference it | ✅ consistent |
| Real-table accessibility | TanStack renders our `<table>` + `<th scope>` + `aria-sort` | ✅ correct |
| Design token contract | foundation §8 / blueprint §11c; status-axis + severity + finality/delivery/signature + state + dark + density | ✅ present |
| URL-driven table state | foundation §5 / blueprint §11d; all list/queue screens | ✅ present (post-patch) |
| Reusable state components | `LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState`; empty ≠ unauthorized | ✅ present (post-patch) |
| Error boundaries | per-workspace + per-tab | ✅ present (post-patch) |
| axe-core gate | in every spec's Playwright block + Iteration-0 CI | ✅ present (post-patch) |
| data-testid convention | `<workspace>.<entity>.<component>.<action-or-state>` everywhere; vendored code keeps them stable | ✅ consistent |

`[ADOPT]` Component foundation is consistent and the twelve self-review findings (SR-1…SR-12) are folded into the specs. No new finding.

---

## 7. Playwright Testability Review

| Check | Result | Verdict |
|---|---|---|
| Locators | role/testid-first; CSS-class-only selectors banned | ✅ |
| Labels | accessible labels asserted; ARIA snapshots available (PW 1.60+) | ✅ |
| data-testid | stable, vendored, conventioned | ✅ |
| Role tests | per-role visibility asserted in every spec | ✅ |
| Auth/session tests | per-role `storageState`; **no-token-in-browser test now uses PW 1.61 `page.localStorage`/`sessionStorage` API directly** (cleaner than `evaluate()`) | ✅ (PW 1.61 helps) |
| API + UI setup | seeded scenarios via simulation/public paths; Testcontainers backend | ✅ |
| SSE tests | assert by event arrival, never `waitForTimeout`; WebSocket/stream traffic now visible in trace (PW 1.61) | ✅ |
| Upload/download tests | file upload (Payments/Files); evidence-bundle download (P1) — PW supports large uploads + downloads | ✅ |
| Dialogs/drawers/tabs | focus-trap + `aria-modal` asserted; PW handles dialogs | ✅ |
| Accessibility tests | axe-core gate + `toMatchAriaSnapshot` | ✅ (post-patch) |
| Parallel-safe test data | simulation seeds are deterministic (ADR-N7 JSON_DIRECT; seed replay) → per-worker isolation feasible | ✅ but see finding |
| Anti-flakiness | no `waitForTimeout`; event-arrival asserts; `expect.soft.poll()` (PW 1.61) for eventual SSE state | ✅ |

`[PLAYWRIGHT-RISK — minor, SR-TEST-1]` **Parallel-safe test data isolation is assumed but not specified.** Deterministic seeds make it *possible*, but nothing states how two parallel workers avoid colliding on the same tenant's rows (e.g. per-worker tenant/branch, or per-worker seed namespace). `[MUST-FIX before heavy test authoring, not before Iteration 0]` — the Iteration-0 plan should define a per-worker data-isolation strategy (per-worker tenant or seed prefix) when it wires Playwright. Not an Iteration-0 *blocker*, but the first thing the Iteration-0 test-harness story must decide.

`[PLAYWRIGHT-RISK — minor, SR-TEST-2]` **WebAuthn/passkey tests** (Keycloak passkeys are `[P1]`) are now feasible in CI via PW 1.61's virtual authenticator — worth noting so the P1 passkey work doesn't get re-scoped as "untestable." Informational, not a gap.

---

## 8. Gap Register

`[PATCH]` Re-classified per the binding interpretation: **all 7 gaps are either `[SHOULD-FIX]` one-line clarifications, or `[MUST-FIX]` items that the Iteration-0 plan itself resolves as foundation tasks.** None is a precondition for starting Iteration 0.

| Gap ID | Severity | Correct Interpretation | Required Action | Blocks Iteration 0? |
|---|---|---|---|---|
| G-1 | `[SHOULD-FIX]` | Backend already rejects a bypassed command (`@PreAuthorize`); only the frontend-facing test cross-reference is missing | Add "forbidden command → 403 even if UI bypassed" to the shared Playwright/API test set, cross-referencing Keycloak-26 §13 | No |
| G-2 | `[SHOULD-FIX]` | Already true in practice (Control Room is thin/`[MVP]`, worklist is `[P1]`); just not stated as one sentence | One sentence in first-spec §4 / blueprint §7: MVP Control Room shows per-queue counts, not the unified worklist | No |
| G-3 | `[SHOULD-FIX]` | No actual contradiction — "9 screens" and "7 workspaces + Evidence hybrid" describe the same design from two counting conventions | State the equivalence once in blueprint §7 | No |
| G-4 | `[MUST-FIX]` | **Not a precondition** — this is a decision Iteration 0's test-harness story is the correct and only place to make | `[ITERATION-0-INPUT]` per-worker Playwright data isolation (per-worker tenant or seed-prefix) becomes an explicit Iteration-0 foundation task | No |
| G-5 | `[MUST-FIX]` | **Not a precondition** — pinning happens at the moment the foundation is vendored, which is an Iteration-0 act by definition | `[ITERATION-0-INPUT]` shadcn CLI + Base UI version pin becomes an explicit Iteration-0 foundation task | No |
| G-6 | `[MUST-FIX]` | **Not a precondition** — pinning happens at the moment the Keycloak image is provisioned, which is an Iteration-0 act by definition | `[ITERATION-0-INPUT]` Keycloak 26.6.x exact patch pin becomes an explicit Iteration-0 foundation task | No |
| G-7 | `[SHOULD-FIX]` | Tiles are already listed (first-spec §4); only the per-tile source-topic annotation is missing | Add source topic per tile when the Iteration-1 SSE story is written | No |

`[READY_FOR_ITERATION_0]` **Zero gaps block Iteration 0.** G-4, G-5, G-6 are `[MUST-FIX]` in the sense that Iteration 0 must not skip them — but they are **inputs Iteration 0 resolves**, not gates Iteration 0 waits on. G-1/G-2/G-3/G-7 are one-line documentation clarifications with no dependency on Iteration 0 at all.

---

## 9. Required Corrections

`[PATCH]` One-line clarifications only — no redesign, no new sections beyond what's listed.

| Document | Required Correction | Priority |
|---|---|---|
| `sepa-nexus-react-nextjs-frontend-blueprint.md` | §7: state "7 top-level workspaces + global Evidence drawer + thin auditor Evidence/Audit workspace = the '9 screens'" equivalence (G-3) | `[SHOULD-FIX]` |
| `sepa-nexus-first-3-screens-ui-spec.md` | §4: one sentence — MVP Control Room shows per-queue counts, not the P1 unified worklist (G-2); add source topic per tile (G-7) | `[SHOULD-FIX]` |
| `sepa-nexus-first-3-screens-ui-spec.md`, `sepa-nexus-next-3-screens-ui-spec.md`, `sepa-nexus-final-3-screens-ui-spec.md` | add "forbidden command → 403 even if UI bypassed" to the shared Playwright/API set, cross-referencing Keycloak-26 §13 (G-1) | `[SHOULD-FIX]` |
| `sepa-nexus-frontend-compatibility-review.md` (this doc) | add §11 Iteration 0 Inputs, carrying G-4/G-5/G-6 forward as explicit tasks (not preconditions) | `[SHOULD-FIX]` — done below |
| `sepa-nexus-iteration-0-foundation-plan.md` (to be written) | must carry G-4 (per-worker Playwright data isolation), G-5 (pin shadcn CLI/Base UI), G-6 (pin Keycloak 26.6.x patch) as explicit foundation tasks | `[MUST-FIX]` in that doc — **not** before it |

None of the `[SHOULD-FIX]` corrections blocks starting Iteration 0; they are one-sentence clarifications best folded in when each doc is next touched. **G-4/G-5/G-6 are inputs to the Iteration-0 plan, not pre-conditions for writing it.**

---

## 10. Final Verdict

```text
VERDICT: READY_FOR_ITERATION_0
WHY: The frontend spec set is technically, securitywise, backend-, data-, and test-consistent. BFF/no-browser-token/GraphQL-read-only/REST-via-BFF/SSE-via-BFF all agree across the blueprint, the three UI specs, the component foundation, and both Keycloak blueprints; the eleven roles are drift-free; every screen has a backing read model and every command a backend owner; versions are current (PG18 baseline not PG19; Keycloak newest 26.6.x; Playwright 1.61). All seven gaps are either one-line documentation clarifications or foundation tasks that Iteration 0 itself is the correct place to resolve — none is a precondition for starting it.
BLOCKERS: none.
CONDITION: Iteration 0 plan must carry G-4, G-5 and G-6 as explicit foundation tasks.
NEXT: sepa-nexus-iteration-0-foundation-plan.md — carrying G-4 (per-worker Playwright data isolation), G-5 (pin shadcn CLI/Base UI), and G-6 (pin Keycloak 26.6.x patch) as explicit tasks, per §11 below.
```

---

## 11. Iteration 0 Inputs from Frontend Compatibility Review `[ADD]`

`[ITERATION-0-INPUT]` This review does not gate Iteration 0 — it hands the following concrete tasks forward to `sepa-nexus-iteration-0-foundation-plan.md`.

| Input | Source Gap | Iteration 0 Task |
|---|---|---|
| Per-worker Playwright data isolation | G-4 | Define per-worker tenant (or seed-prefix) strategy so parallel Playwright workers never collide on the same tenant's rows; wire into the Testcontainers/seed harness |
| Pinned shadcn CLI / Base UI version | G-5 | Vendor shadcn/ui at a pinned CLI version and Base UI primitive set; record the pin in the frontend foundation task |
| Pinned Keycloak 26.6.x patch | G-6 | Provision the Keycloak container/image at an explicit 26.6.x patch version (newest at vendoring time); record the pin in the infra/security foundation task |
| Version pin matrix | G-1…G-7 (consolidated) | Iteration 0 stack story records exact pinned versions: Next.js/React/TS/Tailwind, shadcn CLI, TanStack Table, Playwright 1.61.x, Keycloak 26.6.x, PostgreSQL 18.x |
| Frontend component foundation verification | G-5 | Iteration 0 smoke check: vendored shadcn components render, TanStack Table renders a real `<table>`, design tokens apply |
| BFF / no-browser-token smoke test | §2/§3 of this review | Iteration 0 CI gate: assert no access/refresh token reachable via `page.localStorage`/`page.sessionStorage` (Playwright 1.61 API) after login |
| axe-core accessibility gate | foundation §11g | Iteration 0 CI gate: axe-core scan wired into the Playwright suite from the first screen onward, violations fail the build |
| Forbidden-command 403 test | G-1 | Iteration 0 test template: a role-gated command invoked without the role returns 403 and is audited, independent of UI state |

---

*End of frontend compatibility review. `[NO-CODE]` — review only; no screens, no rewrites, no code. Verified against current sources (July 2026): Playwright 1.61, Keycloak 26.6.4, PG19 Beta (PG18 baseline), shadcn Base-UI-default, TanStack Table v8/v9. Consistent with ADR-N1…N8. Verdict: READY_FOR_ITERATION_0 — zero blockers; G-4/G-5/G-6 carried forward as Iteration-0 foundation tasks, not preconditions.*
