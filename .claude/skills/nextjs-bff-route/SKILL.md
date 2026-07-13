---
name: nextjs-bff-route
description: Use when adding a Next.js server route, middleware, or server action that talks to the backend REST API or handles the Keycloak session — BFF security conventions for this project.
---
# Next.js BFF conventions (Iteration 0 scope)

1. The browser never holds a Keycloak access/refresh token. The BFF holds the session server-side (HttpOnly, Secure, SameSite=Lax cookie); the browser only ever sees an opaque session cookie.
2. Every state-changing route requires a CSRF token validated server-side, per the existing security design.
3. Security headers (CSP, HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy) are set once, in `middleware.ts`, for every response — never per-route.
4. `sepa-api` (backend) is never reachable directly from the browser — CORS on `sepa-api` allows zero browser origins; only the BFF's Node process calls it, server-to-server.
5. Pin Next.js to `16.2.10` or newer — earlier 16.2.x has an unpatched middleware/proxy authorization-bypass advisory that directly undermines rule 1–3 above.
6. After any change: `npm run build && npm run lint` in `frontend/`, then manually confirm security headers with `curl -sI http://localhost:3000/ | grep -i content-security-policy`.
