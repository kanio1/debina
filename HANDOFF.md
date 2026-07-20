# HANDOFF

## Completed program

`FINALITY-TRUTH-AND-READINESS-PROGRAM` is complete on 2026-07-20. ADR-N9 and ADR-N10 remain
frozen and authoritative. No reset, restore, stash, clean, fetch, pull, rebase or push was used.

Local commits, in order:

- `3faa2db docs(adr): authorize finality and ledger reservations`
- `e4e7a75 feat(ledger): prove reservation lifecycle`
- `2f1a591 feat(settlement): add authoritative finality projection`

## Delivered and verified

- LedgerPort has durable V34 reservations, AVAILABLE/RESERVED journal components, typed
  insufficient liquidity, atomic reserve/post/release, same-command replay, deterministic locks
  and append-only journal behavior. PostgreSQL 18 fresh/upgrade, rollback, concurrency, ownership,
  grants and six reverted mutation proofs passed.
- Settlement owns finality authority: V31 catalog; immutable V32 snapshots and authority records;
  exact evidence/source replay and conflict rejection; `finality_at` from the real LedgerPort POST
  event; a narrow payment-owned projection port; no settlement direct payment/ledger write.
- The only executable rule is synthetic `ON_LEDGER_POST`. `ON_CYCLE_SETTLED`,
  `ON_NET_POSITION_SETTLED` (P1) and `ON_INTERNAL_BOOK_POST` are catalogued only. No CSM,
  certification, receipt/delivery/ISO-trigger or legal-finality claim was introduced.
- Payment status history remains compatibility data: business transitions do not write `is_final`
  true. Egress has zero payment write access; ACSC, DISPATCHED, DELIVERED and receipts cannot
  establish or remove finality.
- Independent V31–V34 review: PASS at
  `/tmp/FINALITY-TRUTH-AND-READINESS-PROGRAM/database-review-v31-v34.md`.
- Governance passed. Story inventory regenerated: 76 epics / 285 stories. Capability graph:
  144 nodes / 256 edges / zero cycles. EPIC-32 Story 32.2 is done; EPIC-39 is done for the approved
  synthetic authority scope. Story 32.4 remains SOURCE-BLOCKED on its separate reverse-command and
  narrow pre-finality read contract.
- Two required sequential full backend regressions passed from the unchanged worktree:
  `final-regression-5-sequential.log` and `final-regression-6-sequential.log`, each 412/0/0.
  Scans found only expected negative-test permission denial and controlled scheduler
  `BROKER_UNAVAILABLE`/`DATABASE_PERMISSION` warnings; no unexpected deadlock, connection, Kafka,
  transaction, rollback or async failure.
- Frontend/BFF checks: N/A; no frontend or BFF source/contract changed.

## Persistent constraints

- Do not reopen ADR-N9/N10 or broaden Debina into a real bank, CSM, payment hub, certified product,
  or production integration.
- Never derive finality from business status, ISO status, egress/delivery, receipt or transport.
- Settlement continues to reach money and payment only through `LedgerPort` and
  `PaymentFinalityPort`; no cross-schema write grant.
- Restore generated Javadoc from
  `/tmp/FINALITY-TRUTH-AND-READINESS-PROGRAM/javadoc-baseline.json` after Maven runs.

## Next work

Resume only a separately source-backed READY capability. Do not implement `reverse()` until a
separate source/ADR defines its command and pre-finality read boundary. Do not fabricate a trigger
for the catalog-only finality rules.
