# Gross-instant transaction-coordination decision

## Status

`DECISION REQUIRED — no implementation authorization`

This is not an ADR and does not change ADR-N9, ADR-N10, or any frozen one-writer rule. It
records the source-backed result required before EPIC-33 Story 33.1 / EPIC-36 Story 36.1 can be
implemented.

## Source requirement and current fact

`sepa-nexus-message-flow-and-data-blueprint.md` §8 names `reserve→post→FINAL` "w jednej
transakcji" for the synthetic gross-instant path. ADR-N10 requires the dedicated `ledger_role`,
settlement-owned authority, a narrow payment-owned projection, and no settlement direct write to
either `ledger.*` or `payment.*`.

The current concrete implementations cannot satisfy that requirement as composed:

| Step | Current implementation | Role / connection | Commit boundary |
|---|---|---|---|
| reserve | `JdbcLedgerPort.reserve` | fresh `ledger_role` connection | commits internally |
| post | `JdbcLedgerPort.post` | fresh `ledger_role` connection | commits internally |
| authoritative finality | `SettlementFinalityService.recordAuthority` | fresh `settlement_role` connection | commits internally |
| payment projection | `JdbcPaymentFinalityProjection.project` | Spring `sepa_app` transaction | commits separately |

`GrossInstantTransactionBoundaryProofTest` is a PostgreSQL 18/Testcontainers proof of the actual
path: its success case records four different `txid_current()` values. Its fault injection makes
the payment projection fail after finality: exactly one ledger POST and one settlement finality
record remain committed while `payment.finality_record_id` remains null. This is a real recovery
window, not a single atomic transaction and not a safe basis for a class named
`GrossInstantStrategy` that claims otherwise.

## Independent evidence completed

- `GrossInstantTransactionBoundaryProofTest`: 2/0/0, distinct transaction IDs and the
  post/finality-to-projection crash window.
- `JdbcLedgerPortTest`, `JdbcLedgerPortConcurrencyTest`, `JdbcLedgerPortRollbackTest`, and
  `LedgerReservationMigrationProofTest`: reserve/post/release idempotency, concurrency, rollback,
  and append-only reservation proofs.
- `LedgerSchemaMigrationTest` + `LedgerIntegrityMigrationUpgradePathTest`, and
  `FinalitySchemaMigrationTest` + `FinalityMigrationUpgradePathTest`: fresh and upgrade-path
  PostgreSQL 18 migration evidence.
- `SettlementFinalityServiceTest` and `JdbcPaymentFinalityProjectionIntegrationTest`: authority
  replay/conflict handling, payment projection idempotency/conflict, and its own rollback.
- `LedgerSchemaOwnershipTest` and `SettlementRoleNoLedgerGrantTest`: one-writer grants hold;
  settlement has no ledger access.
- `ModularityTest` and `PaymentNoGodModuleTest`: module boundaries hold.

The consolidated direct Maven run completed `50/0/0` on 2026-07-20.

## Viable choices

1. **A new ADR authorizes a single PostgreSQL transaction using narrowly scoped `SECURITY DEFINER`
   command functions.** One coordinator connection would call ledger-owned reserve/post functions,
   write settlement-owned finality through a settlement-owned function, and invoke the
   payment-owned projection through a payment-owned function. Each function would have a
   non-login owner, schema-qualified body, explicit safe `search_path`, `PUBLIC` execute revoked,
   and a minimal executor grant. This preserves one writer per schema at the table-grant level and
   permits one physical transaction without giving settlement direct table grants. The existing
   `EPIC-10-transaction-coordination-decision-memo.md` contains PostgreSQL 18 proofs that this
   mechanism preserves one `txid`, rollback, pool isolation, and avoids the Hibernate flush hazard.

2. **A new ADR authorizes `SET LOCAL ROLE` on one connection.** This can preserve a PostgreSQL
   transaction, but the existing EPIC-10 proof shows a real Hibernate deferred-flush privilege
   hazard whenever roles change before commit. It would need an explicit, enforceable flush and
   role-switching contract. This is not recommended.

3. **A new ADR explicitly relaxes the source's one-transaction requirement to a durable saga or
   outbox recovery protocol.** This must name the durable command/attempt state, exact retry owner,
   crash recovery semantics, compensation/release rule, business/event outcome, and the visible
   status while a post is final but payment projection is incomplete. The current implementation is
   not such a protocol; calling its four commits a transaction would silently redefine the source.

4. **A coordinator role with direct DML grants or an XA/JTA manager.** Both are material new
   architecture. Direct cross-schema DML contradicts the frozen one-writer boundary; XA/JTA does
   not automatically enlist the current raw `DriverManager` connections. Neither is recommended.

## Recommendation and exact input required

Recommend **option 1**, but only through a new, superseding ADR (or explicit user approval that
authorizes that ADR-level mechanism). The decision must state:

- that the gross-instant command is one PostgreSQL transaction across ledger, settlement, and the
  payment finality projection;
- the exact owner, executor role, signature, input validation, immutable output, and failure
  contract of each ledger-, settlement-, and payment-owned command function;
- that settlement receives `EXECUTE` only, never ledger/payment table grants; `PUBLIC` receives no
  execute grant; and every function fixes `search_path` and uses schema-qualified references;
- how the existing `LedgerPort` and `PaymentFinalityPort` retain module ownership while joining the
  caller's single connection rather than opening and committing their own connections;
- the source-backed business/event outcome for successful post, insufficient liquidity, duplicate
  command, conflict, and recovery; and
- the required Testcontainers matrix: one `txid`, rollback at each step, same-command replay,
  concurrent calls, no duplicate money effect, grants, fresh/upgrade migrations, mutation proofs,
  and two clean backend regressions.

Until that input exists, EPIC-33/36 are decision-blocked. No `GrossInstantStrategy`, reverse,
scheme-profile mapping, CSM behavior, or compensating protocol has been implemented.
