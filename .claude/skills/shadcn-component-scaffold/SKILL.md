---
name: shadcn-component-scaffold
description: Use when adding a vendored shadcn component or Debina screen; preserve accessible semantic UI and BFF boundaries, and do not assume Playwright is either always allowed or always forbidden.
---
# shadcn component scaffolding

Use vendored components, semantic markup, accessible names/roles, deterministic `data-testid="<workspace>.<entity>.<component>.<action-or-state>"`, and real semantic tables. Include loading, error and empty states; never show fake optimistic success or bind selectors solely to styling classes.

Inspect current `frontend/` scripts and use `pnpm`, never generic npm commands. Deliberately separate Server and Client Components. The browser must not store Keycloak access tokens or bypass the authoritative BFF; never log tokens or sensitive payment data.

Before adding Playwright work, inspect EPIC-24 sequencing, capability gates, and whether the screen/workspace and backend capability exist. Classify it `READY`, blocked, or `N/A`; Playwright is capability-gated, not permanently prohibited.
