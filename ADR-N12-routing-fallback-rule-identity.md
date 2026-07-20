# ADR-N12 — Routing Fallback Rule Identity and Immutable Decision Link

## Status

Accepted — Class B technical decision, 2026-07-20.

## Context

The routing blueprint (§4.10) authorizes the static,
`reference_data`-owned `profile_fallback_rules` catalog with source uniqueness
`(profile_id, priority)`.  The same source defines immutable
`routing.route_decisions.fallback_rule_id uuid`, but does not give the fallback
catalog a UUID identity.  V48 implemented the route-decision column without a
binding foreign key.  A fallback decision must not retain an unbound UUID or
make mutable static configuration its only explanation.

The blueprint also defines `condition text` but no condition language or
vocabulary.  This decision cannot create EPC, CSM, participant, timing, or
payment-state semantics.

## Decision

`reference_data.profile_fallback_rules` receives a technical `id uuid` primary
key while retaining `UNIQUE (profile_id, priority)`, the source's ordered rule
identity.  `routing.route_decisions.fallback_rule_id` references that UUID with
`ON DELETE RESTRICT`; a fallback-applied decision must carry a rule identity
and a non-fallback decision must not carry one.  This is an additive,
forward-only migration: V48 rows with `fallback_applied=false` and a null rule
remain valid.

`reference_data_role` remains the sole static-catalog writer. `routing_role`
receives read and narrow `REFERENCES(id)` access only; it never writes
`reference_data.*`.  Routing continues to write only its own decision,
candidate and explanation evidence.  The existing immutable explanation JSON
must snapshot the selected rule's source profile, fallback profile, priority,
identity and condition-support verdict; the UUID link is an integrity link,
not a replacement for the snapshot.

The initial policy supports only rules whose `condition` is null.  It never
evaluates text as SQL, SpEL, JavaScript, or another dynamic language. A
would-be selected non-null condition fails closed and yields no fallback
selection. No configured rule yields no fallback selection. These are internal
technical outcomes, not new externally visible payment statuses. There is no
implicit instant-to-batch fallback.

## Decision matrix

| Criterion | UUID surrogate + source uniqueness (selected) | Composite components in decision | Versioned fallback snapshot table |
|---|---|---|---|
| Correctness / FK integrity | Direct FK from existing UUID column | Requires replacing the source UUID contract | Direct, but adds an unrequired model |
| Atomicity / failure recovery | Normal FK and route transaction | Components can drift without a row identity | Strong, but extra writes/version lifecycle |
| Security / least privilege | `routing_role` reads/references only | Read-only, but weaker referential proof | More grants and lifecycle surface |
| Source fidelity | Preserves ordered uniqueness and existing UUID decision field | Alters the source decision representation | Adds a source-unspecified catalog |
| Migration safety / reversibility | Additive V49/V50; V48 null rows survive | Requires route-table contract replacement | Additive but materially more complex |
| Operability / observability | Stable rule id in decision and snapshot | Multi-column correlation in every reader | Version-selection diagnostics required |
| Performance / complexity | One small catalog key and FK | Wider indexes and joins | Extra table/write path |
| Future rule versioning | UUID identity plus immutable decision snapshot leaves a clean future path | Snapshot components must later be translated | Prematurely decides a version lifecycle |
| Testability / project fit | Existing UUID/Flyway/RLS patterns | Custom composite adapter surface | New versioning behavior unsupported by source |

## Consequences

- V49 creates only the reference-data catalog; V50 binds its technical identity
  to the existing routing evidence boundary.
- Static configuration can be changed only by `reference_data_role`; decisions
  stay immutable and tenant-scoped through the existing routing RLS policies.
- Tests must prove fresh and V48 upgrade migration, role grants, public denial,
  source uniqueness, foreign-key integrity, immutable evidence, fail-closed
  conditions, explicit-only selection and atomic rollback of a failed evidence
  write.
- This decision adds no Kafka topic, no GraphQL/REST API, no payment mutation,
  no settlement action, and no finality behavior.

## Alternatives rejected

- **Keep an unbound `fallback_rule_id`** — rejected: it cannot prove that a
  selected fallback came from explicit configuration.
- **Copy composite components into `route_decisions` instead of a UUID link** —
  rejected: it conflicts with the source-defined UUID field and weakens direct
  referential integrity; explanation remains the immutable snapshot in either
  design.
- **Introduce a versioned immutable fallback-rule snapshot catalog now** —
  rejected: the source does not define a static-rule version lifecycle and the
  explanation snapshot already preserves decision-time evidence.
- **Evaluate `condition text` dynamically** — rejected: no condition language
  or rule vocabulary is authoritative.
