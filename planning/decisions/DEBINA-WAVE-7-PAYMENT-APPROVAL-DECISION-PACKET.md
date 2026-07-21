# Debina Wave 7 payment-approval decision packet

## D7-01 — Application command audit owner is absent

**Affected stories:** EPIC-76 Stories 76.3 and 76.4.  **Classification:** `CAPABILITY-BLOCKED`, not a permission to create a payment-owned audit substitute.

**Authoritative facts:** `sepa-nexus-keycloak-26-security-architecture-blueprint.md` §12 freezes an `audit_log` row for every command in the same transaction; decision commands include comment, before/after snapshots and payment/batch queryability.  `sepa-nexus-blueprint-ownership-integration.md` §3.6 assigns `audit.audit_log` to `evidence-audit` and allows other modules only via an audit port.  The checkout contains no `evidence-audit` module, `audit` schema, audit migration, port, capability node or epic/story owner.

**Options:**

1. Introduce a source-derived evidence-audit capability (schema, append-only audit port, RLS, narrow transactional use) and then use its public port from payment-lifecycle.
2. Store a payment-owned approval event/history/outbox record and call it audit.
3. Proceed without durable audit.

**Recommendation:** option 1.  Options 2 and 3 violate frozen ownership/audit evidence rules.  This is a capability-planning gap, not a new business decision; an owner should be derived from the accepted evidence-audit source before commands proceed.

**Exact input/source required:** an accepted source-derived owner/story for the narrow audit capability, or a team decision that selects an already existing epic to own it without changing the evidence-audit boundary.

**First executable action after resolution:** write a same-transaction audit-port RED proof for an approval decision, then implement the conditional payment approval transition and atomic outbox release.
