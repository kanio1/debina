# SEPA Nexus — Next 3 Screens UI Specification

**Nature.** The second practical UI spec, continuing the exact standard of `sepa-nexus-first-3-screens-ui-spec.md` for the next three workspaces: **Settlement & Liquidity**, **Egress & Delivery**, **Reconciliation & Cases**. Written for a frontend dev, UX designer, and QA/SDET. `[NO-CODE]` — layout, sections, labels, controls, data, behaviour, roles, accessibility, and Playwright test-ids only; no React code, no new modules, no design-system definition. Does not change ADR-N3 (BFF) or ADR-N4 (SSE).
**Roles** = the eleven from the Keycloak blueprint §6 (no screen-only roles). **Data** = main blueprint read models (GraphQL, read-only) + admin commands (§7.2, REST via BFF).

---

## 1. Executive UI Verdict

`[ADOPT]` These three workspaces extend the operator loop from *orient → find → diagnose* into *act → deliver → resolve*: Settlement & Liquidity is where cycles are watched and closed; Egress & Delivery is where outbound artifacts are chased to delivery; Reconciliation & Cases is where drift is triaged and R-messages decided. `[FREEZE]` Three domain invariants are enforced in the UI, not just the backend: **(1)** the **ledger is read-only in the UI** — it appears only as a drill-down summary/tab, never an editable ledger wall, and no UI command mutates it (Settlement uses backend command APIs); **(2)** **delivery ≠ finality** — egress status and settlement finality are shown as separate, differently-labelled things and never conflated; **(3)** **reconciliation detects, classifies, shows evidence, and escalates — it never auto-repairs** (`[REJECT]` autonomous repair, no "fix" button exists anywhere). `[UX-RISK, mitigated]` None of the three overviews is a chart wall — each is a status-and-queue board that routes the operator to the object needing work.

---

## 2. Selected Screens and Rationale

`[ADOPT]` The three requested workspaces are correct and each is a genuine daily job, not a backend-module mirror:
- **Settlement & Liquidity** — the settlement operator's home; cycles + attempts + liquidity + finality, with ledger as read-only drill-down.
- **Egress & Delivery** — the egress operator's home; outbound artifacts + delivery attempts + receipts + retry/cancel + manual-intervention queue.
- **Reconciliation & Cases** — kept as the combined name (not split), because the operator's job flows continuously from *exception in a queue* → *evidence* → *escalate to case* → *R-message decision*; splitting reconciliation and cases into two workspaces would fracture one investigation across two destinations `[MERGE]`. The queue landing is `/work-queue`.

## 3. Continuity with First 3 Screens

`[ADOPT]` Same shell (header + role-filtered left nav + SSE indicator + footer), same `StatusChip`/`EntityTable`/`FilterBar`/`TabbedObjectDetail`/`EvidenceDrawer`/`CommandButton` kit, same object-centered IA (object detail pages with tabs, actions where the decision is made), same four-status-separation discipline carried onto every object that has those axes. The first spec's clarifications hold: overviews may be SSE-live; **detail lists stay query-driven** where live row-churn would disrupt work.

## 4. Shared UI Rules

`[FREEZE]` (identical to the first spec, restated): no browser token storage (HttpOnly BFF session only, ADR-N3); all commands = REST through the BFF; GraphQL read-only; SSE proxied through the BFF, live-read only (ADR-N4); role-based UI (hide, backend enforces); no destructive action without a confirmation dialog; no status by color alone (text + icon, `[ACCESSIBILITY]`); every command has an audit consequence (toast referencing the action); every entity is deep-linked; every interactive control has a stable `data-testid`; loading/empty/error/unauthorized states mandatory per section; stale-data indicator when SSE disconnects or a query passes its freshness budget.

### 4a. Component Foundation & Cross-Cutting Rules `[PATCH][FREEZE]`

Applied from `sepa-nexus-react-component-foundation-blueprint.md`; frontend blueprint §11a–§11g:
- **Component foundation** = shadcn/ui (Base UI) + TanStack Table + Tailwind v4, vendored. **All entity and work-queue tables use TanStack Table** rendering real `<table>` markup — the settlement cycle/attempt tables, the egress artifacts/delivery-attempts/manual-intervention tables, and the reconciliation work-queue and mismatch tables. The work-queue adds TanStack row selection (for bulk assign, P1). `[REJECT]` no admin framework / MUI / AntD / AG Grid as base; no charting dependency.
- **URL-driven table state** — filters, sort, pagination, and search for the settlement, egress, and reconciliation tables (and the `/work-queue`) live in the URL query params (shareable deep link, refresh-surviving).
- **Reusable state components** — `LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState`. `[FREEZE]` **`EmptyState` is not `UnauthorizedState`** — a queue an operator may not see must never render as "no items."
- **No optimistic UI for commands** (global) — cycle close/netting, egress retry/cancel, exception assign/resolve/escalate all reflect only after backend accept.
- **SSE reconnect** for live counters/queues (settlement cycle board, egress delivery counters + manual-intervention queue, reconciliation work-queue count) — capped exponential backoff; stale marker on disconnect; manual `Reconnect` after repeated failures.
- **Error boundaries** — per workspace and per object-detail tab; a crashed panel shows `ErrorState`, never blanks the shell.
- **Desktop-first** posture; **axe-core accessibility gate** in Playwright (violations fail CI).
- `[PATCH][G-1]` **Forbidden command → 403 even if UI is bypassed** — cycle close/netting, egress retry/cancel/mark, exception assign/resolve/false-positive/escalate, and case commands each have a Playwright/API test that invokes the command directly with an insufficient role and asserts `403` + audit entry, cross-referencing `sepa-nexus-keycloak-26-security-architecture-blueprint.md` §13.

