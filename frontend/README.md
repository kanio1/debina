# frontend

Next.js BFF + React 19 UI serving the SEPA Nexus operator/reviewer workspaces.

## Node.js baseline (Iteration 0)

Node.js 24 LTS is pinned exactly to `24.18.0` in `.node-version` by `[USER-DECISION 2026-07-13]`.

## TypeScript version pin (Iteration 0)

`typescript-eslint`'s current peer dependency range is `typescript: '>=4.8.4 <6.1.0'` — it does not list `typescript@^7` and does not yet support TypeScript 7.0 GA (checked via `npm view typescript-eslint peerDependencies`, 2026-07-13). Per the documented fallback rule, TypeScript is pinned to the latest 5.x LTS, `5.9.3`, for Iteration 0. Revisit the TypeScript 7.x pin once `typescript-eslint` publishes a release supporting it.

`[PLANNING-DEFECT 2026-07-14]`: a later session-work-packet instruction asked for a "TypeScript 6.x exact pin". No stable TypeScript 6.x release exists — `npm view typescript versions` shows the registry goes straight from `5.9.3` (latest stable 5.x) to `6.0.0-beta`/dev builds and on to `7.x` dev builds; there was never a stable 6.0.0 GA. The pre-existing `5.9.3` fallback pin above remains correct and is unchanged.

## Package manager

pnpm only (pinned via `packageManager` in `package.json`). Do not create `package-lock.json`.

## Scaffold

Bootstrapped via `pnpm dlx create-next-app@16.2.10` (App Router, TypeScript, Tailwind v4, ESLint, `src/` dir) — EPIC-05 Story 5.1.

Get started:

```bash
pnpm install --frozen-lockfile
pnpm dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.
