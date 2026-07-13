# frontend

Next.js BFF + React 19 UI serving the SEPA Nexus operator/reviewer workspaces.

## TypeScript version pin (Iteration 0)

`typescript-eslint`'s current peer dependency range is `typescript: '>=4.8.4 <6.1.0'` — it does not list `typescript@^7` and does not yet support TypeScript 7.0 GA (checked via `npm view typescript-eslint peerDependencies`, 2026-07-13). Per the documented fallback rule, TypeScript is pinned to the latest 5.x LTS, `5.9.3`, for Iteration 0. Revisit the TypeScript 7.x pin once `typescript-eslint` publishes a release supporting it.