---

## 5. Screen 4 — Settlement & Liquidity

### 5.1 Purpose
`[OPERATOR-WORKFLOW]` Let the settlement operator watch settlement cycles and attempts, understand liquidity and net positions, confirm finality, and close/net cycles where permitted — with ledger impact available as a **read-only** drill-down, never an accounting wall.

### 5.2 Users and Tasks
**Primary users:** `settlement_operator` (acts), `operator`/`payment_viewer`/`auditor` (read). **Tasks:** scan active cycles and their state; open a cycle to see items, net positions, liquidity, finality; open an attempt to see its timeline, ledger impact, liquidity check; close a cycle / run netting where allowed; jump to related payments and reconciliation runs.

### 5.3 Layout
- **Header:** workspace title `Settlement & Liquidity`; global search; user menu; SSE live indicator; `Last update`.
- **Left navigation:** global.
- **Main content (`/settlement`):** a **cycle status board** (cards or compact table of active cycles with state) + a **pending-settlement summary** + an **attempts** section; a filter bar (state, date, participant).
- **Cycle detail (`/settlement/cycles/:cycleId`):** payment header analogue (cycle header with `Cycle status` + `Finality` chips) + tabs `Overview`, `Items`, `Net Positions`, `Liquidity`, `Finality`, `Reconciliation`, `Evidence`; command bar (`Close cycle`, `Run netting`).
- **Attempt detail (`/settlement/attempts/:attemptId`):** attempt header (attempt state + finality badge) + tabs `Attempt Timeline`, `Ledger Impact`, `Liquidity Check`, `Related Payment`.
- **Right panel/drawer:** Evidence drawer (`Open evidence drawer`) over any tab.
- **Footer/status area:** `Last update`; result counts.

### 5.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| Cycle status board | see active cycles at a glance | cards/rows: cycle id, `Cycle status` (Pending/Closing/Closed/Netted/Settled/Reconciled), participant, cut-off, count | filter, row → cycle detail | all roles (read) |
| Pending settlement summary | backlog | `Pending settlement` count + oldest age | click → filtered list | all roles |
| Attempts section | recent/active attempts | table: attempt id, payment link, state, `Finality`, created | row → attempt detail | all roles |
| Cycle: Overview tab | cycle summary | state, timings, counts, cut-off | — | all roles |
| Cycle: Items tab | payments in cycle | item table with payment deep links | — | all roles |
| Cycle: Net Positions tab | netting result | `Net position` per participant | — | all roles |
| Cycle: Liquidity tab | liquidity view | `Liquidity check` summary, reservations | — | all roles |
| Cycle: Finality tab | finality state + action | `Finality` indicator; `Close cycle` action | close cycle (gated) | action: settlement_operator |
| Cycle: Reconciliation tab | related recon | reconciliation runs touching this cycle | link → run | all roles |
| Cycle/Attempt: Evidence tab | proof | evidence bundle pointers, hashes | open drawer | all roles |
| Attempt: Ledger Impact tab | read-only money view | journal lines summary, Σ badge | — (read-only) | all roles; **no edit** |
| Attempt: Liquidity Check tab | why it settled/didn't | reserve/insufficiency result | — | all roles |

### 5.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Filter | `Cycle status` | filter cycles by state | always | — | any |
| Row link | `View cycle` | nav → `/settlement/cycles/:id` | always | — | any |
| Row link | `View attempt` | nav → `/settlement/attempts/:id` | always | — | any |
| Status chip | `Cycle status` | state (text+icon): Pending/Closing/Closed/Netted/Settled/Reconciled | always | — | any |
| Status chip | `Finality` | finality indicator (text+icon): Not final / Final | always | — | any |
| Button | `Close cycle` | BFF REST `POST /settlement/cycles/:id/close` (confirm) | cycle in Closing-eligible state | not eligible / already closed | settlement_operator |
| Button | `Run netting` | BFF REST netting command (confirm) | cycle Closed, not yet Netted | wrong state | settlement_operator |
| Button | `Open next cycle` (P1) | BFF REST open-cycle command | needed by calendar | not needed | settlement_operator |
| Link | `View ledger impact` | attempt Ledger Impact tab (read-only) | attempt has journal | — | any |
| Link | `View related payments` | cycle Items / payment deep links | items exist | — | any |
| Link | `View reconciliation` | Reconciliation tab / run link | run exists | — | any |
| Button | `Open evidence drawer` | open drawer | always | — | any |

`[REJECT]` No control anywhere writes the ledger — `View ledger impact` is read-only; `Close cycle`/`Run netting` are settlement command APIs that book through the backend's `LedgerPort`, never the UI.

