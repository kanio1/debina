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

## Invariants (must survive whichever mechanism is chosen)

| Invariant | Today | Required after Story 10.1 | Source | Degradation risk |
|---|---|---|---|---|
| Payment + ISO lineage both visible after a successful submit response | Guaranteed — one transaction | Not explicitly required by any source, but implied by current read-model code (see below) | inferred from `PaymentService.visiblePayments`/`paymentDetail` | An eventual-consistency window makes this false for some interval |
| Every payment has exactly one `ORIGINAL_INSTRUCTION` identifier | Guaranteed at write time | Same | `MissingPrimaryIdentifierException` javadoc, EPIC-21 Story 21.2 | A payment could exist without one during the lag window |
| `visiblePayments`/`paymentDetail` never 500 on a normal read | True today (every payment always has lineage) | **Not automatically true** — see below | current code, verified this session | A single lagging payment turns the WHOLE list request into a 500, not just one row |
| Idempotency completion reflects a fully-recorded submission | True — `idempotencyStore.complete()` runs after both `payment.*` and `iso.*` writes, same transaction | Same, if lineage stays synchronous; weaker if lineage becomes async | `PaymentService.submitPayment`/`Pain001PersistenceService.persist` | A replay could return a payment ID whose lineage isn't written yet |
| Signature/raw evidence survives a later rejection | True by design, deliberately not atomic with payment creation | Unchanged — this invariant was never coupled to payment atomicity | `Pain001IngestionService` javadoc | None — this is a *different* invariant from the ISO lineage one (see below) |
| One-writer-per-schema | Violated today (`sepa_app` writes both `payment.*` and `iso.*`) | Must hold | `sepa-nexus-blueprint-ownership-integration.md` §3.6.1 rule 1, `[FREEZE]` | N/A — this is the whole reason Story 10.1 exists |
| One transaction per command | True today | Preserved by `SECURITY DEFINER`; broken by outbox/eventual-consistency | inferred | Only the outbox variant degrades this |
| No cross-schema direct DML grants | Violated today | Must hold | same as above | N/A |

## Current read-model dependency on synchronous lineage (verified this session, not assumed)

- `PaymentService.visiblePayments` (`PaymentService.java:105-113`): calls `isoIdentifierLookup.findPrimaryEndToEndIds(...)` once for the whole tenant's payment list, then `requirePrimary(...)` **per row** — if even **one** payment in the list has no `ORIGINAL_INSTRUCTION` lineage row yet, `MissingPrimaryIdentifierException` is thrown and the **entire list request returns 500**, not a partial list with the lagging row omitted.
- `PaymentService.paymentDetail` (`PaymentService.java:123-133`): calls `isoIdentifierLookup.findPrimaryEndToEndId(paymentId)` directly — throws the same exception, mapped to `500 "Payment data integrity error"` (`PaymentProblemHandler.missingPrimaryIdentifier`), for that one payment's detail view.
- There is no `null`-tolerant DTO field, no list-level "skip incomplete rows" behavior, and no `LINEAGE_PENDING` status anywhere in the current code or any source document — this would have to be invented, which `[OPEN-QUESTION]` forbids doing unilaterally.
- **Conclusion**: today's code treats "payment exists" and "payment has lineage" as the same instant, everywhere it reads. Making lineage eventually-consistent without also changing this read code would turn a race condition into a routine 500 on the payment list any time a submit and a list-read interleave — this is a strictly worse outcome than the correction in §14 warned against assuming.

## `ingress.idempotency_keys` — the same problem generalizes beyond `iso`

`ingress.idempotency_keys` is a **third** schema written inside the same transaction (`IdempotencyStore.claim`/`.complete`, via `JdbcIdempotencyStore`), owned by the `ingress` bounded-context module per `CLAUDE.md`'s module map — today also written by `sepa_app`, same one-writer violation as `iso`. Whatever mechanism Story 10.1 picks for `iso-adapter` will face the identical question again the day `ingress` gets its own role — a fix that only works for `iso.*` (e.g. a hand-rolled, ISO-specific function or an ISO-specific outbox topic with no generalizable shape) would not be a real solution, just a deferral of the same problem. `SECURITY DEFINER`'s narrow-command-API shape generalizes trivially (one function per module per command); the outbox pattern already generalizes by construction (ADR-N5 is explicitly per-schema, not iso-specific). This does not change the recommendation below, but it is a reason to weight "does this generalize" in the decision, not just "does this work for iso".

