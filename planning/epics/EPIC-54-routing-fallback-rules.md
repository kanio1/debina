---
status: in-progress
depends_on: [EPIC-53-routing-decision-explanation]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-4, line 1288), [MVP]/[P1]"
---

# EPIC-54 — Routing: reguły fallback (EPIC-ROUTE-4)

## Story 54.1 — Jawne `profile_fallback_rules`

status: done
depends_on: []

Taski:
- [x] **Migracja `reference_data.profile_fallback_rules`.**
      `verify: ./mvnw -f backend test -Dtest=FallbackRuleCatalogMigrationTest,FallbackRuleCatalogMigrationUpgradePathTest` → `3/0/0 PASS` (2026-07-20). V49 gives the source-defined ordered rules a technical UUID while preserving `UNIQUE(profile_id, priority)`; `reference_data_role` remains sole writer, `routing_role` is read-only and `PUBLIC` has no table privilege. V48→V50 upgrade retains pre-existing non-fallback decisions.

## Story 54.2 — Wynik `FALLBACK_SELECTED`

status: done
depends_on: [Story 54.1]

Taski:
- [x] **Zaimplementuj wynik decyzji `FALLBACK_SELECTED`.**
      `verify: ./mvnw -f backend test -Dtest=FallbackSelectedOutcomeTest,FallbackDecisionEvidenceMigrationTest` → `7/0/0 PASS` (2026-07-20). ADR-N12 and V50 bind the source's UUID field to an explicit static rule. The routing-owned transactional recorder writes decision, candidates and fallback snapshot atomically with tenant RLS; failed child evidence rolls all three back, static rules remain read-only to routing, and no payment/settlement/ledger/ISO/egress row is written.

## Story 54.3 — Test: brak niejawnego fallbacku instant→batch

status: done
depends_on: [Story 54.2]

Taski:
- [x] **Test: brak niejawnego fallbacku z instant do batch — musi być jawną regułą.**
      `verify: ./mvnw -f backend test -Dtest=NoImplicitInstantToBatchFallbackTest,FallbackSelectedOutcomeTest,FallbackDecisionEvidenceMigrationTest` → `8/0/0 PASS` (2026-07-20). Only an exact explicit rule may select a fallback; no rule returns no selection. A selected non-null `condition` fails closed because no condition language is source-defined; no dynamic evaluation is introduced.

## Story 54.4 — Fallback wielo-CSM (`[P1]`)

status: blocked
depends_on: [Story 54.2]

`[SOURCE-BLOCKED 2026-07-20]`: §4.10 contains neither a profile-to-CSM representation nor
multi-CSM fallback semantics. ADR-N12 deliberately does not infer them from profile family or
settlement basis. Required evidence: an authoritative catalog and selection semantics.

Taski:
- [ ] **Fallback obejmujący wiele CSM.**
      `verify: ./mvnw -f backend test -Dtest=*MultiCsmFallbackTest*`