### 5.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| Cycle id | `settlement_cycle_dashboard` | uuid (link) | — | `Cycle not found` |
| `Cycle status` | settlement read model | chip text+icon | — | `Status unavailable` |
| `Finality` | settlement finality read model | chip (Not final/Final) | `Not final` | `Unavailable` |
| Cut-off | settlement/reference-data | absolute time | `—` | — |
| `Net position` | settlement positions | amount + ccy per participant | `No positions` | `Unavailable` |
| `Liquidity check` | `liquidity_check_view` | pass/insufficient + amounts | `No check` | `Unavailable` |
| Attempt id | settlement attempt read model | uuid (link) | — | `Attempt not found` |
| Attempt state | settlement read model | chip | — | `Unavailable` |
| Ledger impact | ledger read model (views only) | journal-line summary + Σ badge | `No ledger impact` | `Unavailable` |
| Related payments | cycle items | payment deep links | `No items` | `Unavailable` |
| Cycle board (whole) | GraphQL `settlementCycle`/list | cards/table | `No active cycles` | `Could not load cycles` + retry |

### 5.7 Behaviour
- **Loading:** board skeleton; cycle/attempt headers load first, tabs lazy-load.
- **Empty:** `No active cycles` on the board; each tab has its own empty label; a cycle may legitimately have empty later-stage tabs (e.g. not yet netted).
- **Error:** per-section error with `Retry`; a failed Liquidity tab does not blank the cycle header.
- **Unauthorized:** `Close cycle`/`Run netting` hidden for non-`settlement_operator`; read tabs remain.
- **Stale data:** `Last update` shown; stale marker on SSE disconnect.
- **SSE behaviour:** `[ADOPT]` the **overview** board uses SSE for cycle-status changes and pending counts (a cycle flipping Closing→Closed→Netted updates live); **detail pages** use query refresh + a targeted SSE update on the open cycle's/attempt's status chip only — not live-churning the item tables under the operator.
- **Command success:** `Close cycle` → toast `Cycle close requested — <cycleId>`; the Finality/status chips update on refresh/targeted SSE.
- **Command failure:** toast with reason (e.g. `Cycle not in a closable state`); no state change.
- **Optimistic UI:** **no** — cycle state changes only after the backend command is accepted.
- **Confirmation modal:** `Close cycle` and `Run netting` both confirm (state-changing, high-criticality), showing cycle id + affected counts; `Open next cycle` confirms.

### 5.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| settlement_operator | all sections/tabs | Close cycle, Run netting, Open next cycle (P1) | — |
| operator | all (read) | — | Close/Run/Open commands |
| payment_viewer | all (read, own scope) | — | all commands |
| auditor | all (read, cross-tenant), Evidence | — | all commands |
| other operational roles | all (read, own scope) | — | all commands |

### 5.9 Accessibility
`[ACCESSIBILITY]` `Cycle status` and `Finality` are separately labelled chips with text + icon, announced distinctly (never one merged "status", never color-only). Tabs are an ARIA `tablist` with arrow-key nav + `aria-selected`. Tables are real `<table>` with `<th scope>`; net-position and item tables have sortable columns exposing `aria-sort`. `Close cycle`/`Run netting` confirmations are focus-trapped dialogs, `Esc`-dismissible pre-command. Ledger Impact table is read-only and announced as such. Evidence drawer is an `aria-modal` dialog with focus restore.

### 5.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Cycle status board | `settlement.cycle.status-board` | board renders |
| Cycle status chip | `settlement.cycle.status-chip` | cycle-state rendering |
| Finality chip | `settlement.cycle.finality-chip` | finality ≠ status separation |
| Close cycle button | `settlement.cycle.close-button` | role-gated + confirm |
| Run netting button | `settlement.cycle.run-netting-button` | role-gated + confirm |
| Net position table | `settlement.liquidity.net-position-table` | positions render |
| Liquidity check panel | `settlement.liquidity.check-panel` | liquidity rendering |
| Attempt timeline | `settlement.attempt.timeline` | attempt story |
| Ledger impact (read-only) | `settlement.attempt.ledger-impact-table` | read-only money view |
| Related payments link | `settlement.cycle.related-payments-link` | deep link |
| Evidence drawer trigger | `settlement.cycle.evidence-open-drawer-button` | drawer opens |

`[PLAYWRIGHT]` **Smoke:** open `/settlement`, board renders with a seeded cycle; open a cycle, tabs render. **Role:** `settlement_operator` sees `Close cycle`; `operator` does not. **Data:** a seeded cycle shows distinct `Cycle status` and `Finality` chips; ledger impact tab is read-only (no edit controls). **Negative:** `Close cycle` opens a confirmation and does nothing until confirmed; closing an ineligible cycle surfaces the error toast; SSE cycle-state change updates the board chip **on event arrival**, not on a timer.

### 5.11 Out of Scope
`[REJECT]` No ledger editing (read-only drill-down only). No direct journal posting from UI. No manual finality override outside `Close cycle`/`Run netting` command semantics. No liquidity-provisioning commands (P1+). No cross-cycle netting comparison (P2). No reconciliation actions (those live in Reconciliation & Cases).

---

## 6. Screen 5 — Egress & Delivery

### 6.1 Purpose
`[OPERATOR-WORKFLOW]` Let the egress operator chase outbound artifacts to delivery: see artifacts and their delivery attempts/receipts, retry or cancel where permitted, work the manual-intervention/dead-letter queue, and check signature status — with **delivery status kept strictly separate from settlement finality**.

### 6.2 Users and Tasks
**Primary users:** `egress_operator` (acts), `operator`/`auditor` (read). **Tasks:** scan outbound artifacts and delivery health; open an artifact to see attempts, receipts, signature, related payment; retry/cancel a delivery; work the manual-intervention queue; jump to the related payment and evidence.

