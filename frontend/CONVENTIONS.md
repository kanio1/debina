# Frontend Conventions

EPIC-23 Story 23.2 — formalizing the pattern already used since EPIC-06 Story 6.4, not inventing a new one. Every convention below is derived from what the codebase already does (`git grep data-testid`) or from a frozen decision in `sepa-nexus-react-nextjs-frontend-blueprint.md` / `sepa-nexus-react-component-foundation-blueprint.md`.

## `data-testid` convention

Pattern: `data-testid="{screen}.{component}.{element-or-state}"`.

- `{screen}` — the workspace/page the element lives on (`payments`, `app-shell`).
- `{component}` — the composite component within that screen (`list`, `submit`, `sidebar`, `nav`).
- `{element-or-state}` — a specific interactive element (`submit-button`, `amount-input`) or a rendered state (`loading`, `error`, `empty`, `row`).

Existing examples in this codebase (`frontend/src/components/payments/payments-table.tsx`, `frontend/src/app/payments/page.tsx`, `frontend/src/components/app-shell/app-shell.tsx`):

```
payments.list.table
payments.list.loading / payments.list.error / payments.list.empty / payments.list.row
payments.submit.form / payments.submit.submit-button / payments.submit.end-to-end-id-input
app-shell.sidebar / app-shell.nav.payments / app-shell.current-user / app-shell.logout-link
```

Rules (component-foundation blueprint §6, "Test-id collisions" finding):
- A **dynamically-keyed** row test-id (e.g. a future `payments.list.row.<paymentId>`) is for targeting *one specific row*, never for count assertions — count/existence checks use the table testid plus row role (`payments.list.table` + `role=row`), never a loop over dynamic suffixes.
- State test-ids (`loading`/`error`/`empty`) belong to the shared state component (Story 23.3), not hand-rolled per screen — see `src/components/shared/screen-state.tsx`.
- Every new interactive element (button, input, link, nav item) added to any screen must carry a `data-testid` at the time it is written, not retrofitted later — this is a code-review checklist item (see `A11Y-CHECKLIST.md` for the parallel accessibility rule).

## Deep-link schema

`[ADOPT]` (frontend blueprint §11): one app, workspace segments as Next.js route groups, object detail pages as dynamic segments with tab sub-routes, list/queue screens carry filter/sort/page state in the URL (query params), so a filtered view is shareable and survives a refresh.

Concretely, for the one real workspace today:

```
/payments                  — list + submit (Workspace 2: Payments & Files)
/payments/[paymentId]      — detail (Story 24.2)
```

Future workspaces follow the same shape: `/{workspace}` for the list/queue, `/{workspace}/[id]` for detail, `/{workspace}/[id]/{tab}` for a detail sub-tab (e.g. a future `/payments/[paymentId]/lineage`). No query-param filter state exists yet (only one screen, no filterable list) — add it to the relevant screen's list view the first time a list needs filtering, not before (component-foundation §6 "Table state" finding).

## What this document does not decide

- The full 9-screen route map — that is EPIC-24's job, screen by screen, against the UI specs.
- Enforcement tooling (a custom ESLint rule scanning for interactive elements without `data-testid`) — not built here; today this is a code-review checklist item, consistent with how the accessibility checklist (Story 23.4) is handled until its own CI gate (axe-core, EPIC-24) exists.
