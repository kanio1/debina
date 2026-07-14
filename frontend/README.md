# frontend

Next.js BFF + React 19 UI serving the SEPA Nexus operator/reviewer workspaces.

## Node.js baseline (Iteration 0)

Node.js 24 LTS is pinned exactly to `24.18.0` in `.node-version` by `[USER-DECISION 2026-07-13]`.

## TypeScript version pin

Pinned exactly to `6.0.3` per the project's frozen baseline ("TypeScript 6.x — exact pin"), applied 2026-07-14 once a stable, compatible 6.x release was confirmed to exist (see below).

`[PLANNING-DEFECT 2026-07-14, corrected]`: an earlier session claimed "no stable TypeScript 6.x release exists" and fell back to `5.9.3`. That claim was **wrong** — re-checked directly against the npm registry (`npm view typescript versions --json`, `npm view typescript dist-tags --json`): `6.0.2` and `6.0.3` are real, non-prerelease, published GA versions (distinct from `6.0.0-beta`/`6.0.0-dev.*`/`6.0.1-rc`, which are prereleases). The registry's `latest` dist-tag has simply already moved past the 6.x line to `7.0.2`, which is why an incomplete check (e.g. only reading the `latest` tag) would miss 6.0.3 entirely. Compatibility confirmed before pinning: `typescript-eslint@8.64.0` (the version actually resolved, and npm's current latest) declares `peerDependencies.typescript: ">=4.8.4 <6.1.0"` — `6.0.3` is squarely inside that range; `eslint-config-next@16.2.10` only requires `typescript >=3.3.1`; `next@16.2.10` has no `typescript` peer dependency at all. No breaking change in the [TypeScript 6.0 release notes](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-6-0.html) (removal of `moduleResolution: classic`, forced ESM interop) applies to this project's `tsconfig.json` (`moduleResolution: "bundler"`, `esModuleInterop: true` already). Full regression (`lint`/`typecheck`/`build`/`pnpm audit`) clean after the pin.

## Package manager

pnpm only (pinned via `packageManager` in `package.json`). Do not create `package-lock.json`.

## Environment

`.env.local` is git-ignored (it holds the dev-only Keycloak client secret) and is not part of a fresh clone. Copy the template before running the dev server or a local build:

```bash
cp .env.example .env.local
```

`.env.example` documents the required variables (`KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `BFF_BASE_URL`, `BACKEND_API_BASE_URL`) and their default local values, matching `infra/keycloak/realm-export.json`'s `sepa-web` client.

## Scaffold

Bootstrapped via `pnpm dlx create-next-app@16.2.10` (App Router, TypeScript, Tailwind v4, ESLint, `src/` dir) — EPIC-05 Story 5.1.

Get started:

```bash
cp .env.example .env.local   # first time only
pnpm install --frozen-lockfile
pnpm dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.
