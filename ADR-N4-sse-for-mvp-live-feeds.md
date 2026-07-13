# ADR-N4 — SSE for MVP Live Feeds

## Status

Frozen

## Context

The transport for live dashboard data (payment volumes, egress delivery counters, Kafka lag, SLA breach ticks) was left open as ADR "S14" in the comprehensive review: "pure SSE vs GraphQL-over-WebSocket subscriptions still open." The Master module map lists a `live-analytics` module (Designed ✅, Iteration 4) with no corresponding entry in the main blueprint's module ownership table (§3.6.2) — R-24 in the blueprint review. Both the transport choice and the module's home need to be settled together, since the transport decision determines what `live-analytics`/`reporting` actually builds.

## Decision

`[FREEZE]` **Server-Sent Events (SSE) is the MVP live-feed transport.** A single `reporting`-owned SSE endpoint streams read-model deltas (counters, lifecycle ticks, lag/latency gauges) to the browser; the Next.js BFF proxies the SSE connection server-side (consistent with ADR-N3 — no direct browser-to-backend streaming connection). GraphQL subscriptions are deferred; they are adopted only if a genuine bidirectional use case appears (none is currently named). The `live-analytics` module is folded into `reporting` (see also the ownership integration patch, §3.C) rather than kept as a separate module — it has no aggregate of its own, only a thin aggregation + streaming responsibility.

## Consequences

- One transport, one owner (`reporting`), one endpoint to test (SSE contract test + Playwright event-driven assertions, never `waitForTimeout`).
- Simpler than a GraphQL subscription server for MVP: no WebSocket lifecycle management, cache-friendly, works cleanly behind the BFF proxy.
- Dashboards (S-02 ops overview, live counters) assert on data received over the stream, never on rendering timing.
- `live-analytics` is removed as a standalone row from any future module ownership table; its responsibilities are explicitly listed under `reporting`.
- If a future P2 use case needs true bidirectionality (e.g. collaborative operator actions), a superseding ADR introduces GraphQL subscriptions at that point — not before.

## Alternatives Rejected

- **GraphQL-over-WebSocket subscriptions now** — rejected: no MVP use case needs bidirectionality; adds WebSocket lifecycle and subscription-resolver complexity for the same data an SSE stream already serves.
- **Keep `live-analytics` as a 21st module** — rejected: it has no owned aggregate or schema of its own in any patch; keeping it separate would violate the "every module has an owner and a boundary" discipline for no benefit.
