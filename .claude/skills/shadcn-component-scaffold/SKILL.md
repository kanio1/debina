---
name: shadcn-component-scaffold
description: Use when adding a UI component via the shadcn/ui CLI or composing a new screen — component-foundation and data-testid conventions for this project.
---
# shadcn/ui + TanStack Table conventions (Iteration 0 scope)

1. Components are vendored (copy-paste via the shadcn CLI), never installed as an npm dependency — we own the code so `data-testid` never breaks on a library upgrade.
2. Every interactive element gets `data-testid="<workspace>.<entity>.<component>.<action-or-state>"` — e.g. `payments.list.submit-button`.
3. Tables use TanStack Table (headless) rendering real `<table>`/`<th scope>` — never a `<div>` grid.
4. No optimistic UI: a submitted form shows a pending state until the server confirms, never an immediate assumed-success row.
5. After any change: `npm run build` in `frontend/`, then manually click through the flow once in a browser — no Playwright check at this stage.
