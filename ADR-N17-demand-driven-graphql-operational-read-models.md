# ADR-N17 — Demand-Driven GraphQL Operational Read Models

## Status

Accepted; clarifies ADR-W9 without changing its decision.

## Context

Wave 9 established a thin, source-owned Query transport and its structural boundaries. The
repository now has operational GraphQL reads, but the broader admission and non-goals need a
stable policy so the adapter does not become a generic integration or persistence layer.

## Options

1. Let each UI concern expose generic CRUD/query capabilities — rejected: this hides ownership
   and encourages database-shaped APIs.
2. Add federation, subscriptions, generic filtering, and DataLoader pre-emptively — rejected:
   there is no demonstrated service, streaming, filtering, or measured N+1 need.
3. Admit an operational read only from a demonstrated user journey through a source-owned port —
   selected.

## Decision

GraphQL remains Query-only for operational read models; REST/gRPC own commands. The path is `user journey/view → source-owned read port → GraphQL schema → resolver → codegen → BFF`. Resolvers are thin, one resolver class/package per domain area, use a typed `AuthenticatedQueryContext`, and never access repositories. Operations are allowlisted; cursor pagination, depth/complexity limits and production introspection restriction remain required.

GraphQL suits composed, role-scoped operational views with stable user demand. REST remains appropriate for commands, simple resource downloads/health, and explicit bounded endpoint contracts; internal ports remain appropriate when no user journey needs transport. Prohibited: federation without demonstrated multi-service need, subscriptions without real streaming use case, generic filtering DSL, database-table-generated schema, generic CRUD GraphQL, and DataLoader before measured N+1.

## Consequences

- New GraphQL reads must identify the journey, source port, owner, authorization context, schema
  operation, code generation, BFF operation, and validation evidence.
- The policy preserves the existing Query-only, source-ownership, cursor, complexity, and
  introspection controls; it creates no field, resolver, module dependency, or migration.

## Lifecycle metadata

Confidence: high. Review trigger: measured need contradicting a prohibition. Affected use cases: operational reads. Quality attributes: security, modifiability, performance. Reversibility: adapter policy; does not alter source ownership.
