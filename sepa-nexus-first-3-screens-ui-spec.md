# SEPA Nexus — First 3 Screens UI Specification

**Nature.** A practical, implementation-and-test-ready UI spec for the three highest-value screens, expanding `sepa-nexus-react-nextjs-frontend-blueprint.md` (§7 workspace map). Written for a frontend dev, a UX designer, and a QA/SDET to build and test from directly. `[NO-CODE]` — layout, sections, labels, controls, behaviours, roles, data, and Playwright test-ids only; no React code, no new modules, no design-system definition. Does not change ADR-N3 (BFF) or ADR-N4 (SSE).
**Roles** are the eleven from the Keycloak blueprint §6 — no screen-only roles. **Data** comes from the main blueprint read models (GraphQL, read-only) and admin commands (§7.2, REST via BFF). All labels are English (the system UI is English).

---

## 1. Executive UI Verdict

`[ADOPT]` The three screens form the operator's core loop: **orient → find → diagnose.** Control Room answers *"what needs my attention right now?"*; Payments & Files answers *"find the object I care about"*; Payment Detail answers *"what happened to this one, and what can I do about it?"*. `[FREEZE]` Three non-negotiables carried from the blueprints: **(1)** the four status axes — business, settlement, egress, reconciliation — are shown **separately and labelled**, never merged into one "status" (main blueprint's five-status separation); **(2)** no browser token, all commands through the BFF, GraphQL read-only, SSE for live reads only (ADR-N3/N4); **(3)** every interactive control has a stable `data-testid`, every destructive command confirms, every command is audited. `[UX-RISK, mitigated]` The temptation to make Control Room a wall of charts is rejected — it is a triage board, not a BI dashboard.

---

## 2. Selected Screens and Rationale

`[ADOPT]` The three defaults are correct — they are the only screens on the **Iteration 1 critical path** and the three every role touches:

