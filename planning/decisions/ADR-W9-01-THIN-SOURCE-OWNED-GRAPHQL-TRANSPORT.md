# ADR-W9-01 — Thin source-owned GraphQL transport

**Status:** Accepted (2026-07-21)

## Context

The frozen source assigns each read model to its source module and freezes GraphQL as read-only.
The planning catalog had enforcement (`EPIC-16`) and frontend codegen (`EPIC-23`) but no runtime
owner. Wave 9 needs the existing payment-owned approval queue externally without changing its owner.

## Options compared

| Option | Result |
|---|---|
| A. Resolvers colocated in every domain module | Valid in principle, but couples HTTP/GraphQL transport configuration into source modules and cannot establish one bounded operational endpoint for the first delivery. |
| B. Thin GraphQL transport depending only on public source query ports and DTOs | Accepted: preserves ownership, supports one secured endpoint, and gives ArchUnit/Modulith a narrow dependency boundary. |
| C. Reporting owns all GraphQL reads | Rejected: reporting cannot become a generic domain-query owner. |
| D. Transport reads repositories or foreign schemas directly | Rejected: violates source ownership and one-writer/module boundaries. |

## Decision

Create the `graphql` technical adapter as a thin Spring Modulith module. It may depend only on
public source-module query ports and DTOs. It has no repository, JDBC, native SQL, command-service,
or source-module reverse dependency. Schema has `Query` only; commands remain existing REST BFF
routes. Every sensitive fetcher uses method security and source query ports retain the existing
tenant/branch GUC/RLS path.

## Consequences

- The payment module owns approval queue/detail models and pagination.
- No migration is required for the primary Wave 9 path.
- Structural tests must reject a Mutation root, resolver-to-repository/command dependency, and a
  source module depending back on the adapter.

