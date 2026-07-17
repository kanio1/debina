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

status: done

depends_on: [EPIC-43-egress-rail-outbound-dispatch/Story 43.1]

`[DEPENDENCY NARROWED 2026-07-17 — egress ownership train, Phase B]`: was `depends_on: [Story 14.1]`, which itself transitively requires `EPIC-46/Story 46.1` and `EPIC-47/Story 47.1` (both `not-started`) — but this story's own test, `EgressCannotWritePaymentStatusTest`, only needs `egress_role` (created by `EPIC-43` Story 43.1, `done`) and `payment.payments` (exists since Iteration 0) — it never touches `transport_attempts`/`delivery_receipts`, which are Story 14.1's own tables, not this story's. Confirmed via migration audit: `egress_role` has zero grants anywhere in `backend/src/main/resources/db/migration/payment/*` and no `GRANT USAGE ON SCHEMA payment` — default-deny already applies; this story only needs to prove that with a real Testcontainers negative-grant test, not wait for Story 14.1's unrelated tables.

`[DONE 2026-07-17]`: `payment.payments` has today exactly one status column (`status text CHECK (status IN ('RECEIVED','VALIDATED','REJECTED','DISPATCHED'))`) — no `finality_at` (also confirmed in the prior session, ledger train). Built `EgressCannotWritePaymentStatusTest`: `has_schema_privilege('egress_role','payment','USAGE')` = false (a pure grant signal, insensitive to RLS); `SELECT`/`INSERT`/`UPDATE(status)`/`DELETE`/`TRUNCATE` all denied `42501`.

`[RLS-versus-grant distinction]`: `payment.payments` has `FORCE ROW LEVEL SECURITY` (V4) — applies to EVERY role without `BYPASSRLS`, independent of table grants, and no test connection ever sets `app.tenant_id`. This means even a HYPOTHETICAL erroneous `INSERT`/`UPDATE` grant to `egress_role` would ALSO be blocked by RLS (`NULLIF(current_setting('app.tenant_id', true), '')::uuid` = `NULL`, never matches) — a second, independent defense layer, not a test weakness. To confirm the GRANT layer is proven independently of RLS, `egressRoleHasNoUsageGrantOnPaymentSchema` reads only privilege metadata (`has_schema_privilege`), which RLS never touches.

`6/6 PASS`. **Mutation-proof, 3/3 caught then reverted** (scratch migration `V29`, deleted after each mutation, never committed): (1) `egress_role` granted full `SELECT, UPDATE` on `payment.payments` → 3 tests FAIL (`egressRoleHasNoUsageGrantOnPaymentSchema`, `egressRoleCannotSelectPaymentPayments`, `egressRoleCannotUpdatePaymentStatusColumn`); (2) `egress_role` granted `INSERT` → `egressRoleHasNoUsageGrantOnPaymentSchema` FAIL (grant layer caught directly; the `INSERT` itself is additionally blocked by RLS, see above); (3) `egress_role` granted column-scoped `UPDATE(status)` only (no full `UPDATE`, no `INSERT`) → `egressRoleHasNoUsageGrantOnPaymentSchema` FAIL. `git diff --check` clean after each revert, scratch file deleted.

Taski:
- [x] **Grant-test: rola `egress` nie ma zapisu na `payment.payments`.**
      `verify: ./mvnw -f backend test -Dtest=*EgressCannotWritePaymentStatusTest*` → `Tests run: 6, Failures: 0, Errors: 0` — PASS (2026-07-17).

## Story 14.3 — Asercja delivered ≠ final

status: not-started
depends_on: [Story 14.1, EPIC-39-settlement-finality-model/Story 39.2]

`[CYCLE-DETECTED, naprawione 2026-07-16 — dual-agent governance/backlog-redesign session]`: `depends_on` narrowed from the whole `EPIC-39-settlement-finality-model` epic to `Story 39.2` specifically (`FinalityPolicy` + `settlement_finality_records` — the actual capability this story's assertion reads). The unqualified whole-epic reference, combined with `EPIC-39` Story 39.4's own `depends_on` pointing specifically back at this story, formed a real cycle (14.3 → all of EPIC-39 → 39.4 → 14.3) — the same shape as the `EPIC-31`/`EPIC-43` cycle (H1), found incidentally while building `planning/capability-graph.json`, not one of the ten named hypotheses. `Story 14.3` and `EPIC-39` Story 39.4 assert the exact same thing (`DeliveredNotFinalTest`, explicitly marked "współdzielony z EPIC-14" in `EPIC-39`'s own file) — they are a shared-test pair like `CycleCloseRaceTest` (EPIC-34/EPIC-37) or `SignatureBeforeParseOrderingTest` (EPIC-19/EPIC-31), not a blocking dependency of one on the other's completion. See `planning/BACKLOG-REDESIGN.md` for the full writeup.

Taski:
- [ ] **Test: dostarczenie (`DELIVERED`) nie zmienia `settlement_finality_records` — finalność ustawia wyłącznie `settlement`.**
      `verify: ./mvnw -f backend test -Dtest=*DeliveredNotFinalTest*` (współdzielony z `EPIC-39` Story 39.4 — jedna implementacja, dowolna strona może zbudować pierwsza).