### 6.3 Layout
- **Header:** `Egress & Delivery`; global search; user menu; SSE live indicator; `Last update`.
- **Left navigation:** global.
- **Main content (`/egress`):** an **egress overview** (delivery counters: pending/claimed/delivered/failed + `Failed deliveries` + `Manual intervention` counts) + an **outbound artifacts table** + filter bar (delivery status, channel, recipient, date).
- **Artifact detail (`/egress/artifacts/:artifactId`):** artifact header (`Delivery status` chip + `Signature status` chip + recipient/channel) + tabs `Overview`, `Delivery Attempts`, `Receipts`, `Signature`, `Related Payment`, `Evidence`; command bar (`Retry delivery`, `Cancel delivery`).
- **Manual intervention (`/egress/manual-intervention`):** the dead-lettered / manual-intervention queue table with per-row actions.
- **Right panel/drawer:** Evidence drawer.
- **Footer/status area:** `Last update`; counts.

### 6.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| Egress overview | delivery health | counters pending/claimed/delivered/failed; `Failed deliveries`; `Manual intervention` count | click → filtered / queue | all roles (read) |
| Outbound artifacts table | list artifacts | artifact id, type, `Recipient`, `Channel`, `Delivery status`, `Signature status`, created, actions | filter, row → detail | all roles |
| Artifact: Overview tab | artifact summary | type, recipient, channel, current delivery state | — | all roles |
| Artifact: Delivery Attempts tab | attempt history | attempts with time, result, backoff; retry action | retry (gated) | action: egress_operator |
| Artifact: Receipts tab | delivery receipts | `Delivery receipts` list (receipt ≠ finality note); each receipt has a `Download receipt` action `[CHANGE, Playwright-review Gap-R1]` | download | all roles |
| Artifact: Signature tab | signing status | `Signature status` (Signed/Unsigned/Verify verdict) | — | all roles |
| Artifact: Related Payment tab | link to source | payment deep link | link | all roles |
| Manual intervention queue | dead-lettered / stuck | queue rows: artifact, reason, since; actions | retry/cancel/mark (gated) | actions: egress_operator |
| Evidence tab/drawer | proof | evidence pointers, hashes | open drawer | all roles |

### 6.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Filter | `Delivery status` | filter artifacts | always | — | any |
| Filter | `Channel` | filter by channel | always | — | any |
| Filter | `Recipient` | filter by recipient | always | — | any |
| Row link | `View artifact` | nav → `/egress/artifacts/:id` | always | — | any |
| Status chip | `Delivery status` | REQUESTED→…→DELIVERED/FAILED (text+icon) | always | — | any |
| Status chip | `Signature status` | Signed / Unsigned / Verify verdict (text+icon) | always | — | any |
| Button | `Retry delivery` | BFF REST resend (lightweight confirm) | artifact failed/retryable | delivered/terminal | egress_operator |
| Button | `Cancel delivery` | BFF REST cancel (confirm dialog) | artifact cancelable | terminal/delivered | egress_operator |
| Button | `Mark for manual intervention` | BFF REST mark command (confirm) | artifact stuck/failed | already flagged/terminal | egress_operator |
| Button `[CHANGE, Gap-R1]` | `Download receipt` | download the receipt file (was: `View delivery receipt` link) | receipt exists | receipt not yet received | any (viewer+) |
| Link | `View payment` | nav → `/payments/:id` | always | — | any |
| Button | `Open evidence drawer` | open drawer | always | — | any |

`[FREEZE]` `Delivery status` and `Signature status` are shown; **neither is settlement finality** — no finality label appears on this workspace at all (delivery ≠ finality).

### 6.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| Artifact id | `egressDeliveries` | uuid (link) | — | `Artifact not found` |
| Artifact type | egress read model | text (pacs.002/camt.029/pacs.004/result file) | — | — |
| `Recipient` | egress read model | participant/name | `—` | — |
| `Channel` | egress read model | text | `—` | — |
| `Delivery status` | egress read model + SSE | chip text+icon | — | `Unavailable` |
| `Signature status` | signature verdict via evidence | chip text+icon | `Unsigned` | `Unavailable` |
| Delivery attempts | `transport_attempts` | table (time, result, backoff) | `No attempts` | `Unavailable` |
| `Delivery receipts` | `delivery_receipts` | list; each downloadable as a file (PDF/text receipt artifact) `[ADD, Gap-R1]` | `No receipts` | `Unavailable` |
| Manual intervention queue | egress dead-letter/manual read model | queue table | `No manual-intervention items` | `Unavailable` |
| Related payment | egress→payment link | payment deep link | `—` | — |
| Artifacts table (whole) | GraphQL `egressDeliveries(filter)` | table | `No outbound artifacts match your filters` | `Could not load artifacts` + retry |

