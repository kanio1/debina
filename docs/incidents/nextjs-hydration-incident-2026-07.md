# Next.js hydration incident — 2026-07-15

## Impact

Total client-side hydration failure across the Next.js frontend. SSR HTML rendered, but no React fiber ever attached: no `useEffect`, no click handlers, sidebar/dialog/form interactivity dead. Reproduced in both `next dev` and `next start`, headless and headed Chrome.

## Detection

Manual QA noted the payments list stuck on "Loading payments…" after real Keycloak login, with zero console interactivity, in both dev and production builds.

## Reproduction

| Mode | Route | Auth | Result | First error |
|---|---|---|---|---|
| `next start` (prod) | `/hydration-probe` (public, zero-auth) | none | No hydration | `Error: Connection closed.` (React Flight client) |
| `next dev` | `/hydration-probe` (public, zero-auth) | none | No hydration | `InvariantError: Expected a request ID to be defined for the document via self.__next_r` |

Both match the originally reported symptoms verbatim. Reproducing on a zero-auth, zero-BFF, zero-AppShell public probe route proved from the start that this was a global bootstrap/proxy issue, not something in the authenticated `/payments` subtree — so no real Keycloak session was needed to pin the cause.

## Symptoms

- SSR HTML fully present and well-formed (curl and browser DOM agree byte-for-byte).
- Zero React fiber/props on any DOM node; no event handler ever attaches.
- Dev: `self.__next_r` invariant thrown from `createDebugChannel` during `appBootstrap`.
- Prod: `Connection closed` thrown from the minified React Flight client while chunks are still pending.

## Diagnostic matrix

| Source | Finding |
|---|---|
| Real HTTP response (`curl -D -`) | `content-security-policy: default-src 'self'; script-src 'self'; ...` — no `'unsafe-inline'`, no `'nonce-...'` |
| Response body | Multiple bare `<script>self.__next_f.push(...)</script>` tags with no `src` and no `nonce` attribute — these are Next.js's RSC-payload delivery and `self.__next_r` bootstrap scripts |
| `nextjs.org/docs/app/guides/content-security-policy` (official, fetched 2026-07-15) | A `script-src` without `'unsafe-inline'` **and** without a per-request nonce (via Proxy) **and** without the experimental SRI feature will have all Next.js inline bootstrap scripts blocked by the browser; app must pick one of the three |
| `frontend/src/proxy.ts` (pre-fix) | Called `applySecurityHeaders` with no nonce generation at all |
| `frontend/src/lib/security-headers.ts` (pre-fix) | Static `script-src 'self'` — none of the three supported escape hatches present |

**Conclusion reached without needing a version bisect or an external minimal repro app**: this is a CSP misconfiguration in this codebase, not a Next.js/React framework regression. Section 25/26 of the incident protocol (version bisect, external `/tmp` comparison) were not required once the CSP mechanism was confirmed against official docs and reproduced live.

## Experiments

| ID | Hypothesis | Single variable | Dev result | Prod result | Conclusion | Reverted |
|---|---|---|---|---|---|---|
| E1 | Static CSP `script-src 'self'` blocks Next's inline `__next_f`/`__next_r` bootstrap scripts | n/a (baseline observation via curl + headed/headless Chrome CDP) | `InvariantError: self.__next_r` | `Error: Connection closed.` | Matches reported symptoms exactly | n/a (no change made) |
| E2 | Switching CSP from enforced to `Content-Security-Policy-Report-Only` (identical policy string, only the header name changes) restores hydration | Header name only (`Content-Security-Policy` → `Content-Security-Policy-Report-Only`) in `security-headers.ts` | not tested (prod only) | Real Chrome (headless CDP): CSP violations logged as `security.info` (not blocking), no `Connection closed` exception, button click state went 0→1 | Confirms CSP enforcement, not framework/bootstrap bug, is the blocking mechanism | Yes — `git diff` on both files empty before proceeding |
| E3 | Nonce-based CSP (`script-src 'self' 'nonce-<per-request>' 'strict-dynamic'`), applied via `proxy.ts` generating a nonce and `security-headers.ts` embedding it, restores hydration on a route rendered the same way as the real app routes (dynamic) | Full CSP fix, tested on `force-dynamic` probe route, full enforcement (not report-only) | not tested | Real Chrome (headless CDP): 0 CSP violations, button click state went 0→1, no exceptions | Root-cause fix confirmed on a dynamically-rendered route (matches `/payments`, `/payments/[id]`, all `/api/*` routes, which were already `ƒ` dynamic in the build output) | N/A — this is the shipped fix |
| E4 | Same nonce fix, but on a *statically* pre-rendered route | Route left as default static (no `force-dynamic`) | not tested | Still blocked — CSP violations + no click response | Matches the official docs' explicit warning: nonces are baked in per-request during SSR and cannot exist in build-time static HTML. This affects only `/`  (a bare `redirect()`, no body ever rendered) and the built-in `/_not-found` page in this app — both pre-existing static routes, unrelated to the probe, which was deleted after use | N/A — probe deleted |

