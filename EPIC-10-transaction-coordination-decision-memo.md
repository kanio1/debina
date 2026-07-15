# EPIC-10 — Transaction Coordination Decision Memo

## Status

`PROPOSED — REQUIRES USER DECISION`

This is not an ADR and not `[FREEZE]`. It documents a local, reversible proof executed in this session and a recommendation. It does not authorize starting `EPIC-10` Story 10.1 — see "Conditions for starting Story 10.1" at the end.

## Problem

`payment.payments`, `payment.outbox_events`, `payment.payment_status_history`, `payment.payment_events` (schema `payment`) and `iso.iso_messages`, `iso.payment_iso_identifiers`, `iso.message_lineage` (schema `iso`) are today all written by the same DB role, `sepa_app`, inside one Spring `@Transactional` method (`PaymentService.submitPayment` for JSON_DIRECT, `Pain001PersistenceService.persist` for pain.001) — one Postgres transaction, one physical connection. `EPIC-10` Story 10.1 requires `iso-adapter` to become the sole writer of `iso.*`, which necessarily removes `sepa_app`'s write grant on `iso.*`. No source document (`sepa-nexus-blueprint-ownership-integration.md` §3.6/§9, `sepa-nexus-message-flow-and-data-blueprint.md` §8, ADR-N2) says what mechanism replaces today's single-transaction write once that happens.

## Current transaction inventory

| Write | Schema | Class / method | JPA or JDBC | Transaction owner | Flush timing |
|---|---|---|---|---|---|
| `payment.payments` | payment | `PaymentCreationWriter.create` → `PaymentRepository.save` | JPA (Hibernate, `GenerationType.UUID`) | `PaymentService.submitPayment` / `Pain001PersistenceService.persist` (`@Transactional`) | Deferred — write-behind until flush/commit |
| `payment.outbox_events` | payment | `PaymentCreationWriter.create` → `OutboxEventRepository.save` | JPA | same | Deferred |
| `payment.payment_events` | payment | `PaymentHistoryRecorder.recordEvent` | JDBC (`JdbcTemplate`) | same | Immediate |
| `payment.payment_status_history` | payment | `PaymentHistoryRecorder.recordTransition` | JDBC | same | Immediate |
| `iso.iso_messages` / `iso.payment_iso_identifiers` / `iso.message_lineage` | iso | `JsonDirectLineageRecorder.record` / `Pain001LineageRecorder.record` | JDBC | **same** `@Transactional` as the `payment.*` writes above | Immediate |
| `ingress.idempotency_keys` | ingress | `IdempotencyStore.claim` / `.complete` | JDBC | same | Immediate |
| `iso.iso_message_parse_errors` | iso | `IsoParseErrorRecorder.record` | JDBC | **none** — called from `Pain001IngestionService.submit()`, deliberately not `@Transactional` | Immediate, auto-commits standalone, survives a later stage's failure by design |
| `signature.signature_keys` / `signature.message_signatures` / `signature.signature_verification_events` | signature | `JdbcKeyRegistryStore` / signature verification, via `SignatureConnectionFactory` | JDBC, **raw `DriverManager.getConnection`** — not the Spring-managed `DataSource` | **its own, separate physical connection** — never joins whatever Spring transaction is open in the caller | Immediate, auto-commits independently, always durable regardless of what happens afterward in `Pain001IngestionService` |

The `signature` row is the load-bearing fact for this decision: it is the one module today that **already has** a genuinely separate DB role (`signature_role`, confirmed via real, non-modified `infra_postgres_1` grants — `signature_role` has zero grants on `payment`/`iso`, `sepa_app` has zero grants on `signature`), and the codebase already had to answer "how does a payment-lifecycle-initiated call reach a separately-owned schema" for it. The answer it chose, in real shipped code, is: **a separate connection, sequenced before the atomic tail, explicitly non-rolled-back.** `Pain001IngestionService`'s own javadoc states this directly: "the archive/verify stages must durably persist raw evidence and the signature verdict even when a later stage... rejects the message — only the payment-creation tail... is one atomic unit."

## Real DB role inventory (read-only audit, `infra_postgres_1`, no grants modified)

| Role | LOGIN | SUPERUSER | BYPASSRLS | INHERIT | payment DML | iso DML | signature DML |
|---|---|---|---|---|---|---|---|
| `sepa_app` | t | f | f | t | INSERT/SELECT/UPDATE on all 5 tables | INSERT/SELECT/UPDATE on all 4 tables | none |
| `signature_role` | t | f | f | t | none | none | INSERT/SELECT(/UPDATE on `signature_keys`) |
| `sepa_migration` | t | t | t | t | (superuser, Flyway only) | (superuser, Flyway only) | (superuser, Flyway only) |

## Decision matrix