### 6.7 Behaviour
- **Loading:** overview counters skeleton; table skeleton; artifact header loads first.
- **Empty:** filter-aware empty on the table; `No manual-intervention items` when queue clear.
- **Error:** per-section error + `Retry`; a failed Receipts tab does not blank the header.
- **Unauthorized:** `Retry`/`Cancel`/`Mark` hidden for non-`egress_operator`; read tabs remain.
- **Stale data:** `Last update` + stale marker on SSE disconnect.
- **SSE behaviour:** `[ADOPT]` the **overview** uses SSE for delivery counters and the manual-intervention queue count (live delivery health); the **manual-intervention queue** may live-append new dead-letters; **artifact detail** uses query refresh + a targeted SSE update on the open artifact's delivery-status chip only.
- **Command success:** `Retry delivery` → toast `Retry requested — artifact <id>`; a new attempt appears on the Delivery Attempts tab on refresh. `Cancel` → toast `Cancel requested`.
- **Command failure:** toast with reason (e.g. `Artifact already delivered`); no state change.
- **Receipt download `[ADD, Playwright-review Gap-R1]`:** `Download receipt` triggers a file download (not a navigation) — the browser's native download flow, filename pattern `receipt-<artifactId>-<receiptId>.<ext>`; success is a downloaded, non-empty file, no toast needed (the download itself is the confirmation); a receipt requested before it exists is disabled, not a broken link. The download is audited same-transaction as any other command (an operator exporting a receipt is a logged action, per the platform's audit-every-command rule).
- **Optimistic UI:** **no** — retry/cancel/mark change state only after the backend command is accepted (upstream settlement/finality is never touched by these).
- **Confirmation modal:** `Cancel delivery` = full confirm (destructive) showing artifact id + recipient; `Retry delivery` = lightweight confirm; `Mark for manual intervention` = confirm showing reason; `Download receipt` = no confirm (non-destructive read/export).

### 6.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| egress_operator | all sections/tabs, manual queue | Retry, Cancel, Mark for manual intervention | — |
| operator | all (read) | — | Retry/Cancel/Mark |
| auditor | all (read, cross-tenant), Evidence | — | all commands |
| payment_viewer | artifacts/read (own scope) | — | all commands |
| other operational roles | read (own scope) | — | all commands |

### 6.9 Accessibility
`[ACCESSIBILITY]` `Delivery status` and `Signature status` are separate labelled chips (text + icon), announced distinctly; no finality chip present here by design. Tabs are ARIA `tablist`. Delivery-attempts and manual-intervention tables are real `<table>` with `<th scope>` and `aria-sort` where sortable. `Cancel delivery` confirmation is a focus-trapped dialog. Manual-intervention queue supports keyboard row actions. Live regions (`aria-live="polite"`) announce counter changes on the overview only, not per-row churn.

### 6.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Egress overview counters | `egress.overview.delivery-counters` | counters render + SSE |
| Outbound artifacts table | `egress.artifact.table` | artifacts render |
| Delivery status chip | `egress.artifact.delivery-status-chip` | delivery status (not finality) |
| Signature status chip | `egress.artifact.signature-status-chip` | signature status |
| Retry delivery button | `egress.artifact.retry-button` | role-gated + lightweight confirm |
| Cancel delivery button | `egress.artifact.cancel-button` | role-gated + confirm dialog |
| Manual intervention list | `egress.delivery.manual-intervention-list` | queue renders |
| Mark for manual button | `egress.artifact.mark-manual-button` | role-gated command |
| Delivery attempts table | `egress.artifact.delivery-attempts-table` | attempt history |
| View payment link | `egress.artifact.view-payment-link` | deep link |
| Download receipt button `[ADD, Gap-R1]` | `egress.artifact.download-receipt-button` | `page.waitForEvent('download')` pattern |

`[PLAYWRIGHT]` **Smoke:** open `/egress`, overview + artifacts table render; open an artifact, tabs render. **Role:** `egress_operator` sees `Retry`/`Cancel`; `operator` does not. **Data:** a seeded failed artifact appears in the manual-intervention queue; `Delivery status` and `Signature status` render as distinct chips with no finality label present. **Negative:** `Cancel delivery` opens a confirmation and does nothing until confirmed; retrying a delivered artifact surfaces the error toast; SSE delivery-counter change updates the overview **on event arrival**; `Download receipt` on an artifact with no receipt yet is disabled, not a dead link. `[ADD, Gap-R1]` **Download:** `Download receipt` triggers `page.waitForEvent('download')`; assert a non-empty file with the `receipt-<artifactId>-<receiptId>.<ext>` filename pattern; the download itself produces an audit-log entry, asserted via the API layer.

### 6.11 Out of Scope
`[REJECT]` No settlement finality shown or edited here (delivery ≠ finality). No re-signing UI (signing is a backend/egress concern; `Signature status` is read-only display). No payment-status editing. No cross-artifact bulk retry (P1). No transport configuration. No optimistic state changes.

---

## 7. Screen 6 — Reconciliation & Cases

### 7.1 Purpose
`[OPERATOR-WORKFLOW]` One workspace for the full drift-to-decision flow: a unified work queue of open exceptions, reconciliation runs with their mismatches and evidence, exception assignment/resolution, and escalation into cases with R-message decisions. `[REJECT]` **Reconciliation detects, classifies, shows evidence, and escalates — it never auto-repairs; no "fix" or "correct" command exists.**

### 7.2 Users and Tasks
**Primary users:** `reconciliation_operator` (exceptions), `case_operator` (cases), `operator` (triage), `auditor` (read). **Tasks:** work the queue; open a run to see sources/mismatches/evidence; open an exception to see evidence and related payment/settlement/egress; assign/resolve/mark-false-positive; escalate to a case; decide an R-message case; create an action request (a request, not a mutation).

