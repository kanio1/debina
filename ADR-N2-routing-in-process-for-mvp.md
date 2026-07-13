# ADR-N2 — Routing Is an In-Process Modulith Module for MVP

## Status

Frozen

## Context

The document set contradicts itself on how `routing` is invoked. The Master Architecture, CPC-SP settlement-engine review, and the algorithm-research synthesis all describe routing as "the one out-of-process gRPC service" — the sole justified boundary crossing in an otherwise in-process Modulith. The main message-flow blueprint (§3.7 event table, §4.10 route-resolution pipeline) instead models `routing` as an in-process module that consumes `payment.validated` from Kafka and produces `payment.routed` — no gRPC call anywhere in the flow. These are two different systems, and the contradiction is the direct cause of G7 ("routing-down failure policy unspecified") never being resolved: a policy for a network seam that may not exist cannot be written correctly until the seam itself is decided.

## Decision

`[FREEZE]` For MVP (Iterations 0–5), `routing` is an **in-process Spring Modulith module** that consumes `payment.validated` and publishes `payment.routed` / `route.failed`, exactly as designed in blueprint §3.9/§4.10 (route-resolution pipeline ending at an immutable decision + explanation snapshot). No gRPC call is made to reach it. The out-of-process gRPC extraction of `routing` — the "≤1 out-of-process gRPC service" the architecture reserves as a ceiling — is deferred to a **P2 educational exercise**, undertaken only after MVP is stable, with its own G7 degraded-mode policy (`@Retryable` backoff, then a named deterministic queue-and-hold or static-default-route behaviour) written at the time of extraction, not before.

## Consequences

- Zero MVP consumers of gRPC/Protobuf. The `.proto` contract folder exists from Iteration 0 (gated in CI) but stays empty until the P2 extraction.
- G7 is dissolved for MVP: there is no network hop between `payment-lifecycle` and `routing` to fail, so no degraded-mode policy is needed until extraction happens.
- Routing tests are ordinary Modulith/ArchUnit boundary tests (no `routing`→`settlement`/`ledger`/`payment`/`egress` write) plus the routing test lab (decision tables, per-profile reachability, no-implicit-fallback, snapshot immutability) — no contract/backward-compat (buf) test surface in MVP.
- "≤1 out-of-process gRPC" remains true as a stated ceiling; it is simply not exercised until the extraction exercise, which is explicitly labelled `[P2]` `[EDU-VALUE]`, not a hidden scope cut.
- Any future gRPC extraction must land as a superseding ADR, not a silent scope change.

## Alternatives Rejected

- **Out-of-process gRPC `SelectRoute` service in MVP** — rejected: introduces a real network seam, ceremony, and an unresolved G7 policy onto the MVP critical path for a decision that adds no new business lesson before Iteration 5; contradicts the pipeline-not-engine design already frozen in §4.10.
- **Drop the gRPC ceiling entirely** — rejected: the one honest out-of-process boundary has real educational value (protobuf + contract testing) and is worth keeping as a named P2 exercise rather than deleting.
