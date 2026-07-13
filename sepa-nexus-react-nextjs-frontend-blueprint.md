# SEPA Nexus — React + Next.js Frontend Blueprint

**Scope.** The frontend design that closes decision-gate blocker B5 (frontend half) and R-15. **Method-first:** operator task analysis → object-centered information architecture → screen consolidation → *then* React/Next.js architecture. Consistent with ADR-N3 (BFF), ADR-N4 (SSE), the Keycloak/security blueprint's role model, and the main blueprint's read models + §7.2 admin commands. `[NO-CODE]` — IA, maps, tables, conventions only; no component code. Does not reopen ADR-N1…N8.
**Central decision `[FREEZE]`:** the frontend is **not** a page-per-backend-module app. The 18 candidate screens collapse into **7 workspaces** organized by the operator's *objects and jobs*, not by the org chart of the backend.

---

## 1. Executive UX + Frontend Verdict

`[UX-RISK, mitigated]` A naive "one screen per module" build produces 18 top-level pages that force an operator investigating **one payment** to visit six of them — a classic `[COGNITIVE-LOAD]` failure. The fix is **object-centered IA**: the payment (or file, or cycle, or case) is the hero; everything about it — lineage, routing, settlement, egress, reconciliation, evidence — lives as **tabs on that object's detail page**, not as separate destinations. Result: **7 workspaces**, each a job, with object detail pages carrying the cross-module story as tabs. `[FREEZE]` BFF (ADR-N3), read-only GraphQL, REST commands through the BFF, SSE live feeds (ADR-N4), deep links to every entity, Playwright-first test-ids. MVP ships 5 of the 7 workspaces; 2 are `[P1]`.

---

## 2. UX Problem Statement

`[OPERATOR-WORKFLOW]` The operator's real questions are object-shaped, not module-shaped: *"what happened to this payment?"*, *"why didn't this file settle?"*, *"what's in my queue right now?"*, *"did this cycle close clean?"*. A module-mirrored UI answers none of these in one place — it makes the operator the integration layer. The blueprint's job is to make the **object the integration layer** and the operator a decision-maker. Secondary problem: **command misplacement** — a "resolve recall" button on a generic admin page is unusable; it belongs on the case, at the moment of decision, with the evidence visible.

---

## 3. Operator Roles and Jobs

Roles are the eleven from the Keycloak blueprint §6 — no frontend-only roles.

| Role | Primary Job | Daily Tasks | Rare Tasks | Critical Actions |
|---|---|---|---|---|
| `operator` | triage the day | scan Ops Control Room, open flagged objects | manual correlation (P1) | assign exception |
| `payment_viewer` | investigate payments | search, open payment detail, read lineage | — | none |
| `payment_submitter` | inject traffic | submit payment/file | bulk file submit | submit |
| `settlement_operator` | run settlement | watch cycles, verify finality | close cycle manually | close cycle |
| `egress_operator` | ensure delivery | watch delivery queue, retry failures | cancel artifact | resend/cancel |
| `reconciliation_operator` | clear exceptions | work the exception queue, classify | bulk resolve | resolve/false-positive |
| `case_operator` | decide R-messages | resolve recalls/returns, escalate | — | resolve recall |
| `reference_data_admin` | keep catalogs current | edit codes/calendars/profiles | onboard participant | CRUD catalog |
| `simulation_operator` | drive the lab | launch scenarios, replay | build new scenario | launch/replay |
| `auditor` | verify, cross-tenant | read evidence, trace lineage | export evidence | none (read-only) |
| `security_admin` | keys & roles | rotate keys (P1), assign roles | — | manage keys |

---

## 3a. Operator Personas `[ADD]`

`[FREEZE]` The table above is a permission matrix, not a description of who uses the system. **Nine personas** `[CHANGE: 7→9, persona-driven]` give the twelve roles a human referent — each maps onto **existing or newly-added** roles/permissions (§9); this section does not invent a role on its own, it only names who a role belongs to. Kept intentionally short: goal, life outside the system, daily decisions, what frustrates them, what they're afraid of getting wrong.

**The Operator** (`operator`) — Runs the morning triage before anything else on her desk. Outside the system: fields the first "is everything okay?" Slack ping of the day. Opens Control Room, scans for red tiles, decides in under two minutes which flagged object gets her attention first. Frustration: dashboards that make her hunt for what changed since yesterday instead of showing it. Key decision: "is this worth escalating, or does it resolve itself." Fear: missing a stuck payment until a client calls about it.

**The Payment Approver** (`payment_approver`, `[ADD, persona-driven]`) — The checker half of maker-checker; never the same person as the maker on the same payment, structurally, not by convention. Outside the system: this is often a treasury/back-office role with real signing authority outside the platform too — the platform's approval is one more instance of a decision they already own. Daily decision: approve, reject with a comment, or pull one item out of a batch to decide separately. Frustration: a batch arriving with no context beyond a number — she needs to see *why* it's asking her, not just *that* it is. Fear: approving something she didn't actually look at closely enough, because the queue was long and the last twenty were routine.

**The Supervisor** (`operator` + `ops_senior` permission, `[P1]`) — Same role as the Operator, elevated by a permission, not a second identity. Outside the system: covers for absent teammates, fields the escalations Operators can't close alone. Daily decision: whether an edge case needs a manual ISO correlation or should wait for the next scenario replay. Frustration: having to explain *why* a permission gate exists to a junior operator mid-incident. Fear: approving a manual override that turns out to be wrong and unrecoverable.