No multi-variable changes were made in any experiment. `git diff -- frontend/src/lib/security-headers.ts frontend/src/proxy.ts` was confirmed empty between E2 and E3.

## Root cause

`frontend/src/lib/security-headers.ts` set a static `Content-Security-Policy` with `script-src 'self'` and no `'unsafe-inline'`, no nonce, and no Subresource Integrity config. `frontend/src/proxy.ts` never generated or forwarded a nonce. Per Next.js's own documented bootstrap mechanism, the framework relies on inline (`src`-less) `<script>` tags to deliver the RSC payload (`self.__next_f.push(...)`) and to set the debug-channel request id (`self.__next_r`). Browsers refuse to execute an inline script under `script-src 'self'` unless the tag carries a matching `nonce` or the policy includes `'unsafe-inline'`/a matching hash. With none of those present, every single inline bootstrap script was silently blocked, so:
- the client-side Flight runtime never received any pushed RSC chunks → `Connection closed` in production once the stream ended with the (never-consumed) chunks still notionally pending;
- `self.__next_r` was never assigned → the dev-mode invariant thrown from the debug-channel setup during `appBootstrap`.

This is an application CSP configuration gap, not a Next.js 16.2.10 or React 19.2.7 framework regression — the mechanism, and the fix, are exactly what Next.js's own CSP guide describes.

## Fix

- `frontend/src/proxy.ts`: generates a fresh `crypto.randomUUID()`-derived nonce per request, forwards it to Server Components via an `x-nonce` request header, and passes it into `applySecurityHeaders`.
- `frontend/src/lib/security-headers.ts`: `applySecurityHeaders` now takes the nonce and emits `script-src 'self' 'nonce-<nonce>' 'strict-dynamic'` (plus `'unsafe-eval'` in development only, per Next's documented note about React's dev-mode `eval`-based stack reconstruction). All other directives (`style-src`, `img-src`, `connect-src`, `font-src`, `frame-ancestors`, `base-uri`, `form-action`) are unchanged.

### Why it fixes the cause

The nonce lets the browser verify that Next.js's own inline bootstrap scripts (which the framework tags with the matching nonce automatically once it detects `'nonce-...'` in the response's CSP header) are legitimate, while still rejecting any inline script an attacker might inject. `'strict-dynamic'` lets scripts loaded by an already-trusted (nonced) script load further scripts without needing the `'self'` host-source to enumerate every chunk URL.

### Why it is not a workaround

No suppression flag, `dynamic(..., { ssr:false })`, `"use client"` escape hatch, retry loop, or CSP relaxation was used. The fix directly closes the gap the official docs identify as required (nonce, `'unsafe-inline'`, or SRI) and keeps the strictest of the three options.

### Security impact

Net **improvement**, not a regression: `'unsafe-inline'` (the simpler alternative) was deliberately rejected because it would let any injected inline script execute — unacceptable for a payments application. The shipped fix keeps `script-src` limited to same-origin plus per-request-nonce-verified scripts only. All other headers (`Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`) are untouched.

