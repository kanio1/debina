# ADR-N1 — Iteration 0 Precedes Iteration 1

## Status

Frozen

## Context

The consolidated roadmap (Master Architecture & State §11) starts at "Iteration 1 — Spine" and folds platform-foundation work (Keycloak realm, RLS plumbing, CI gates, Testcontainers harness, compose stack, contract folders, Modulith skeleton) into the same iteration as the first business vertical slice. Only the HLD (Tier 0 / Phase 0 / TS-01…TS-17), which sits at the bottom of the truth hierarchy and is partially superseded, separates the two concerns. The ownership integration blueprint's own rule — "wire the four architecture gates into CI before the first domain table is written" — is unsatisfiable under the current roadmap, because there is no phase whose exit criteria is "the gates exist."

## Decision

`[FREEZE]` Iteration 0 ("Platform Skeleton / Foundation") is a mandatory, separate phase that precedes Iteration 1. It delivers the monorepo, Spring Modulith module stubs with `ApplicationModules.verify()` as a blocking CI gate, the Podman/compose stack (PostgreSQL 18, Kafka, Keycloak, OTel), Flyway folder-per-module scaffolding, contract folders (OpenAPI/AsyncAPI/GraphQL SDL), Testcontainers + Playwright frameworks, and the CI pipeline itself. Iteration 0 implements **zero business flow**. Iteration 1 begins only after Iteration 0's acceptance criteria are green, and is re-cut to a pure payment-spine vertical slice.

## Consequences

- The roadmap gains a phase with its own exit criteria; no domain table may be created before Iteration 0 is green.
- All later iterations depend on Iteration 0's CI gates (Modulith verify, ArchUnit pack, RLS two-token negative, empty-GUC-zero-rows) being live from day one.
- Iteration 1's scope shrinks to exactly the payment spine (ingress → idempotency → lifecycle → outbox → Kafka → read model → GraphQL → React timeline → 1 Playwright happy path), removing infrastructure concerns that previously inflated it.
- The HLD's Tier 0/Phase 0/TS-xx content is the source material for Iteration 0's task breakdown; the HLD itself is marked superseded elsewhere except for this material.

## Alternatives Rejected

- **Keep foundation work inside Iteration 1** (Master §11 as written) — rejected: hides infrastructure risk inside a business-value iteration, breaks the ownership integration's own CI-gates-first rule, and gives Iteration 1 no clean exit criteria.
- **No formal Iteration 0; treat foundation as "Sprint 1" ad hoc** (old HLD sprint plan) — rejected: superseded by the CPC-SP re-topology; sprint numbers no longer map to the current module set.
