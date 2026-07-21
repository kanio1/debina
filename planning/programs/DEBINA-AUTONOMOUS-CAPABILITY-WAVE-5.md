# Debina Autonomous Capability Wave 5

## Baseline and historical reconciliation

- Actual baseline: `fb5a9e0d904ad0d18a0cbe6fd283540e3ad965a6` on `main`.
- Wave 3 and Wave 4 routing work is reconciled as historical evidence and is not replayed or counted in this wave.
- `.agents/skills` remains the `../.claude/skills` discovery bridge; explicit governing skills are loaded as applicable. No implicit-routing claim is made.

## Candidate inventory and queue

| Story/capability | Initial readiness | Source-backed implementation boundary |
|---|---|---|
| EPIC-37/37.1 | READY | `NetDeferredStrategy` for the already-verified typed pair. |
| EPIC-37/37.2 + EPIC-34/34.1 | READY after 37.1 | one shared G6 cycle-membership capability. |
| EPIC-37/37.3 + EPIC-34/34.2 | READY after shared FSM | one shared SQL-netting capability. |
| EPIC-37/37.4 | READY after netting | `ON_CYCLE_SETTLED` only from a real SETTLED fact. |
| EPIC-55/55.1--55.4 | pending dependent audit | settlement-owned cutoff/cycle read boundary and routing outcomes. |
| EPIC-55/55.3 | pending independent audit | coarse read-only liquidity precheck contract. |

Primary queue: EPIC-37/37.1, shared EPIC-37/37.2 + EPIC-34/34.1, shared EPIC-37/37.3 + EPIC-34/34.2, EPIC-37/37.4, then promoted READY EPIC-55 stories.

Reserve queue: EPIC-38/38.1, EPIC-38/38.2, EPIC-40/40.2, EPIC-40/40.3 and EPIC-50/50.1 only after their independent readiness audits.

## Active sources and decisions

- `README.md`: frozen typed strategy, one-writer-per-schema, LedgerPort-only movement and separate finality axes.
- `ADR-N10`: immutable settlement finality authority and payment-owned projection.
- `ADR-N11`: restricted to the historical gross-instant transaction path; it is not generalized here.
- `sepa-nexus-message-flow-and-data-blueprint.md` §4.6/§4.10/§4.11/§8: cycle DDL, G6 lock, netting, cutoff/cycle read boundary and deferred finality.
- Current V9 has the static `reference_data.settlement_cutoff_calendar`; V32 has settlement's existing finality representation.

## Implementation and verification

- Class B ADR-N13 selects a separate deferred-cycle attempt representation rather than extending
  ADR-N11's RLS-protected gross-instant SECURITY DEFINER evidence. V51 is additive and creates
  cycle state, immutable items, deterministic positions and command receipts; V52 grants only
  settlement's read access to the reference-data cutoff catalog.
- Delivered once: EPIC-37/37.1; shared EPIC-37/37.2 + EPIC-34/34.1; shared
  EPIC-37/37.3 + EPIC-34/34.2; EPIC-37/37.4; EPIC-55/55.1. Shared stories count as two
  planning records but one capability/proof each.
- PostgreSQL 18 focused evidence: structural RED for absent `NetDeferredStrategy`; cycle suite
  `7/0/0`, focused strategy/finality/cycle suite `14/0/0`, V50-to-V52 upgrade `1/0/0`, and
  cutoff reader `1/0/0`. The state-guard mutation made the CLOSING-membership assertion fail and
  was immediately restored before GREEN.
- V51 fresh and V50-to-V52 upgrade prove routing V45--V50 evidence survives. Grant proofs show
  settlement role writes only settlement, has no ledger/payment write, routing has no cycle write,
  and PUBLIC has no new grant. No RLS was added to operational cycle tables.
- Kafka and ISO: N/A; no topic, producer, consumer, XML or lineage contract changed. Frontend/BFF:
  N/A; no frontend/BFF surface changed. Full backend regressions after final code change: `485/0/0`
  in 3:41 and `485/0/0` in 3:38.

### Database review

**PASS.** V51/V52 are forward-only additive migrations with no table rewrite/backfill. The new
objects are settlement-owned, use exact integer minor units, source-backed uniqueness/FKs and
least-privilege grants. Testcontainers covers fresh and V50 upgrade, grants/PUBLIC, G6 concurrency,
replay, rollback/no partial positions, zero sum and immutable finality evidence. No ledger/payment
write or unapproved SECURITY DEFINER surface was added.

## Remaining blockers

- EPIC-35/35.3 remains capability-blocked: no source-backed profile mapping to basis, liquidity mode, finality rule and reference-data version.
- Calendar rollover, automatic next-cycle creation, participant policy and external CSM semantics are Class C and will not be inferred.
- EPIC-55/55.2 is SOURCE-BLOCKED: no authoritative outcome precedence for cutoff/cycle-state disagreement.
  EPIC-55/55.3 is SOURCE-BLOCKED on its account/amount/result/consistency contract; 55.4 is
  CAPABILITY-BLOCKED by 55.2.

## Commits and next work

- `4c47158 feat(settlement): add deferred cycle finality slice` — ADR-N13, V51/V52,
  EPIC-37/37.1--37.4, shared EPIC-34/34.1--34.2, EPIC-55/55.1 and all recorded proof.
- No push occurred. Next highest-value work is a source-backed resolution of EPIC-55/55.2's
  cutoff/cycle disagreement policy; without it, 55.4 must remain blocked.

## Reserve audit and completion shape

| Candidate | Classification | Exact evidence |
|---|---|---|
| EPIC-38/38.1 | SOURCE-BLOCKED | §4.11 names internal posting/finality but defines neither an internal-account eligibility contract nor a distinct authoritative `ON_INTERNAL_BOOK_POST` source identity; reusing ADR-N11 would be an unapproved transaction change. |
| EPIC-38/38.2 | SOURCE-BLOCKED | File/cycle assignment has no file identity, intake, idempotency, signature or result policy; it cannot be inferred from EPIC-73. |
| EPIC-40/40.2 | SOURCE-BLOCKED | §4.11 explicitly says outcomes differ by basis/mode/cutoff but supplies no outcome matrix. |
| EPIC-40/40.3 | SOURCE-BLOCKED | No queue representation/selection/replay/cancellation contract; automatic next-cycle creation is expressly prohibited. |
| EPIC-50/50.1 | CAPABILITY-BLOCKED | Current egress state supports outbound-message claiming only; retry/DLQ persistence required by the story is absent. |

Wave 5 completion shape: **READY-EXHAUSTED**. Five independently counted capabilities were verified
(the two EPIC-34/37 pairs were correctly delivered and counted once); all remaining preferred and
reserve candidates have an exact source/capability blocker.
