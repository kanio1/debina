# SEPA Nexus — Frontend Template and Component Adoption

**Nature.** A technology-foundation decision for the frontend component layer, plus a brutal self-review of the frontend design so far, plus concrete corrections folded back into the UI specs. `[NO-CODE]` — decisions, comparison matrices, component-mapping tables, and correction lists only; no React implementation, no design-system token definitions. Does not reopen ADR-N1…N8, and does not change the BFF/SSE/GraphQL-read-only/role model. This document is cited elsewhere as `sepa-nexus-react-component-foundation-blueprint.md`-equivalent content — filename and content are now aligned.
**Frozen inputs it must honor:** BFF (no browser token), GraphQL read-only, REST commands via BFF, SSE via BFF (ADR-N3/N4); the eleven-role model; the object-centered IA and nine-screen specs; the `data-testid` convention `<workspace>.<entity>.<component>.<action-or-state>`; no `waitForTimeout`, no CSS-class-only selectors.

---

## 1. Executive Decision

```text
PRIMARY: shadcn/ui (Base UI primitives) + TanStack Table + Tailwind v4 — vendored, headless, owned
SECONDARY INSPIRATION: shadcn `dashboard-01` block (layout/shell reference only, not adopted wholesale); Refine's Keycloak auth-provider shape (P2 reference for how a data provider could wrap the BFF — architecture not adopted)
REJECTED: React-admin, Refine (as base architecture), Tailwind UI Application UI (as a paid template dependency), satnaing/shadcn-admin (as a wholesale template — inspect only), Nuxt UI Dashboard (wrong framework entirely), MUI Dashboard, Ant Design / Ant Design Pro, AG Grid
```

`[FREEZE]` The decisive reason is ownership and control: the operator console's whole value is *precise, testable, role-gated, audit-correct* behaviour, and every one of those words is easier when the component code lives **in our repo** (stable `data-testid`, no library churn breaking selectors, no vendor markup to fight for accessibility) and when the data grid is a **headless engine we render ourselves** (real `<table>` with `<th scope>`/`aria-sort`, exactly as the specs demand).

`[SELF-REVIEW VERDICT]` The frontend design so far is **strong on IA and testability, thin on three things** the specs quietly assumed: a **design-token/theme decision**, an **error/loading/empty state system** as real components (not per-screen prose), and a **URL-state contract** for tables. All three are fixed in this document and folded back into the specs (§6, §11).

---

## 2. Candidate Matrix

| Candidate | Adopt / Defer / Reject | Best Use | Risk | Reason |
|---|---|---|---|---|
| **shadcn/ui + TanStack Table + Tailwind v4** | `[ADOPT]` | full operator console foundation | low — copy-paste means we own upgrade timing | satisfies every requirement (R1–R10, below) without a fight |
| shadcn `dashboard-01` block | `[DEFER]` — inspect only | shell/layout reference | low | a starting-point layout to study, not a dependency; our `AppShell` is custom-composed from primitives |
| `satnaing/shadcn-admin` | `[DEFER]` — inspect only | sidebar/nav pattern reference | medium — it's a full opinionated admin template, easy to over-adopt wholesale | useful to look at, wrong to import as a dependency; we build our own from primitives |
| Tailwind UI Application UI | `[REJECT]` | — | medium — paid, licensed component library, not owned code | violates R1 (own the code); another licensing surface for a portfolio project |
| React-admin | `[REJECT]` | — | high — MUI-opinionated, resource/data-provider core | fights the GraphQL-read + REST-command-via-BFF split; markup not owned |
| Refine | `[REJECT]` as base / `[DEFER]` its Keycloak auth-provider as P2 reference | — | high as base — imposes a data-provider/resource model | our data path is deliberately two-channel (GraphQL read, REST command); Refine's core assumes one |
| Nuxt UI Dashboard | `[REJECT]` | — | n/a | wrong framework — this is a Next.js/React project; **never mix Nuxt and Next.js in one app** |
| MUI Dashboard | `[REJECT]` | — | medium — opinionated markup, heavy override burden | not owned; fights our token/accessibility control |
| Ant Design / Ant Design Pro | `[REJECT]` | — | medium — same class of risk as MUI | not owned; opinionated markup |
| AG Grid | `[REJECT]` | — | medium — enterprise grid dependency | `<div>` grid, not a real `<table>`; violates R2 |

---

## 3. Recommended Stack