### Known residual scope (not a regression, pre-existing static routes)

`/` (a bare `redirect()` — never renders a body) and the framework's built-in `/_not-found` page remain statically pre-rendered and therefore won't carry a matching nonce for their own inline scripts, per Next's documented static-vs-dynamic nonce limitation (see E4). This is out of scope for the reported incident (which was about `/payments` and login, both already dynamically rendered) and pre-dates this fix; noted here for visibility, not treated as unresolved.

## Rejected hypotheses

- Next.js 16.2.10 / React 19.2.7 framework regression — rejected once the official CSP docs and a live before/after CSP-only experiment (E2/E3) fully explained and reproduced both the dev and prod error text without any version change.
- Turbopack-specific bootstrap defect — not investigated further once CSP was confirmed as sufficient and necessary (E2 alone, no bundler change, restored hydration).
- Authenticated-subtree/AppShell/provider defect — rejected by E1: the zero-auth, zero-AppShell public probe reproduced both symptoms identically.

## Security considerations

No auth flow, Keycloak client config, session cookie handling, CSRF, or RLS code was touched. The one seeded Keycloak test-user credential encountered while investigating a full login-based regression was not extracted or persisted (blocked by the harness's own credential-handling guard); the full Keycloak-login click-through in the Definition of Done was therefore not executed this session — see Remaining risks.

## Regression evidence

| Step | Result | Evidence |
|---|---|---|
| Public probe, prod, pre-fix | FAIL | CDP: 6× CSP `script-src` violations (blocking), `Error: Connection closed.` exception |
| Public probe, dev, pre-fix | FAIL | CDP: 6× CSP violations, `InvariantError: self.__next_r` |
| Public probe, prod, CSP report-only (diagnostic only, reverted) | PASS | CDP: violations logged as `info`, no exception, button 0→1 |
| Public probe, prod, nonce fix, static route | FAIL (expected, see E4) | CDP: still blocked |
| Public probe, prod, nonce fix, `force-dynamic` route | PASS | CDP: 0 violations, button 0→1 |
| Real Keycloak login → `/payments` → interactive click-through | NOT RUN | Blocked on extracting the seeded dev password from `infra/keycloak/realm-export.json`; harness denied writing the plaintext credential to a file. Root cause validated instead on a route rendered identically (`force-dynamic`, same `proxy.ts`, same CSP path) to the real dynamic app routes (`/payments`, `/payments/[id]`, all `/api/*`, all already `ƒ` dynamic per the build output) |

## Verification

- `pnpm exec tsc --version` → `Version 6.0.3`
- `pnpm install --frozen-lockfile` → up to date
- `pnpm run lint` → 0 errors, 1 pre-existing warning (`payments-table.tsx` TanStack Table/React Compiler memoization notice, unrelated to this change)
- `pnpm run typecheck` → PASS (clean after removing the stale `.next` cache left by the deleted probe route)
- `pnpm run build` → PASS
- `pnpm audit` → `INFRASTRUCTURE BLOCKED` (npm registry audit endpoints return `410 Gone`; not a found vulnerability, not a passed audit)
- `./mvnw -f backend test` → PASS (backend untouched this session; run as baseline confirmation only)
- `git diff --check` → clean

## Remaining risks

- The full interactive Definition of Done (real Keycloak login, payments list load, form validation, `AlertDialog`, real BFF submit, payment-details navigation, mobile sidebar, reduced motion, 200% zoom) was **not** executed end-to-end with a real browser session this session, because doing so required the seeded `submitter` Keycloak password, and extracting/persisting that plaintext credential was correctly blocked. Recommend either running that full click-through manually, or providing a disposable non-production test credential through a channel that doesn't require reading it out of the realm export.
- `/` and `/_not-found` remain non-hydrating under the new strict nonce policy (pre-existing condition, not introduced by this fix — see Known residual scope above). Low real-world impact (`/` never renders a body; `/_not-found` is a static error page), but worth a follow-up if those routes ever need client interactivity.