**The Administrator** (`reference_data_admin`) — Owns the catalogs everyone else's numbers depend on. Outside the system: gets the email when a new participant needs onboarding or a calendar needs next year's holidays. Key decision: whether a change is safe to apply now or needs a validity-window-dated future change — now including the approval matrix and limit policies themselves (`[ADD, persona-driven]`), which makes her decisions upstream of everyone else's, not just settlement/validation behavior. Frustration: not knowing who else's screen will look different the moment she saves. Fear: a fat-fingered edit silently changing settlement, validation, **or approval-threshold** behavior for everyone.

**The Auditor** (`auditor`) — Never touches a command button; reads everything, writes nothing. Outside the system: answers "prove it" questions from someone who wasn't in the room. Key decision: which evidence trail actually answers the question being asked, not just the one that's easiest to pull. Frustration: raw data presented as a wall instead of a trail she can follow. Fear: missing the one record that would have changed the conclusion.

**The Exception Analyst** (`reconciliation_operator`) — Works the shared queue all day, every day. Outside the system: this *is* her day — the queue is her inbox. Key decision: assign to herself, resolve, or mark false-positive — fast, because the queue refills. Frustration: a teammate who already claimed the item she just opened (the assignment-race she runs into for real, not hypothetically). Fear: waving through a false positive that was actually real drift.

**The Fraud/Risk Analyst** (`reconciliation_operator` + `ops_senior`, `[ADD, persona-driven]`) — Shares the Exception Analyst's role, not a separate identity — the elevation is context, not permission, exactly like Supervisor over Operator. Outside the system: this is the person who gets called when a payment "looks wrong" before anyone can say precisely why. Daily decision: release a held payment, reject it, or escalate to someone who can see more. Frustration: a hold with a rule name and no story — *why* did this trigger, not just *that* it did. Fear: releasing something that turns out to be exactly what the rule was built to catch.

**The Case & Recall Owner** (`case_operator`) — Downstream of the Analyst: gets exceptions only once they're escalated into something with a real R-message decision attached. Outside the system: this is closer to a legal/compliance judgment call than a queue-clearing job. Key decision: resolve the recall, or escalate further — there's no "maybe" state. Frustration: a case arriving without the evidence trail already assembled. Fear: getting return-vs-reversal wrong, which the platform's own finality model treats as a hard, unforgiving line.

**The Tenant/Security Configuration Owner** (`security_admin`) — The persona `[P1, currently under-specified]` — see the required-fix in Keycloak's role matrix below. Outside the system: onboards a new Organization/branch, rotates a compromised key, reassigns a role after a team change. Key decision: whether a role reassignment is routine or needs a second approver. Frustration: "keys & roles" is the whole job description today, with nothing said about tenant/branch onboarding — see the technical-consequence note. Fear: a stale role assignment left over from someone who changed teams months ago.

---

## 3b. Key Cross-Persona Workflows `[ADD]`

`[OPERATOR-WORKFLOW]` The per-screen "users and tasks" sections describe one persona's view of one screen. What's missing is the handoff between personas — the actual shape of a workflow in a shared system. Three, minimally:

- **Exception → Case handoff:** Exception Analyst works the queue → assigns to herself → resolves, marks false-positive, or **escalates**. On escalate, ownership passes to the Case & Recall Owner, who inherits the evidence trail already assembled — the handoff succeeds or fails on whether that trail is actually complete at the moment of escalation (a real, testable condition, not a formality).
- **Reference-data change → everyone else:** Administrator edits a catalog with a validity window → the change becomes live at its effective date → every other persona's numbers (settlement, routing, validation, **and now approval thresholds**) shift without any of them taking an action. This is the one workflow with no explicit "receiver" — which is exactly why the Administrator's fear (a fat-fingered edit affecting everyone silently) is a real operational risk, not a hypothetical.
- **Any decision → Auditor, eventually:** every command any persona takes lands in the audit trail whether or not anyone expects to be asked about it. The Auditor's workflow starts *after* the fact, on a trail she didn't design and can't influence — her only tool is whether that trail is complete and traceable to a decision, not whether it's convenient to read.
- **4EV, single payment `[ADD, persona-driven]`:** Payment Operator prepares (draft → submitted) → Approval Matrix decides whether a gate applies → if yes, Payment Approver sees it in her queue, opens it, reads *why* it's there (matrix rule, VoP result if any, fraud signal if any), approves with an optional comment or rejects with a required one. The Payment Operator gets the outcome as a notification, not a queue item — the handoff is one-directional once submitted.
- **4EV, batch `[ADD, persona-driven]`:** same shape, one level up — the Approver decides the batch as one action by default, but can pull one item out to decide separately without the whole batch waiting on it. The workflow's success condition is specifically this partial-override path, not just the all-or-nothing case, which is the one a naive implementation gets wrong.
- **Fraud hold → Fraud/Risk Analyst → back to Payment Operator or Approver `[ADD, persona-driven]`:** a hold is a detour, not a dead end — release sends the payment back into the flow exactly where it left off (still subject to any pending 4EV gate), reject ends it, escalate hands it to the Supervisor. The Payment Operator/Approver only ever sees "held for review" and the eventual outcome — never the fraud rule internals, which stay the Analyst's.
- **VoP no-match → Approver override, always audited `[ADD, persona-driven]`:** this is not the Payment Operator's decision to make even if she holds `payment_approver` for other payments — a no-match is exactly the case step-up authentication (Keycloak-26 blueprint §6a) exists for, so the override is deliberately a *second, fresher* authentication event, not a rubber stamp on the same session that submitted the payment.