`[FREEZE]` **Next.js + shadcn/ui + Tailwind + TanStack Table.** The prompt's default consideration also named **shadcn charts/Recharts** — checked and **not adopted for MVP**: Control Room is a triage board, not a BI wall, and no other screen needs charting in Iterations 0–5. Recharts remains the fallback choice **if** a `[P2]` charting need ever appears, precisely because it composes cleanly with shadcn rather than because it was assumed upfront.

Verified against the requirements the foundation must satisfy:

| # | Requirement | Why non-negotiable | Met? |
|---|---|---|---|
| R1 | Own the component code (stable `data-testid`) | Playwright-first testability; no silent selector breakage | ✅ vendored |
| R2 | Headless/real-DOM tables (`<table>`+`<th scope>`+`aria-sort`) | specs mandate real tables for accessibility | ✅ TanStack |
| R3 | Accessibility primitives (ARIA, keyboard, focus-trap) built-in | drawers/dialogs/tabs must be focus-trapped | ✅ Radix/Base UI |
| R4 | No coupling to a data-provider/CRUD abstraction | GraphQL-read + REST-command-via-BFF is deliberately split | ✅ no data provider |
| R5 | Role-gated command + confirm + audit toast as a reusable primitive | every command in every spec is role+confirm+audit | ✅ composable from primitives |
| R6 | Drawer/dialog/tabs/badge/toast/table/form/combobox available | the exact kit the specs reference | ✅ all present |
| R7 | SSR / Next.js App Router friendly | BFF is a Next.js server | ✅ |
| R8 | Tailwind-native theming via tokens | consistent status chips, four-status separation, dark mode | ✅ (§6) |
| R9 | TypeScript 6 strict | type-safe read-model → UI mapping | ✅ |
| R10 | No runtime lock-in / MIT-licensed | portfolio project; forkable, auditable, free | ✅ |

`[ADD]` Two foundation pieces the specs assumed but never named, now frozen: **URL-driven table state** (filters/sort/pagination in the query string — shareable, refresh-surviving) and a **state-system component family** (`LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState`, with empty ≠ unauthorized). Full contract: §6 (Review Findings) and the frontend blueprint §11d/§11e, which this decision feeds.

---

## 4. Components to Adopt

| SEPA Nexus Need | Component / Block | Source | Notes |
|---|---|---|---|
| App shell | custom, composed from `sidebar` + header/footer | shadcn `sidebar` primitive | role-filtered nav; SSE indicator is custom |
| Sidebar | `sidebar` | shadcn/ui | collapses at narrow width (desktop-first) |
| Header | custom | composed from shadcn primitives | workspace title, search, user menu, live indicator |
| Command palette (Cmd-K, P1) | `command` | shadcn/ui | global search/nav, not MVP |
| Dashboard cards | `card` | shadcn/ui | Control Room `SummaryCard` |
| Data table | `table` (markup) + **TanStack Table** (engine) | shadcn/ui + TanStack | every `EntityTable`/`WorkQueueTable` |
| Filters | `input`/`select`/`popover`/`calendar`/`combobox` | shadcn/ui | `FilterBar`, URL-driven state |
| Tabs | `tabs` | shadcn/ui | `TabbedObjectDetail`, ARIA tablist/tab/tabpanel |
| Drawer/sheet | `sheet` | shadcn/ui | `EvidenceDrawer` |
| Alert dialog | `alert-dialog` | shadcn/ui | destructive/state-changing command confirmation |
| Toast | `sonner` | shadcn/ui | command success/failure + audit-consequence surfacing |
| Badge/status chip | `badge` | shadcn/ui | `StatusChip` — text+icon variants per status axis |
| Timeline | custom semantic `<ol>` | none (hand-built) | no shadcn primitive fits; kept intentionally simple |
| Charts | **none adopted for MVP** | — | `[REJECT]` for MVP; Recharts is the `[P2]` fallback if ever needed |
| Skeleton | `skeleton` | shadcn/ui | `LoadingState` |
| Empty state | custom, built on shadcn primitives | — | `EmptyState`, visually distinct from `UnauthorizedState` |
| Pagination | TanStack Table pagination API + shadcn `button` | TanStack + shadcn/ui | URL-driven |
| Forms | `form` (react-hook-form + zod) | shadcn/ui | `CatalogEditor` (Reference Data) |
| Disclosure | `collapsible` | shadcn/ui | raw-payload progressive disclosure (Evidence) |
| Icons | lucide-react | lucide | paired with text, never color-alone |