1. **Operations Control Room** — the post-login landing; the daily orientation surface; the only screen that is live (SSE). Every role starts here.
2. **Payments & Files** — the entry point to the payment object (the system's hero object); the Iteration 1 spine's visible list.
3. **Payment Detail / Timeline** — the primary diagnostic screen; where the cross-module story (lineage → routing → settlement → egress → reconciliation) becomes one object with tabs, and where the status-separation discipline pays off.

Settlement, Egress, Reconciliation & Cases, and Simulation Lab are deferred to a later spec — they are workspace-shaped but not on the first vertical slice, and three screens is the right depth to specify precisely rather than four shallowly.

---

## 3. Shared UI Rules

`[FREEZE]` Apply to all three screens (and every later one):
- **No browser token storage** — the browser holds only the HttpOnly BFF session cookie (ADR-N3).
- **All commands** = REST through a BFF route handler (bearer attached server-side); **GraphQL is read-only**; **SSE** is proxied through the BFF and used **only** for live read updates (ADR-N4).
- **Role-based UI** — nav items, tabs, and action buttons render only for roles that may use them (Keycloak §6); hiding is UX, the backend is the real gate.
- **No destructive action without a confirmation dialog**; **no status conveyed by color alone** (always text + shape/icon, `[ACCESSIBILITY]`); **every command has an audit consequence** (surfaced as a toast referencing the action).
- **Every entity has a deep link** (`/payments/:id`, `/files/:id`, …); ids rendered anywhere are links.
- **Every interactive control has a stable `data-testid`** using `<workspace>.<entity>.<component>.<action-or-state>`.
- **Loading/empty/error/unauthorized** states are mandatory per section — never a blank panel.
- **Stale-data indicator** whenever SSE is disconnected or a query is older than its freshness budget.

### 3a. Component Foundation & Cross-Cutting Rules `[PATCH][FREEZE]`

Applied from `sepa-nexus-react-component-foundation-blueprint.md`; frontend blueprint §11a–§11g:
- **Component foundation** = shadcn/ui (Base UI) + TanStack Table + Tailwind v4, vendored; all tables (Payments, Files) are **TanStack Table** rendering real `<table>` markup; `[REJECT]` no admin framework / MUI / AntD / AG Grid as base; no charting dependency (Control Room is a triage board, not a BI wall).
- **URL-driven table state** — Payments & Files filters, sort, pagination, and search live in the URL query params (shareable deep link, refresh-surviving).
- **Reusable state components** — `LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState` replace per-section prose. `[FREEZE]` **`EmptyState` is not `UnauthorizedState`; unauthorized data must never look like empty results.**
- **No optimistic UI for commands** (global) — command state reflects only after backend accept.
- **SSE reconnect** (Control Room live tiles, Payment Detail status chips) — capped exponential backoff; stale marker on disconnect; manual `Reconnect` after repeated failures.
- **Error boundaries** — one per workspace and one per object-detail tab; a crashed panel shows `ErrorState`, never blanks the shell.
- **Desktop-first** posture — min-width supported; sidebar collapses at narrow width; no mobile-only screens.
- **axe-core accessibility gate** in Playwright — violations fail CI, per screen.
- `[PATCH][G-1]` **Forbidden command → 403 even if UI is bypassed** — every role-gated command has a Playwright/API test that invokes it directly (not through the hidden button) with an insufficient role and asserts a `403` + audit entry, cross-referencing `sepa-nexus-keycloak-26-security-architecture-blueprint.md` §13. Hiding a button is UX; this test proves the backend gate holds regardless.

---

## 4. Screen 1 — Operations Control Room

### 4.1 Purpose
`[OPERATOR-WORKFLOW]` Give the operator, in one glance on login, a triage answer: *is the platform healthy, and what needs attention now?* Then route them (deep link) to the object that needs work. Not analysis — orientation.

### 4.2 Users and Tasks
**Primary users:** `operator` (main), and every other role as a read-only landing. **Tasks:** scan system health; see today's payment volume and how much is stuck; see active alerts and the latest failures; jump to the right workspace. No commands are issued here in MVP (triage → navigate).

### 4.3 Layout
- **Header** (global): product mark, workspace title `Operations Control Room`, global search, user menu (role, logout), **SSE live indicator** (`Live feed connected` / `Live feed disconnected`), `Last update <relative time>`.
- **Left navigation** (global): workspace links filtered by role — `Control Room`, `Payments & Files`, `Settlement & Liquidity`, `Egress & Delivery`, `Reconciliation & Cases`, `Simulation Lab`, `Reference Data / Admin`.
- **Main content:** a **summary-card grid** (6 cards, not charts) + an **Active alerts** list + a **Latest failed events** list.
- **Right panel/drawer:** none on this screen (Evidence drawer is a detail-screen concern).
- **Footer/status area:** SSE connection status + last-update timestamp (mirrors header for long pages).

### 4.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| System status card | is the platform healthy | `System status`: Healthy / Degraded / Down; component rollup (ingress, settlement, egress, Kafka lag) | click → (P1) health detail | all roles |
| Payments today card | today's volume + stuck count | `Payments today` count; `Pending settlement` count | click → Payments & Files (filtered) | all roles |
| Pending settlement card | settlement backlog | `Pending settlement` count; oldest age | click → Settlement workspace | all roles (link gated) |
| Failed deliveries card | egress health | `Failed deliveries` count (dead-lettered + manual-intervention) | click → Egress workspace | all roles (link gated) |
| Open exceptions card | reconciliation backlog | `Open exceptions` count by severity | click → Reconciliation workspace | all roles (link gated) |
| Live feed card | freshness | `Live feed connected` state; `Last update` | reconnect (P1) | all roles |
| Active alerts | what needs attention now | list: severity, message, entity link, time | filter by severity (P1) | all roles |
| Latest failed events | recent concrete failures | list: type (route.failed, settlement.failed, egress.dead_lettered, reconciliation.run.failed), entity deep link, time | click row → entity detail | all roles |

`[PATCH][G-2]` **MVP scope note:** the cards above are **per-queue counts** (Pending settlement, Failed deliveries, Open exceptions — each its own read model), not the unified `operatorWorklist` composite — that composite is `[P1]` (needs the case module). MVP Control Room is intentionally "several thin tiles," not "one worklist."

`[PATCH][G-7]` **Source topic per tile:** `System status` ← platform health rollup; `Payments today`/`Pending settlement` ← `settlement.*` topics (§3.7 v2); `Failed deliveries` ← `egress.dead_lettered`/`egress.manual_intervention_required`; `Open exceptions` ← `reconciliation.exception.detected`; `Latest failed events` ← `route.failed`/`settlement.failed`/`egress.dead_lettered`/`reconciliation.run.failed`. All via the single `reporting`-owned SSE endpoint (ADR-N4).

### 4.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Live indicator | `Live feed connected` / `Live feed disconnected` | shows SSE state | always | — | any |
| Timestamp | `Last update <relative>` | shows freshness | always | — | any |
| Quick link (button) | `View payments` | nav → `/payments` | always | — | any (viewer+) |
| Quick link | `View exceptions` | nav → `/work-queue` | role allows | role lacks recon access | operator, reconciliation_operator |
| Quick link | `View egress` | nav → `/egress` | role allows | role lacks egress access | operator, egress_operator |
| Card (clickable) | (per card title) | deep link to filtered workspace | always | — | any (target gated) |
| Alert row link | (entity id) | deep link to entity detail | always | — | any (target gated) |
| Severity filter (P1) | `Severity` | filter alerts | list has data | empty | any |
| Reconnect (P1) | `Reconnect live feed` | re-open SSE | disconnected | connected | any |

### 4.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| `System status` | reporting rollup (GraphQL + SSE) | Healthy/Degraded/Down + text | `Status unavailable` | `Could not load system status` + retry |
| `Payments today` | `operatorWorklist`/reporting projection | integer | `0 payments today` | `Unavailable` |
| `Pending settlement` | settlement read model | integer + oldest age | `Nothing pending` | `Unavailable` |
| `Failed deliveries` | egress read model (dead_lettered + manual) | integer | `No failed deliveries` | `Unavailable` |
| `Open exceptions` | reconciliation read model | integer by severity | `No open exceptions` | `Unavailable` |
| `Last update` | SSE last-event time | relative time | `Never` | `Live feed disconnected` |
| Active alerts | reporting alert stream (SSE) | severity + message + link + time | `No active alerts` | `Alerts unavailable` |
| Latest failed events | topic-derived read model | type + entity link + time | `No recent failures` | `Unavailable` |

### 4.7 Behaviour
- **Loading:** skeleton cards; counts show a spinner placeholder, never `0` while loading.
- **Empty:** each card shows its own empty label (above); the screen is never blank.
- **Error:** per-card error with an inline `Retry`; one failed card does not blank the others.
- **Unauthorized:** a role with no workspace access still sees the read-only cards it is allowed; links it cannot use are hidden, not disabled-with-tooltip.
- **Stale data:** if SSE disconnects, header + footer flip to `Live feed disconnected`, `Last update` freezes, and a subtle stale banner appears; counts are marked stale, not hidden.
- **SSE update:** counts and alert/failure lists update **on event arrival** (no polling); a brief highlight on a changed count.
- **Command success/failure:** N/A — no commands here in MVP.
- **Optimistic UI:** no (read-only screen).
- **Confirmation modal:** none (no destructive actions).

### 4.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| operator | all cards + alerts + failures | — (navigate only) | — |
| payment_viewer | all cards | — | recon/egress/settlement quick-links if not permitted |
| settlement_operator | all cards | — | egress/recon quick-links |
| egress_operator | all cards | — | settlement/recon quick-links |
| reconciliation_operator | all cards | — | settlement/egress quick-links |
| case_operator | all cards | — | non-case quick-links |
| simulation_operator | all cards | — | operational quick-links except Simulation |
| reference_data_admin | all cards | — | operational quick-links |
| auditor | all cards (read) | — | all command-bearing links |
| security_admin | all cards | — | operational quick-links except Admin |

### 4.9 Accessibility
`[ACCESSIBILITY]` Keyboard: cards and links are tab-focusable with visible focus rings; arrow-key traversal within lists. ARIA: cards are `role="button"` with `aria-label` (e.g. `Payments today: 1240, view payments`); live regions (`aria-live="polite"`) announce SSE count changes without stealing focus. Status is text + icon, never color alone (`Healthy` green check, `Degraded` amber triangle, `Down` red octagon — each with its word). Lists have proper `role="list"`/`listitem`. Timestamps use readable relative text with an absolute time in `title`.

### 4.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Live indicator | `control-room.system.live-indicator.state` | SSE connected/disconnected state |
| Last update | `control-room.system.last-update.value` | freshness rendering |
| System status card | `control-room.system.status-card` | health rollup renders |
| Payments today card | `control-room.payments.today-card` | count renders + links |
| Pending settlement card | `control-room.settlement.pending-card` | count renders |
| Failed deliveries card | `control-room.egress.failed-card` | count renders |
| Open exceptions card | `control-room.reconciliation.open-card` | count renders |
| Active alerts list | `control-room.alerts.list` | alerts render + deep links |
| Failed events list | `control-room.failures.list` | failures render + deep links |
| View payments link | `control-room.nav.view-payments.link` | navigation |

`[PLAYWRIGHT]` **Smoke:** login → Control Room renders, live indicator present. **Role:** `egress_operator` sees egress link, `payment_viewer` does not see recon/settlement links. **Data:** counts populate from a seeded scenario; an injected `settlement.failed` appears in Latest failed events **on SSE event** (assert by event arrival, never `waitForTimeout`). **Negative:** disconnected SSE flips indicator to `disconnected` and marks counts stale.

### 4.11 Out of Scope
No charts/graphs (P2). No commands issued here. No per-payment detail. No cross-tenant aggregation for non-auditor roles. No historical trend analysis. No configuration.

---

## 5. Screen 2 — Payments & Files

### 5.1 Purpose
`[OPERATOR-WORKFLOW]` Find the payment or file the operator cares about, fast, and get to its detail — plus submit synthetic payments/files where the role allows. One workspace, two tabs (`[MERGE]` of the old separate payment-list and file-dashboard screens).

### 5.2 Users and Tasks
**Primary users:** `payment_viewer`, `payment_submitter`, `operator`. **Tasks:** search by id/EndToEndId/UETR; filter by status/date/tenant/branch; scan the table; open a detail; submit a payment or upload a file (if permitted).

### 5.3 Layout
- **Header:** workspace title `Payments & Files`; global search; user menu.
- **Left navigation:** global (as §4.3).
- **Main content:** **tab bar** `Payments` | `Files`; below it a **command bar** (submit actions, role-gated) + a **filter bar** (search, status, date range, tenant/branch); below that the **table** for the active tab.
- **Right panel/drawer:** none (detail lives on its own screen).
- **Footer/status area:** result count + pagination + `Last update`.

### 5.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| Tab bar | switch object type | `Payments`, `Files` | tab select | all roles (Files tab always visible; submit gated separately) |
| Command bar | submit traffic | `Submit payment`, `Upload file` | buttons (role-gated) | `payment_submitter` only (hidden otherwise) |
| Filter bar | narrow results | `Search…`, `Status`, `Date range`, `Tenant`, `Branch` | inputs/dropdowns | tenant/branch shown only if role spans them |
| Payments table | list payments | columns: Payment ID, EndToEndId, UETR, Amount, Business status, Settlement status, Egress status, Created, actions | sort, row click, `View details` | active on Payments tab |
| Files table | list inbound files | columns: File ID, Filename, Items, Accepted/Rejected, Status, Result file, Received, actions | sort, row click, `View details` | active on Files tab |
| Pagination | page through results | page size, next/prev | controls | when results exceed page size |
| Upload dialog: drop zone `[ADD, Playwright-review Gap-R1]` | receive a pain.001 batch file | drag-drop target + `Browse files` fallback button + accepted-format hint (`.xml`, max size) | drag-over highlight, drop, click-to-browse | opens from `Upload file`; `payment_submitter` |
| Upload dialog: progress | show upload/processing state | filename, byte progress bar, `Uploading…` → `Processing…` → `Processed` stages | cancel-in-flight (upload stage only) | visible once a file is selected/dropped |
| Upload dialog: validation result | surface accept/reject before commit | per-file: `Valid` / `Invalid — <reason>` (wrong extension, oversized, malformed XML) | `Submit` (enabled only if valid), `Remove file` | client-side check on select; server-side re-check on submit |

### 5.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Tab | `Payments` | show payments table | always | — | any |
| Tab | `Files` | show files table | always | — | any |
| Button | `Submit payment` | open submit form → BFF REST `POST /payments` | role allows | submitting in progress | payment_submitter |
| Button | `Upload file` | open upload → BFF REST `POST /files` | role allows | upload in progress | payment_submitter |
| Drop zone `[ADD]` | `Drag and drop a file here, or browse` | accept dropped/browsed file into the upload dialog | dialog open | upload in progress | payment_submitter |
| Button `[ADD]` | `Browse files` | native file picker fallback for the drop zone | dialog open | upload in progress | payment_submitter |
| Progress bar `[ADD]` | `Uploading…` / `Processing…` | shows byte-upload then server-processing progress | file selected/dropped | — | payment_submitter |
| Button `[ADD]` | `Remove file` | discard the selected file before submit | file selected | upload already submitted | payment_submitter |
| Button `[ADD]` | `Submit` (upload dialog) | commit the validated file → BFF REST `POST /files` | file passed client-side validation | invalid file / no file selected | payment_submitter |
| Search input | `Search by payment ID, EndToEndId or UETR` | filter query (GraphQL) | always | — | any |
| Dropdown | `Status` | filter by business status | always | — | any |
| Date range | `Date range` | filter by created date | always | — | any |
| Dropdown | `Tenant` | filter by tenant | role spans tenants | single-tenant role | operator/auditor (multi) |
| Dropdown | `Branch` | filter by branch | role spans branches | single-branch role | tenant-wide roles |
| Row link | `View details` | nav → `/payments/:id` or `/files/:id` | always | — | any (viewer+) |
| Status chip | (status text) | shows status (text+icon) | always | — | any |
| Refresh | `Refresh` | re-run query | not loading | loading | any |

### 5.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| Payment ID | `payments` read model | uuid (link) | — | — |
| EndToEndId | `iso.payment_iso_identifiers` | string (JSON_DIRECT included) | `—` | — |
| UETR | `iso.payment_iso_identifiers` | string | `—` | — |
| Amount | `payments` | amount + currency | — | — |
| Business status | `payment_status_history` (current) | chip: text + icon | — | — |
| Settlement status | settlement read model | chip: text + icon | `Not settled` | — |
| Egress status | egress read model | chip: text + icon | `Not sent` | — |
| Created | `payments` | absolute + relative | — | — |
| Payments table (whole) | GraphQL `payments(filter)` | table | `No payments match your filters` | `Could not load payments` + retry |
| File ID / Filename | `inboundFiles` | uuid (link) / text | — | — |
| Items / Accepted / Rejected | `inboundFile` | integers | `0` | — |
| File status | `inboundFiles` | chip | — | — |
| Result file | `result_file_dashboard` | link or `—` | `—` | — |
| Files table (whole) | GraphQL `inboundFiles(filter)` | table | `No files match your filters` | `Could not load files` + retry |

### 5.7 Behaviour
- **Loading:** table skeleton rows; filters remain interactive.
- **Empty:** filter-aware empty message (above); offers `Clear filters`.
- **Error:** table-level error with `Retry`; filters preserved.
- **Unauthorized:** `Submit payment`/`Upload file` hidden for non-submitters; tenant/branch filters hidden for single-scope roles.
- **Stale data:** a `Refresh` affordance + `Last update`; this screen is **not** SSE-live in MVP (lists are query-driven; live counts live on Control Room) — `[ADOPT]` to avoid row-churn under the operator's cursor.
- **Command success:** submit → toast `Payment submitted — <paymentId>` with a deep link; the new row appears on next refresh (no optimistic insert).
- **Command failure:** submit → error toast with the reason (e.g. `Idempotency conflict (409)`); form stays open with input preserved.
- **Upload flow `[ADD, Playwright-review Gap-R1]`:** drop or browse a file → **client-side validation** (extension, size) runs immediately, before any network call — an invalid file shows `Invalid — <reason>` inline and disables `Submit`, no upload attempted. A valid file uploads with a visible byte-progress bar (`Uploading…`), then a server-side processing stage (`Processing…`) — **server-side validation is authoritative**: a file that passes the client check but fails server-side (malformed XML, business-rule violation) surfaces as a post-upload error toast + the file's row landing in `Files` with a `Rejected` status and a reason, never a silent drop. `Remove file` is available any time before `Submit` is pressed; once submitted, the dialog closes and the file's progress is tracked via its row in the `Files` table, not the closed dialog.
- **Optimistic UI:** **no** — submissions confirm server-side before the row is trusted (idempotency + audit correctness matter more than instant feedback); this includes the upload's `Processing…` stage — the file row shows `Processing` until the backend confirms `Accepted`/`Rejected`, never an optimistic `Accepted`.
- **Confirmation modal:** submit payment = no confirm (idempotent, non-destructive); upload file = lightweight confirm showing filename + item count estimate, shown only after client-side validation passes.

### 5.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| payment_viewer | both tables (own scope) | — | Submit payment, Upload file |
| payment_submitter | both tables | Submit payment, Upload file | — |
| operator | both tables | — | Submit/Upload (unless also submitter) |
| auditor | both tables (cross-tenant read) | — | all commands |
| other operational roles | both tables (own scope) | — | Submit/Upload |

### 5.9 Accessibility
`[ACCESSIBILITY]` Tables are real `<table>` with `<th scope>` headers, sortable columns exposing `aria-sort`, and row links reachable by keyboard. Filters are labelled form controls with `aria-describedby` for format hints (e.g. UETR pattern). Status chips carry text (not color alone). Tab bar is an ARIA `tablist`/`tab`/`tabpanel`. Submit dialogs trap focus and restore it on close. Search has an accessible label matching its placeholder. `[ADD, Gap-R1]` The upload drop zone is not drag-only: `Browse files` is a real, keyboard-reachable button behind a native `<input type="file">`, so the entire upload flow is usable without drag-and-drop; the progress bar and validation message are announced via `aria-live="polite"` so a screen-reader user gets the same `Uploading…`/`Processing…`/`Invalid — <reason>` feedback a sighted user sees.

### 5.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Payments tab | `payments.workspace.tab.payments` | tab switch |
| Files tab | `payments.workspace.tab.files` | tab switch |
| Submit payment button | `payments.command.submit-payment.button` | role-gated command |
| Upload file button | `payments.command.upload-file.button` | role-gated command |
| Search input | `payments.filter.search.input` | filtering |
| Status filter | `payments.filter.status.dropdown` | filtering |
| Date range | `payments.filter.date-range.input` | filtering |
| Tenant filter | `payments.filter.tenant.dropdown` | role-scoped filter |
| Payments table | `payments.list.table` | data render |
| Payment row | `payments.list.row.<paymentId>` | row + deep link |
| Business status chip | `payments.list.business-status.chip` | status separation |
| Files table | `payments.files.table` | data render |
| View details link | `payments.list.view-details.link` | navigation |
| Upload drop zone `[ADD]` | `payments.upload.drop-zone` | drag-drop file acceptance |
| Upload browse button `[ADD]` | `payments.upload.browse-button` | `setInputFiles` fallback path |
| Upload progress bar `[ADD]` | `payments.upload.progress-bar` | byte-progress + processing-stage assertion |
| Upload validation message `[ADD]` | `payments.upload.validation-message` | invalid-file negative test |
| Upload submit button `[ADD]` | `payments.upload.submit-button` | enabled-only-when-valid assertion |

`[PLAYWRIGHT]` **Upload-specific:** drag-drop via Playwright's `dispatchEvent`/`setInputFiles` on the drop zone; a malformed/oversized fixture file asserts `payments.upload.validation-message` shows `Invalid — <reason>` and `payments.upload.submit-button` stays disabled — no network call fires; a valid fixture asserts the progress bar transitions `Uploading…` → `Processing…` → the file's row reaching `Accepted`/`Rejected` via the `Files` table (query-driven, per §5.7), never a fixed wait.

`[PLAYWRIGHT]` **Smoke:** open workspace, both tabs render. **Role:** `payment_submitter` sees `Submit payment`; `payment_viewer` does not. **Data:** filter by status narrows rows; searching a seeded EndToEndId returns the matching payment; ISO ids come from `iso.payment_iso_identifiers`. **Negative:** submit with a duplicate Idempotency-Key surfaces the `409` conflict toast; cross-tenant row is not visible to a single-tenant token; an oversized or wrong-extension file is rejected client-side with no upload attempt (Gap-R1).

### 5.11 Out of Scope
No raw ISO/XML shown here (that is Payment Detail → ISO Lineage). No inline status editing. No bulk operations (P1). No payment detail (separate screen). No settlement/egress actions. No GraphQL mutations.

---

## 6. Screen 3 — Payment Detail / Timeline

### 6.1 Purpose
`[OPERATOR-WORKFLOW]` The primary diagnostic screen: tell the whole story of one payment across every module, with the **four status axes kept distinct**, and expose the role-gated actions where the operator would decide. This is where the object-centered IA pays off — six modules' worth of data, one page, tabs.

### 6.2 Users and Tasks
**Primary users:** `payment_viewer`, `operator`, and the operational roles for their tab's actions. **Tasks:** read current statuses; walk the timeline; inspect ISO lineage; see the routing decision; check the settlement attempt and finality; check egress delivery; check reconciliation; open evidence; follow links to the related file / settlement attempt / egress artifact / exception.

### 6.3 Layout
- **Header (payment header):** `Payment details`; payment id (copyable); **four status chips in one row, each labelled** — `Business status`, `Settlement status`, `Egress status`, `Reconciliation status`; amount + currency; debtor → creditor summary; `EndToEndId`, `UETR`; primary role-gated actions (e.g. on relevant tabs).
- **Left navigation:** global.
- **Main content:** **tab bar** — `Timeline`, `ISO Lineage`, `Routing`, `Settlement`, `Egress`, `Reconciliation`, `Evidence`; the active tab's panel below.
- **Right panel/drawer:** **Evidence drawer** — opens over any tab via `Open evidence drawer`; shows raw-vs-parsed, signature verdict badge, hashes.
- **Footer/status area:** `Last update`; correlation id (`correlationId`) shown for support.

### 6.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| Payment header | at-a-glance identity + 4 statuses | id, 4 labelled status chips, amount/ccy, debtor/creditor, EndToEndId, UETR | copy id, tab-relevant actions | all roles (read) |
| Timeline tab | ordered lifecycle story | status-history events with time, source, correlationId | — | default tab; all roles |
| ISO Lineage tab | raw→parsed→validated→correlated chain | lineage entries, identifiers panel, verdict badges (incl. JSON_DIRECT) | expand entry, open evidence | all roles |
| Routing tab | why this route | route decision + explanation snapshot + candidates/reachability | — | all roles |
| Settlement tab | attempt + finality | attempt timeline, finality badge (accepted≠final), ledger-impact summary, liquidity check | link → attempt detail | all roles; actions gated |
| Egress tab | delivery story | outbound artifacts, delivery attempts, receipts, signature status | resend/cancel (gated) | actions: egress_operator |
| Reconciliation tab | drift/exception status | related exceptions, severity, source run | link → exception | actions: reconciliation_operator |
| Evidence tab / drawer | proof | raw vs parsed, verdict, hashes, audit entries | open drawer, (P1) export | all roles (auditor cross-tenant) |

### 6.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Tab | `Timeline` | show timeline | always | — | any |
| Tab | `ISO Lineage` | show lineage | always | — | any |
| Tab | `Routing` | show routing decision | always | — | any |
| Tab | `Settlement` | show settlement | always | — | any |
| Tab | `Egress` | show egress | always | — | any |
| Tab | `Reconciliation` | show reconciliation | always | — | any |
| Tab | `Evidence` | show evidence | always | — | any |
| Status chip | `Business status` | show business status (text+icon) | always | — | any |
| Status chip | `Settlement status` | show settlement status | always | — | any |
| Status chip | `Egress status` | show egress status | always | — | any |
| Status chip | `Reconciliation status` | show reconciliation status | always | — | any |
| Button | `Open evidence drawer` | open drawer | always | — | any |
| Button | `Resend` (Egress tab) | BFF REST resend artifact | artifact resendable | delivered/final | egress_operator |
| Button | `Cancel` (Egress tab) | BFF REST cancel artifact (confirm) | artifact cancelable | terminal | egress_operator |
| Link | `Settlement attempt` | nav → `/settlement/attempts/:id` | attempt exists | — | any (target gated) |
| Link | `Outbound artifact` | nav → `/egress/artifacts/:id` | artifact exists | — | any (target gated) |
| Link | `Related file` | nav → `/files/:id` | from a file | — | any |
| Link | `Reconciliation exception` | nav → `/exceptions/:id` | exception exists | — | any (target gated) |
| Copy | `Copy payment ID` | copy id | always | — | any |

### 6.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| Payment id | `payment(id)` | uuid + copy | — | `Payment not found` |
| `Business status` | `payment_status_history` (current) | chip text+icon | — | `Status unavailable` |
| `Settlement status` | settlement read model | chip | `Not settled` | `Unavailable` |
| `Egress status` | egress read model | chip | `Not sent` | `Unavailable` |
| `Reconciliation status` | reconciliation read model | chip | `Not reconciled` | `Unavailable` |
| Amount / currency | `payments` | amount + ISO ccy | — | — |
| Debtor / creditor | `payments` | name + account summary | `—` | — |
| `EndToEndId` / `UETR` | `iso.payment_iso_identifiers` | string (JSON_DIRECT included) | `—` | — |
| Timeline | `paymentTimeline` | ordered events (time, source, correlationId) | `No events yet` | `Timeline unavailable` |
| ISO Lineage | `messageLineage` + `messageEvidence` | chain + verdict badges | `No lineage` | `Lineage unavailable` |
| Routing | `routeExplanation` + candidates | decision + explanation | `Not routed` | `Routing unavailable` |
| Settlement | attempt + finality views | attempt timeline + finality badge | `No settlement attempt` | `Unavailable` |
| Egress | `egressDeliveries` + artifacts | lifecycle chips, attempts, receipts | `No outbound artifact` | `Unavailable` |
| Reconciliation | exception/run read models | exceptions + severity | `No reconciliation findings` | `Unavailable` |
| Evidence | `messageEvidence` + audit | raw/parsed, verdict, hashes | `No evidence` | `Evidence unavailable` |
| correlationId | timeline/event | string (copyable) | `—` | — |

### 6.7 Behaviour
- **Loading:** header loads first (id + statuses), tabs lazy-load their own panels with skeletons.
- **Empty:** each tab has its own empty label; the payment can exist with empty later-stage tabs (e.g. not yet settled).
- **Error:** per-tab error with `Retry`; a failed Settlement tab does not blank the header or Timeline.
- **Unauthorized:** action buttons (Resend/Cancel) hidden for non-permitted roles; a target-gated link still renders but leads to a permission-appropriate view.
- **Stale data:** `[ADOPT]` this screen may subscribe to SSE for **this payment's** status changes (a live status chip update while investigating) — SSE via BFF, and only status chips + timeline append update live; tabs already open refresh their affected panel. `Last update` shown; on disconnect, a stale marker.
- **Command success:** Resend → toast `Resend requested — artifact <id>`; the Egress tab reflects a new delivery attempt on refresh.
- **Command failure:** Resend/Cancel → error toast with reason; no state change.
- **Optimistic UI:** **no** — egress actions are real transport commands; show the requested state only after the command is accepted.
- **Confirmation modal:** `Cancel` (destructive) requires confirm showing artifact id + recipient; `Resend` is a lightweight confirm (idempotent but operator-visible).

### 6.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| payment_viewer | all tabs (own scope) | — | Resend, Cancel |
| operator | all tabs | — (unless also op role) | Resend, Cancel |
| egress_operator | all tabs | Resend, Cancel (Egress tab) | recon/settlement actions |
| reconciliation_operator | all tabs | (recon actions live in Recon workspace) | egress actions |
| settlement_operator | all tabs | (cycle actions in Settlement workspace) | egress actions |
| auditor | all tabs (cross-tenant read), Evidence | — | all commands |
| case_operator | all tabs | (case actions in Cases workspace) | egress/settlement actions |

### 6.9 Accessibility
`[ACCESSIBILITY]` The four status chips are individually labelled and announced (`Business status: Settled`) — never distinguished by color alone. Tabs are an ARIA `tablist` with arrow-key navigation and `aria-selected`; each panel is a labelled `tabpanel`. The Evidence drawer is a modal dialog with focus trap, `aria-modal`, and focus restore to the trigger. Timeline is an ordered list with time as readable text (absolute in `title`). Copy buttons announce success via a live region. Deep links are real links (keyboard-activable). Confirmation dialogs trap focus and are dismissible by `Esc` (except mid-command).

### 6.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Payment header | `payment.detail.header` | header renders id + statuses |
| Business status chip | `payment.detail.business-status.chip` | status separation |
| Settlement status chip | `payment.detail.settlement-status.chip` | status separation |
| Egress status chip | `payment.detail.egress-status.chip` | status separation |
| Reconciliation status chip | `payment.detail.reconciliation-status.chip` | status separation |
| Timeline tab | `payment.detail.tab.timeline` | tab + ordered events |
| ISO Lineage tab | `payment.detail.tab.iso-lineage` | lineage chain + JSON_DIRECT |
| Routing tab | `payment.detail.tab.routing` | routing decision |
| Settlement tab | `payment.detail.tab.settlement` | accepted≠final rendering |
| Egress tab | `payment.detail.tab.egress` | delivery lifecycle |
| Reconciliation tab | `payment.detail.tab.reconciliation` | exceptions |
| Evidence drawer trigger | `payment.detail.evidence.open-drawer.button` | drawer opens |
| Resend button | `payment.detail.egress.resend.button` | role-gated command |
| Cancel button | `payment.detail.egress.cancel.button` | destructive + confirm |
| Settlement attempt link | `payment.detail.link.settlement-attempt` | deep link |

`[PLAYWRIGHT]` **Smoke:** open a seeded payment, header + four status chips render, Timeline is the default tab and events are ordered. **Role:** `egress_operator` sees `Resend`/`Cancel` on the Egress tab; `payment_viewer` does not. **Data:** ISO Lineage shows identifiers from `iso.payment_iso_identifiers` (including a JSON_DIRECT payment); Settlement tab renders an accepted-but-not-final state distinctly from final. **Negative:** `Cancel` opens a confirmation dialog and does nothing until confirmed; a non-permitted role gets no action buttons; SSE status update changes the Business status chip **on event arrival**, not on a timer.

### 6.11 Out of Scope
No status editing beyond the role-gated egress commands. No settlement-cycle or case-resolution actions (those live in their own workspaces). No raw XML editing. No re-parsing from this screen (manual correlation/replay is a P1 admin command elsewhere). No cross-payment comparison.

---

## 7. Shared Components Needed

`[ADOPT]` From the frontend blueprint's component kit (§18, on the §11a foundation: shadcn/ui + TanStack Table + Tailwind v4), the three screens require: `AppShell` (header + role-filtered left nav + SSE indicator + footer), `SummaryCard` (Control Room), `AlertList`/`FailedEventList` (Control Room), `FilterBar` (search + status + date + tenant/branch, URL-state), `EntityTable` (**TanStack Table**, deep-linked rows, `aria-sort`, URL state), `StatusChip` (text + icon, four variants), `TabbedObjectDetail` (Payment Detail), `Timeline`, `EvidenceDrawer` (modal, focus-trapped), `CommandButton` (role-gated + confirm + audit toast), `SubmitPaymentDialog`/`UploadFileDialog`, and the **`LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState`** family (empty ≠ unauthorized). No new components beyond the kit; no design-system definition here.

## 8. Shared Data-testid Convention

`[FREEZE]` `data-testid="<workspace>.<entity>.<component>.<action-or-state>"`. Workspaces used here: `control-room`, `payments`, `payment`. Every interactive control and every asserted state node carries one. Bans (from the blueprint): no `waitForTimeout`, no CSS-class-only selectors, no token in browser storage, no GraphQL mutations, no action button without a role+audit mapping. `[ADD]` Each suite also runs an **axe-core accessibility scan** (violations fail CI), asserts **URL-state** round-trips (Payments & Files filter/sort → shareable URL → restores on reload), and asserts **empty ≠ unauthorized** (`payments.list.empty-state` vs `payments.list.unauthorized-state`).

## 9. Open Questions for Later Screens

- Operator worklist (`operatorWorklist`) placement — Control Room landing tab vs its own P1 surface (leaning: Control Room tab).
- Whether Payments & Files gains live counts (currently query-driven by design to avoid row churn).
- Settlement/Egress/Reconciliation workspace specs (next UI-spec document).
- Reference-data admin editors' inline-edit vs form pattern.
- Evidence export format and gating (auditor, P1).

## 10. Impact on React/Next.js Frontend Blueprint

`[CHANGE, additive]` This spec **refines** the blueprint's S-00/Control Room, Payments & Files, and Payment Detail entries with exact sections, labels, controls, data sources, behaviours, role rules, accessibility, and test-ids — it does not add screens or change ADR-N3/N4. Two clarifications to fold back into the blueprint when next edited: **(a)** Payments & Files is **query-driven, not SSE-live** in MVP (only Control Room and the Payment Detail status chips are live); **(b)** the Payment Detail header standardizes on **four** labelled status chips (business/settlement/egress/reconciliation), which should be the canonical status-separation pattern for every object detail screen.

---

*End of first-3-screens UI spec. `[NO-CODE]` — layout, labels, controls, data, behaviour, roles, accessibility, and Playwright test-ids only. Consistent with the React/Next.js frontend blueprint, the Keycloak role model, ADR-N3 (BFF) and ADR-N4 (SSE), and the main blueprint read models + §7.2 commands. Ready for a frontend dev, UX designer, and QA/SDET to build and test from.*