---

## 4. Task Frequency / Criticality Matrix

Frequency ∈ {daily, weekly, rare, training-only}; Criticality ∈ {low, medium, high, critical}; UI Priority ∈ {top-nav, workspace, tab, drawer, admin-only, defer}.

| Task | Frequency | Criticality | Fast Access? | Audit? | UI Priority |
|---|---|---|---|---|---|
| Triage the daily queue | daily | high | yes | no | top-nav (Control Room) |
| Investigate one payment | daily | high | yes | no | workspace + object detail |
| Read ISO lineage | daily | medium | yes | no | **tab** (on payment) |
| See routing decision | weekly | medium | no | no | **tab** (on payment) |
| Watch settlement cycle | daily | high | yes | no | workspace |
| Check finality/ledger impact | daily | critical | yes | no | **tab** (on attempt/cycle) |
| Liquidity/balances | weekly | medium | no | no | **tab/drawer** |
| Watch delivery queue | daily | high | yes | no | workspace |
| Retry/cancel delivery | weekly | high | yes | yes | **action on artifact** |
| Work exception queue | daily | high | yes | yes | workspace |
| Resolve recall/case | weekly | critical | yes | yes | **action on case** |
| Launch/replay simulation | daily (lab) | low | yes | yes | workspace (Lab) |
| Edit reference data | weekly | medium | no | yes | admin workspace |
| Read evidence/audit | rare | high | no | yes | **drawer** + auditor workspace |
| Manage keys/roles | rare | high | no | yes | admin-only |

The matrix drives the consolidation: everything marked **tab/drawer/action** is *not* a standalone screen.

---

## 5. Object-Centered Information Architecture

Design by object, not module. Each object has one home workspace, its cross-module story as tabs, its actions where the decision is made, and a deep link.

| Object | Primary Workspace | Related Tabs | Related Actions | Deep Link |
|---|---|---|---|---|
| payment | Payments & Files | Timeline, ISO Lineage, Routing, Settlement, Egress, Reconciliation, Evidence | (submit at list level) | `/payments/:paymentId` |
| file | Payments & Files | Items, Result File, Errors, Related Payments | submit file | `/files/:fileId` |
| ISO message | (tab of payment) | — (lineage panel) | manual correlate (P1) | via payment/evidence |
| route decision | (tab of payment) | explanation, candidates | — | via payment |
| settlement attempt | Settlement & Liquidity | Attempt Timeline, Ledger Impact, Liquidity Check | — | `/settlement/attempts/:attemptId` |
| settlement cycle | Settlement & Liquidity | Cycle Items, Net Positions, Finality, Reconciliation, Evidence | close cycle | `/settlement/cycles/:cycleId` |
| liquidity check | (tab of attempt) / drawer | — | — | via attempt |
| ledger journal entry | (tab of attempt) / Evidence | read-only lines, Σ badge | — | via attempt/evidence |
| outbound artifact | Egress & Delivery | Delivery Attempts, Receipts, Signature, Evidence | resend, cancel | `/egress/artifacts/:artifactId` |
| delivery attempt | (tab of artifact) | — | retry | via artifact |
| reconciliation run | Reconciliation & Cases | Results, Exceptions, Sources, Evidence | — | `/reconciliation/runs/:runId` |
| exception | Reconciliation & Cases (queue) | detail, source run, evidence | assign, resolve, false-positive | via run / work-queue |
| case | Reconciliation & Cases | Timeline, Evidence, Related Payment, Outbound Responses | resolve recall, escalate, close | `/cases/:caseId` |
| simulation run | Simulation Lab | Scenario, Seed, Generated Events, Trace | launch, replay | `/simulation/runs/:runId` |
| evidence record | Evidence/Audit (+ drawer everywhere) | raw vs parsed, verdict, hashes | export (P1) | `/evidence/:evidenceId` |

---

## 6. Screen Consolidation Decisions

`[MERGE]`/`[SPLIT]`/`[DEFER]`/`[REJECT]` applied to all 18 candidate screens.