---

## 5. Screen Mapping

| SEPA Screen | Starting Template / Component | Custom Work Needed |
|---|---|---|
| Operations Control Room | `card` grid (dashboard cards) + custom `AlertList`/`FailedEventList` | SSE wiring, live-tile highlight-on-change, stale-disconnect state, role-filtered quick links |
| Payments & Files | shadcn `table` + TanStack Table + `tabs` (Payments/Files) | URL-state filters, advanced search (3 identifiers), file upload dialog with drag-drop/progress (per the Playwright test-learning corrections), idempotency-conflict toast wiring |
| Payment Detail / Timeline | `tabs` (7 tabs) + `sheet` (Evidence) + custom `Timeline` | **four separately-labelled status chips** (fully custom composition — no template has this), SSE-targeted single-chip update, copy-to-clipboard buttons, role-gated egress action bar |
| Settlement & Liquidity | `table`+TanStack (cycle board) + `tabs` (cycle/attempt detail) | disabled-until-eligible `Close cycle`/`Run netting` buttons, **read-only ledger drill-down enforcement** (no write control anywhere in the DOM), finality-vs-status separation |
| Egress & Delivery | `table`+TanStack (artifacts) + `tabs` + manual-intervention queue table | retry/cancel command buttons with no-optimistic-UI wiring, receipt download (`waitForEvent`-testable), **delivery status shown with zero finality label anywhere on the workspace** |
| Reconciliation & Cases | `table`+TanStack (`WorkQueueTable` variant with selection) + `tabs` (run/exception/case) | assign/resolve/false-positive/escalate command bar, **no "fix"/"repair" control anywhere** (negative-test-worthy absence), assignment-race/stale-conflict state (per Playwright corrections) |
| Simulation Lab | `card` (scenario catalog) + `table` (runs) + `tabs` (run detail) | deterministic-seed display + copy, replay command, generated-events trace with deep links to every downstream object |
| Reference Data / Admin | `sheet`+`form` (`CatalogEditor`) + `table` (per catalog) | diff-preview-on-save, validity-window versioning (never destructive overwrite), version-conflict negative state |
| Evidence / Audit | `sheet`+`collapsible` (`EvidenceDrawer`+`DisclosureSection`, global) + thin `table`-based auditor workspace | progressive disclosure of raw payload (role-gated, collapsed by default), cross-tenant search (auditor only), **empty ≠ unauthorized** enforced here most strictly of any screen |

No screen is a template drop-in — every one composes the same ~19-component kit (§4) around SEPA Nexus's own object model. This is intentional: it's why the kit is small and headless rather than a themed admin template.

---

## 6. Review Findings and Corrections

`[SELF-REVIEW]` Kill-attempt first, per the project's own discipline.

| Area | Finding | Correction | Severity |
|---|---|---|---|
| Design system | No design-token/theme decision existed; shadcn *amplifies* a design system, it doesn't create one — without tokens it produces inconsistent chips | Token contract added: status-axis, severity, state (empty≠unauthorized), dark-mode, density tokens, text+icon rule | `[MUST-FIX]` |
| State handling | Loading/empty/error/unauthorized states were prose per-screen, not components — 40+ hand-written variants would drift | One reusable `*State` component family across all 9 screens | `[MUST-FIX]` |
| Table state | Filter/sort/page state was not deep-linkable — a filtered work-queue couldn't be shared or survive refresh | URL-driven table state (query params) on every list/queue screen | `[MUST-FIX]` |
| Command discipline | No optimistic-UI stance was stated as a global rule, only implied per-command | Global `[FREEZE]`: no optimistic UI for any command, anywhere | `[SHOULD-FIX]` |
| Live-feed resilience | SSE reconnection/backoff behaviour was underspecified — a flapping connection could hammer the BFF | Capped exponential backoff + stale marker + manual reconnect, specified once, applied everywhere | `[SHOULD-FIX]` |
| Failure containment | No error-boundary strategy at the shell level — a thrown render error could blank the whole app | Per-workspace and per-tab error boundaries | `[SHOULD-FIX]` |
| Accessibility enforcement | Every screen's §_.9 lists ARIA rules, but nothing enforces them in CI | axe-core gate wired into every Playwright suite, violations fail the build | `[SHOULD-FIX]` |
| Component sizing | ~19-component kit across 9 screens — is it buildable in the MVP frontend budget? | Assessed: roughly half are thin shadcn wrappers; realistic for the Iteration-1→5 spread. No change, just stated. | `[ACCEPTABLE]` |
| Info-leak risk | "No rows" and "you may not see these rows" looked identical in some table specs | `EmptyState` and `UnauthorizedState` explicitly distinguished; unauthorized never rendered as empty | `[MUST-FIX]` |
| Responsive posture | Mobile/responsive stance was unstated, inviting inconsistent breakpoints | Desktop-first frozen; sidebar collapses at narrow width; no mobile screens in MVP | `[SHOULD-FIX]` |
| Test-id collisions | Dynamically-keyed row test-ids (e.g. `payments.list.row.<paymentId>`) could be misused for count assertions | Clarified: existence/count checks use the table testid + row role; a specific row uses the id suffix — never the reverse | `[ACCEPTABLE]` |
| Token ownership | No stated owner for future token changes | Tokens live in the frontend repo; a status-axis color change is a reviewed change | `[ACCEPTABLE]` |

