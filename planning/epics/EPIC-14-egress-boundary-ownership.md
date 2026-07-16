---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-43-egress-rail-outbound-dispatch]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-6, line 1262); sepa-nexus-blueprint-ownership-integration.md §9 (line 350, 'Egress confirmation & boundary')"
---

# EPIC-14 — Ownership: granica egress (EPIC-OWN-6)

`[FREEZE]` egress zarządza wyłącznie stanem transportu, nigdy finalnością.

## Story 14.1 — `CLAIMED` + `transport_attempts`/`delivery_receipts`

status: not-started
depends_on: [EPIC-46-egress-delivery-attempts-retry/Story 46.1, EPIC-47-egress-delivery-receipts-five-status/Story 47.1]

`[ADD MISSING EDGE 2026-07-16 — dependency-inventory deep-dive session]`: this story's own grant-test asserts `egress` is the sole writer of three tables — `outbound_messages`, `transport_attempts`, `delivery_receipts` — but only `outbound_messages` was reachable from the previously-declared dependencies (`EPIC-09`, `EPIC-43` at epic level). `transport_attempts` is created by `EPIC-46-egress-delivery-attempts-retry.md` Story 46.1's migration; `delivery_receipts` is created by `EPIC-47-egress-delivery-receipts-five-status.md` Story 47.1's migration. Neither was linked at any granularity before this session — a genuinely missing edge, not merely an over-broad one, so both are added at story level (not whole-epic) to avoid manufacturing a false epic-level cycle. Checked for cycles: `EPIC-46` Story 46.3 and `EPIC-45` Story 45.4 depend back on `EPIC-14` Story 14.2 (not 14.1), and both `Story 46.1`/`Story 47.1` have `depends_on: []` — no story-level cycle introduced.

Taski:
- [ ] **Grant-test: `egress` jedynym writerem `outbound_messages`/`transport_attempts`/`delivery_receipts`.**
      `verify: ./mvnw -f backend test -Dtest=*EgressSchemaOwnershipTest*`

## Story 14.2 — Test: egress nie pisze `payment.status`

status: not-started
depends_on: [Story 14.1]

Taski:
- [ ] **Grant-test: rola `egress` nie ma zapisu na `payment.payments`.**
      `verify: ./mvnw -f backend test -Dtest=*EgressCannotWritePaymentStatusTest*`

## Story 14.3 — Asercja delivered ≠ final

status: not-started
depends_on: [Story 14.1, EPIC-39-settlement-finality-model/Story 39.2]

`[CYCLE-DETECTED, naprawione 2026-07-16 — dual-agent governance/backlog-redesign session]`: `depends_on` narrowed from the whole `EPIC-39-settlement-finality-model` epic to `Story 39.2` specifically (`FinalityPolicy` + `settlement_finality_records` — the actual capability this story's assertion reads). The unqualified whole-epic reference, combined with `EPIC-39` Story 39.4's own `depends_on` pointing specifically back at this story, formed a real cycle (14.3 → all of EPIC-39 → 39.4 → 14.3) — the same shape as the `EPIC-31`/`EPIC-43` cycle (H1), found incidentally while building `planning/capability-graph.json`, not one of the ten named hypotheses. `Story 14.3` and `EPIC-39` Story 39.4 assert the exact same thing (`DeliveredNotFinalTest`, explicitly marked "współdzielony z EPIC-14" in `EPIC-39`'s own file) — they are a shared-test pair like `CycleCloseRaceTest` (EPIC-34/EPIC-37) or `SignatureBeforeParseOrderingTest` (EPIC-19/EPIC-31), not a blocking dependency of one on the other's completion. See `planning/BACKLOG-REDESIGN.md` for the full writeup.

Taski:
- [ ] **Test: dostarczenie (`DELIVERED`) nie zmienia `settlement_finality_records` — finalność ustawia wyłącznie `settlement`.**
      `verify: ./mvnw -f backend test -Dtest=*DeliveredNotFinalTest*` (współdzielony z `EPIC-39` Story 39.4 — jedna implementacja, dowolna strona może zbudować pierwsza).
