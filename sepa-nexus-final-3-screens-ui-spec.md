# SEPA Nexus — Final 3 Screens UI Specification

**Nature.** The third and final practical UI spec, continuing the exact standard of `sepa-nexus-first-3-screens-ui-spec.md` and `sepa-nexus-next-3-screens-ui-spec.md` for the last three workspaces: **Simulation Lab**, **Reference Data / Admin**, **Evidence / Audit**. For a frontend dev, UX designer, and QA/SDET. `[NO-CODE]` — layout, sections, labels, controls, data, behaviour, roles, accessibility, and Playwright test-ids only; no React code, no new modules, no design-system definition. Does not change ADR-N3 (BFF) or ADR-N4 (SSE).
**Roles** = the eleven from the Keycloak blueprint §6 (no screen-only roles). **Data** = main blueprint read models (GraphQL, read-only) + admin commands (§7.2, REST via BFF).

---

## 1. Executive UI Verdict

`[ADOPT]` These three complete the console: Simulation Lab is where the operator *drives* deterministic traffic and failure; Reference Data / Admin is where the platform's *configuration truth* is maintained; Evidence / Audit is where any object's *proof* is inspected. `[FREEZE]` Three invariants enforced in the UI: **(1)** Simulation **never writes domain tables directly** — every scenario runs through the system's public ingress/CSM paths, and the UI's only action is "launch/replay a scenario," never "insert a payment/settlement/egress row"; **(2)** **no admin change bypasses audit** — every Reference Data create/edit/deactivate confirms and produces an audit entry with actor/before/after, and versioned catalogs are edited by validity window, never destructively overwritten; **(3)** Evidence/Audit uses **progressive disclosure + role gating** — raw payloads and cross-tenant audit are never the default view; they are opened deliberately, by a permitted role. `[UX-RISK, mitigated]` Evidence/Audit is **not** a data dump — it is a structured, role-gated inspector.

---

## 2. Selected Screens and Rationale

