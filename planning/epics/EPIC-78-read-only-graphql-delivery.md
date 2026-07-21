---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants, EPIC-76-payment-approval-maker-checker]
source: "sepa-nexus-message-flow-and-data-blueprint.md §§3.6.5, 6.6, 7.2; sepa-nexus-blueprint-ownership-integration.md §3.6; sepa-nexus-keycloak-26-security-architecture-blueprint.md §§7, 9-10, 13; ADR-N3"
---

# EPIC-78 — Read-only GraphQL delivery

Source-derived Wave 9 owner for the GraphQL transport/runtime that was previously absent from the
catalog. Domain modules retain read-model ownership. The adapter may depend only on public,
module-owned query ports and DTOs; it owns neither data nor commands.

## Story 78.1 — Secured read-only Spring GraphQL foundation

status: in-progress
depends_on: [EPIC-09-ownership-schema-grants]

Taski:
- [ ] **Implement the fixed `/graphql` query transport, authentication, production introspection policy, and bounded depth/complexity.**
      `verify: ./mvnw -f backend test -Dtest=*GraphQL*Test*`

## Story 78.2 — Payment-owned approval GraphQL queries

status: not-started
depends_on: [Story 78.1, EPIC-76/Story 76.5]

Taski:
- [ ] **Expose paginated approval queue/detail DTOs only through the payment-owned public query port, with role and RLS proof.**
      `verify: ./mvnw -f backend test -Dtest=*Approval*GraphQL*Test*`

## Story 78.3 — Fixed-destination BFF GraphQL proxy and runtime contract

status: not-started
depends_on: [Story 78.2, EPIC-23/Story 23.1B]

Taski:
- [ ] **Proxy only fixed backend GraphQL queries through the server session and prove authentication, correlation propagation, and no token exposure.**
      `verify: cd frontend && pnpm run lint && pnpm run typecheck && pnpm run build`

## Story 78.4 — Conditional audit query exposure

status: blocked
depends_on: [EPIC-77/Story 77.3, Story 78.1]

`[RESERVE]` Audit query exposure is not required for the approval workspace and must not delay it.