`[SELF-REVIEW VERDICT]` No finding is architectural; all are paper-fixable and folded into §11 (Adoption Plan) and the UI specs (already patched in a prior pass for most; §9 items below).

---

## 7. What Not To Build From Scratch

`[ADOPT]` Taken from shadcn/ui + TanStack Table + Radix/Base UI, not hand-rolled:
- ARIA roles, keyboard interaction, and focus-trapping for dialogs, sheets, tabs, and popovers (Base UI primitives).
- Table sorting, filtering, pagination, row-selection, and column-state engines (TanStack Table).
- Accessible form primitives, validation wiring (react-hook-form + zod via shadcn `form`).
- Toast queueing/dismissal lifecycle (`sonner`).
- Base color-token infrastructure and dark-mode variable swapping (Tailwind v4 + shadcn theme conventions).
- Icon set and consistent stroke-weight iconography (lucide-react).
- Skeleton loading primitives.
- Combobox/command-palette keyboard navigation.

---

## 8. What Must Remain Custom

`[FREEZE]` No template or library provides these — they are SEPA Nexus's own domain, and building them is the actual engineering work of this project:
- **Four-status header** (Business / Settlement / Egress / Reconciliation shown separately, never merged).
- **Payment timeline** (ordered lifecycle story with source + correlationId per event).
- **ISO lineage tab** (raw→parsed→validated→correlated chain, including the `JSON_DIRECT` synthetic path).
- **Settlement finality indicators** (accepted/posted/delivered ≠ final, shown as its own axis).
- **Egress ≠ finality separation** (the Egress & Delivery workspace carries no finality label at all, by design).
- **Reconciliation evidence workflow** (detect → classify → evidence → escalate; no repair control exists anywhere).
- **Role-gated command bar** (per-command role + confirm + audit-toast wiring, applied consistently across all 9 screens).
- **BFF-only command model** (every command is REST-through-BFF; no component may hold or transmit a token).
- **Playwright `data-testid` convention** (`<workspace>.<entity>.<component>.<action-or-state>`, applied to every interactive element).

---

## 9. Adoption Plan

`[ADD]` Ten sequenced steps, Iteration-0-through-Iteration-1 scoped:

1. **Install baseline UI library** — vendor shadcn/ui (pinned CLI version, Base UI primitive set) + Tailwind v4 into the Next.js project.
2. **Import/adapt dashboard shell** — build `AppShell` from `sidebar`+header/footer primitives; inspect (not import) `dashboard-01`/`shadcn-admin` for layout ideas only.
3. **Build component inventory** — vendor the ~19-component kit (§4) into `components/ui/` + `components/sepa/` (custom composites: `StatusChip`, `EvidenceDrawer`, `CommandButton`, `Timeline`).
4. **Map UI specs to components** — walk all 9 screens (§5) confirming each control in the UI specs has a named component home.
5. **Add shared `data-testid` wrapper convention** — bake the convention into the composite components once, so every consumer inherits it.
6. **Add accessibility review checklist** — one checklist per screen derived from each spec's §_.9, used in code review until the axe-core gate (step 9) is live.
7. **Build a lightweight component preview** — a minimal Storybook-equivalent (or a `/dev/components` route) if useful for isolated component review; skip if it doesn't earn its setup cost by Iteration 1.
8. **Implement first 3 screens** — Operations Control Room, Payments & Files, Payment Detail/Timeline (Iteration 1 spine), against the first-3-screens UI spec.
9. **Add Playwright smoke/role/data tests** — per the first-3-screens spec's §_.10 tables, plus the axe-core gate wired into CI.
10. **Run a UI review after the first 3 screens, before continuing** — check against §6's findings (state family used correctly, no optimistic UI crept in, empty≠unauthorized holds) before building screens 4–9.

