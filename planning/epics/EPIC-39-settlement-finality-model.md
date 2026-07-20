---
status: done
depends_on: [EPIC-32-ledger-core/Story 32.2]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-5, line 1297), [MVP]"
---

# EPIC-39 — Settlement: model finalności (EPIC-SETTLE-5)

`[FREEZE]` accepted/posted/delivered ≠ final. Finalność to jawna, konfigurowana profilem reguła.

`[SAFETY-CORRECTION 2026-07-20]`: `payment` history no longer falsely derives finality from a
terminal business-FSM state; V30 corrects known `REJECTED`/`DISPATCHED` false positives and targeted
fresh/upgrade tests prove it. ADR-N10 now supplies the missing catalog, snapshot, record, projection,
idempotency and conflict authority. The approved synthetic-laboratory `ON_LEDGER_POST` path is
implemented and verified; catalog-only rules remain deliberately without fabricated triggers.

`[DONE 2026-07-20]`: ADR-N10 narrows this authority slice's real dependency to the verified
`LedgerPort` terminal-event capability (EPIC-32 Story 32.2), rather than the broader strategy
resolver epic. PostgreSQL 18 evidence is recorded in
`/tmp/FINALITY-TRUTH-AND-READINESS-PROGRAM/execution-plan.md` checkpoint K and the independent
V31–V34 review. No CSM, receipt, delivery, ISO-status or legal-finality behavior was added.

## Story 39.1 — Katalog `finality_rule`

status: done
depends_on: []

`[DONE 2026-07-20]`: V31 supplies the versioned catalog with exactly the four ADR-N10 laboratory
rule codes; fresh and V30→V34 PostgreSQL 18 migration proofs pass.

Taski:
- [x] **Migracja katalogu `reference_data.finality_rules`.**
      `verify: ./mvnw -f backend test -Dtest=FinalitySchemaMigrationTest,FinalityMigrationUpgradePathTest` → `4/0/0` PASS (2026-07-20).

## Story 39.2 — `FinalityPolicy` + `settlement_finality_records`

status: done
depends_on: [Story 39.1]

`[DONE 2026-07-20]`: `SettlementFinalityService` freezes an immutable snapshot and appends exactly
one authority record per payment using the catalogued `ON_LEDGER_POST` rule. Exact replay returns
the existing record; changed evidence or a different source fails closed. `PaymentFinalityPort`
projects only through the payment owner and settlement has no `payment.*` grant.

Taski:
- [x] **Policy selection, immutable snapshots/records and narrow payment projection.**
      `verify: ./mvnw -f backend test -Dtest=SettlementFinalityServiceTest,JdbcPaymentFinalityProjectionIntegrationTest,FinalitySchemaMigrationTest` → `10/0/0` PASS (2026-07-20).

## Story 39.3 — Test: accepted/posted ≠ final

status: done
depends_on: [Story 39.2]

`[DONE 2026-07-20]`: no authority record exists for a reservation/release or for a business/ISO
label; only the configured, real ledger `POST` event can establish laboratory finality. The
deprecated business-history `is_final` value remains false for business transitions.

Taski:
- [x] **No finality before the configured authoritative trigger.**
      `verify: ./mvnw -f backend test -Dtest=SettlementFinalityServiceTest,PaymentHistoryFreshMigrationFinalityTest,PaymentHistoryOwnershipTest` → `15/0/0` PASS (2026-07-20).

## Story 39.4 — Test: dostawa ≠ finalność

status: done
depends_on: [Story 39.2]

`[CYCLE-DETECTED, naprawione 2026-07-16 — dual-agent governance/backlog-redesign session]`: `depends_on` no longer references `EPIC-14-egress-boundary-ownership/Story 14.3`. That back-reference, combined with `EPIC-14` Story 14.3's own (unqualified, whole-epic) dependency on `EPIC-39`, formed a real cycle (39.4 → 14.3 → all of EPIC-39 → 39.4). This story and `EPIC-14` Story 14.3 assert the exact same thing (`DeliveredNotFinalTest`) — a shared-test pair, not a blocking dependency in either direction; whichever side is built first implements the shared class. See `planning/BACKLOG-REDESIGN.md` for the full writeup.

`[DONE 2026-07-20]`: `ACSC`, `DISPATCHED`, `DELIVERED` and receipt are not catalogued triggers;
`egress_role` retains zero payment-schema access. Delivery or transport cannot set or unset
finality.

Taski:
- [x] **Transport, delivery and receipt do not establish finality.**
      `verify: ./mvnw -f backend test -Dtest=SettlementFinalityServiceTest,EgressCannotWritePaymentStatusTest` → `12/0/0` PASS (2026-07-20; shared boundary evidence with EPIC-14 Story 14.3).
