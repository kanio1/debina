---
status: in-progress
depends_on: [EPIC-32-ledger-core, EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-MONEY-2, line 1251), [MVP], Iteracja 2"
---

# EPIC-33 — Rozliczenie natychmiastowe (EPIC-MONEY-2)

## Story 33.1 — `GrossInstantStrategy` jedna transakcja

status: done
depends_on: []

`[DONE 2026-07-20]`: user-approved ADR-N11 freezes the dedicated executor and narrow module-owned
`SECURITY DEFINER` command functions. `GrossInstantOneTxFlowTest` proves on PostgreSQL 18/Testcontainers
one txid and backend PID across RESERVE, POST, ON_LEDGER_POST finality and payment projection; same-command
replay is duplicate-free and injected failures before/after every command boundary roll back all durable effects.

`[EVIDENCE EXPANDED 2026-07-20]`: the same PostgreSQL 18 fixture now proves concurrent identical
whole-transaction retry, concurrent conflict fail-closed, crossed-account deterministic locking and
cross-tenant RLS rollback. V34→V43 upgrade, function-security and mutation evidence are recorded in
`GROSS-INSTANT-ADR-N11-EXECUTION-EVIDENCE.md`.

Taski:
- [x] **Zaimplementuj `GrossInstantStrategy`: reserve→post→FINAL w jednej transakcji.**
      `verify: ./mvnw -f backend test -Dtest=*GrossInstantOneTxFlowTest*` → `9/0/0 PASS` (2026-07-20).

## Story 33.2 — Ścieżka niewystarczającej płynności

status: done
depends_on: [Story 33.1]

Taski:
- [x] **Test: odrzucenie przy niewystarczającej płynności, status RJCT, brak częściowego zapisu.**
      `verify: ./mvnw -f backend test -Dtest=*GrossInstantOneTxFlowTest*` → `9/0/0 PASS` (2026-07-20;
      `insufficientLiquidityIsAtomic_rejectsBusinessStatusAndCreatesNoMoneyOrFinality`).

## Story 33.3 — Timer SLA + zdarzenie breach

status: blocked
depends_on: [Story 33.1]

`[SOURCE-BLOCKED 2026-07-20]`: the blueprint says a gross-instant SLA breach emits
`payment.sla.breached`, but ADR-N8's authoritative AsyncAPI catalog defines no such topic,
payload, owner, key, or consumer. It also supplies no source-backed per-payment SLA threshold or
profile field from which a timer could be calculated. Creating an internal/Kafka event, timer
policy, or new status would invent a contract and risks conflating telemetry with business
rejection. Needed decision packet: source-approved event contract and timing-policy owner; no
payment/finality behavior is changed while blocked.

Taski:
- [ ] **Timer SLA + event breach.** `[SOURCE-BLOCKED]`
      `verify: ./mvnw -f backend test -Dtest=*SlaBreachTimerTest*` — NOT RUN; missing ADR-N8 contract.

## Story 33.4 — Atrybuty finalności/timeout

status: done
depends_on: [Story 33.1, EPIC-39-settlement-finality-model]

Opis: `revocation_cutoff`, `timeout_at` ≠ odrzucenie biznesowe.

**[DONE 2026-07-20]** Blueprint §4.3 directly names nullable
`payment.payments.timeout_at timestamptz(3)` and `revocation_cutoff timestamptz(3)`.
Migration `V44` adds only those attributes in the payment-owned schema; existing writer grants and
RLS remain unchanged. `FinalityTimeoutAttributesTest` proves both a fresh PostgreSQL 18 schema and
an upgrade from V43 retain `VALIDATED` with `finality_at IS NULL` while both timing facts are set.
An intentional `CHECK (timeout_at IS NULL)` mutation made both test paths fail and was restored.
Independent database review: **PASS** for source fidelity, ownership, type precision, additive
upgrade safety, and preservation of the five status axes. No SLA timer, revocation request/cutoff
policy, business status, or finality derivation is inferred by this slice.

Taski:
- [x] **Test: `timeout_at` i `revocation_cutoff` nie są tym samym co odrzucenie biznesowe.**
      `verify: ./mvnw -f backend test -Dtest=FinalityTimeoutAttributesTest` → `2/0/0 PASS`
      (2026-07-20; PostgreSQL 18 fresh + V43→V44 upgrade; mutation proof PASS).
