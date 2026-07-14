# Accessibility Review Checklist (EPIC-23 Story 23.4)

Temporary, code-review-enforced checklist — stands in for the automated axe-core CI gate until that gate is wired (component-foundation blueprint §9, adoption-plan step 6→9; the gate itself is EPIC-24 scope, run against the first 3 screens). Every item below is taken from an existing `[ACCESSIBILITY]` block in the UI specs (`sepa-nexus-first-3-screens-ui-spec.md`) or from the component-foundation blueprint's §6 review findings — nothing here is a new rule invented for this checklist.

Use this per screen, at PR review time, until the axe-core gate exists.

## Structural

- [ ] Every data table is a real `<table>` (`<th scope="col">` headers, real `<tr>`/`<td>`) — never a `<div>` grid (frontend blueprint §11a `[FREEZE]`).
- [ ] Sortable columns expose `aria-sort`.
- [ ] Row links/actions are reachable by keyboard (tab order, `Enter`/`Space` activation), not click/hover-only.
- [ ] Tab bars use ARIA `tablist`/`tab`/`tabpanel` with arrow-key navigation and `aria-selected`.
- [ ] Lists (e.g. Control Room alert lists, Timeline) use `role="list"`/`listitem` or a real ordered/unordered list element.

## State handling (empty ≠ unauthorized)

- [ ] Loading, error, empty, and unauthorized states each use the shared state component family (`src/components/shared/screen-state.tsx`, Story 23.3) — never a hand-rolled per-screen variant.
- [ ] Empty state and unauthorized state are **visually and semantically distinct** — an empty-but-permitted view never reads as "you may not see this" (component-foundation §6 "Info-leak risk" finding, `[MUST-FIX]`).
- [ ] Every state node carries its own `data-testid` (`{screen}.{component}.{state}` — see `CONVENTIONS.md`) so a test can assert which state actually rendered, not infer it from absence of content.

## Status and color

- [ ] Status is always text **plus** icon — never color alone (e.g. `Healthy`/green-check, `Degraded`/amber-triangle, `Down`/red-octagon, each labelled with its word).
- [ ] Status chips are individually labelled and announced (e.g. `Business status: Settled`), not merged into one ambiguous chip.

## Keyboard and focus

- [ ] Every interactive element (card, button, link, row) is tab-focusable with a visible focus ring.
- [ ] Modals/drawers (Evidence drawer, submit/upload dialogs) trap focus while open and restore focus to the trigger on close.
- [ ] Confirmation dialogs are dismissible by `Esc`, except for mid-command states where cancellation would be unsafe.
- [ ] No control is drag-only — any drag interaction (e.g. a future file upload drop zone) has a real, keyboard-reachable button alternative behind a native input.

## Live regions and announcements

- [ ] SSE-driven counters/live updates use `aria-live="polite"` and never steal focus.
- [ ] Async feedback (upload progress, copy-to-clipboard success) is announced via a live region, not only shown visually.

## Forms

- [ ] Every form control has a associated `<label>` (via shadcn `Field`/`FieldLabel`, already wired for the Payments submit form).
- [ ] Format-hint text (e.g. UETR pattern, IBAN format) is wired via `aria-describedby`, not a floating hint with no programmatic association.

## Foundation guarantees (do not re-implement, verify they're intact)

These are provided by shadcn/ui + Base UI/Radix primitives (component-foundation §7) — the checklist item is to confirm the primitive was used, not rebuilt by hand:
- [ ] Dialogs/sheets/tabs/popovers use the vendored `components/ui/*` primitives (ARIA roles, keyboard interaction, focus-trapping already correct) rather than a custom `<div>`-based implementation.
- [ ] Tables use TanStack Table's headless engine for sort/filter/pagination state, not hand-rolled state management.
