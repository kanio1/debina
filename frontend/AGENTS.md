# AGENTS.md — frontend

Next.js 16.2.10+ BFF + React 19 UI (App Router), Node.js 24 LTS pinned exactly to `24.18.0`, TypeScript pinned exactly to `6.0.3`, Tailwind v4, shadcn/ui CLI v4 (Base UI, vendored components — never an npm dependency), TanStack Table. Full pin rationale: `frontend/README.md`.

## Package manager: pnpm only

`pnpm-lock.yaml` is canonical. Never run `npm install`/`npm run *` in this directory and never create `package-lock.json` or `yarn.lock` — no exceptions, no "if pnpm isn't present" fallback (it always is).

## Commands

After any change: `pnpm run build && pnpm run lint && pnpm run typecheck`.

## Conventions

- `data-testid` pattern, deep-link schema: `frontend/CONVENTIONS.md` — do not restate or fork these rules here.
- Accessibility checklist: `frontend/A11Y-CHECKLIST.md`.
- This Next.js major version (16.2.10+) has breaking changes vs. older training data — check `frontend/node_modules/next/dist/docs/` before writing App Router code, and heed deprecation notices.
- Server-side session (HttpOnly cookie); the browser never holds a Keycloak access/refresh token. `sepa-api` is unreachable directly from the browser. See `nextjs-bff-route` skill for the full BFF security contract.

## Playwright

Gated by capability, not by a hardcoded iteration number — see `planning/AGENTS.md` for the current gate. When it opens: TypeScript + Playwright, role-aware fixtures, business-level page objects — see `typescript6-playwright-engineering` skill.