---

## 10. Rejected Options

`[REJECT]`, explicitly, regardless of how they might be pitched later:

| Rejected | Why | Safer Alternative |
|---|---|---|
| Nuxt as a second frontend in the same app | different framework entirely; would fracture the Next.js BFF model (ADR-N3) | one Next.js app, full stop |
| React-admin as the whole operator console | resource/data-provider core fights the GraphQL-read + REST-command split | shadcn/ui + TanStack Table, composed per screen |
| GraphQL mutations | violates the frozen read-only GraphQL boundary | REST commands through the BFF |
| Template code that assumes browser token storage | violates ADR-N3 (no browser token) at the component level | BFF-only command model (§8) |
| Templates without accessibility or testability built in | would require retrofitting ARIA/keyboard/focus-trap badly | Radix/Base UI-backed shadcn primitives, which have this natively |
| Visual-only dashboards with no operator workflow | Control Room must be a triage board, not decoration | live tiles tied to real queue counts, not chart wallpaper |
| MUI/AntD/AG Grid as base (restated) | opinionated markup, not owned, heavier override burden | shadcn/ui + TanStack Table |
| Component-kit-as-npm-dependency (any) | breaks "own the code"; library updates can silently break `data-testid` selectors | vendored, copy-paste components only |

---

## 11. Required Corrections to Existing UI Specs

| Existing Document | Required Correction | Reason | Priority |
|---|---|---|---|
| `sepa-nexus-react-nextjs-frontend-blueprint.md` | Confirm §11a–§11g cite this document as their source decision (already done in a prior patch pass) | keeps the two documents from drifting on the same decision | `[SHOULD-FIX]` — verify only |
| `sepa-nexus-first-3-screens-ui-spec.md` | Payments & Files: add drag-drop upload zone + progress + invalid-file validation (already identified independently in the Playwright test-learning review, still unapplied) | file rail is under-designed relative to its real failure modes | `[MUST-FIX]` before Iteration-1 file-rail stories |
| `sepa-nexus-next-3-screens-ui-spec.md` | Reconciliation & Cases: add assignment-race/stale-conflict state; Egress & Delivery: change "View delivery receipt" to a download action (both already identified in the Playwright review, still unapplied) | real multi-operator hazard + audit-export need | `[SHOULD-FIX]` before the relevant P1 stories |
| `sepa-nexus-final-3-screens-ui-spec.md` | none | already consistent | — |
| `sepa-nexus-playwright-test-learning-business-development.md` | fix its own citation of this document (was pointing at a filename that didn't exist — corrected in this pass) | prevents the exact naming-drift this document itself was a victim of | `[DONE]` — fixed in this patch |

---

## 12. Final Decision

```text
DECISION: shadcn/ui (Base UI) + TanStack Table + Tailwind v4, vendored and owned
WHY: Maximizes the three properties this console exists to demonstrate — ownership, testability, and accessibility — without fighting an opinionated admin framework's data-provider assumptions or an unowned component kit's markup. Every requirement (R1–R10) is met without compromise, and the twelve self-review findings are all paper-fixable, not architectural.
CORRECTIONS_REQUIRED: yes — three items: (1) file-upload richness in Payments & Files [MUST-FIX before Iteration 1], (2) reconciliation assignment-race + egress receipt-download in the next-3-screens spec [SHOULD-FIX before the relevant P1 stories], (3) this document's own filename/content alignment [DONE — this patch].
NEXT: proceed to sepa-nexus-iteration-0-foundation-plan.md, which vendors this foundation (Adoption Plan steps 1–7) as explicit foundation tasks.
```

---

*End of frontend template and component adoption decision. `[NO-CODE]` — decisions, matrices, and mappings only; component vendoring, tokens, and config land in Iteration 0. Grounded in verified July-2026 sources (shadcn/ui Base-UI-default + CLI v4; TanStack Table headless v8/v9; Refine/React-admin as rejected bases). Consistent with ADR-N1…N8, the nine-screen UI specs, the frontend/Keycloak blueprints, and the BFF/SSE/GraphQL-read-only/role model.*