| Variant | One transaction | One-writer-per-schema | JPA safety | Pool safety | Complexity | Verdict |
|---|---|---|---|---|---|---|
| **Wspólny writer** (today's `sepa_app`) | yes | no — this is exactly what Story 10.1 removes | trivially safe (no role switch) | n/a | lowest | Not a candidate — it's the state Story 10.1 exists to end |
| **`SET LOCAL ROLE`** | yes (proven, see below) | yes, at the DML-grant level | **hazardous** — deferred Hibernate writes execute under whichever role is active at flush/commit, not the role active at `save()` (proven, see below) | safe — `SET LOCAL` auto-reverts at transaction end, no `RESET ROLE` needed, proven below | medium — every write site must reason about "what role is active right now" and force explicit flushes around JPA saves | Technically works; carries an ongoing, easy-to-reintroduce hazard |
| **`SECURITY DEFINER` functions** | yes | yes | avoids the flush hazard (writes happen inside a single SQL statement per call, no interleaved JPA state) | safe | adds a PL/pgSQL surface to maintain per cross-schema write | Not built or tested this session — flagged as an alternative worth a future proof, not implemented |
| **Two datasources + XA/JTA** | no | yes | n/a | adds a distributed-transaction manager and its own failure modes | highest | Not built — explicitly out of scope (packet §22) |
| **Outbox/eventual consistency** (already the established pattern for Kafka, ADR-N5) | no — two Postgres transactions, sequenced | yes | safe (no shared session at all) | safe | matches an already-frozen, already-implemented pattern (ADR-N5) | **Already the codebase's own precedent for exactly this shape of problem** (`signature`) |

## `SET LOCAL ROLE` proof — results

Two new, permanent proof tests (`backend/src/test/java/com/sepanexus/epic10/`, kept as documentation for this decision, not deleted — deliberately synthetic, never wired into production code):

**`SetLocalRoleSqlProofTest`** (pure JDBC, 7/7 PASS):
- Same `txid_current()` across three `SET LOCAL ROLE` switches inside one transaction — **same physical transaction confirmed.**
- A login role with `NOINHERIT` and no active `SET ROLE` cannot write either schema (`42501`) — least-privilege confirmed.
- `proof_payment_role` cannot write the iso-equivalent table and vice versa — isolation confirmed.
- An error after both roles have written triggers a rollback that undoes **both** roles' rows — one transaction, one all-or-nothing outcome, confirmed.
- `SET LOCAL ROLE` reverts automatically at both commit and rollback, on the same physical connection, with no explicit `RESET ROLE` — pool-safety confirmed.
- Two parallel connections do not leak role or GUC state into each other — confirmed (structurally guaranteed by Postgres's per-session state, not something `SET LOCAL` specifically has to get right).

**`SetLocalRoleJpaFlushProofTest`** (real `PaymentEntity`/`PaymentRepository`, `@SpringBootTest`, 2/2 PASS):
- `save()` a `PaymentEntity` while `proof_payment_role` is active, switch to `proof_iso_role`, write to an iso-equivalent table, let the transaction commit → **the deferred `payment.payments` INSERT executes at commit time, under `proof_iso_role`, which has no grant on `payment` at all → `42501`, whole transaction rolls back** (including the iso-equivalent row, which looked "already written").
- The same sequence with an explicit `entityManager.flush()` immediately after `save()`, before the role switch → succeeds, exactly one payment row committed.

This confirms the hazard the packet's §26 asked to check for is real, not hypothetical, and specific to interleaving JPA writes with role switches inside one transaction — not the same as the already-known, already-fixed FK-timing bug from earlier this session (that was about *foreign keys*; this is about *privileges*, and it fails silently far from the line that "caused" it unless every JPA save is manually flushed before any subsequent role switch).

## Recommendation

**Prefer the outbox/eventual-consistency pattern (already frozen, ADR-N5) over `SET LOCAL ROLE`**, for two reasons, not one:

1. `SET LOCAL ROLE` is proven *possible*, but its safety depends on every future developer remembering to `flush()` before every role switch, forever — a discipline burden with no compiler or test enforcement beyond "someone remembers." `SECURITY DEFINER` functions would remove that specific hazard but were not built or proven this session.
2. The codebase has **already made this exact choice once**, in shipped code, for the one module (`signature`) that already has a separate role: separate connection, sequenced not transactional, earlier stage durable regardless of later failure. Recommending `SET LOCAL ROLE` for `iso-adapter` would mean two different, inconsistent answers to the identical question ("how does payment-lifecycle reach a separately-owned schema") depending on which module you're looking at — `signature` one way, `iso-adapter` another. Consistency with the `signature` precedent is itself an argument, independent of the proof results.

The practical consequence, if this recommendation is accepted: the current "one HTTP request, payment + ISO lineage written atomically" guarantee would need to change shape — most likely to "archive/verify/map durably first (as pain.001 already does for signature), then the atomic payment-only tail; iso lineage recorded via the same outbox/inbox mechanism already used for Kafka, or synchronously against `iso.*` but no longer inside `payment-lifecycle`'s own transaction." This is a real behavior change to `PaymentService.submitPayment`/`Pain001PersistenceService.persist`, not a mechanical one — which is exactly why this memo stops short of implementing it.

## Risks not resolved by this memo

- Whether "iso lineage eventually consistent instead of atomic with payment creation" is acceptable given `EndToEndId` lineage is read by `PaymentService.visiblePayments`/`paymentDetail` (EPIC-21) — a payment could briefly exist with no lineage row. No source states an acceptable staleness window.
- Whether `SECURITY DEFINER` functions are worth a dedicated proof before ruling `SET LOCAL ROLE` out entirely — not attempted this session (packet explicitly scoped the proof to `SET LOCAL ROLE`).

## Conditions for starting Story 10.1

1. User decides between `SET LOCAL ROLE` (accepting the discipline burden, evidenced above) and the outbox/eventual-consistency pattern (accepting the atomicity/behavior change, evidenced above) — or requests a `SECURITY DEFINER` proof first.
2. Whichever is chosen, `PaymentService.submitPayment`/`Pain001PersistenceService.persist` need a real redesign pass, not a copy-paste of the proof code — the proof tables are synthetic and prove the mechanism only, not the production write order.
3. Only after (1)+(2) should `payment/V21` (or later) introduce a real `iso_adapter_role` and revoke `sepa_app`'s grant on `iso.*`.