## `SECURITY DEFINER` proof — results

Five new, permanent proof test classes (`backend/src/test/java/com/sepanexus/epic10/`, same convention as the `SET LOCAL ROLE` proofs — synthetic schemas/roles, never wired into production code), **18/18 PASS**:

**`SecurityDefinerPrivilegeProofTest`** (5/5): caller cannot write the iso-equivalent table directly (`42501`); the function owner can; an authorized caller can `EXECUTE`; an unauthorized/untrusted role cannot (`42501`); `PUBLIC` has no implicit `EXECUTE` (`has_function_privilege` check). **Found and documented a real, non-obvious Postgres detail**: `EXECUTE` on the function alone is not sufficient — the caller also needs `USAGE` on the schema just to resolve the qualified function name (`GRANT USAGE ON SCHEMA proof_iso TO proof_payment_role`, granted with no table-level grant alongside it).

**`SecurityDefinerTransactionProofTest`** (5/5): the function call shares the caller's `txid_current()` (same physical transaction, recorded from inside the function itself for proof); `current_user` is `proof_iso_role` **only** during the function's own execution and is back to `proof_payment_role` immediately after the call returns, still inside the same transaction (`session_user` never changes at all — confirms `SECURITY DEFINER` never touches session-level identity, only the current statement); commit persists both the caller's and the function's writes; a caller-side failure **after** the function already "succeeded" rolls back both; a failure **inside** the function rolls back the caller's writes too.