`[ADOPT]` Simulation Lab and Reference Data / Admin are genuine top-level workspaces (distinct daily jobs, distinct roles). **Evidence / Audit is a hybrid `[MERGE]`**: it is primarily the **global Evidence drawer** already present on every object detail screen (the fastest path — proof is always one click from the object you're looking at), **plus** a thin top-level **Evidence/Audit workspace** for the `auditor` role (cross-tenant search + audit-trail browsing that has no single object home). Making Evidence/Audit *only* a top-level page would force operators to leave the object they're investigating to see its proof — the drawer is the ergonomic default; the workspace is the auditor's cross-cutting entry point.

## 3. Continuity with Previous UI Specs

`[ADOPT]` Same shell, same `StatusChip`/`EntityTable`/`FilterBar`/`TabbedObjectDetail`/`EvidenceDrawer`/`CommandButton` kit, same object-centered IA, same command discipline (role-gated + confirm + audit toast), same status-separation and no-color-only rules, same SSE policy (overviews/queues may be live; immutable/detail data query-driven). All six prior screens' rules carry forward unchanged.

## 4. Shared UI Rules

`[FREEZE]` (restated): no browser token storage (HttpOnly BFF session only, ADR-N3); all commands = REST through the BFF; GraphQL read-only; SSE proxied through the BFF, live-read only (ADR-N4); role-based UI (hide, backend enforces); no destructive action without a confirmation dialog; no status by color alone (text + icon, `[ACCESSIBILITY]`); every command has an audit consequence; every entity deep-linked; every interactive control has a stable `data-testid`; loading/empty/error/unauthorized states mandatory; stale-data indicator when needed.

### 4a. Component Foundation & Cross-Cutting Rules `[PATCH][FREEZE]`

Applied from `sepa-nexus-react-component-foundation-blueprint.md`; frontend blueprint §11a–§11g:
- **Component foundation** = shadcn/ui (Base UI) + TanStack Table + Tailwind v4, vendored. The **Simulation Lab** scenarios/runs tables and the **Reference Data** catalog tables use **TanStack Table** with real `<table>` markup; `[REJECT]` no admin framework / MUI / AntD / AG Grid as base; no charting dependency.
- **URL-driven table/list state** — **Simulation Lab** run/scenario list filters, sort, and pagination, and **Reference Data / Admin** catalog table filter/search, live in the URL query params (shareable, refresh-surviving).
- **Reference Data / Admin uses audit-aware state components** — the `*State` family plus the diff-preview/confirm flow; a save conflict surfaces via `ErrorState`, never a silent overwrite.
- **Evidence / Audit progressive disclosure** is built on the shadcn **Sheet + Collapsible** pattern — `EvidenceDrawer` (`sheet`, `aria-modal`, focus-trap) with `DisclosureSection` (`collapsible`) for raw-payload; raw stays collapsed by default and is role-gated.
- **Reusable state components** — `LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState`. `[FREEZE]` **`EmptyState` is not `UnauthorizedState`** — an evidence record or catalog a role may not see must never render as "no records."
- **No optimistic UI for commands** (global) — launch/replay, catalog create/edit/deactivate, evidence-bundle download all reflect only after backend accept.
- **Error boundaries** — per workspace and per tab; a crashed panel shows `ErrorState`, never blanks the shell.
- **Desktop-first** posture; **axe-core accessibility gate** in Playwright (violations fail CI).
- `[PATCH][G-1]` **Forbidden command → 403 even if UI is bypassed** — launch/replay, catalog create/edit/deactivate, and evidence-bundle download each have a Playwright/API test that invokes the command directly with an insufficient role and asserts `403` + audit entry, cross-referencing `sepa-nexus-keycloak-26-security-architecture-blueprint.md` §13.
- **SSE** applies only to Simulation Lab run-status (per §5.7); Reference Data and Evidence/Audit are query-driven — no SSE, so no reconnect concern there.

---

## 5. Screen 7 — Simulation Lab

### 5.1 Purpose
`[OPERATOR-WORKFLOW]` Let the simulation operator run deterministic educational/test scenarios and failure profiles, replay them by seed, and trace what each run produced (payments, files, settlement, egress, reconciliation) — all through the system's **public paths**, never a back door.

### 5.2 Users and Tasks
**Primary users:** `simulation_operator` (acts), `operator`/`auditor` (read). **Tasks:** browse scenarios; launch a scenario (optionally with a failure profile and seed); watch run status; replay a run by its seed; open the run's generated events; jump to the payments/files/artifacts/exceptions it created; open evidence.

### 5.3 Layout
- **Header:** `Simulation Lab`; global search; user menu; SSE live indicator; `Last update`.
- **Left navigation:** global.
- **Main content (`/simulation`):** a **Scenarios** section (catalog with launch action) + a **Runs** section (recent/active runs with status) + a filter bar (scenario, status, date).
- **Run detail (`/simulation/runs/:runId`):** run header (`Run status` + `Deterministic seed` + scenario name) + tabs `Scenario`, `Seed`, `Generated Events`, `Trace`; command bar (`Replay run`, `Cancel running simulation` if applicable).
- **Right panel/drawer:** Evidence drawer.
- **Footer/status area:** `Last update`; run counts.

### 5.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| Scenarios | catalog of runnable scenarios | scenario name, description, default failure profile | `Launch simulation` (gated) | all roles view; launch: simulation_operator |
| Runs | recent/active runs | run id, scenario, `Run status`, `Deterministic seed`, started | filter, row → run detail | all roles |
| Run: Scenario tab | what was run | scenario config + `Failure profile` used | — | all roles |
| Run: Seed tab | reproducibility | `Deterministic seed` value (copyable) | copy seed | all roles |
| Run: Generated Events tab | what it produced | `Generated events` list via `simulation.event.generated` → public paths | — | all roles |
| Run: Trace tab | cross-module effects | deep links to created payments/files/settlement/egress/reconciliation | links | all roles |
| Evidence drawer | proof of a produced object | evidence pointers | open drawer | all roles |

### 5.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Button | `Launch simulation` | BFF REST `POST /simulations/:profile` (confirm) | scenario selected | run in progress (same scenario) | simulation_operator |
| Dropdown | `Failure profile` | choose failure injection | launching | — | simulation_operator |
| Input | `Deterministic seed` | set/show seed | launching (optional) | — | simulation_operator |
| Button | `Replay run` | BFF REST replay with same seed (confirm) | run complete | still running | simulation_operator |
| Button | `Cancel running simulation` | BFF REST cancel (confirm) | run active | not running | simulation_operator |
| Copy | `Copy seed` | copy seed value | run has seed | — | any |
| Row link | `View run` | nav → `/simulation/runs/:id` | always | — | any |
| Link | `View created payments` | nav → payment list (filtered) / Trace tab | run produced payments | — | any |
| Link | `View generated events` | Generated Events tab | events exist | — | any |
| Button | `Open evidence drawer` | open drawer | always | — | any |

`[REJECT]` No control writes a domain table — `Launch`/`Replay` invoke the simulation command API, which drives traffic through public ingress/CSM paths (`csm.response.received` is the only entry for simulator output); the UI never inserts payments/settlement/egress rows.

### 5.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| Scenario name/description | `simulation_scenarios` | text | `No scenarios` | `Could not load scenarios` + retry |
| `Failure profile` | `failure_profiles` | text | `None` | — |
| Run id | `simulationRun` | uuid (link) | — | `Run not found` |
| `Run status` | simulation read model + SSE | chip text+icon (Running/Completed/Failed/Cancelled) | — | `Unavailable` |
| `Deterministic seed` | `simulation_runs` | string (copyable) | `—` | — |
| `Generated events` | `simulation_*_trace` / generated events read model | list (type, target, time) | `No events` | `Unavailable` |
| Trace links | run→created-entity read models | payment/file/settlement/egress/exception deep links | `Nothing produced yet` | `Unavailable` |
| Runs table (whole) | GraphQL `simulationRun`/list | table | `No runs yet` | `Could not load runs` + retry |

### 5.7 Behaviour
- **Loading:** scenarios/runs skeleton; run header loads first, tabs lazy-load.
- **Empty:** `No scenarios` / `No runs yet`; Trace tab `Nothing produced yet` for a just-launched run.
- **Error:** per-section error + `Retry`.
- **Unauthorized:** `Launch`/`Replay`/`Cancel` hidden for non-`simulation_operator`; read tabs remain.
- **Stale data:** `Last update` + stale marker on SSE disconnect.
- **SSE behaviour:** `[ADOPT]` the **Runs** section uses SSE for run-status changes (Running→Completed/Failed live); **run detail** uses query refresh + a targeted SSE update on the open run's status chip; Generated Events may live-append while a run is active.
- **Command success:** `Launch` → toast `Simulation launched — run <id>` with a deep link; `Replay` → toast `Replay launched — run <id>` (new run, same seed).
- **Command failure:** toast with reason (e.g. `Scenario already running`); no state change.
- **Optimistic UI:** **no** — a run appears only after the backend accepts the launch.
- **Confirmation modal:** `Launch`, `Replay`, and `Cancel running simulation` confirm (they generate real traffic / stop a run), showing scenario + seed.

### 5.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| simulation_operator | scenarios, runs, all tabs | Launch, Replay, Cancel | — |
| operator | scenarios, runs (read) | — | Launch/Replay/Cancel |
| auditor | runs (read, cross-tenant), Evidence | — | all commands |
| other roles | read (own scope) | — | all commands |

### 5.9 Accessibility
`[ACCESSIBILITY]` `Run status` is a text + icon chip, never color-only. `Deterministic seed` is exposed as readable, copyable text with a copy button announcing success via a live region. Scenario cards are keyboard-focusable with `aria-label`. Tabs are an ARIA `tablist`. `Launch`/`Replay`/`Cancel` confirmations are focus-trapped dialogs. Generated Events and Runs tables are real `<table>` with `<th scope>` and `aria-sort` where sortable.

### 5.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Scenario list | `simulation.scenario.list` | scenarios render |
| Launch button | `simulation.scenario.launch-button` | role-gated + confirm |
| Failure profile dropdown | `simulation.scenario.failure-profile-dropdown` | profile selection |
| Runs table | `simulation.run.table` | runs render |
| Run status chip | `simulation.run.status-chip` | status rendering |
| Seed value | `simulation.run.seed-value` | deterministic seed shown |
| Replay button | `simulation.run.replay-button` | role-gated + confirm |
| Cancel button | `simulation.run.cancel-button` | role-gated + confirm |
| Generated events | `simulation.run.generated-events-list` | events render |
| Trace links | `simulation.run.trace-links` | deep links to created entities |

`[PLAYWRIGHT]` **Smoke:** open `/simulation`, scenarios + runs render; open a run, seed + tabs render. **Role:** `simulation_operator` sees `Launch`/`Replay`; `operator` does not. **Data:** launching a seeded scenario produces a run whose Trace tab deep-links to a created payment; the same seed on `Replay` yields an identical run (determinism). **Negative:** `Launch` opens a confirmation and does nothing until confirmed; a non-`simulation_operator` sees no launch controls; SSE run-status change updates the runs chip **on event arrival**.

### 5.11 Out of Scope
`[REJECT]` No direct domain-table writes (public paths only). No scenario authoring UI (P2 — scenarios are seeded/config). No editing generated events. No settlement/egress/reconciliation actions here (those live in their own workspaces via the deep links). No non-deterministic runs.

---

## 6. Screen 8 — Reference Data / Admin

### 6.1 Purpose
`[OPERATOR-WORKFLOW]` Let the reference-data admin maintain the platform's configuration truth — participants, accounts, service levels, scheme/validation/mapping/render profiles, calendars, reason codes, status catalog, route profiles — with **every change confirmed, versioned, and audited**. Not plain CRUD: these rows drive settlement, routing, validation, and rendering, so changes have business consequences.

### 6.2 Users and Tasks
**Primary users:** `reference_data_admin` (acts), `operator`/`auditor` (read). **Tasks:** browse a catalog; create/edit/deactivate an entry within its validity window; update a calendar or profile; view the audit trail of a change.

### 6.3 Layout
- **Header:** `Reference Data`; global search; user menu; `Last update` (query-driven, not SSE — config is not live-streamed).
- **Left navigation:** global; within the workspace, a **catalog selector** (left rail or tabs): `Participants`, `Participant accounts`, `Service levels`, `Scheme profiles`, `Validation profiles`, `Mapping profiles`, `Render profiles`, `Calendars`, `Reason codes`, `Status catalog`, `Route profiles`.
- **Main content:** the selected catalog's **table** + command bar (`Create`, and per-row `Edit`/`Deactivate`/`View audit`) + filter/search.
- **Editor:** a **form drawer/dialog** for create/edit, showing validity window and a diff preview on save.
- **Right panel/drawer:** audit trail for the selected entry (`View audit`).
- **Footer/status area:** result count; `Last update`.

### 6.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| Catalog selector | choose which catalog | list of 11 catalogs | select | all roles view |
| Catalog table | list entries | catalog-specific columns + validity window + status | `Create`, row `Edit`/`Deactivate`/`View audit`, filter | commands: reference_data_admin |
| Editor form (drawer/dialog) | create/edit an entry | fields + validity window + diff preview | save (confirm), cancel | reference_data_admin |
| Audit trail panel | change history of an entry | actor, timestamp, before/after, correlationId | open/close | all roles (read); actor detail per role |
| Deactivate confirm | end-date an entry | validity-window impact + affected consumers note | confirm/cancel | reference_data_admin |

### 6.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Button | `Create` (e.g. `Create participant`) | open editor → BFF REST create (confirm) | catalog editable | read-only catalog | reference_data_admin |
| Button | `Edit` | open editor → BFF REST update (confirm) | entry editable | deactivated/locked | reference_data_admin |
| Button | `Deactivate` | BFF REST deactivate/end-date (confirm dialog) | entry active | already inactive | reference_data_admin |
| Button | `Update calendar` | open calendar editor → BFF REST (confirm) | calendar catalog | — | reference_data_admin |
| Button | `Update profile` | open profile editor → BFF REST (confirm) | profile catalog | — | reference_data_admin |
| Button | `View audit` | open audit trail panel | entry has history | — | any (read) |
| Search/filter | (catalog-specific) | filter entries | always | — | any |
| Status chip | (active/inactive/scheduled) | validity status (text+icon) | always | — | any |
| Button | `Save` | commit editor (confirm + diff) | form valid | invalid/unchanged | reference_data_admin |

`[REJECT]` No admin change bypasses audit — every `Create`/`Edit`/`Deactivate`/`Update` is confirmed, produces an audit entry (actor/before/after/correlationId), and is versioned by validity window (no destructive in-place overwrite of a live-referenced row).

### 6.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| Participant (name/BIC) | `reference_data.participants` | text | `No participants` | `Could not load` + retry |
| `Participant accounts` | `participant_accounts` | account refs + currency | `No accounts` | `Unavailable` |
| `Service levels` | `service_levels` | text | `No service levels` | `Unavailable` |
| `Scheme profiles` | `scheme_profiles` | code + family | `No profiles` | `Unavailable` |
| `Validation profiles` / `Mapping profiles` / `Render profiles` | reference-data profile catalogs | code + validity | `No profiles` | `Unavailable` |
| `Calendars` | `business_calendars` / `settlement_cutoff_calendar` | dates + sessions | `No calendar` | `Unavailable` |
| `Reason codes` | `iso_reason_codes` | code + family | `No codes` | `Unavailable` |
| Status catalog | `status_catalog` | codes | `No statuses` | `Unavailable` |
| Route profiles | routing config catalogs | code + rules | `No route profiles` | `Unavailable` |
| Validity window | each catalog | valid_from/valid_to | `—` | — |
| Audit trail | `audit_log` | actor + before/after + time | `No changes recorded` | `Unavailable` |

### 6.7 Behaviour
- **Loading:** catalog table skeleton; editor loads current values before enabling save.
- **Empty:** per-catalog empty label; `Create` still available (if permitted).
- **Error:** table-level error + `Retry`; save errors keep the editor open with values preserved.
- **Unauthorized:** `Create`/`Edit`/`Deactivate`/`Update`/`Save` hidden for non-admins; catalogs remain readable; `View audit` read for permitted roles.
- **Stale data:** `Last update` + `Refresh`; **no SSE** (config is not a live stream; concurrent-edit conflicts surface on save as a version conflict).
- **SSE behaviour:** none `[ADOPT]` — reference data is query-driven; live-streaming config changes would be noise.
- **Command success:** save → toast `<Catalog> updated — <id>`; the row reflects on refresh with new validity.
- **Command failure:** toast with reason (e.g. `Validity window overlaps existing version` / `Version conflict — reload`); editor stays open.
- **Optimistic UI:** **no** — a change is trusted only after the backend accepts it (business consequences demand server confirmation).
- **Confirmation modal:** `Create`/`Edit`/`Save` confirm with a **diff preview** (before/after); `Deactivate` confirms with an affected-consumers note.

### 6.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| reference_data_admin | all catalogs, audit | Create, Edit, Deactivate, Update, Save | — |
| operator | all catalogs (read) | — | all edit commands |
| auditor | all catalogs + audit (cross-tenant read) | — | all edit commands |
| security_admin | catalogs (read); keys elsewhere | — | reference-data edit commands |
| other roles | read (own scope) | — | all edit commands |

### 6.9 Accessibility
`[ACCESSIBILITY]` Catalog tables are real `<table>` with `<th scope>` and `aria-sort`. The editor form has labelled fields, `aria-describedby` for validity-window rules, and inline validation messaging tied to fields. Diff preview presents before/after as an accessible comparison (not color-only — additions/removals labelled). Confirm/deactivate dialogs are focus-trapped, `Esc`-dismissible pre-command. Catalog selector is keyboard-navigable (`tablist` or listbox). Status chips are text + icon.

### 6.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Catalog selector | `reference-data.catalog.selector` | catalog switching |
| Participants table | `reference-data.participant.table` | catalog renders |
| Create participant button | `reference-data.participant.create-button` | role-gated command |
| Edit profile button | `reference-data.profile.edit-button` | role-gated command |
| Update calendar button | `reference-data.calendar.update-button` | role-gated command |
| Editor form | `reference-data.editor.form` | create/edit flow |
| Diff preview | `reference-data.editor.diff-preview` | confirm-with-diff |
| Deactivate button | `reference-data.participant.deactivate-button` | destructive + confirm |
| Audit trail panel | `reference-data.entry.audit-panel` | audit visibility |
| Validity status chip | `reference-data.entry.validity-chip` | validity rendering |

`[PLAYWRIGHT]` **Smoke:** open Reference Data, switch catalogs, participants table renders. **Role:** `reference_data_admin` sees `Create`/`Edit`/`Deactivate`; `operator` sees read-only catalogs, no edit controls. **Data:** creating a participant shows a diff preview on confirm and an audit entry appears in the audit panel with actor/before/after. **Negative:** `Deactivate` opens a confirmation and does nothing until confirmed; a non-admin has no edit controls; an overlapping validity window surfaces the conflict error; saving a stale version surfaces `Version conflict — reload`.

### 6.11 Out of Scope
`[REJECT]` No audit-bypassing edits. No destructive in-place overwrite of live-referenced rows (versioned by validity window). No bulk import (P1). No route-rule authoring beyond profile config (routing logic is in the module). No Keycloak role/user management here (that is security admin, separate). No ledger/settlement/egress data editing.

---

## 7. Screen 9 — Evidence / Audit

### 7.1 Purpose
`[OPERATOR-WORKFLOW]` Give an operator the **proof** behind any object — raw/parsed message, hashes, signature verdict, audit trail, correlation/trace ids — via progressive disclosure and role gating, and give the `auditor` a cross-tenant workspace to browse audit and evidence that has no single object home. `[UX-RISK, mitigated]` Not a data dump: raw payloads and cross-tenant audit are opened deliberately, by a permitted role — never the default view.

### 7.2 Users and Tasks
**Primary users:** every role (via the drawer, own scope), `auditor` (via the workspace, cross-tenant). **Tasks:** from any object, open the Evidence drawer to see hashes, signature verdict, raw-vs-parsed (progressive disclosure), and the command/audit history; copy correlation/trace ids; follow related-entity links; (auditor) browse the audit trail and evidence records cross-tenant; (P1) download an evidence bundle if permitted.

### 7.3 Layout
- **Evidence drawer (global, on every object detail):** header (entity id + `Signature verdict` chip) + collapsible sections `Hashes`, `Raw message` (collapsed by default), `Parsed message`, `Audit trail`, `Command history`, `Related entities`, `Correlation ID`/`Trace ID`.
- **Evidence/Audit workspace (`/evidence`, auditor):** header `Evidence / Audit`; a **search/filter bar** (entity type, id, actor, correlationId, date); an **audit-trail table** + an **evidence-records table**; row → the same drawer content as a full page.
- **Left navigation:** global (workspace link visible to `auditor`).
- **Footer/status area:** `Last update`.

### 7.4 Sections

| Section | Purpose | Content | Controls | Visibility Rules |
|---|---|---|---|---|
| Signature verdict | signature outcome | `Signature verdict` (Verified/Failed/Not applicable) chip | — | all roles |
| Hashes | integrity | `Payload hash`, raw message hash (copyable) | copy | all roles |
| Raw message | raw bytes (progressive disclosure) | `Raw message` (collapsed; expand deliberately) | expand (gated) | roles permitted to see raw; auditor always |
| Parsed message | canonical view | `Parsed message` fields | — | all roles |
| Audit trail | who did what | `Audit trail`: actor, action, time, before/after | filter | all roles (own scope); auditor cross-tenant |
| Command history | commands on this entity | `Command history` table: command, actor, role, result, time | — | all roles (own scope) |
| Related entities | navigation | `Related payment`/`Related file`/`Related artifact` links | links | all roles |
| Correlation/Trace | tracing | `Correlation ID`, `Trace ID` (copyable) | copy | all roles |
| Workspace search (auditor) | cross-tenant find | search by entity/actor/correlationId | filter | auditor |

### 7.5 Controls and Labels

| Control Type | Label | Action | Enabled When | Disabled When | Role Required |
|---|---|---|---|---|---|
| Chip | `Signature verdict` | show verdict (text+icon) | always | — | any |
| Copy | `Copy correlation ID` | copy `Correlation ID` | id present | — | any |
| Copy | `Copy trace ID` | copy `Trace ID` | id present | — | any |
| Copy | `Copy payload hash` | copy `Payload hash` | present | — | any |
| Disclosure | `Show raw message` | expand raw payload | role permitted | not permitted | raw-permitted roles / auditor |
| Button | `Download evidence bundle` (P1) | BFF REST download | role permitted + P1 enabled | not permitted | auditor (P1) |
| Link | `Open related entity` | nav → payment/file/artifact | related exists | — | any (target gated) |
| Filter (workspace) | (entity/actor/correlationId/date) | filter audit/evidence | auditor workspace | — | auditor |
| Row link (workspace) | `View evidence` | open drawer content | always | — | auditor |

`[REJECT]` Raw payload is **not** shown by default — it is behind a deliberate `Show raw message` disclosure, role-gated; cross-tenant audit is auditor-only. Evidence/Audit exposes **read** actions plus copies; the only heavier action is a P1, role-gated `Download evidence bundle`.

### 7.6 Data Displayed

| Field / Label | Source | Format | Empty State | Error State |
|---|---|---|---|---|
| `Signature verdict` | signature verdict via `messageEvidence` | chip (Verified/Failed/Not applicable) | `Not applicable` | `Unavailable` |
| `Payload hash` / raw hash | `payload_hashes` / evidence read model | hash string (copyable) | `—` | `Unavailable` |
| `Raw message` | `messageEvidence` (raw ref) | bytes preview (on expand) | `No raw payload` | `Unavailable` |
| `Parsed message` | `messageEvidence` (parsed) | field view | `No parsed view` | `Unavailable` |
| `Audit trail` | `audit_log` | actor + action + before/after + time | `No audit entries` | `Unavailable` |
| `Command history` | audit/command read model | command + actor + role + result + time | `No commands` | `Unavailable` |
| `Correlation ID` / `Trace ID` | event/trace metadata | string (copyable) | `—` | — |
| Related entities | evidence link read models | deep links | `—` | — |
| Workspace tables (auditor) | GraphQL `messageEvidence`/audit read models | tables | `No records match` | `Could not load` + retry |

### 7.7 Behaviour
- **Loading:** drawer opens with a skeleton; sections load independently; raw stays collapsed until expanded.
- **Empty:** per-section empty labels; `Not applicable` verdict for unsigned channels.
- **Error:** per-section error + `Retry`; a failed raw fetch does not blank hashes/verdict.
- **Unauthorized:** `Show raw message` hidden for non-permitted roles; cross-tenant workspace hidden for non-auditors; `Download evidence bundle` hidden unless permitted (P1).
- **Stale data:** evidence/audit are immutable records — no staleness concern; `Last update` shown on the workspace.
- **SSE behaviour:** **none** `[ADOPT]` — evidence and audit are append-only immutable records; a re-query on open is sufficient, live-streaming would add nothing.
- **Command success:** copy actions confirm via a live region (`Correlation ID copied`); `Download evidence bundle` (P1) → toast `Bundle downloaded` and an audit entry (the download itself is audited).
- **Command failure:** copy failure → error toast; download failure → reason toast.
- **Optimistic UI:** N/A (read + copy + download).
- **Confirmation modal:** none for read/copy; `Download evidence bundle` (P1) confirms if it exports PII, and is itself audited.

### 7.8 Role-Based Behaviour

| Role | Can View | Can Act | Hidden Elements |
|---|---|---|---|
| auditor | drawer + workspace (cross-tenant), raw, audit, command history | copy ids, `Download evidence bundle` (P1) | — |
| operator | drawer (own scope): verdict, hashes, parsed, audit, related | copy ids | raw (unless permitted), cross-tenant workspace, download |
| payment_viewer | drawer (own scope): verdict, hashes, parsed | copy ids | raw, audit detail, workspace, download |
| reference_data_admin / operational roles | drawer (own scope) per need | copy ids | raw (unless permitted), workspace, download |
| security_admin | drawer + signature/key-relevant evidence | copy ids | cross-tenant audit (unless also auditor) |

### 7.9 Accessibility
`[ACCESSIBILITY]` The Evidence drawer is an `aria-modal` dialog with focus trap and focus restore to its trigger. `Signature verdict` is a text + icon chip (Verified/Failed/Not applicable), never color-only. Collapsible sections are proper disclosure widgets (`aria-expanded`), raw collapsed by default. Copy buttons announce success via `aria-live`. Audit and command-history tables are real `<table>` with `<th scope>` and `aria-sort`. Correlation/Trace ids are readable, selectable text with copy affordances. Raw message, when expanded, is in a scrollable region with an accessible label.

### 7.10 Playwright Testability

| Element | data-testid | Test Purpose |
|---|---|---|
| Signature verdict chip | `evidence.record.signature-verdict-chip` | verdict rendering |
| Payload hash value | `evidence.record.payload-hash-value` | hash display + copy |
| Raw message disclosure | `evidence.record.raw-message-disclosure` | progressive disclosure + gating |
| Parsed message view | `evidence.record.parsed-message-view` | parsed rendering |
| Audit trail table | `audit.trail.table` | audit rows render |
| Command history table | `audit.command-history.table` | command history renders |
| Correlation ID copy | `audit.correlation-id.copy-button` | copy correlation id |
| Trace ID copy | `audit.trace-id.copy-button` | copy trace id |
| Download bundle button (P1) | `evidence.record.download-bundle-button` | role-gated export |
| Related entity link | `evidence.record.related-entity-link` | deep link |

`[PLAYWRIGHT]` **Smoke:** open an object's Evidence drawer, verdict + hashes + parsed render; raw is collapsed. **Role:** `auditor` can expand `Show raw message` and see cross-tenant audit; `payment_viewer` cannot expand raw and has no workspace link. **Data:** signature verdict matches the seeded evidence; copying correlation id places it on the clipboard (assert via the live-region confirmation, not `waitForTimeout`); audit trail shows a prior command with actor/role. **Negative:** raw disclosure is absent/disabled for non-permitted roles; `Download evidence bundle` is hidden unless permitted; cross-tenant record is not visible to a single-tenant, non-auditor token.

### 7.11 Out of Scope
`[REJECT]` No raw payload as default content (progressive disclosure only). No evidence/audit editing (append-only, immutable). No cross-tenant browsing for non-auditors. No evidence deletion or redaction from UI. No re-hashing/re-verifying from this screen. No bulk export in MVP (single bundle download is P1).

---

## 8. Shared Components Needed

`[ADOPT]` All from the existing kit (on the §11a foundation: shadcn/ui + TanStack Table + Tailwind v4) — no new components: `AppShell`, `FilterBar` (URL-state), `EntityTable` (**TanStack Table**, deep-linked, `aria-sort`, URL state), `StatusChip` (adds `Run status`, validity, `Signature verdict` variants — all text+icon), `TabbedObjectDetail`, `EvidenceDrawer` (shadcn `sheet`, the canonical Evidence/Audit surface, with progressive-disclosure sections), `CommandButton` (role-gated + confirm + audit toast), `CatalogEditor` (shadcn `sheet` + `form` with diff preview, Reference Data), `DisclosureSection` (shadcn `collapsible`, progressive disclosure for raw payload), and the **`LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState`** family (empty ≠ unauthorized). No design-system definition here.

## 9. Shared Data-testid Convention

`[FREEZE]` `data-testid="<workspace>.<entity>.<component>.<action-or-state>"`. Workspaces added here: `simulation`, `reference-data`, `evidence`, `audit`. Every interactive control and asserted state node carries one. Bans (unchanged): no `waitForTimeout`; no CSS-class-only selectors; no reliance on visible text alone for critical operator actions; no token in browser storage; no GraphQL mutations; no command button without a role+audit mapping. `[ADD]` Each suite also runs an **axe-core accessibility scan** (violations fail CI), asserts **URL-state** round-trips (Simulation run list + Reference Data catalog filter → shareable URL → restores on reload), and asserts **empty ≠ unauthorized** (e.g. `evidence.record.empty-state` vs `evidence.record.unauthorized-state`).

## 10. Impact on React/Next.js Frontend Blueprint

`[CHANGE, additive]` This spec refines the blueprint's Simulation Lab, Reference Data / Admin, and Evidence/Audit surfaces (S-16…S-18 range) with exact sections, labels, controls, data sources, behaviours, roles, accessibility, and test-ids — no new screens, no ADR change. Three canonical patterns to fold back: **(a)** Simulation acts only through public paths — the UI has no domain-write control, only launch/replay; **(b)** Reference Data is versioned-by-validity + diff-preview + always-audited — never plain destructive CRUD; **(c)** Evidence/Audit is primarily the **global drawer** (progressive disclosure, role-gated raw) plus a thin **auditor workspace** — not a top-level page every operator lives in. With this, all nine MVP-relevant screens (three specs) are specified to implementation-and-test depth.

## 11. Open Questions

- Evidence-bundle download format and PII-gating rules (P1) — needs a data-governance decision before build.
- Whether Reference Data gains a scheduled-change (future-dated validity) preview timeline (P1 UX).
- Simulation scenario authoring UI (P2) vs seeded-config-only (MVP).
- Auditor workspace retention/window controls for very large audit sets (P1 pagination/streaming-export).
- Consolidated cross-workspace search (global command palette) — a later, cross-cutting concern.

---

*End of final-3-screens UI spec. `[NO-CODE]` — layout, labels, controls, data, behaviour, roles, accessibility, and Playwright test-ids only. Completes the nine-screen UI specification set alongside the first- and next-3-screens specs; consistent with the React/Next.js frontend blueprint, the Keycloak role model, ADR-N3 (BFF) and ADR-N4 (SSE), and the main blueprint read models + §7.2 commands. Simulation writes no domain tables; admin changes never bypass audit; Evidence/Audit uses progressive disclosure and role gating, never a data dump.*