| Current Screen | Decision | New Location | Reason |
|---|---|---|---|
| App shell / login / health | `[ADOPT]` keep | shell (S-00) | foundation |
| Operator worklist | `[MERGE]` | tab/landing of **Ops Control Room** (+`operator_worklist` read model, P1) | the daily triage surface; not its own island |
| Operations overview | `[MERGE]` | **Ops Control Room** | same job as worklist — one control room |
| Payment list | `[ADOPT]` | **Payments & Files** landing | primary search/entry |
| Payment timeline | `[MERGE]` → tab | Payment detail → **Timeline tab** | it's one facet of a payment, not a page |
| ISO lineage viewer | `[MERGE]` → tab | Payment detail → **ISO Lineage tab** | investigated *about a payment* |
| File processing dashboard | `[MERGE]` | **Payments & Files** → Files section + file detail | files live beside payments |
| Routing decision viewer | `[MERGE]` → tab | Payment detail → **Routing tab** | a payment's routing facet |
| Settlement attempt timeline | `[MERGE]` → tab | Attempt detail → **Attempt Timeline tab** (in Settlement & Liquidity) | facet of an attempt |
| Liquidity dashboard | `[MERGE]` → tab/drawer | Settlement & Liquidity (+ attempt Liquidity Check tab) | rarely a standalone destination |
| Ledger journal read-only view | `[MERGE]` → tab | Attempt **Ledger Impact tab** + Evidence workspace | money proof shown in context |
| Egress dashboard | `[ADOPT]` | **Egress & Delivery** landing | a real daily job (delivery) |
| Delivery attempts / retry dashboard | `[MERGE]` → tab+action | Artifact detail → **Delivery Attempts tab**; retry as artifact action | retry happens *on an artifact* |
| Exception queue | `[ADOPT]` | **Reconciliation & Cases** → queue | a real daily job (triage) |
| Reconciliation run dashboard | `[MERGE]` → object page | Reconciliation & Cases → run detail | run is an object with tabs |
| Case / R-message dashboard | `[MERGE]` → object page | Reconciliation & Cases → case detail | case is an object with actions |
| Simulation scenario dashboard | `[ADOPT]` | **Simulation Lab** | distinct lab job |
| Reference-data admin | `[ADOPT]` | **Reference Data / Admin** | distinct admin job |
| Evidence/audit viewer | `[SPLIT]` | **Evidence/Audit** workspace (auditor) **+ Evidence drawer** everywhere | needed both as a destination and inline |

Net: 18 candidates → **7 workspaces** + object detail pages with tabs + a global Evidence drawer.

---

## 7. Final Workspace Map

`[FREEZE]` Seven workspaces (within the 6–8 target). Roles reference the Keycloak blueprint.

| Workspace | Purpose | Contains | Primary Roles | MVP/P1/P2 |
|---|---|---|---|---|
| 1. Ops Control Room | daily triage; live health | worklist (P1), volumes, SLA/lag tiles (SSE), flagged objects | operator, all (read) | `[MVP]` (thin) / worklist `[P1]` |
| 2. Payments & Files | investigate & submit payments/files | payment list, payment detail (7 tabs), file list, file detail (4 tabs) | payment_viewer, payment_submitter, operator | `[MVP]` |
| 3. Settlement & Liquidity | run settlement, prove finality | cycle list/detail (tabs), attempt detail (tabs), liquidity/balances | settlement_operator, operator | `[MVP]` (cycle close Iter 4) |
| 4. Egress & Delivery | ensure outbound delivery | artifact list/detail (tabs), delivery queue, retry/cancel actions | egress_operator, operator | `[MVP]` |
| 5. Reconciliation & Cases | clear exceptions, decide R-messages | work-queue, run detail, exception detail, case detail (actions) | reconciliation_operator, case_operator | `[P1]` (exception assign `[MVP]`) |
| 6. Simulation Lab | drive deterministic traffic/failure | scenario launcher, run detail, replay | simulation_operator | `[MVP]` (Iter 3) |
| 7. Reference Data / Admin | catalogs, keys, roles | catalog editors, key registry (P1), role view | reference_data_admin, security_admin | `[MVP]` (thin) / `[P1]` (full) |
| (Evidence/Audit) | cross-tenant evidence/audit read | evidence search + drawer everywhere | auditor | `[P1]` workspace / `[MVP]` drawer |

Evidence/Audit is a workspace for `auditor` (P1) but ships as a **global drawer** in MVP so any object's evidence is one click away.

`[PATCH][G-3]` **Naming equivalence:** documents in this project sometimes count "9 screens" (the three UI specs, three workspaces each) and sometimes "7 workspaces" (this table). Both are correct and describe the same design: **7 top-level workspaces + a global Evidence drawer + a thin auditor Evidence/Audit workspace = the "9 screens."** This is not a scope discrepancy.

---

## 8. Workspace Structure: Pages, Tabs, Drawers

| Workspace | Page | Tabs | Drawers / Panels | Commands |
|---|---|---|---|---|
| Ops Control Room | `/` | Worklist (P1), Overview | Evidence drawer | (none — triage → deep link) |
| Payments & Files | `/payments`, `/payments/:paymentId` | Timeline, ISO Lineage, Routing, Settlement, Egress, Reconciliation, Evidence | Evidence drawer | submit payment (list) |
| Payments & Files | `/files`, `/files/:fileId` | Items, Result File, Errors, Related Payments | Evidence drawer | submit file (list) |
| Settlement & Liquidity | `/settlement`, `/settlement/cycles/:cycleId` | Cycle Items, Net Positions, Finality, Reconciliation, Evidence | Liquidity panel | close cycle |
| Settlement & Liquidity | `/settlement/attempts/:attemptId` | Attempt Timeline, Ledger Impact, Liquidity Check | Evidence drawer | — |
| Egress & Delivery | `/egress`, `/egress/artifacts/:artifactId` | Delivery Attempts, Receipts, Signature, Evidence | Evidence drawer | resend, cancel |
| Reconciliation & Cases | `/work-queue`, `/reconciliation/runs/:runId` | Results, Exceptions, Sources, Evidence | exception panel | assign, resolve, false-positive |
| Reconciliation & Cases | `/cases/:caseId` | Timeline, Evidence, Related Payment, Outbound Responses | Evidence drawer | resolve recall, escalate, close |
| Simulation Lab | `/simulation`, `/simulation/runs/:runId` | Scenario, Seed, Generated Events, Trace | — | launch, replay |
| Reference Data / Admin | `/admin/reference-data`, `/admin/keys` (P1) | per-catalog editors | — | CRUD, rotate key (P1) |