### 7.3 Layout
- **Header:** `Reconciliation & Cases`; global search; user menu; SSE live indicator; `Last update`.
- **Left navigation:** global.
- **Work queue (`/work-queue`):** unified exception queue table (severity, type, `As of`, age, assignee) + filter bar (severity, mismatch type, assignment, date) + queue counts.
- **Run detail (`/reconciliation/runs/:runId`):** run header (`As of` + counts + run status) + tabs `Summary`, `Sources`, `Mismatches`, `Evidence`, `Related Actions`.
- **Exception detail (`/exceptions/:exceptionId`):** exception header (`Severity` + `Mismatch type` + `As of`) + tabs `Overview`, `Evidence`, `Related Payment`, `Related Settlement`, `Related Egress`, `Case`; command bar (`Assign to me`, `Resolve exception`, `Mark as false positive`, `Escalate to case`, `Create action request`).
- **Case detail (`/cases/:caseId`):** case header (case type + status) + tabs `Timeline`, `Decision`, `Related Payment`, `Outbound Responses`, `Evidence`; command bar (`Resolve recall`, `Escalate`, `Close case` — P1).
- **Right panel/drawer:** Evidence drawer / evidence bundle.
- **Footer/status area:** `Last update`; queue counts.

### 7.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| Work queue | triage open exceptions | table: `Severity`, `Mismatch type`, `As of`, age, assignee, links | filter, assign, row → exception | all roles (read); actions gated |
| Queue counts | backlog by severity | `Open exceptions` by severity | click → filtered | all roles |
| Run: Summary tab | run result | `As of`, counts, run status | — | all roles |
| Run: Sources tab | what was compared | `reconciliation_run_sources` | — | all roles |
| Run: Mismatches tab | findings | mismatch rows with type + severity + exception links | row → exception | all roles |
| Run: Evidence tab | proof | `Evidence bundle` pointers | open drawer | all roles |
| Run: Related Actions tab | what was requested | action requests raised (requests, not mutations) | — | all roles |
| Exception: Overview | one exception | severity, type, as_of, description | assign/resolve/FP/escalate | actions: reconciliation_operator |
| Exception: assignment-conflict state `[ADD, Playwright-review Gap-R1]` | prevent silent overwrite in a shared queue | inline banner: `Already assigned to <operator>` when a second operator's `Assign to me` loses a race | — (informational; the losing operator sees the current assignee, no retry button needed — they simply see the true state) | all roles |
| Exception: Evidence | proof bundle | `Evidence bundle` pointers, hashes | open drawer | all roles |
| Exception: Related Payment/Settlement/Egress | context | deep links to the three objects | links | all roles |
| Exception: Case | escalation link | linked case or `Escalate to case` | escalate (gated) | action: reconciliation_operator |
| Case: Timeline/Decision/Outbound | R-message work | case story, decision panel, camt.029/pacs.004 responses | resolve/escalate/close (P1) | actions: case_operator |

### 7.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Filter | `Severity` | filter queue | always | — | any |
| Filter | `Mismatch type` | filter queue | always | — | any |
| Status badge | `Severity` | severity (text+icon): Info/Warning/Critical | always | — | any |
| Field | `As of` | show snapshot watermark | always | — | any |
| Button | `Assign to me` | BFF REST assign command (optimistic-locking write — see §7.7) | exception unassigned/reassignable | already mine, or lost the race to another operator (see assignment-conflict state) | reconciliation_operator |
| Button | `Resolve exception` | BFF REST resolve (confirm) | assigned/open | already resolved | reconciliation_operator |
| Button | `Mark as false positive` | BFF REST FP command (confirm) | open | resolved | reconciliation_operator |
| Button | `Escalate to case` | BFF REST escalate → creates case (confirm) | open, escalatable | already escalated | reconciliation_operator |
| Button | `Create action request` | BFF REST action-request command (a request, not a mutation) | open | — | reconciliation_operator |
| Link | `Open related payment` | nav → `/payments/:id` | payment linked | — | any |
| Link | `Related settlement` | nav → `/settlement/cycles/:id` | linked | — | any |
| Link | `Related egress artifact` | nav → `/egress/artifacts/:id` | linked | — | any |
| Button | `Open evidence bundle` | open drawer / evidence | always | — | any |
| Button | `Resolve recall` (case, P1) | BFF REST case resolve (confirm) | case open | resolved | case_operator |
| Button | `Escalate` (case, P1) | BFF REST case escalate (confirm) | case open | — | case_operator |
| Button | `Close case` (case, P1) | BFF REST close (confirm) | case resolvable | open decisions | case_operator |

`[REJECT]` No `Fix`, `Correct`, `Repair`, or `Adjust ledger/settlement/egress` control exists — every action is assign/resolve/false-positive/escalate/action-request, and `Create action request` produces a **request** routed to the owning module, never a direct mutation.

### 7.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| `Severity` | reconciliation exception read model | badge text+icon | — | `Unavailable` |
| `Mismatch type` | exception read model | text | — | — |
| `As of` | `reconciliation_runs` watermark | absolute time | `—` | — |
| Assignee | exception read model | user/`Unassigned` | `Unassigned` | — |
| Work queue (whole) | GraphQL `exception_queue` | table | `No open exceptions` | `Could not load queue` + retry |
| Run summary | `reconciliation` run read model | as_of + counts + status | `No run data` | `Unavailable` |
| Sources | `reconciliation_run_sources` | list | `No sources` | `Unavailable` |
| Mismatches | run results read model | table with exception links | `No mismatches` | `Unavailable` |
| `Evidence bundle` | evidence pointers read model | pointer list + hashes | `No evidence` | `Unavailable` |
| Related payment/settlement/egress | exception link read models | deep links | `—` | — |
| Case timeline/decision | case read models | events + decision panel | `No case activity` | `Unavailable` |
| Outbound responses | case→egress read model | camt.029/pacs.004 links | `No responses` | `Unavailable` |