**`SecurityDefinerSearchPathProofTest`** (2/2) — the classic `SECURITY DEFINER` search_path hijack (CVE-2007-2138-class), executed for real, not just inspected via DDL: a function with `SET search_path = proof_iso, pg_temp` and a schema-qualified body ignores an attacker-planted `public.stamp()` function; a deliberately vulnerable twin with no `SET search_path` clause (inheriting the caller's own default `"$user", public` search_path) **does** get hijacked — the recorded value is literally `'HIJACKED'`, proving the vulnerability is real, not theoretical, and proving the defense (explicit, minimal `search_path`) actually prevents it.

**`SecurityDefinerPoolIsolationProofTest`** (4/4): `current_user`, `search_path`, and the tenant GUC are all back to the caller's own baseline after commit, after a caller-side rollback, and after a function-internal-failure rollback (the tenant GUC — `set_config(..., true)` — reverts to the empty string at the end of the transaction regardless of commit or rollback, consistent with the existing `TenantGucConfigurer`/RLS empty-GUC convention already used elsewhere in this codebase); two parallel connections never see each other's role or GUC state.

**`SecurityDefinerJpaFlushProofTest`** (2/2, real `PaymentEntity`/`PaymentRepository`, `@SpringBootTest`) — **the hazard from `SetLocalRoleJpaFlushProofTest` is absent**: `save()` a `PaymentEntity` with no explicit flush, call the `SECURITY DEFINER` function, commit — succeeds, because the caller's own `current_user` never actually changes for the function's duration (confirmed independently by the transaction proof above), so Hibernate's deferred flush at commit time still executes under the caller's own role regardless of what happened in between. The explicit-flush variant also succeeds, as expected — `flush()` is never *required* here purely for privilege reasons (it may still be needed for ordinary reasons unrelated to this decision, e.g. needing a generated value before a later query in the same transaction).

**Non-vacuousness** (packet §44): three of the six required mutations were executed and reverted — granting the caller a direct `INSERT` on the iso-equivalent table made `callerCannotDirectlyWriteIsoTable` fail as expected; omitting the `PUBLIC` `REVOKE` made `publicHasNoImplicitExecuteGrant` fail as expected; changing the function to `SECURITY INVOKER` made `authorizedCallerCanExecuteTheFunction` fail as expected (the caller has no direct grant, so invoker-rights execution hits the same `42501` a direct write would). The fourth ("remove secure search_path") is proven by construction — the vulnerable/safe pair already exists side by side in `SecurityDefinerSearchPathProofTest` and both branches execute for real. The fifth ("run function on a separate connection") does not apply to `SECURITY DEFINER` by construction — it is categorically a single-statement, same-connection mechanism, unlike the alternative (a literal second connection, which is what `signature`/`SET LOCAL ROLE`'s rejected alternatives would need); there is no code path to mutate. All temporary grant/security mutations were reverted; `grep -rn MUTATION-TEST-TEMP backend/src` → clean.

## Comparison: signature evidence vs. ISO lineage (packet §38 — do not conflate)

| Property | Signature evidence | ISO lineage |
|---|---|---|
| Must survive a later failure (mapping, idempotency conflict) | Yes — by design | No — the reverse: today it's atomic *with* the payment, and no source asks for it to survive a payment-creation failure |
| Required by the successful-payment read model | No — never read by `visiblePayments`/`paymentDetail` | **Yes** — `requirePrimary` throws if absent, verified this session |
| May exist without a `Payment` row | Yes — signature verdicts are recorded even for rejected messages before any payment exists | No — lineage rows reference an already-created `payment_id` |
| Immediate consistency required with payment creation | No | **Yes, today** — and no source has said this requirement changes |

This is exactly the correction packet §14 asked for: the two are not the same kind of durability requirement, and "signature already uses a separate connection" is not, by itself, evidence that ISO lineage should too. It remains true (see Recommendation below) that `SECURITY DEFINER` avoids ever having to answer this question at all — it doesn't weaken consistency, so it never needs the signature/ISO distinction to be resolved in one particular way.

## Outbox/eventual-consistency staleness analysis (packet §37)

| Moment | Payment row | ISO lineage | API list/detail | Recovery |
|---|---|---|---|---|
| Immediately after payment commit | Committed, visible | Not yet written | `paymentDetail` for this payment → `500` (current code, unless changed); `visiblePayments` → `500` for the **whole tenant's list** if this payment is included | N/A yet |
| Before the outbox consumer runs | Same as above | Still pending | Same as above | Depends on consumer poll interval — no source states a target |
| After the consumer successfully writes lineage | Committed | Committed | Both work normally | N/A |
| Consumer permanent failure (poison event) | Committed | **Never written** | Payment is permanently unreadable via list/detail unless the read code changes | No documented recovery — DLQ/retry pattern exists for Kafka inbox (`payment.inbox_events`) but nothing analogous is documented for an iso-lineage outbox consumer |

Answers to the packet's specific questions, where the sources actually answer them, and `[OPEN-QUESTION]` where they don't:
- What does `POST submit` return? — Today: `201` with the full payment, lineage already included by construction. Under outbox: still `201` (the payment write itself stays synchronous either way), but `[OPEN-QUESTION]` whether the response should signal "lineage pending."
- Can a payment be visible without `EndToEndId`? — `[OPEN-QUESTION]` — no source says; today's code says no (throws), which would need to change deliberately, not by accident.
- Does `paymentDetail` return `500`/`null`/pending? — Today: `500`. `[OPEN-QUESTION]` whether that's acceptable during the lag window or must become `null`/a pending marker — inventing `LINEAGE_PENDING` here would be exactly the kind of unilateral architecture decision `CLAUDE.md` forbids.
- Maximum acceptable staleness? — `[OPEN-QUESTION]` — not stated anywhere.
- Poison-event operator recovery? — `[OPEN-QUESTION]` — the existing DLQ pattern is documented for Kafka inbox events (`EPIC-27` Story 27.4, itself `not-started`), not for an iso-lineage outbox specifically.
- Does replay create a duplicate? — Not applicable to the outbox variant specifically; `Idempotency-Key` behavior is unchanged either way.
- Does idempotency completion happen before lineage? — Under today's synchronous model, no (both happen in the same transaction, order doesn't matter). Under outbox, **yes, necessarily** — `idempotencyStore.complete()` would have to run before lineage exists, meaning a replayed request could return a payment ID whose lineage isn't written yet, compounding the read-model gap above.

## Decision matrix v2

| Variant | One-writer | One TX | JPA safety | API consistency | Security surface | Recovery | Verdict |
|---|---|---|---|---|---|---|---|
| Shared writer (today) | No | Yes | Safe | Fully consistent | N/A | N/A | Not a candidate — the state Story 10.1 ends |
| `SET LOCAL ROLE` | Yes | Yes (proven) | **Hazardous** (proven) | Fully consistent | Grant-based only, no function surface | Same-transaction rollback, proven | Works, ongoing discipline burden |
| **`SECURITY DEFINER`** | Yes | Yes (proven) | **Safe** (proven — no hazard) | Fully consistent | New surface (function `EXECUTE`/`search_path`) — but fully provable and provably defensible, both demonstrated this session | Same-transaction rollback, proven, including function-internal failure | **Passes every gate in packet §40** |
| Outbox/eventual consistency | Yes | No (sequenced) | Safe (no shared session) | **Broken today's read code** — verified, not assumed (see above) | Safest (fully separate transactions/connections) | Matches ADR-N5/`signature` precedent, but no documented poison-event recovery for iso specifically | Architecturally consistent with `signature`, but requires a real read-model redesign first, not just a write-side change |

## Recommendation

**Recommend `SECURITY DEFINER`, `PROPOSED — REQUIRES USER ACCEPTANCE`** — it is the first variant in three proof sessions to satisfy every gate in packet §40 simultaneously: same physical transaction, atomic rollback in every direction tested (including function-internal failure), no JPA flush hazard (proven, not assumed), no mandatory `flush()` discipline burden, narrow `EXECUTE`-only grant with `PUBLIC` revoked, a demonstrated-and-defended `search_path` attack, a `NOLOGIN` non-superuser, non-`BYPASSRLS` function owner, and preserved GUC/pool state.

This **revises** the previous session's recommendation (outbox/eventual-consistency), for a concrete, evidence-based reason: that recommendation assumed — per packet §14's explicit warning against doing exactly this — that "`signature` already uses a separate connection" generalizes to "`iso` lineage should too." Actually checking the current read-model code (this session) shows it does not generalize safely: `visiblePayments`/`paymentDetail` require lineage synchronously today, with no pending-state handling anywhere, so moving to eventual consistency would introduce a real regression (list-wide `500`s) that the earlier memo did not check for. `SECURITY DEFINER` avoids this regression entirely because it never breaks the one-transaction guarantee the read model implicitly depends on — while still satisfying one-writer-per-schema.

The `signature`-precedent argument from the earlier recommendation is not wrong, it is *scoped too broadly*: `signature` evidence and ISO lineage have different durability requirements (see comparison table above), so the same mechanism is not obligated to serve both, and the proof results this session show a mechanism that doesn't have to choose.

## Risks not resolved by this memo

- `SECURITY DEFINER` functions are a genuinely new kind of object for this codebase to maintain (no precedent yet for CI/lint/review conventions around PL/pgSQL function bodies, migration authoring, or how `iso-adapter`'s Java persistence port should be shaped to call them) — packet §36's "narrow command API through a public port" is a design sketch, not implemented.
- `ingress.idempotency_keys` faces the identical one-writer problem and is not solved by this session — noted above as a generalization argument, not addressed in code.
- The exact set of `SECURITY DEFINER` functions `iso-adapter` would need (one for `JsonDirectLineageRecorder`'s three-table write, one for `Pain001LineageRecorder`'s, one for `IsoParseErrorRecorder`'s — which today runs non-transactionally by design and may not need this treatment at all) has not been designed.

## Conditions for starting Story 10.1

1. **User accepts `SECURITY DEFINER`** (or requests further proof/an alternative) — this memo recommends it but does not constitute acceptance.
2. Design the real narrow command-API surface for `iso-adapter` (packet §35/§36: no generic `execute_sql`/`insert_into`-style function; one function per real domain-persistence operation; called only through a public port owned by `iso-adapter`, never `PaymentRepository`/`PaymentController`/ad hoc `JdbcTemplate` calls scattered through `payment-lifecycle`).
3. Real redesign pass of `PaymentService.submitPayment`/`Pain001PersistenceService.persist` to call that port instead of `JsonDirectLineageRecorder`/`Pain001LineageRecorder` directly — the proof functions and tables are synthetic and prove the mechanism only.
4. Resolve the `ingress.idempotency_keys` generalization (condition, not blocker — could be a follow-up epic, but should be acknowledged before Story 10.1 is called "done").
5. Only after (1)-(3) should `payment/V21`+ introduce a real `iso_adapter_role`, the real functions, and revoke `sepa_app`'s grant on `iso.*`.