---

## 9. Role-to-Workspace Matrix

`[FREEZE]` Roles = Keycloak blueprint §6. Frontend hides; backend enforces. `[ADD]` Read against §3a: the workspace a role opens is the workspace its persona actually lives in daily (Operator → Control Room; Exception Analyst → Reconciliation & Cases work-queue; Case & Recall Owner → the same workspace's case detail, one layer downstream) — the table below is where that daily reality becomes a navigation rule, not a new fact on its own.

| Role | Workspace Access | Visible Commands | Hidden Commands |
|---|---|---|---|
| operator | 1,2,3,4,5(read),6(read) | assign exception (P1) | close cycle, resend, resolve recall, CRUD |
| payment_viewer | 2 (read) | — | all |
| payment_submitter | 2 | submit payment/file | all others |
| payment_approver `[ADD, persona-driven]` | 2 (approval queue) | approve/reject payment/batch, VoP override (+step-up) | all others |
| settlement_operator | 1,3 | close cycle | resend, resolve recall, CRUD |
| egress_operator | 1,4 | resend, cancel | close cycle, resolve recall, CRUD |
| reconciliation_operator | 1,5 | assign, resolve, false-positive | close cycle, resend, CRUD |
| case_operator | 1,5 | resolve recall, escalate, close case | close cycle, resend, CRUD |
| reference_data_admin | 7 | catalog CRUD | all operational commands |
| simulation_operator | 6 | launch, replay | all domain commands |
| auditor | all (read) + Evidence/Audit | — (read-only) | every command |
| security_admin | 7 (keys/roles) | manage keys (P1) | operational commands |

---

## 10. Command Placement Matrix

`[OPERATOR-WORKFLOW]` Actions where the decision is made, with the evidence visible.

| Command | Best Location | Confirmation? | Audit? | Role |
|---|---|---|---|---|
| Submit payment/file | Payments & Files list (primary action) | no (idempotent) | yes | payment_submitter |
| Approve payment/batch `[ADD, persona-driven]` | Approval queue (Payments & Files) → item detail, matrix rule + VoP/fraud context visible | yes | yes | payment_approver |
| Reject payment/batch `[ADD, persona-driven]` | Approval queue → item detail (comment required) | yes, with required comment | yes | payment_approver |
| Override VoP mismatch `[ADD, persona-driven]` | Payment detail → VoP result panel (header action) | yes + step-up re-auth | yes, same-TX | payment_approver / ops_senior |
| Release/reject/escalate fraud hold `[ADD, persona-driven]` | Fraud hold panel (queue + payment detail) | yes | yes | reconciliation_operator + ops_senior |
| Retry delivery | Artifact detail → Delivery Attempts tab | yes | yes | egress_operator |
| Cancel outbound artifact | Artifact detail (header action) | yes (destructive) | yes | egress_operator |
| Close settlement cycle | Cycle detail → Finality tab (header action) | yes | yes | settlement_operator |
| Assign exception | Exception panel (in queue + run) | no | yes | reconciliation_operator |
| Resolve exception | Exception detail (with evidence shown) | yes | yes | reconciliation_operator |
| Resolve recall/case | Case detail → Timeline tab (decision panel, evidence adjacent) | yes | yes | case_operator |
| Launch simulation | Simulation Lab → scenario launcher | no | yes | simulation_operator |
| Replay simulation | Run detail (header action) | no | yes | simulation_operator |
| Update reference data | Reference Data editor (inline row action) | yes (versioned) | yes | reference_data_admin |

---

## 11. Frontend Architecture

`[ADOPT]` Next.js (App Router) + React + TypeScript 6 + pnpm. Server components fetch through the BFF; client components handle interaction and SSE subscription. Type generation: OpenAPI → TS types for REST commands; GraphQL codegen for read models — both in CI, drift is a build break. Structure: one app, workspace segments as route groups, object detail pages as dynamic segments with tab sub-routes, a shared Evidence drawer, a single role→workspace map driving nav.

---

## 11a. Component Foundation `[PATCH][FREEZE]`

`[FREEZE]` **Frontend foundation = shadcn/ui + TanStack Table + Tailwind v4** (full rationale, options, and rejections: `sepa-nexus-react-component-foundation-blueprint.md`; not re-argued here). Concretely:
- **shadcn/ui vendored into the project** (`components/ui/`) on **Base UI / Radix-style primitives** — the code lives in our repo, so every element carries a stable `data-testid` and no library update silently breaks selectors; the primitives provide ARIA roles, keyboard interaction, and focus-trapping.
- **TanStack Table** (headless) backs **all** entity and work-queue tables — we render **real `<table>` markup** with `<th scope>` and `aria-sort`, never a `<div>` grid.
- **Tailwind v4**, token-driven styling (§11c); **lucide-react** icons (paired with text, never color-alone).
- `[REJECT]` no admin framework as base (React-admin / Refine); `[REJECT]` no MUI / Ant Design / AG Grid as base; `[REJECT]` **no charting dependency in MVP** (Control Room is a triage board, not a BI wall — charts are `[P2]`).

### 11b. Component Mapping `[ADD]`

| SEPA Component | Foundation | Notes |
|---|---|---|
| `AppShell` | shadcn `sidebar` + custom header/footer | role-filtered nav; SSE indicator + live region |
| `FilterBar` | shadcn `input`/`select`/`popover`/`calendar`/`combobox` | writes to URL state (§11d) |
| `EntityTable` | **TanStack Table** + shadcn `table` | real `<table>`, `<th scope>`, `aria-sort`, deep-linked rows, URL state |
| `WorkQueueTable` | **TanStack Table** variant of `EntityTable` | adds selection + assign column (reconciliation) |
| `StatusChip` | shadcn `badge` | text + icon variants per status axis; never color-alone |
| `TabbedObjectDetail` | shadcn `tabs` | ARIA tablist/tab/tabpanel |
| `Timeline` | custom semantic `<ol>` | ordered; absolute time in `title` |
| `EvidenceDrawer` | shadcn `sheet` (or `dialog`) + `collapsible` | `aria-modal`, focus-trap, focus-restore; progressive-disclosure sections |
| `CommandButton` | shadcn `button` + `alert-dialog` (confirm) + `sonner`/`toast` (audit) | role-gated; confirm on destructive; audit toast on success |
| `CatalogEditor` | shadcn `sheet` + `form` (react-hook-form + zod) + diff view | reference-data create/edit with diff preview |
| `DisclosureSection` | shadcn `collapsible` | raw-payload progressive disclosure (evidence) |
| `SummaryCard` | shadcn `card` + custom + live region | SSE-updated counts (Control Room) |
| `LoadingState` | shadcn `skeleton` + custom | one of the four-state family (§11e) |
| `EmptyState` | custom | "no results" — visually distinct from `UnauthorizedState` |
| `ErrorState` | custom | error + retry, inside an error boundary |
| `UnauthorizedState` | custom | "you don't have access" — never rendered as empty |

### 11c. Design Token Contract `[ADD]`

`[NO-CODE]` The token *contract* (not a full design system), as Tailwind v4 CSS variables:
- **Status-axis tokens** — each separated axis (business / settlement / egress / reconciliation) gets a named token set, each paired with a **required icon**.
- **Severity tokens** — Info / Warning / Critical (reconciliation), text + icon.
- **Finality / delivery / signature tokens** — distinct from each other and from settlement business status (delivery ≠ finality made visual).
- **State tokens** — loading / empty / error / unauthorized; **empty ≠ unauthorized** visual treatment.
- **Dark-mode tokens** — token-swapped, not restyled (read-heavy ledger/evidence screens benefit).
- **Density tokens** — compact table density for data-dense operator work, without breaking touch targets.
- `[FREEZE]` **text + icon rule; no color-only status** — every status/severity is conveyed by text and shape/icon, never color alone (`[ACCESSIBILITY]`).
- Tokens live in the frontend repo; a status-axis color change is a reviewed change.

### 11d. URL-Driven Table State `[ADD]`

`[FREEZE]` For every list and work-queue screen, **filters, sorting, pagination, search, and useful tab state are stored in the URL query params** (Next.js `useSearchParams`), so a filtered/sorted view is a **shareable deep link** and survives refresh. This extends "every entity is deep-linked" to list *state*. TanStack Table drives this natively.

### 11e. State System `[ADD]`

`[FREEZE]` A single reusable component family — `LoadingState`, `EmptyState`, `ErrorState`, `UnauthorizedState` — replaces per-section state prose. Every screen section uses these, not hand-written variants.

```text
EmptyState is not UnauthorizedState.
Unauthorized data must never look like empty results.
```

### 11f. Cross-Cutting Behaviour Rules `[ADD/FREEZE]`

- `[FREEZE]` **No optimistic UI for commands** — command state reflects only after backend accept (`Create action request` shows *requested*, never *applied*).
- `[ADD]` **SSE reconnect with capped exponential backoff**; **stale marker on SSE disconnect** (counts marked stale, never silently frozen); **manual `Reconnect`** after repeated failures.
- `[ADD]` **Error boundaries** — one per workspace and one per object-detail tab; a crashed panel shows `ErrorState` with retry, never blanks the shell.
- `[FREEZE]` **Desktop-first posture** — min-width supported; sidebar collapses at narrow width; no mobile-only screens in MVP.

### 11g. Playwright / Accessibility Gates `[ADD][PLAYWRIGHT][ACCESSIBILITY]`

- **axe-core accessibility gate in Playwright** — every screen suite runs an axe scan; violations fail CI (operationalizes each screen's accessibility rules).
- **Stable `data-testid`** in the frozen convention (vendored code makes them durable).
- **No CSS-class-only selectors**; **no `waitForTimeout`** (assert by event/state arrival).
- **URL-state tests** — a filtered/sorted table yields a shareable URL that restores on reload.
- **empty-vs-unauthorized tests** — assert the correct state renders; unauthorized ≠ empty.
- **no-token-in-browser-storage test** (ADR-N3).

---

## 12. BFF Model

`[FREEZE]` Per ADR-N3 and the Keycloak blueprint §8: `sepa-web` confidential client; Next.js server does the code exchange; HttpOnly Secure session cookie; **no token in the browser**. Every browser data call → Next.js route handler → attaches bearer server-side → `sepa-api`. Refresh server-side. This is the only token-handling location in the whole frontend.

---

## 13. Data Access Model

`[FREEZE]`:
- browser **never** stores an access token (ADR-N3);
- browser calls the Next.js server/BFF only;
- BFF calls backend **REST** (commands) and **GraphQL** (reads);
- **GraphQL is read-only** — no mutation resolver, no GraphQL write in MVP;
- REST commands are **role-gated** (Keycloak §11) and audited;
- SSE is **proxied through the BFF** (ADR-N4) — no direct browser↔backend stream;
- **no browser→Kafka**, ever.

---

## 14. REST Commands through BFF

Every command maps to the Keycloak §11 / main blueprint §7.2 inventory. The browser posts to a Next.js route handler; the handler attaches the bearer and forwards to the backend REST command; the response returns through the BFF. No command is a GraphQL mutation. Each command button in §10 has exactly one backend endpoint, one role, and an audit requirement — **no button without a role+audit mapping** (Playwright asserts this).

---

## 15. GraphQL Read Models

`[ADOPT]` The 30+ read models already in main blueprint §6.6 back the workspaces: `payment(id)`/`paymentTimeline`/`messageLineage`/`routeExplanation`/settlement+finality views/`egressDeliveries`/`exception_queue`/`case(id)`/`simulationRun`/`ledgerBalances`, plus the two added by the paper pass — `inboundFile(fileId)`/`inboundFiles` and `operatorWorklist`. Read-only, enforced by the existing ArchUnit gate. Object detail tabs are just different queries against the same object id.

---

## 16. SSE Live Feeds

`[FREEZE]` Per ADR-N4: one `reporting`-owned SSE endpoint streams read-model deltas (volumes, delivery counters, Kafka lag, SLA ticks) to the Ops Control Room and live tiles. The browser opens the stream **to the Next.js server**, which proxies it. Playwright asserts live updates by **event arrival**, never `waitForTimeout`.

---

## 17. Deep Links and Routing

`[FREEZE]` Every entity id rendered anywhere is a link.

| Route | Purpose | Entity |
|---|---|---|
| `/payments` | list/search | payment |
| `/payments/:paymentId` | detail (7 tabs) | payment |
| `/files/:fileId` | file detail (4 tabs) | file |
| `/settlement/cycles/:cycleId` | cycle detail | settlement cycle |
| `/settlement/attempts/:attemptId` | attempt detail | settlement attempt |
| `/egress/artifacts/:artifactId` | artifact detail | outbound artifact |
| `/reconciliation/runs/:runId` | run detail | reconciliation run |
| `/cases/:caseId` | case detail | case |
| `/simulation/runs/:runId` | run detail | simulation run |
| `/evidence/:evidenceId` | evidence detail | evidence record |

---

## 18. Component Model

`[ADOPT]` A small shared kit, reused across workspaces and built on the §11a foundation (shadcn/ui + TanStack Table): `AppShell`, `ObjectHeader` (id + status chips + primary actions), `TabbedObjectDetail`, `Timeline`, `StatusChip`, `SeverityBadge`, `EntityTable`/`WorkQueueTable` (TanStack Table, deep-linked rows, URL state), `EvidenceDrawer` (+ `DisclosureSection`), `CommandButton` (role-gated + confirmation + audit-aware), `CatalogEditor`, `SummaryCard`/`LiveTile` (SSE), `LineageGraph`. `[CHANGE]` The four states are now a **reusable component family** — `LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState` (§11e) — not per-screen prose, and **empty ≠ unauthorized**. Object pages compose these — no bespoke page shells. Component→foundation mapping: §11b.

---

## 19. Test IDs and Playwright Strategy

`[PLAYWRIGHT]` Convention: `data-testid="<workspace>.<entity>.<component>.<action-or-state>"`. Examples: `payments.timeline.status-chip`, `payments.detail.iso-lineage-tab`, `settlement.cycle.close-button`, `egress.artifact.retry-button`, `reconciliation.exception.severity-badge`, `case.detail.resolve-button`, `simulation.run.replay-button`. `[ADD]` Every screen suite also runs an **axe-core accessibility scan** (violations fail CI), asserts **URL-state** round-trips (filtered/sorted view → shareable URL → restores on reload), asserts **empty ≠ unauthorized** (the correct `*State` renders), and asserts **no token in browser storage** (ADR-N3).

| Workspace | Smoke | Role Test | Data Test | MVP/P1/P2 |
|---|---|---|---|---|
| Shell/Control Room | login + render | role-gated nav | live tile updates (SSE) | `[MVP]` |
| Payments & Files | open payment/file detail | submitter sees submit, viewer doesn't | ISO ids from `iso.payment_iso_identifiers`; timeline order | `[MVP]` |
| Settlement & Liquidity | open cycle/attempt | operator sees close; viewer doesn't | accepted≠final rendering; Σ badge | `[MVP]` |
| Egress & Delivery | open artifact | egress_operator sees resend | lifecycle chips; retry via command not GraphQL | `[MVP]` |
| Reconciliation & Cases | open run/case | recon/case commands gated | severity + source run; resolve via admin cmd | `[P1]` |
| Simulation Lab | launch scenario | sim_operator only | same-seed identical E2E | `[MVP]` |
| Reference Data / Admin | open editor | admin only | versioned edit | `[MVP]` thin |

Bans `[FREEZE]`: no `waitForTimeout`; no CSS-class-only selectors; no token in browser storage; no GraphQL mutations; **no action button without a role+audit mapping**.

---

## 20. UX Non-Goals

`[REJECT]`/non-goal for MVP: page-per-module; a standalone screen for every read model; dense "everything on one wall" dashboards; Three.js/3D before an asserting SSE feed (`[P2]`); search-heavy free exploration (`[P2]`); risk/VoP panels (`[P2]`); regulator-style report builders (`[P2]`); client-side token handling; GraphQL mutations; real-time collaborative editing.

---

## 21. MVP / P1 / P2 Scope

| UI Capability | Scope | Reason |
|---|---|---|
| App shell / login / health | `[MVP]` | foundation |
| Ops Control Room (thin: tiles + SSE) | `[MVP]` | daily entry; worklist is `[P1]` |
| Payments & Files + payment detail/timeline + ISO lineage tab | `[MVP]` | the Iteration 1 spine's visible product |
| Settlement & Liquidity basic (cycle/attempt, finality) | `[MVP]` | Iteration 2/4 |
| Egress & Delivery basic | `[MVP]` | Iteration 2 |
| Simulation Lab | `[MVP]` | Iteration 3 keystone |
| Role-based visibility + Playwright smoke/happy path | `[MVP]` | testability from day one |
| Operator worklist | `[P1]` | composite over four queues (needs case module) |
| Richer Reconciliation/Cases workspace | `[P1]` | after finality/ledger proven |
| Advanced egress retry ops; Evidence/Audit workspace; reference-data admin (full) | `[P1]` | wave 1 |
| Advanced analytics, Three.js, search-heavy, risk/VoP, regulatory dashboards | `[P2]` | labs on evidence, not calendar |

---

## 22. Iteration 0 / Iteration 1 Impact

- **Iteration 0:** **vendor the component foundation** (shadcn/ui on Base UI + TanStack Table + Tailwind v4; §11a) with the design-token contract (§11c) and the four-state family (§11e); app shell + BFF login (ADR-N3) + health page; the shared component kit skeleton (`AppShell`, `EntityTable`, `CommandButton`, `EvidenceDrawer` stubs); OpenAPI/GraphQL codegen wired into CI (drift = break); Playwright framework with per-role `storageState`, the `waitForTimeout` lint ban, and the **axe-core accessibility gate** (§11g); the test-id convention enforced; URL-state utilities (§11d). **No business screens** — just the shell, login, health, foundation, and the gates.
- **Iteration 1:** Payments & Files goes live for the spine — payment list + payment detail with the **Timeline** and **ISO Lineage** tabs (ISO ids from `iso.payment_iso_identifiers`, JSON_DIRECT included per ADR-N7); `operator`/`payment_viewer`/`payment_submitter` role gating; the first Playwright happy path (submit → timeline) and the idempotency-replay assertion; `correlationId` visible in the shell footer.

---

## Cross-document consistency

| Area | Signature | Keycloak/Security | React/Next.js | Consistency Rule |
|---|---|---|---|---|
| Token model | — | BFF (§8) | BFF (§12) | all three cite ADR-N3; no browser token |
| Live feeds | — | SSE proxied (§8) | SSE proxied (§16) | all cite ADR-N4; BFF-proxied only |
| Signature boundary | verify/sign ports, no domain writes (§3) | key-admin role (`security_admin`) | signature verdict shown as evidence badge, no signature UI writes | matches ownership blueprint §3.6.1 rule 11 |
| Claims → RLS | — | claims→GUC→selective RLS (§9) | frontend never sees GUCs; reads through BFF | Keycloak claims support RLS |
| Roles | key_admin caller | 11 roles (§6) | role→workspace matrix uses those 11 (§9) | frontend role matrix = Keycloak roles, no frontend-only roles |
| Commands | egress uses SigningPort | admin-command authz matrix (§11) | command placement maps to that inventory (§10/§14) | frontend commands ⊆ §7.2 inventory; each has role+audit |
| GraphQL | verdict read via evidence | reads only | read-only, no mutations (§13/§15) | no GraphQL mutations anywhere |
| Domain integrity | no domain writes from signature (§3) | RLS/grants prevent leaks | UI cannot write domain via GraphQL | no domain writes from `signature`; UI writes only via role-gated REST |
| IA discipline | — | — | operator-workflow IA, 7 workspaces (§5–§7) | frontend is workflow-driven, not backend-module-driven |
| Component foundation | — | — | shadcn/ui + TanStack Table + Tailwind v4, vendored (§11a) | own the code; real `<table>`; no admin framework/MUI/AntD/AG Grid; no MVP charting |
| State system | — | — | `LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState` family (§11e) | empty ≠ unauthorized; unauthorized never rendered as empty |

---

*End of frontend blueprint. `[NO-CODE]` — IA, maps, and conventions only; components land per EPIC-FE-0/1 in the owning iterations. Consistent with ADR-N3/N4, the Keycloak/security blueprint, the signature module blueprint, and the main blueprint read models + §7.2 commands. Operator-workflow IA — 18 candidate screens consolidated to 7 workspaces.*

```text
NEXT:
Create sepa-nexus-iteration-0-foundation-plan.md.
```