### 7.7 Behaviour
- **Loading:** queue skeleton; run/exception/case headers load first, tabs lazy-load.
- **Empty:** `No open exceptions` on a clear queue; each tab has its own empty label.
- **Error:** per-section error + `Retry`; a failed Mismatches tab does not blank the run header.
- **Unauthorized:** exception commands hidden for non-`reconciliation_operator`; case commands hidden for non-`case_operator`; read tabs remain.
- **Stale data:** `Last update` + stale marker on SSE disconnect; `As of` is always shown so the operator knows the snapshot is deterministic and fixed (not "live").
- **SSE behaviour:** `[ADOPT]` the **work queue** uses SSE for new/open exception counts and new-exception appends (live triage); **run detail** is query-driven (a run's `as_of` snapshot is immutable — live updates would be misleading); **exception/case detail** use query refresh + a targeted SSE update on the open item's status/assignee chip only.
- **Command success:** `Assign to me` → toast `Assigned — <exceptionId>`; `Resolve exception` → toast `Resolved`; `Escalate to case` → toast `Case created — <caseId>` with a deep link. State updates on refresh/targeted SSE.
- **Command failure:** toast with reason (e.g. `Exception already resolved`); no state change.
- **Assignment race / stale conflict `[ADD, Playwright-review Gap-R1]`:** the work queue is shared — two `reconciliation_operator`s can legitimately open the same unassigned exception and both press `Assign to me` within the same window. This is a real production hazard, not an edge case: the backend command is an **optimistic-locking write** (compare-and-set on the current assignee, same pattern already used for Reference Data catalog version conflicts). The first request to reach the backend wins and gets the normal `Assigned — <exceptionId>` toast. The second request is **rejected, not silently overwritten** — it receives a `409`-equivalent conflict response, and the UI responds by showing the assignment-conflict banner (`Already assigned to <operator>`) instead of a generic error toast, then refreshes the exception's assignee to the true current value. The losing operator never sees their own name applied and then reverted — the UI never claims success it doesn't have (no optimistic UI, per the platform-wide rule).
- **Optimistic UI:** **no** — assignment/resolution/escalation reflect only after the backend command is accepted; `Create action request` shows the request as *requested*, never as *applied* (it is not a mutation). This is precisely why the assignment race above cannot silently overwrite: the UI never shows "assigned to me" before the backend confirms it.
- **Confirmation modal:** `Resolve exception`, `Mark as false positive`, `Escalate to case`, and all case commands confirm (state-changing); `Assign to me` is lightweight (no confirm) — the race-conflict outcome above is exactly why no confirm dialog is needed instead: the backend, not a confirm step, is the arbiter.

### 7.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| reconciliation_operator | queue, runs, exceptions | Assign, Resolve, Mark FP, Escalate, Create action request | case-decision commands |
| case_operator | queue (read), cases | Resolve recall, Escalate, Close case (P1) | exception-resolution commands |
| operator | queue, runs, exceptions (read) | Assign to me (P1) | resolve/FP/escalate/case commands |
| auditor | all (read, cross-tenant), Evidence | — | all commands |
| other roles | read (own scope) | — | all commands |

### 7.9 Accessibility
`[ACCESSIBILITY]` `Severity` is a text + icon badge (Info/Warning/Critical), never color-only. Queue and mismatch tables are real `<table>` with `<th scope>` and `aria-sort`. `As of` is readable absolute time (with relative in `title`). Tabs are ARIA `tablist`. All confirm dialogs (`Resolve`, `Mark FP`, `Escalate`, case commands) are focus-trapped and `Esc`-dismissible pre-command. `Assign to me` and row actions are keyboard-operable. Evidence bundle drawer is `aria-modal` with focus restore. Live region announces queue-count changes on the work-queue only. `[ADD, Gap-R1]` The assignment-conflict banner is announced via `aria-live="assertive"` (it corrects a just-attempted action, unlike the passive queue-count updates) so a screen-reader user learns immediately that their assignment did not take effect and who holds it instead.

### 7.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Work queue table | `reconciliation.work-queue.table` | queue renders |
| Severity badge | `reconciliation.exception.severity-badge` | severity rendering (text+icon) |
| As-of value | `reconciliation.run.as-of-value` | deterministic snapshot shown |
| Assign to me button | `reconciliation.exception.assign-to-me-button` | role-gated command |
| Resolve exception button | `reconciliation.exception.resolve-button` | role-gated + confirm |
| Mark false positive button | `reconciliation.exception.false-positive-button` | role-gated + confirm |
| Escalate to case button | `reconciliation.exception.escalate-button` | escalation + confirm |
| Evidence bundle trigger | `reconciliation.exception.evidence-bundle-button` | evidence opens |
| Related payment link | `reconciliation.exception.related-payment-link` | deep link |
| Case escalate button | `case.detail.escalate-button` | case command (P1) |
| Case resolve button | `case.detail.resolve-button` | case command (P1) |
| Assignment-conflict banner `[ADD, Gap-R1]` | `reconciliation.exception.assignment-conflict-banner` | two-context race assertion |

`[PLAYWRIGHT]` **Smoke:** open `/work-queue`, queue renders with a seeded exception; open a run, `As of` and mismatches render. **Role:** `reconciliation_operator` sees `Assign to me`/`Resolve`; `case_operator` sees case commands but not exception-resolution; `operator` sees neither. **Data:** a seeded mismatch shows `Severity` + `Mismatch type` + `As of`; escalating creates a case and the toast deep-links to it. **Negative:** no `Fix`/`Repair` control exists anywhere (assert absence); `Resolve exception` opens a confirmation and does nothing until confirmed; resolving an already-resolved exception surfaces the error toast; SSE new-exception append updates the queue count **on event arrival**. `[ADD, Gap-R1]` **Assignment race:** open the same seeded exception in two browser contexts (two `reconciliation_operator` sessions), press `Assign to me` in both near-simultaneously; assert exactly one context gets the `Assigned — <exceptionId>` toast, the other gets `reconciliation.exception.assignment-conflict-banner` showing the winner's identity, and the exception's assignee — re-queried after both commands settle — matches the winner in **both** contexts (no split-brain state).

### 7.11 Out of Scope
`[REJECT]` No autonomous or manual data repair — reconciliation never writes to payment/settlement/ledger/egress; `Create action request` is a request to the owning module, not a mutation. No ledger/settlement/egress editing from this workspace. No bulk auto-resolution (P1 bulk assign only). No case creation outside the escalation flow. No R-message rendering/sending from UI (that is egress, triggered by case decision).

---

## 8. Shared Components Needed

`[ADOPT]` All from the existing kit (on the §11a foundation: shadcn/ui + TanStack Table + Tailwind v4) — no new components: `AppShell`, `FilterBar` (URL-state), `EntityTable` (**TanStack Table**, deep-linked rows, `aria-sort`, URL state), `StatusChip` (now also `Cycle status`, `Finality`, `Delivery status`, `Signature status`, `Severity` variants — all text+icon), `TabbedObjectDetail`, `Timeline`, `EvidenceDrawer` (+ evidence-bundle mode), `CommandButton` (role-gated + confirm + audit toast), `WorkQueueTable` (a filterable/assignable/selectable **TanStack Table** variant of `EntityTable`), `DecisionPanel` (case decision, P1), and the **`LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState`** family (empty ≠ unauthorized). No design-system definition here.

## 9. Shared Data-testid Convention

`[FREEZE]` `data-testid="<workspace>.<entity>.<component>.<action-or-state>"`. Workspaces added here: `settlement`, `egress`, `reconciliation`, `case`. Every interactive control and asserted state node carries one. Bans (unchanged): no `waitForTimeout`; no CSS-class-only selectors; no reliance on visible text alone for critical operator actions; no token in browser storage; no GraphQL mutations; no command button without a role+audit mapping. `[ADD]` Each suite also runs an **axe-core accessibility scan** (violations fail CI), asserts **URL-state** round-trips (settlement/egress/reconciliation table filter/sort + `/work-queue` → shareable URL → restores on reload), and asserts **empty ≠ unauthorized** (e.g. `reconciliation.work-queue.empty-state` vs `reconciliation.work-queue.unauthorized-state`).

## 10. Impact on React/Next.js Frontend Blueprint

`[CHANGE, additive]` This spec refines the blueprint's Settlement & Liquidity, Egress & Delivery, and Reconciliation & Cases workspaces (S-08…S-15 range) with exact sections, labels, controls, data sources, behaviours, roles, accessibility, and test-ids — no new screens, no ADR change. Three canonical patterns to fold back when the blueprint is next edited: **(a)** ledger is **UI-read-only** everywhere (drill-down tab, never editable), enforced as "no ledger-writing control exists"; **(b)** delivery status and settlement finality are **never** shown together as one status — Egress carries no finality label at all; **(c)** reconciliation exposes **no repair control** — the canonical command set is assign/resolve/false-positive/escalate/action-request, and `Create action request` is a request, not a mutation. SSE policy confirmed: overviews/queues live; immutable snapshots (`as_of` runs) and detail item-tables query-driven.

## 11. Open Questions for Later Screens

- Simulation Lab spec (launch/replay, seed display) — next UI-spec document.
- Reference Data / Admin editors (inline-edit vs form; versioned catalog UX).
- Evidence/Audit workspace for `auditor` (cross-tenant), and evidence-export gating (P1).
- Operator worklist final placement (Control Room tab vs standalone) — leaning Control Room tab.
- Case workspace depth (full R-message decision UX is P1; MVP shows read + escalation link only).
- Bulk operations pattern (bulk assign/resolve) — P1, needs a shared multi-select affordance.

---

*End of next-3-screens UI spec. `[NO-CODE]` — layout, labels, controls, data, behaviour, roles, accessibility, and Playwright test-ids only. Continues the standard of the first-3-screens spec; consistent with the React/Next.js frontend blueprint, the Keycloak role model, ADR-N3 (BFF) and ADR-N4 (SSE), and the main blueprint read models + §7.2 commands. Ledger stays UI-read-only; delivery ≠ finality; reconciliation detects-and-escalates, never repairs.*
