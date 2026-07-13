# ADR-N3 — Next.js BFF Token Model

## Status

Frozen

## Context

The BFF-vs-SPA frontend token ADR (originally flagged as open item "S15" in the comprehensive architecture review) was recommended but never frozen: the master document's Keycloak adoption table lists "BFF as the default" as a suggestion, and the comprehensive review's Keycloak section reaches the same conclusion, but no document commits to it. Meanwhile no screen inventory, navigation model, or role→screen matrix exists at all — the frontend is the widest gap in the whole document set, and none of it is decomposable into stories without a frozen token model, because every screen's data-fetching and auth story depends on it.

## Decision

`[FREEZE]` **BFF is the default and only MVP token model.** The Next.js application holds a server-side session (HttpOnly, Secure cookie); the Next.js server exchanges the Keycloak authorization code for tokens and never exposes access/refresh tokens to the browser. All GraphQL reads and REST commands from the browser go through the Next.js server, which attaches the appropriate bearer token server-side. DPoP-bound SPA-with-token is documented as the alternative model for a future, explicitly-labelled educational track (teaching sender-constrained tokens), never the MVP default.

## Consequences

- Every frontend screen story assumes a server session; no token handling logic exists in browser-side code.
- Keycloak client configuration: `sepa-web` is a confidential client (Authorization Code + PKCE, server-side exchange), not a public SPA client.
- Codegen (OpenAPI → TS, GraphQL codegen) targets server-side fetchers; no client-side token refresh logic is built.
- The DPoP/SPA alternative remains available as a P2 teaching module if the lab wants to demonstrate sender-constrained tokens explicitly, but it does not block or compete with the BFF default.
- Unblocks EPIC-FE-0 (frontend foundation) and every subsequent screen story (§14 of the review).

## Alternatives Rejected

- **SPA-with-token as the default** — rejected: tokens in the browser widen the attack surface for no MVP-relevant lesson; Keycloak 26.x's DPoP capability is real but is a P1/P2 depth feature, not a default.
- **Leaving the ADR open "to decide later"** — rejected: every frontend story silently depends on this decision; leaving it open blocks all of §14, which is unacceptable given the frontend is already the lowest-scoring area in the review (4/10).
