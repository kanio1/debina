---
status: in-progress
depends_on: [EPIC-09]
source: "sepa-nexus-message-flow-and-data-blueprint.md §§3.5, 3.6.1-3.6.3, 4.7; sepa-nexus-blueprint-ownership-integration.md §3.6.1-3.6.5; sepa-nexus-keycloak-26-security-architecture-blueprint.md §§6, 9, 12-13"
---

# EPIC-77 — Evidence-audit: immutable application command log

The source-owned `evidence-audit` module owns the `audit` schema and append-only
application audit. It records application commands only; Keycloak login/admin events,
raw payment payloads, GraphQL, frontend and hash chaining remain out of this epic.

## Story 77.1 — Immutable audit persistence, ownership, RLS and grants

status: done
depends_on: [EPIC-09]

Description: Create only the source-required `audit.audit_log` table, its evidence-audit
module boundary and the narrow application-command record. Prove append-only grants,
tenant/branch RLS, empty-GUC default denial and the auditor's read-only override.

Completion criterion: isolated PostgreSQL 18 fresh and V54 upgrade proofs establish the
schema, ownership, canonical decision snapshots, direct-write denial and reader isolation.

Tasks:
- [x] **Add forward-only audit schema, append-only table, ownership roles and RLS policies.** V55-V57 create only `audit.audit_log`, the NOLOGIN function owner, reader/system roles and append-only grants; `evidence_records`/`payload_hashes` remain absent because they have no Wave 8 use case.
      `verify: ./mvnw -f backend test -Dtest=CommandAuditMigrationTest` → PASS (3 PostgreSQL 18 tests: fresh, V54→V57 upgrade, forced RLS, tenant/branch/empty context, auditor read-only override, canonical JSONB and direct mutation/function privilege denial).
- [x] **Expose evidence-audit's public typed append/query contracts without repositories or entities.** `CommandAuditPort`/DTOs are public, while JDBC/function infrastructure is internal.
      `verify: ./mvnw -f backend test -Dtest=CommandAuditArchitectureTest,ModularityTest` → PASS (public API and ApplicationModules verification).

## Story 77.2 — Same-transaction command audit and denied-command boundary

status: in-progress
depends_on: [Story 77.1]

Description: Implement the Class B accepted narrow append mechanism so successful commands append
audit rows on the caller's physical PostgreSQL transaction without granting payment-lifecycle
direct `audit.audit_log` DML. Implement the separate evidence-audit denial path for rejected
authorization attempts and its observable technical-failure behavior.

Completion criterion: transaction/connection identity, rollback, positive/negative grant and
denial-audit tests pass against PostgreSQL 18.

Tasks:
- [x] **Implement the typed successful-command `CommandAuditPort` through the accepted narrow boundary.** No `REQUIRES_NEW`, after-commit listener, event or second connection is used; controlled decision append failure rolls back state, outbox and idempotency.
      `verify: ./mvnw -f backend test -Dtest=ApprovalSubmissionIntegrationTest` → PASS (PostgreSQL 18 rollback/outbox/idempotency proof; dedicated physical transaction-identity test remains final-gate evidence).
- [x] **Implement the separate denied-command recorder and audit-safe failure contract.** It records trusted attempted identity and safe reason category without turning an audit failure into authorization; unavailable denial audit surfaces a technical failure with no mutation.
      `verify: ./mvnw -f backend test -Dtest=ApprovalSubmissionIntegrationTest` → PASS (forbidden attempt and controlled denial-audit failure proof).

## Story 77.3 — Internal audit query and integrity proof

status: not-started
depends_on: [Story 77.2]

Description: Provide the evidence-audit-owned, typed, read-only query boundary by correlation,
payment, batch, actor and tenant with deterministic cursor pagination. No HTTP, GraphQL or UI
adapter is introduced.

Completion criterion: ordinary tenant/branch callers and the narrow auditor context have the
source-required distinct, read-only views without entity/repository leakage.

Tasks:
- [ ] **Implement the internal query port and Testcontainers integrity/read-isolation proof.**
      `verify: ./mvnw -f backend test -Dtest=CommandAuditQueryIntegrationTest` → scoped query, auditor override and cursor proof passes.
