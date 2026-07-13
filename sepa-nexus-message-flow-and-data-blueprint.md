# SEPA Nexus — Message Flow & Data Blueprint (Inbound → Core → Outbound)

**Purpose.** The detailed design of how every core SEPA message is received, processed, stored, calculated, booked, cleared and settled — and how outbound transactions are collected and dispatched to banks / institutions / clearing houses / branches. Written to be directly decomposable into epics → stories → tasks (§8 provides the seed breakdown).
**Consistency.** Implements the Master Architecture (CPC-SP, 20 modules, PG18 baseline) and closes gaps G1–G5 from the Gap Register in concrete design.
**Markers:** `[FACT]` verified · `[DECISION]` binding choice · `[ALT]` considered alternative · `[EDU]` intentional learning surface.

---

## 1. Method Statement (how this document was produced)

Three complementary methods, applied in order — each produces the input for the next:

1. **Event Storming (process level)** — for the *business* truth: commands, domain events, policies, and read models per message flow. Output: the flow narratives in §2/§6 (orange = event, blue = command, purple = policy).
2. **Hexagonal Architecture (Ports & Adapters), mapped onto Spring** — for the *technical* truth: every channel is an adapter, the core speaks only canonical types, each port names its exact Spring mechanism (controller, listener, scheduled dispatcher, batch job). Output: §3 component model, §5 egress model.
3. **Access-Path-Driven Schema Design** — for the *database* truth: a senior PostgreSQL DBA designs tables from the queries, not from the entities. First list the hot access paths and their frequency/latency class, then shape tables, partitions, and indexes to serve exactly those paths. Output: §4.

Business lens (applied throughout, not as a separate section): each stage carries its **SLA class**, **who acts** (PSP, bank branch, operator, scheduler), and **what it costs when it fails** — because that is what turns a flow diagram into prioritized backlog.

---

## 1a. Domain Vocabulary Note `[ADD, domain-grounding]`

`[FREEZE]` **Standards vocabulary vs. our object names.** Our single canonical `payment.payments` aggregate corresponds to what SEPA/ISO 20022 literature calls, at different layers, a *payment order* (customer/operator intent) and a *payment instruction* (`CdtTrfTxInf`, the processed unit). We deliberately collapse these into one aggregate — the intent/instruction split is correct for a production bank and unnecessary for this educational platform. Grouping is expressed by `batch_id` (business/operational grouping; the SEPA-Clearer term is "bulk," which we do not adopt as an object name). The ISO *message* is never the business object: `egress.outbound_messages` (`OutboundMessage`) and `ingress` `InboundMessage` are the message envelopes, distinct from the payment. **"Interchange" is a card-scheme term and is never used here.** `ClearingSubmission` and `TransportFile` are recognized `[P1]` refinements (dispatch package and physical file), not MVP objects — their roles are covered in MVP by `egress` + `routing` and by `inbound_files`/`outbound_files` respectively.

---

## 2. Inbound: how each SEPA message arrives (Event-Stormed flows)

### 2.1 Channel decision matrix `[DECISION]`

REST vs queue vs file — settled per counterparty type, mirroring how the real world works (external parties never get our Kafka; Kafka is internal backbone only):

| Message | Real-world source | Our channel | Sync/async | Why |
|---|---|---|---|---|
| pain.001 (single) | Corporate/PSP initiates credit transfer | **REST** `POST /api/v1/payments` (JSON) or `POST /api/v1/iso/pain001` (XML) | Sync accept (202) → async processing | Initiators expect immediate accept/reject of *receipt*, not of settlement |
| pain.001 (batch file) | Corporate/gov/bank file drop | **File channel** (`POST /api/v1/files` multipart, SFTP-like semantics) | Async; result file later | KIR/EBA reality is file-in/file-out |
| pacs.008 (interbank CT) | Sending bank → us as CSM-side | **REST XML** (participant channel, signed) | Sync accept → async settle | Bank channels are signed request/response |
| pacs.002 (status from CSM) | CSM responds to our submission | **Internal Kafka** topic fed by the CSM simulator (`simulation`) `[EDU]`; a webhook adapter exists as an alternative profile | Async | Teaches out-of-order/duplicate handling on the status path |
| camt.056 / camt.029 / pacs.004 (R-messages) | Recall/resolution/return | Same participant REST-XML channel | Async | Post-MVP (`case`), channel already generic |
| Internal branch payment | Another Jednostka of same Uczestnik | **In-process REST** (same API, branch-scoped token) | Sync | Intra-bank never leaves the platform |

**The intermediate layer question, answered `[DECISION]`:** yes, there is a deliberate intermediate layer — the **Staging Pipeline** — between "bytes arrived" and "payment exists": `channel adapter → (verify signature on raw bytes) → raw archive → idempotency gate → canonical mapping → persist + outbox`, all before any business processing. It exists because the four guarantees (auditability of the original, exactly-once effect, XML safety, tenant/branch resolution) must hold *identically for every channel*, so they live once, between the adapters and the core — not inside either. `[ALT considered and rejected]`: direct controller→service→Kafka without staging (loses raw evidence + replays unsafely); an external ESB/integration bus (WSO2/Camel) — overkill for a Modulith lab and would hide the learning surface.

### 2.2 Event-Stormed flow: pain.001 single (REST JSON/XML)

```
[Command] SubmitPayment (PSP token, Idempotency-Key, body)
  → policy: authenticate (OIDC) → resolve tenant/branch → rate/size gate
  → policy: if XML channel: verify signature on RAW BYTES (G1) → then parse hardened (no DTD, entity limits)
  → [Event] RawMessageArchived (sha256, channel, tenant, branch)
  → policy: IdempotencyStore.putOrGet(source, key, request_hash)
       hit+same hash → replay stored response (202, same paymentId)   [Event] DuplicateSubmissionReplayed
       hit+diff hash → 409                                            [Event] IdempotencyConflictDetected
       miss → continue
  → iso-adapter: parse → select iso_message_version (message_type+scheme_profile+direction+business_date)
       → iso.iso_messages(direction=INBOUND, parse_status)                          [Event] iso.message.parsed
       → iso.iso_message_validation_results (XML_SCHEMA, ISO_STRUCTURAL, SCHEME_PROFILE, CANONICAL_MAPPING)
            any FAIL → iso.message.rejected + PaymentRejectedAtIngress (evidence kept, NO payment created)
       → iso.payment_iso_identifiers (MsgId, InstrId, EndToEndId, TxId, UETR, Orgnl*)
       → iso.message_lineage(lineage_role=ORIGINAL_INSTRUCTION, iso_message_id, raw_message_id, payment_id)
       -- [ownership] iso-adapter records the message trace + applies reason/status codes from reference_data,
       --   and makes NO business decision (no route, no reject-for-liquidity, no finality); business status
       --   is set by payment-lifecycle. (Direct JSON path uses the seeded JSON_DIRECT pseudo-version — §2.2a —
       --   so identifiers + lineage are recorded uniformly with every other channel; `iso_message_id` stays NOT NULL.)
       fail → 422 + reason codes                                      [Event] PaymentRejectedAtIngress
  → ONE DB TX: insert payments + status_history(RECEIVED) + audit + outbox(payment.received)
  → 202 {paymentId, status:RECEIVED}
  → relay publishes payment.received → payment-lifecycle consumes (inbox-dedup) → FSM advances
```
SLA class: accept < 300 ms p95 (excluding settlement). Failure cost: an initiator retry storm — which is exactly why idempotency is stage one, not an afterthought.

### 2.2a Direct JSON payments: `JSON_DIRECT` pseudo message-version `[ADD][ADR-N7][FREEZE]`

`[MVP-BLOCKER, closed]` The REST-JSON channel (§2.1) has no ISO message to parse, but §2.2's promise that identifiers and lineage are recorded for every channel must still hold — otherwise a JSON-submitted payment cannot be correlated the same way an XML-submitted one can, and `iso.payment_iso_identifiers`'s composite primary key `(payment_id, source_message_type, iso_message_id)` cannot accept a null `iso_message_id` without breaking the correlation model that fixed the original "ISO identity flattened into business state" bug (§4.3). The fix is a **seeded, synthetic ISO message-type/version pair**, not a schema exception:

- One row is seeded into `iso.iso_message_versions` at `message_type = 'JSON_DIRECT'`, pointing at a trivial pass-through `scheme_profile_id` / `validation_profile_code` / `mapping_profile_code`.
- Every JSON submission creates exactly **one** `iso.iso_messages` row: `direction='INBOUND'`, `message_type='JSON_DIRECT'`, `parse_status='SKIPPED'` (parsing was never needed — the JSON payload is already canonical-shaped), `raw_message_id` pointing at the archived raw JSON body.
- Identifiers extracted directly from the JSON body (`endToEndId`, etc.) are written into `iso.payment_iso_identifiers` **against that `iso_messages` row**, exactly as any other channel.
- One `iso.message_lineage` row is written with `lineage_role='ORIGINAL_INSTRUCTION'`.
- `iso_message_id` stays `NOT NULL` everywhere. **No nullable shortcut. No bypass of lineage.**

```text
POST /api/v1/payments (JSON)
  → raw archive (ingress.raw_inbound_messages)
  → idempotency gate
  → create iso.iso_messages(direction=INBOUND, message_type='JSON_DIRECT', parse_status='SKIPPED', raw_message_id=...)
  → extract identifiers directly from the JSON payload (no XML parse)
  → write iso.payment_iso_identifiers(iso_message_id = the JSON_DIRECT row, end_to_end_id, ...)
  → write iso.message_lineage(lineage_role='ORIGINAL_INSTRUCTION', iso_message_id=..., payment_id=...)
  → create canonical payment (payment.payments)
```

`[SYNTHETIC]` `JSON_DIRECT` makes no claim to be a real ISO 20022 message type — it exists purely to keep the lineage/correlation model uniform for a channel (REST-JSON PSP submission) that is real in this platform even though it has no ISO equivalent. Full rationale and the schema-weakening alternatives rejected: `ADR-N7`.

### 2.2b Four-Eyes Verification / Maker-Checker `[ADD, persona-driven][MVP]`

`[FREEZE]` **A fifth status axis, orthogonal to the four already frozen (business/settlement/egress/reconciliation) — approval status.** Never merged with business status; a payment can be `PENDING_APPROVAL` and have no business status yet at all, because the existing FSM has not started. This is a **prefix gate**, not a rewrite: it sits between "payment row + identifiers + lineage exist" (§2.2, unchanged) and "`payment.received` is published, FSM begins" (§2.2, unchanged). Below the Approval Matrix threshold, the gate is a no-op and every existing MVP flow (Iterations 1–3, simulation traffic) is untouched.

```text
[Command] SubmitPayment  →  (exactly as §2.2, through raw archive + idempotency + iso-adapter + payment row insert)
  → policy: ApprovalMatrix.evaluate(amount, payment_type, batch_size, tenant_policy, risk_signal)
       no match / below threshold → payment_approvals(status=NOT_REQUIRED) → publish payment.received immediately (today's behavior, unchanged)
       match → payment_approvals(status=PENDING_APPROVAL, maker_user_id, matrix_rule_id, expires_at=now()+24h)
                → 202 {paymentId, approvalStatus:PENDING_APPROVAL}   [payment.received NOT published yet — FSM has not started]
  → [Command] ApprovePayment / RejectPayment (checker_user_id, decision_comment)
       guard: checker_user_id != maker_user_id                                    [STRUCTURAL self-approval block, defense in depth beyond role separation]
       guard: caller holds `payment_approver`, not `payment_submitter`, for this tenant/branch
       approve → payment_approvals(status=APPROVED, decided_at) → publish payment.received → FSM begins exactly as §2.2
       reject  → payment_approvals(status=REJECTED, decided_at, decision_comment required) → terminal, payment never enters the FSM
  → [Scheduled, system-role] ExpiryChecker: PENDING_APPROVAL past expires_at → status=EXPIRED, terminal, same as reject
```

**Batch/bulk:** one `batch_id` group of `payment_approvals` rows; `ApproveBatch`/`RejectBatch` decides the group in one command (fast path — a checker approving a routine batch does not open every item). Item-level override remains available: a checker can pull one item out of the batch and reject/approve it individually before deciding the rest, without a full workflow-engine to model this — it is one more guard clause on the same command, not a second engine.

```sql
CREATE TABLE payment.payment_approvals (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  payment_id uuid NOT NULL REFERENCES payment.payments(id),
  batch_id uuid NULL,                                        -- groups N rows for one batch decision
  status text NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','CANCELLED','EXPIRED','NOT_REQUIRED')),
  maker_user_id text NOT NULL,
  checker_user_id text NULL,
  decision_comment text NULL,                                -- required on REJECTED
  matrix_rule_id uuid NULL REFERENCES reference_data.approval_matrix_rules(id),
  submitted_for_approval_at timestamptz NULL,
  decided_at timestamptz NULL,
  expires_at timestamptz NULL,
  CHECK (checker_user_id IS NULL OR checker_user_id != maker_user_id)             -- self-approval structurally impossible, not just convention
);
CREATE INDEX approval_queue_idx ON payment.payment_approvals(status, matrix_rule_id) WHERE status = 'PENDING_APPROVAL';  -- the checker's queue

CREATE TABLE reference_data.approval_matrix_rules (   -- reuses the existing versioned-catalog pattern (§4.13a), no new pattern
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  tenant_id uuid NOT NULL,
  min_amount numeric NULL, payment_type text NULL, max_batch_size int NULL, risk_level text NULL,
  requires_approval boolean NOT NULL DEFAULT true,
  requires_step_up boolean NOT NULL DEFAULT false,           -- feeds §2.2c step-up gate
  valid_from date NOT NULL, valid_to date NULL
);
```

`[DB-OWNERSHIP]` `payment.payment_approvals` is owned by `payment-lifecycle` (same schema as `payments` — it is a prefix on that module's own lifecycle, not a new module); `reference_data.approval_matrix_rules` is owned by `reference-data`, read-only elsewhere, exactly like every other profile/rule catalog in §4.13a.

**Idempotency reuse:** `ApprovePayment`/`RejectPayment`/`ApproveBatch` all carry an `Idempotency-Key` and go through the **same** `idempotency_keys` store as §2.2 — no second idempotency mechanism invented. A retried approve-click is a safe no-op, not a double-decision.

`[ADD, security-review]` **Two-checker race, stated explicitly (mirrors the reconciliation-exception assignment-race already frozen elsewhere in this document):** two different `payment_approver`s can open the same `PENDING_APPROVAL` payment and both act within the same window. The `payment_approvals.status` transition is the concurrency control — whichever `UPDATE ... WHERE status='PENDING_APPROVAL'` commits first wins and flips the row to `APPROVED`/`REJECTED`; the second `UPDATE` matches zero rows (status no longer `PENDING_APPROVAL`) and the handler returns a conflict, not a silent second decision. No advisory lock, no `SELECT ... FOR UPDATE SKIP LOCKED` needed — the same optimistic, status-transition pattern already frozen for reconciliation assignment, reapplied here rather than invented twice.

### 2.2c Approval-Matrix-Adjacent Controls: Limits/Velocity, Fraud/Risk Hold, VoP `[ADD, persona-driven]`

`[FREEZE]` **One decision surface, several inputs** — not four parallel engines. `ApprovalMatrix.evaluate()` (§2.2b) takes `risk_signal` as one of its inputs; the three mechanisms below are three ways that signal gets set. This keeps the system honest about "MVP, not a compliance engine": one evaluated gate, several deterministic, config-driven contributors.

- **Limits & Velocity** `[MVP]` — `reference_data.limit_policies` (daily user limit, tenant limit, single-payment limit, batch limit), checked at submission. A breach sets one of `risk_signal ∈ {NONE, REQUIRE_APPROVAL, REQUIRE_STEP_UP, BLOCK}` — feeding straight into the same matrix evaluation, not a separate block/allow path.
- **Fraud/Risk Hold** `[MVP, thin]` — `payment.fraud_holds(payment_id, rule_triggered, status ∈ {HELD_FOR_REVIEW, RELEASED, REJECTED, ESCALATED}, decided_by, decision_comment)`. Rules are simple and deterministic (high amount, new recipient, unusual country, unusual payment count, large batch) — **and are exactly the kind of thing `simulation`'s `failure_profiles` already inject deterministically elsewhere in this platform** (§4, ADR-N6); fraud-hold triggering reuses that existing injection mechanism rather than inventing a second one. `release`/`reject`/`escalate` are role-gated (`reconciliation_operator`/`operator` with `ops_senior`), same shape as an exception decision.
- **Verification of Payee (VoP)** `[CHANGE: P2→MVP, thin — regulatory grounding below]` — `risk.vop_checks` (already owned by `risk`, previously `[P2]`) is promoted to `[MVP]` for its **minimal check only**: call → result `∈ {MATCH, CLOSE_MATCH, NO_MATCH, UNAVAILABLE}` → `NO_MATCH` blocks submission by default; `override` requires `payment_approver` (or `ops_senior`) **and** writes an audit row in the same transaction as the override, never a bare status flip. The richer anomaly-scoring/corridor-stats side of `risk` (§3.6.2) stays `[P2]` — only the payee-name↔IBAN check itself is promoted. `[VERIFIED]` this mirrors the EU Instant Payments Regulation's real VoP scheme (Regulation (EU) 2024/886, Art. 5c, mandatory in the Eurozone since 9 October 2025): four-result vocabulary (match/close match/no match/other), sub-3-second SLA, payer-can-still-proceed-on-mismatch-with-warning semantics — the platform already used this exact vocabulary before this promotion, so the change is a priority correction, not a redesign.

**Explicitly not built** `[REJECT, matches decision-gate discipline]`: a general-purpose fraud ML model (rule-based only, deterministic, testable); a full BPMN approval workflow engine (one matrix, one gate, no engine); DORA-scale incident/resilience tooling (`[REFERENCE ONLY]` — DORA (Regulation (EU) 2022/2554) has been fully applicable since 17 January 2025 with 2026 as its enforcement-maturity year, but its register-of-information/third-party-oversight/board-accountability apparatus is genuinely out of scope for an educational MVP and was already `[DEFER]`red under `resilience-incident` in this project's own backlog — this section does not reopen that call).

### 2.2d Rail-Dependent Grouping Rule `[ADD, domain-grounding][FREEZE]`

**Batch rails (our file channel, pain.001) group many instructions into one dispatch unit; instant rails are strictly one payment = one message, never grouped.** This is not a stylistic choice — it mirrors the real EPC inter-PSP rule (SCT Inst inter-PSP messages carry exactly one transaction) and it is a **named Playwright test target**: the batch path (§2.3) exercises multi-item upload + partial-rejection + delayed status; the instant path exercises single-item + timeout + reservation/restore. Neither path's grouping behavior may leak into the other — a test asserting the file rail's `batch_id` grouping must never find an equivalent grouping construct on the instant path, and vice versa.

### 2.3 Event-Stormed flow: pain.001 batch file (the file rail, G5 closed)

```
[Command] SubmitFile (multipart, file signature, metadata)
  → verify FILE signature on raw bytes → archive file (bytes + sha256) → file-level idempotency:
       same sha256 seen → return original result-file reference (idempotent replay)
       same MsgId, different sha256 → reject DUPL                     [Event] DuplicateFileRejected
  → [Event] FileAccepted(fileId, expectedCount)
  → ASYNC file job (Spring Batch, §3.4): read → validate item → map item → write item
       per item OK  → staged payment row + outbox(payment.received)
       per item BAD → file_item row status=REJECTED + reason codes (job continues: SKIP policy)
  → [Event] FileProcessed(ok, rejected)                                [policy] item-level partial accept `[DECISION]`
  → policy: egress builds RESULT FILE (pain.002-style per-item statuses) → signs → delivers (§6.4)
```
SLA class: file accepted < 2 s; processing throughput target 500 items/s `[EDU]` (tunable chunk size is a lab dial). This flow is the KIR *zbiory wynikowe* pattern made executable.

### 2.4 Event-Stormed flow: pacs.002 status inbound (from CSM)

```
[Event in] csm.response.received (Kafka, key=payment_id or orgnl_msg_id; produced by simulation)
  → inbox gate: unique message_id → duplicate → iso.iso_message_correlation(status=IGNORED_DUPLICATE), no-op
  → iso-adapter: parse → iso.iso_messages(direction=INBOUND, type=pacs.002)         [Event] iso.message.parsed
  → CORRELATION (writes iso.iso_message_correlation), match order:
       1. OrgnlMsgId+OrgnlTxId   2. OrgnlMsgId+OrgnlInstrId+OrgnlEndToEndId   3. UETR (if unique)
       4. OrgnlMsgId+OrgnlEndToEndId   5. fallback E2E+amount+ccy+participant+time-window
     → exactly 1 → status=MATCHED (+score, matched_by)          [Event] iso.message.correlated (key=payment_id)
     → >1        → status=AMBIGUOUS → NO payment mutation → operator queue
     → 0         → status=ORPHANED  → [Event] iso.message.orphaned → DLQ/operator queue
  → payment-lifecycle (ONLY here): FSM policy on the MATCHED status
       weaker/late vs current state → ignore / late-transition / investigate
  → ONE DB TX: status_history (status_code+reason_code+source_iso_message_id) + payments.business_status_code
       + audit + (if final) outbox(payment.status.reported)
```
`[ARCH-RISK]` Binding split: `iso-adapter` decides *"this pacs.002 matches payment X"* (correlation); `payment-lifecycle` decides *"payment X may move to status Y"* (transition). The adapter never moves a payment out of `SETTLED`. This is the deliberately richest `[EDU]` surface: delayed, duplicated, out-of-order, ambiguous and orphaned statuses all land here, driven deterministically by the simulation seed.

### 2.5 What Spring answers for reception (summary; detail in §3)

| Stage | Spring mechanism |
|---|---|
| HTTP endpoints | Spring MVC `@RestController` (+ Framework 7 native API versioning for ISO SRU versions) |
| AuthN/Z | `spring-boot-starter-oauth2-resource-server` + custom `JwtAuthenticationConverter` (roles + tenant/branch/organization claims) |
| Signature-before-parse | Servlet `OncePerRequestFilter` ordered **before** any body deserialization; caches raw bytes for archive |
| XML hardening (G1) | Central `XMLInputFactory`/`DocumentBuilderFactory` config bean: DTD off, external entities off, expansion + size limits |
| Idempotency | `IdempotencyStore` port; **PG18 two-step is the MVP implementation** (§4.2); PG19 `ON CONFLICT DO SELECT` is a lab-only seam behind the same interface, not a near-term swap (§4.8) |
| Transactional staging | `@Transactional` application service per flow; Modulith `@ApplicationModuleListener` for in-process reactions |
| Outbox → Kafka | Poller `@Scheduled` dispatcher (visible, debuggable `[EDU]`) over each module's own `<schema>.outbox_events` (§4.4, `ADR-N5`); `KafkaTemplate` publish `[ALT: Modulith Event Externalization — noted, rejected for MVP to keep the mechanics visible]`. Topic ownership per §3.7 v2 (`ADR-N8`). |
| Kafka consumption | `@KafkaListener` (Spring Kafka 4.1) + inbox dedupe + `DefaultErrorHandler` → DLQ topics |
| File jobs | **Spring Batch 6** `[DECISION]` — chunked step, `SkipPolicy` for item-level partial accept, restartable, job repository = same PG (own schema) |

---

## 3. Core Processing: the Spring component model (Ports & Adapters)

One deployable (Modulith). Per module: its ports (interfaces the outside sees), its Spring beans, and its transaction boundaries. Naming below is binding for stories/tasks.

### 3.1 `ingress` (+ `signature`)
- `PaymentSubmissionController` (`/api/v1/payments`, `/api/v1/iso/pain001`), `FileSubmissionController` (`/api/v1/files`) — thin: validate shape, delegate.
- `RawBodyCachingFilter` → `MessageSignatureVerificationFilter` (bank/file channels only; verdict stored) → controller. Order pinned by `@Order`.
- `IngestionService` (`@Transactional`): archive → idempotency → canonical → persist → outbox. One method per flow (`ingestSingle`, `ingestFile`, `ingestInterbank`).
- Ports: `IdempotencyStore`, `RawMessageArchive`, `CanonicalMapper` (implemented by `iso-adapter`), `ClockPort` (G8 — the shared-kernel `Clock` bean; **no component calls `Instant.now()`**).

### 3.2 `payment-lifecycle`
- `PaymentEventsListener` (`@KafkaListener` on `payment.lifecycle`, inbox-gated) → `PaymentLifecycleService`.
- `TransitionTable` — the FSM as data: `Map<(State, Event), Transition(to, guards[], sideEffects[])>`; a forbidden pair throws `IllegalTransition` (metered). `[EDU]` prime unit-test surface.
- Optimistic locking via `@Version` on `Payment`; duplicate/late statuses resolved by the policy table from §2.4.
- Emits lifecycle events through the same outbox (module-local outbox writer, shared relay).

### 3.3 `settlement` + `ledger` (the money path)
- `SettlementOrchestrator` — reacts to `payment.routed`; freezes a `settlement_profile_snapshots` row; resolves the strategy via `SettlementStrategyResolver(settlement_basis, liquidity_mode)` — **never a switch on profile/CSM name** (§4.11).
- `GrossInstantStrategy`: ONE `@Transactional` unit → `LedgerPort.reserve(...)` → `post(...)` → `settlement_attempts` row → status out via outbox. Rejection path releases reservation in the same unit. SLA timer starts at ingress timestamp (from `ClockPort`).
- `NetDeferredStrategy`: assigns to open `settlement_cycle` **in the same TX that checks cycle state** (G6: membership decided under `SELECT … FOR UPDATE` on the cycle row or fails to next cycle when state=CLOSING).
- `CycleScheduler` (`@Scheduled`, cron from `reference-data` cut-off calendar, runs as **service-role identity** G3): `OPEN→CLOSING→CLOSED→NETTED→SETTLED`; netting = one SQL aggregation (§4.6); settlement = batch journal entries **via `LedgerPort`**.
- **`LedgerPort` is the only path from `settlement` to money** (`reserve/post/release/reverse`). `ledger` is the sole writer of `journal_entries`, `journal_lines`, `liquidity_accounts`, `balance_snapshots`, `ledger_reversals`; `settlement` never issues SQL against `ledger.*`. Enforced three ways: Spring Modulith `allowedDependencies` (no settlement→ledger repository), a DB grant test (`settlement_role` has no write on `ledger.*`), and an ArchUnit rule (no `ledger` repository referenced outside the `ledger` module). Corrections are `REVERSAL` entries through the port — never mutation (§4.5).

### 3.4 File processing (`ingress` × Spring Batch)
- `Pain001FileJob`: `FlatFileItemReader`-equivalent StAX reader (hardened factory) → `ItemProcessor` (validate + map) → `ItemWriter` (staged payments + outbox rows, chunk=100). `SkipPolicy` records rejected items to `inbound_file_items` with reason codes. Restartable (job repository in PG, schema `batch`). Job launch: async on file acceptance; re-launch idempotent by `fileId`.

### 3.5 Background identity (G3, binding rule — corrected for selective RLS, §4.7)
Every non-request component (`OutboxDispatcher`, `CycleScheduler`, `ReconciliationRunner`, Spring Batch jobs, `EgressDispatcher`) opens its DB work via a `SystemSessionInitializer`. What that means depends on which tables it touches (§4.7): on **RLS-protected tenant tables** (`payments`, `raw_inbound_messages`, `audit_log`), it sets `app.role='system_<name>'` GUC and a narrow `p_system_*` policy grants exactly the rows it needs. On **queue and ledger tables** (each module's own `<schema>.outbox_events`/`<schema>.inbox_events` per §4.4 `ADR-N5`, `journal_lines`, `liquidity_accounts`), there is **no RLS to satisfy** — the worker's DB role either has the `GRANT` or it doesn't, checked at connection time. The `OutboxDispatcher` specifically runs as `outbox_dispatcher_role`, which is granted `SELECT`/`UPDATE(published_at)` across every module's outbox table and **nothing else** — it has no grant on any domain table (§4.4). Either way, no background component may run against an RLS-protected table with a default (empty-GUC) session — enforced by a Testcontainers test that asserts zero visible rows on an uninitialized session against `payments`, and a separate test asserting the queue/ledger roles (including the dispatcher role) have exactly their intended grants (no more, no less).

### 3.6 Domain Model & Schema-per-Module Ownership

`[ADD]` SEPA Nexus is **one Spring Modulith deployable**, but every module owns its own business boundary, PostgreSQL schema, aggregate roots, repositories, migrations, event contracts and write permissions. This section is the ownership contract; it gates every migration and module.

#### 3.6.1 Ownership principle — ten binding rules

`[DB-OWNERSHIP]` Every table, aggregate, event, topic and read model has **exactly one owner**.
1. A module writes **only** to its own schema; cross-schema writes are an architecture violation `[REJECT]`.
2. Others read via domain port / query service / service view / read model / event projection — never a foreign repository or table.
3. `ledger` is the **only** writer of `journal_entries`, `journal_lines`, `liquidity_accounts`, `balance_snapshots`, `ledger_reversals`.
4. `settlement` requests ledger booking **only** through `LedgerPort` (`reserve/post/release/reverse`); never SQL against `ledger.*`.
5. `egress` owns transport/delivery state **only**; it never mutates `payments.*` or `settlement_*`.
6. `iso-adapter` owns message lineage/mapping **only**; it makes **no** business decision (no route, no reject-for-liquidity, no finality).
7. `reference-data` owns catalogs (status, reason, participants, calendars, service levels, scheme profiles) — never transactional state.
8. `risk`/`VoP` are **advisory-only** in MVP; they may not block or reject settlement.
9. `simulation` exercises the **same public ports and Kafka topics** as real traffic; never direct domain-table inserts.
10. **GraphQL is read-only** in MVP; the write path is REST/gRPC/command APIs.

`[ARCH-RISK]` Four violations to guard against: `payment-lifecycle` becoming a God Module; `settlement` writing `journal_lines` directly; `iso-adapter` embedding business logic; `egress` treating "delivered" as "final".

#### 3.6.2 Module → schema → tables → aggregates (with priority)

| Module | Owned schema | Owned tables | Aggregates | Priority |
|---|---|---|---|---|
| `ingress` | `ingress` | `raw_inbound_messages`, `idempotency_keys`, `inbound_files`, `inbound_file_items` | `InboundMessage`, `InboundFile`, `IdempotencyRecord` | `[MVP]` |
| `iso-adapter` | `iso` | `payment_iso_identifiers`, `iso_messages`, `message_lineage` | `IsoMessageLineage`, `IsoParsedMessage` | `[MVP]` |
| `payment-lifecycle` | `payment` | `payments`, `payment_status_history`, `payment_events` | `Payment`, `PaymentLifecycle` | `[MVP]` |
| `routing` | `routing` | `route_decisions`, `route_candidate_results`, `route_decision_explanations`, `participant_reachability`; `routing_profiles`/`route_rules`/`fallback_rules` (config co-owned w/ reference-data, §3.6.4) | `RouteDecision`, `RoutingProfile` | `[MVP]` decisions / `[P1]` config |
| `settlement` | `settlement` | `settlement_attempts`, `settlement_attempt_events`, `settlement_profile_snapshots`, `settlement_cycles`, `settlement_items`, `settlement_positions`, `settlement_queue_items`(P1), `settlement_finality_records`, `settlement_liquidity_checks` | `SettlementAttempt`, `SettlementCycle`, `SettlementPosition` | `[MVP]` |
| `ledger` | `ledger` | `liquidity_accounts`, `liquidity_reservations`, `journal_entries`, `journal_lines`, `balance_snapshots`, `ledger_reversals` | `LedgerAccount`, `JournalEntry` | `[MVP]` |
| `reconciliation` | `reconciliation` | `reconciliation_runs`, `reconciliation_run_sources`, `reconciliation_results`, `reconciliation_exceptions`, `exception_events`, `evidence_bundles`, `evidence_pointers`, `reconciliation_profiles_snapshot`; `action_requests`/`exception_comments`/`exception_assignments`(P1) | `ReconciliationRun`, `ReconciliationException`, `EvidenceBundle` | `[MVP]` core / `[P1]` egress-ISO-case recon |
| `case` | `case` | `cases`, `case_decisions`, `case_messages`, `case_return_links`, `case_status_history` | `Case`, `CaseDecision` | `[P1]` |
| `egress` | `egress` | `outbound_messages`, `outbound_files`, `outbound_artifacts`, `transport_attempts`, `delivery_receipts`, `transport_receipts_in`, `egress_profile_snapshots`, `outbound_artifact_lineage`, `manual_delivery_actions` | `OutboundMessage`, `DeliveryReceipt`, `OutboundArtifact` | `[MVP]` (files/receipts-in `[P1]`) |
| `reference-data` | `reference_data` | `participants`, `participant_accounts`, `iso_reason_codes`, `status_catalog`, `business_calendars`, `service_levels`, `scheme_profiles`, `settlement_cutoff_calendar` | `Participant`, `ReasonCodeCatalog`, `StatusCatalog` | `[MVP]` (calendars `[P1]`) |
| `risk` | `risk` | `vop_checks`, `fraud_signals`, `anomaly_windows`, `corridor_stats` | `VopCheck`, `RiskSignal` | `[P2]` |
| `simulation` | `simulation` | `simulation_scenarios`, `simulation_runs`, `failure_profiles`, `generated_events` | `SimulationScenario`, `SimulationRun` | `[P1]` `[EDU-ONLY]` |
| `reporting` | `reporting` | `statement_headers`, `statement_lines`, `dashboard_snapshots` | `Statement`, `StatementLine` | `[P2]` |
| `security` | `security` | `tenants`, `branches`, `service_roles`, `role_permissions` | `SecurityPrincipal`, `ServiceRole` | `[MVP]` |
| `evidence-audit` | `evidence`, `audit` | `evidence_records`, `audit_log`, `payload_hashes` | `EvidenceRecord`, `AuditEntry` | `[MVP]` |
| `batch` | `batch` | Spring Batch metadata | `BatchJobRun` | `[MVP]` |

`[DEFER]` **One-writer-per-schema**, and schemas are created **per module, per iteration** — not upfront. Earliest-needed: `ingress`, `iso`, `payment`, `ledger`, `settlement`, `egress`, `reference_data`, `evidence`/`audit`, `security`, `batch`.

#### 3.6.3 Allowed & forbidden dependencies

| From | Allowed (port/read-model/event) | Forbidden |
|---|---|---|
| `ingress` | `iso-adapter` port, `security`, `reference-data`, `evidence-audit` | direct `payment`/`settlement`/`ledger`/`egress` |
| `iso-adapter` | `reference-data`, `evidence-audit` | change `payment.status`; route; settle |
| `payment-lifecycle` | `iso-adapter` query port, `reference-data` | write `ledger`/`settlement`/`egress`/`routing` |
| `routing` | `payment` query, `reference-data` | settle; post ledger; dispatch |
| `settlement` | `routing` read model, **`LedgerPort`**, `reference-data` | direct `ledger.*` writes |
| `ledger` | `reference-data` | depend on `settlement`/`payment` |
| `reconciliation` | `ledger`/`settlement`/`egress` **read models** | mutate any source module |
| `case` | `iso-adapter` correlation, `payment` read model + `RETURNED` transition via port, payment intake port (return payment), `LedgerPort.release` only when `finality_at IS NULL`, `reference-data`, `egress` events | `ledger.*` write; `LedgerPort.reverse` for a business return; `payments` direct write; render/deliver ISO |
| `egress` | `payment` read model, `routing` profile, `reference-data`, `iso-adapter` renderer | update `payments.status`/`settlement_*` |
| `risk` | `payment` events/read model, `reference-data` | block/reject settlement (MVP) |
| `simulation` | **public command ports**, `csm.response.received` | direct domain-table inserts |
| `reporting` | events/read models | become a domain write path |
| `reference-data` | admin/security | own transactional state |

**Forbidden-access list `[REJECT]`:** `settlement`→`ledger.journal_*` write · `egress`→`payments.status` · `risk`→block settlement (MVP) · `simulation`→domain insert · GraphQL mutation→domain write · `reference-data`→transactional state · `iso-adapter`→business decision · `case`→`ledger.journal_*` write · `case`→`LedgerPort.reverse` for a business return (a return is a **new payment**, never an undo of a final settlement) · `case`→`payments` direct write · `case`→render/deliver ISO (that is `egress`).

#### 3.6.4 Config-vs-decision split (routing)
`[CHANGE]` `routing_profiles`, `route_rules`, `fallback_rules`, `scheme_profiles`, `business_calendars`, `settlement_cutoff_calendar` are **static catalogs owned by `reference-data`**. `route_decisions` and operational `participant_reachability` are **owned by `routing`**. This prevents config and decisions from mixing and stops duplicated calendars between `settlement` and `routing`.

#### 3.6.5 Architectural tests (mandatory CI gates)
Modulith `verify()` · ArchUnit (no foreign repository, no `settlement`→`ledger` repo, no `Instant.now()` outside `ClockPort`, GraphQL→read-only) · SQL/Testcontainers (module role can't write foreign schema; `settlement_role` has no `ledger.*` write; unbalanced entry rejected at COMMIT; `journal_lines` update/delete denied; queue tables not RLS; tenant/evidence tables enforce RLS) · Kafka (producer-owns-topic, per-`payment_id` ordering, duplicate CSM response no-op) · Playwright (payment detail shows ISO ids from `iso.payment_iso_identifiers`; egress dashboard shows the unified transport lifecycle `REQUESTED→…→DELIVERED→CLOSED`; reconciliation shows severity+run). Full suite in the test-strategy section.

### 3.7 Kafka Topic Catalog v2 / AsyncAPI Source of Truth

`[PATCH][ADR-N8][FREEZE]` This table is the **sole source of truth** for every Kafka topic in SEPA Nexus. AsyncAPI specifications are **generated from this table**, never authored independently; no other document (integration patch, module blueprint, or prior version of this section) may define, rename, or add a topic without first landing here. A topic name appearing anywhere else that is not in this table is a documentation defect — `[EVENT-RISK]` — and must be reconciled into this table before use.

Kafka is the internal backbone only — **never exposed to external counterparties** (they use REST/file channels). Every topic has exactly **one producer-owner**, an **explicit key**, an **ordering guarantee**, a **DLQ rule**, and a **contract owner** responsible for the AsyncAPI schema. Standing rules, unchanged and reaffirmed: a module never produces onto another module's topic · `csm.response.received` is the **only** entry point for the simulator's output, proving simulation exercises the real status path, not a back door · ordering is guaranteed by single-partition-per-key · every consumer group is inbox-gated (dedupe on `message_id`) · DLQ is per consumer group · **no topic-per-profile, no topic-per-status, no topic-per-transport-type** — status and profile are carried as *data on* an event, never encoded as a topic name.

| Topic | Producer Owner | Key | Consumers | Ordering Guarantee | DLQ Rule | Contract Owner | MVP/P1/P2 |
|---|---|---|---|---|---|---|---|
| `payment.received` | `ingress` | `payment_id` | `payment-lifecycle`, `risk` | strict per payment | per-consumer-group DLQ | `ingress` | `[MVP]` |
| `payment.validated` | `payment-lifecycle` | `payment_id` | `routing` | strict per payment | per-consumer-group DLQ | `payment-lifecycle` | `[MVP]` |
| `payment.routed` | `routing` | `payment_id` | `settlement`, `egress` (projection) | strict per payment | per-consumer-group DLQ | `routing` | `[MVP]` |
| `route.failed` | `routing` | `payment_id` | `payment-lifecycle`, ops dashboard | per payment | per-consumer-group DLQ | `routing` | `[MVP]` |
| `settlement.attempted` | `settlement` | `payment_id` | reporting/monitoring | per payment | per-consumer-group DLQ | `settlement` | `[MVP]` |
| `settlement.completed` | `settlement` | `payment_id`/`cycle_id` | `payment-lifecycle`, `egress`, `reconciliation`, `reporting` | per payment/cycle | per-consumer-group DLQ | `settlement` | `[MVP]` |
| `settlement.failed` | `settlement` | `payment_id` | `payment-lifecycle`, `risk`, `egress` | per payment | per-consumer-group DLQ | `settlement` | `[MVP]` |
| `csm.response.received` | `simulation`/CSM adapter | `payment_id`/`orgnl_msg_id` | `payment-lifecycle`, `iso-adapter` | per payment/correlation | per-consumer-group DLQ | `simulation` | `[MVP]` |
| `payment.status.reported` | `payment-lifecycle` | `payment_id` | `egress`, `reporting` | strict per payment | per-consumer-group DLQ | `payment-lifecycle` | `[MVP]` |
| `egress.delivery.requested` | `egress` | `outbound_message_id` | monitoring/audit | per outbound msg | per-consumer-group DLQ | `egress` | `[MVP]` |
| `egress.delivery.confirmed` | `egress` | `outbound_message_id` | reporting/audit | per outbound msg | per-consumer-group DLQ | `egress` | `[MVP]` |
| `egress.dead_lettered` `[ADD]` | `egress` | `outbound_message_id` | ops dashboard, alerting | per outbound msg | terminal state — this **is** the DLQ signal | `egress` | `[MVP]` |
| `egress.manual_intervention_required` `[ADD]` | `egress` | `outbound_message_id` | ops dashboard, alerting | per outbound msg | terminal state — no further retry | `egress` | `[MVP]` |
| `reconciliation.run.completed` `[RENAME from reconciliation.completed]` | `reconciliation` | `run_id`/`cycle_id` | reporting/dashboard | per run/cycle | per-consumer-group DLQ | `reconciliation` | `[MVP]` |
| `reconciliation.run.failed` `[ADD]` | `reconciliation` | `run_id` | ops dashboard, alerting | per run | terminal — triggers `RETRY_REQUIRED`/`MANUAL_INVESTIGATION` | `reconciliation` | `[MVP]` |
| `reconciliation.exception.detected` `[RENAME from reconciliation.mismatch.detected]` | `reconciliation` | `exception_id` | investigation/dashboard | per exception | per-consumer-group DLQ | `reconciliation` | `[MVP]` |
| `risk.signal.detected` | `risk` | `participant_id`/`corridor_id` | dashboard/security | per participant | best-effort | `risk` | `[P2]` |
| `simulation.event.generated` | `simulation` | `scenario_run_id` | **normal domain ingress/CSM paths only** | per scenario seq | n/a (synthetic driver, not a domain fact) | `simulation` | `[MVP]` |
| `audit.evidence.recorded` | `evidence-audit` | `evidence_id` | evidence search/dashboard | per evidence | per-consumer-group DLQ | `evidence-audit` | `[MVP]` |
| `iso.message.parsed` | `iso-adapter` | `iso_message_id` | `payment-lifecycle`, reporting | per ISO message | per-consumer-group DLQ | `iso-adapter` | `[MVP]` |
| `iso.message.rejected` | `iso-adapter` | `raw_message_id` | `ingress`, egress/reporting | per raw message | per-consumer-group DLQ | `iso-adapter` | `[MVP]` |
| `iso.message.correlated` | `iso-adapter` | `payment_id` | `payment-lifecycle`, `reconciliation` | per correlation | per-consumer-group DLQ | `iso-adapter` | `[MVP]` |
| `iso.message.orphaned` | `iso-adapter` | `iso_message_id` | ops dashboard, DLQ handler | per message | terminal — operator queue | `iso-adapter` | `[MVP]` |
| `case.opened` | `case` | `case_id` | reporting/dashboard | per case | per-consumer-group DLQ | `case` | `[P1]` |
| `case.resolved` | `case` | `case_id` | `egress` (renders camt.029), `payment-lifecycle`, reporting | per case | per-consumer-group DLQ | `case` | `[P1]` |
| `case.closed` | `case` | `case_id` | reporting/dashboard, `reconciliation` | per case | per-consumer-group DLQ | `case` | `[P1]` |
| `case.escalated` | `case` | `case_id` | ops dashboard, monitoring | per case | per-consumer-group DLQ | `case` | `[P1]` |
| `case.return_payment_requested` | `case` | `case_id` | monitoring (return itself goes via normal payment path) | per case | per-consumer-group DLQ | `case` | `[P1]` |

**Retired names (do not use — alias-noted, not a second valid spelling):** `reconciliation.completed` → use `reconciliation.run.completed`; `reconciliation.mismatch.detected` → use `reconciliation.exception.detected`.

**Rules (reaffirmed):** a module never produces onto another module's topic · `csm.response.received` is the **only** entry point for the simulator's output · ordering via single-partition-per-key on `payment_id` (or `run_id`/`cycle_id`/`case_id`/`exception_id` where noted) · every consumer inbox-gated · DLQ per consumer group, except the three topics above (`egress.dead_lettered`, `egress.manual_intervention_required`, `reconciliation.run.failed`) which **are themselves** the terminal/DLQ signal, not sources needing a further DLQ. `[CHANGE, ISO integration]` the four `iso.message.*` topics are the **only** ISO topics; `iso.message.mapped`, `iso.outbound.rendered`, `iso.outbound.signed` stay **internal Modulith events** `[DEFER]` (promote to topics only when external observability needs them); a separate `pacs002.correlated` topic is `[REJECT]`ed as an alias of `iso.message.correlated`. `[PATCH-NOTE]` this v2 catalog supersedes and closes the topic-naming drift found between the egress and reconciliation integration patches and the prior version of this table — those patch documents now reference this section rather than restate topic names.

### 3.8 ISO Adapter Responsibilities (boundary)

`[ADD]` `iso-adapter` owns, and is limited to: **parsing** (hardened XML→object), **versioning** (deterministic `iso_message_version` selection), **mapping** (ISO→canonical), **validation** (XML/schema/structural/profile/mapping → `iso.iso_message_validation_results`), **identifier extraction** (→ `iso.payment_iso_identifiers`), **correlation** (→ `iso.iso_message_correlation`). It **does not**: set `payments.business_status_code`, make routing decisions, settle or post ledger, decide finality, or perform egress delivery. It **reads** `reference_data` catalogs, **publishes** the four ISO events, and **writes only** `iso.*`. Enforced by Modulith `allowedDependencies` + ArchUnit (no `iso-adapter`→`payment`/`routing`/`settlement`/`ledger`/`egress` repository) + SQL grants (no write on foreign schemas). `[ARCH-RISK]` this concentration of surface is acceptable **only** because the adapter makes no business decision — it records, it never rules.

### 3.9 Routing Module Responsibilities (boundary)

`[ADD]` `routing` **resolves candidate profiles, checks participant eligibility (from `reference-data`), checks profile-specific reachability (its own runtime state), reads cut-off/cycle state from `settlement`, may perform a coarse liquidity-mode precheck (read-only, P1), creates an immutable route decision + explanation, and publishes it**. It **never** writes payment status, settlement, or ledger, and never renders an egress artifact. It is a **route-resolution pipeline that ends at a recorded decision**, not an engine that acts (full model §4.10).

**Ports:** `RouteDecisionService` (orchestrates), `CandidateProfileResolver` (scheme/service/currency→candidates), `ParticipantReachabilityService` (per-profile), `EligibilityPolicy` (reads reference-data), `FallbackPolicy` (explicit config), `CutoffStateReader` (**read-only** from settlement), `LiquidityModePrecheckPort` (**read-only coarse**, P1), `RouteDecisionRepository` (immutable `routing.*` writes), `RouteExplanationReadModel`. Enforced by Modulith `allowedDependencies` + ArchUnit (no `routing`→`settlement`/`ledger`/`payment`/`egress` write) + SQL grants. `[ARCH-RISK]` guard against God Module: routing *reads* cut-off/liquidity and *records* a decision; it moves no money and mutates no payment.

### 3.10 Settlement Module Responsibilities (boundary)

`[ADD]` `settlement` **consumes a route decision, freezes a settlement-profile snapshot, resolves a strategy by `settlement_basis`+`liquidity_mode` (never by profile/CSM name), runs the settlement-attempt lifecycle, requests money movement only through `LedgerPort`, applies the profile's `finality_rule`, and publishes the outcome.** It owns `settlement_attempts`, `settlement_attempt_events`, `settlement_profile_snapshots`, `settlement_cycles`, `settlement_items`, `settlement_positions`, `settlement_queue_items` (P1), `settlement_finality_records`, `settlement_liquidity_checks`, plus runtime cut-off/cycle state. It **never** writes `ledger.*` (LedgerPort only), never re-routes, never renders/delivers, never decides ISO correlation. `[ARCH-RISK]` settlement is a **strategy-selecting orchestrator over an immutable ledger**, not an RTGS/CSM — enforced by Modulith `allowedDependencies` + ArchUnit (no `ledger` repository outside `ledger`) + SQL grant (`settlement_role` has no `ledger.*` write). Full model §4.11.

**Ports:** `SettlementStrategyResolver(basis, liquidity_mode) → Strategy`, `SettlementStrategy` (`GrossInstant`/`NetDeferred`/`InternalBook`/`FileBatch`/`BulkCgsLike`P1/`PrefundedInstant`P1), `LedgerPort` (`reserve/post/release/reverse`), `CutoffCycleStateService` (settlement-owned runtime), `FinalityPolicy` (reads `finality_rule` from reference-data), `SettlementProfileSnapshotWriter`.

### 3.11 Egress Module Responsibilities (boundary)

`[ADD]` `egress` **renders and delivers outbound artifacts (ISO messages, result files, status reports), records delivery attempts, transport receipts and delivery state, applies an `egress_profile` (transport, retry, signing, artifact types), retries idempotently, and exposes operator dashboards + manual resend**. It owns `outbound_messages`, `outbound_files`, `outbound_artifacts`, `transport_attempts`, `delivery_receipts`, `transport_receipts_in`, `egress_profile_snapshots`, `outbound_artifact_lineage`, `manual_delivery_actions`. It **never** writes `payments.*`/`settlement_*`/`ledger.*`, decides finality, makes routing decisions, performs ISO business validation, or sets business status. `[ARCH-RISK]` egress is a **profile-driven, idempotent transport layer over immutable upstream truth** — a technical ACK it receives is transport state, never a business status; `[REJECT]` per-CSM egress engines (behaviour is an `egress_profile` row). Enforced by Modulith `allowedDependencies` + ArchUnit (no `egress`→`payment`/`settlement`/`ledger` write) + SQL grants. Full model §6.8–§6.9.

**Ports:** `ArtifactRenderPort` (calls the `iso-adapter` renderer — egress builds no ISO semantics itself), `SignaturePort` (`signature` module), `TransportAdapter` (webhook/file-drop/internal-topic), `DeliveryDispatcher` (`@Scheduled`, `FOR UPDATE SKIP LOCKED`), `RetryPolicy` (from `egress_profile`), `EgressProfileSnapshotWriter`, `ManualDeliveryCommand` (role-gated resend).

**Five-status separation `[EGRESS-RISK]` `[MVP-BLOCKER]`:** payment business status (`payment-lifecycle`) ≠ ISO message status (`iso-adapter`/ISO codes) ≠ settlement finality (`settlement`) ≠ transport delivery status (`egress`) ≠ delivery receipt (`egress`). Five independent facts: a `DELIVERED` pacs.002 does not move `finality_at`; a receipt is a transport ACK, not `ACSC`; a failed delivery leaves payment and settlement untouched.

### 3.12 Reconciliation Module Responsibilities (boundary)

`[ADD]` `reconciliation` **runs on a deterministic `as_of` snapshot, collects evidence read-only from source modules, compares expected vs actual, classifies mismatches against a reference-data taxonomy, records immutable results, creates severity-scored exceptions, exposes evidence bundles (pointers), and escalates to operator / case / action request**. It owns `reconciliation_runs`, `reconciliation_run_sources`, `reconciliation_results`, `reconciliation_exceptions`, `exception_events`, `evidence_bundles`, `evidence_pointers`, `reconciliation_profiles_snapshot`, and (P1) `action_requests`/`exception_comments`/`exception_assignments`. It **never** writes `payment`/`settlement`/`ledger`/`egress`/`iso`/`case`, never sets finality or status, never resends egress artifacts, never posts to the ledger. `[ARCH-RISK]` reconciliation is an **evidence comparator that escalates, not a back-office that repairs** — `ACTION_REQUESTED` is a request to another module/operator, never a mutation; it may **request** `case` to open but `case` decides and the normal path executes. Enforced by Modulith `allowedDependencies` + ArchUnit (no `reconciliation`→source-module write; reads read models/events only) + SQL grants (no write on any foreign schema).

**Ports:** `AsOfSnapshotService` (freezes watermark + source refs — `[MVP-BLOCKER]` one run = one `as_of`), `EvidenceCollector` (per source, read-only), `ComparisonEngine` (per profile), `MismatchClassifier` (reference-data taxonomy), `SeverityPolicy` (reference-data rules), `EvidenceBundleWriter` (pointers not copies), `ExceptionLifecycleService`, `ActionRequestPort` (request-only, P1), `CaseEscalationPort` (request `case` open, never write `case.*`, P1). Model chain: `as_of → collect evidence → compare expected vs actual → classify mismatch → result → exception → severity → evidence bundle → escalate`. Full model §4.12.

### 3.13 Case Module Responsibilities (R-messages: reject / return / recall)

`[ADD]` `case` **resolves the type of an R-message/internal trigger, validates the intended action against the payment's settlement/finality state (`as_of`), collects evidence (pointers), applies reference-data case rules, makes a decision, and requests any effect through the owning module** — a return payment through the normal payment path, an outbound camt.029/pacs.004/pacs.028 through `egress` (rendered by `iso-adapter`), an escalation to an operator. It writes only `case.*` and **never** settles, books money, changes payment `business_status_code`, sets finality, renders ISO, or sends outbound directly. `[ARCH-RISK]` `case` is a **decision-and-coordination module**, not a bank back-office — every money/status/ISO effect is a *request*, never a direct write (§3.6.3 forbidden-access list). Enforced by Modulith `allowedDependencies` + ArchUnit (no `case`→`payment`/`settlement`/`ledger`/`egress`/`iso`/`reconciliation` write; no reachable `LedgerPort.reverse`) + SQL grants.

**Ports:** `CaseTypeResolver` (reference-data catalog), `RMessageRuleEngine` (reference-data rules), `PaymentStateValidator` (`as_of` read of payment/settlement/finality — gates action legality), `OutboundResponsePort` (**request** egress to render+deliver camt.029/pacs.004/pacs.028 — never renders itself), `ReturnPaymentRequestPort` (**request** a return payment through normal intake), `DuplicateConflictClassifier`.

**Reject / Return / Recall / Reversal — four distinct concepts** `[MVP-BLOCKER]` `[LEDGER-RISK]`: **reject** (`pacs.002` RJCT/AC04 — money never moved, no new payment, legal before finality only); **return** (`pacs.004` — money moves back as a **new opposite-direction payment**, legal after finality); **recall** (`camt.056` — a *request* to cancel/return: before finality may cancel via lifecycle + `LedgerPort.release`; after finality can only lead to a return); **internal reversal** (`ledger` only, booking-error correction before finality via `LedgerPort.reverse` — **never** a business return, never driven by `case`).

**Case timing matrix** `[MVP-BLOCKER]` `[FINALITY-RISK]` (validated against `as_of` payment state): before route → validate only (recall illegal); after route, pre-settlement → reject or recall (return illegal — no money moved); post-acceptance, pre-finality → recall via release (reject illegal — already accepted); post-finality → generate return as new payment (recall too late, business reversal forbidden); post-egress → case closed (new R-message on same event → dedupe); after recon mismatch → `RECONCILIATION_LINKED_CASE`; after SLA/timeout → `CASE_EXPIRED`/operator escalation. Full model §4.14; the R-message ISO matrix, case-type taxonomy and decision outcomes live there too.

---

## 4. Data Layer: access-path-driven PostgreSQL design (PG18, PG19-ready)

### 4.1 The access paths first (the DBA's question: "what will be asked, how often, how fast?")

| # | Access path | Class | Serving structure |
|---|---|---|---|
| P1 | putOrGet idempotency key | Hot OLTP, <5 ms | `idempotency_keys` unique + covering index |
| P2 | insert payment + lineage + history + outbox (one TX) | Hot OLTP | slim `payments` row + `iso.payment_iso_identifiers` + append tables |
| P3 | payment by id / by OrgnlEndToEndId (status match) | Hot OLTP | PK on `payments`; correlation via `iso.payment_iso_identifiers` indexes (G4) |
| P4 | timeline of one payment | Read model | `payment_status_history` clustered by (payment_id, seq) |
| P5 | outbox: oldest unpublished N | Hot poller | partial index `WHERE published_at IS NULL` |
| P6 | inbox: seen message_id? | Hot consumer | PK on message_id (insert-or-conflict = the check) |
| P7 | ledger: reserve/post with balance check | Hot OLTP, serialized per account | account row lock + append movements |
| P8 | net positions per cycle | Batch aggregate | one `GROUP BY` over `settlement_items` |
| P9 | reconciliation sums (Σ=0, per day/cycle) | Batch, REPEATABLE READ | movement partitions + covering indexes |
| P10 | dashboard: recent events, lag, p95 | Streaming read | monthly partitions on `payment_events`, BRIN on time |
| P11 | egress: oldest undelivered N per channel | Hot poller | partial index on `outbound_messages` |
| P12 | file item statuses for result file | Batch read | `inbound_file_items` by (file_id) |

Design rules derived: **slim hot rows** (payments carries currents only; everything historical is append-only elsewhere); **partition what grows monotonically** (events, journal lines, audit) monthly by time; **partial indexes for queues** (P5, P11) so pollers never scan the settled past; **UUIDv7 keys** (`uuidv7()` is native in PG18) for insert locality; **BRIN on time columns** of partitioned tables; **fillfactor 100** on append-only tables (no updates → no HOT space needed), **fillfactor ~85** on `payments` (status updates stay HOT).

### 4.2 Ingress tables

`[PATCH v2, deep-research applied]` Two corrections from DBA review: (1) the raw archive must **not** deduplicate — the same message legitimately arriving twice (retry, resend) must produce two evidence rows; losing the second receipt destroys the audit trail. Idempotency is `idempotency_keys`' job, not the archive's. (2) `idempotency_keys` uniqueness scope on files was too wide (`tenant_id, msg_id` alone), and PG19's `ON CONFLICT DO SELECT` is demoted from "headline" to **lab-only experimental seam** — PG19 is beta; the PG18 two-step is the real MVP path (see §4.8).

```sql
CREATE TABLE raw_inbound_messages (
  id            uuid PRIMARY KEY DEFAULT uuidv7(),
  received_at   timestamptz(3) NOT NULL,
  channel       text NOT NULL,                    -- REST_JSON | REST_XML | FILE | INTERNAL
  tenant_id     uuid NOT NULL, branch_id uuid,
  sender_id     uuid,                              -- participant/PSP identity presenting the message
  transport_id  text,                              -- e.g. HTTP request id / file transfer id, for support tracing
  message_type  text,                              -- pain.001 | pacs.008 | pacs.002 | ... | NULL for opaque JSON
  payload       bytea NOT NULL,                   -- raw bytes, pre-parse (evidence)
  payload_sha256 bytea NOT NULL,
  signature_verdict text,                          -- VERIFIED | FAILED | N/A
  parse_verdict text                               -- OK | REJECTED | NULL until parsed
  -- [CHANGE] NO UNIQUE on payload_sha256: this table is append-only EVIDENCE, not a dedupe gate.
  --          A duplicate receive of the same bytes must still archive a second row; the business
  --          outcome is deduplicated by idempotency_keys below, not by losing the raw evidence.
) PARTITION BY RANGE (received_at);               -- monthly
CREATE INDEX rim_tenant_received ON raw_inbound_messages(tenant_id, received_at DESC);
CREATE INDEX rim_sha ON raw_inbound_messages(payload_sha256);   -- lookup, not unique

CREATE TABLE idempotency_keys (
  source_id     uuid NOT NULL,                    -- client/participant identity
  idem_key      text NOT NULL,
  request_hash  bytea NOT NULL,
  payment_id    uuid,
  raw_message_id uuid REFERENCES raw_inbound_messages(id),   -- [ADD] evidence link
  response_code smallint NOT NULL,
  first_seen_at timestamptz(3) NOT NULL,           -- [ADD]
  last_seen_at  timestamptz(3) NOT NULL,           -- [ADD] bumped on every replay
  expires_at    timestamptz(3),                    -- [ADD] synthetic retention window
  PRIMARY KEY (source_id, idem_key)
);
-- P1 covering read: INCLUDE avoids heap fetch on replay
CREATE UNIQUE INDEX idem_covering ON idempotency_keys(source_id, idem_key)
  INCLUDE (request_hash, payment_id, response_code);
-- [SELF-CORRECT, deep-research] MVP path is PG18 two-step, NOT the PG19 headline this doc used before:
--   Step 1: INSERT ... ON CONFLICT (source_id, idem_key) DO NOTHING RETURNING *;
--   Step 2: if no row returned → SELECT * WHERE (source_id, idem_key) = (...);
--   Compare request_hash: match → replay stored response_code; mismatch → 409 IDEMPOTENCY_CONFLICT.
-- PG19 `ON CONFLICT DO SELECT RETURNING` (one statement, no dead tuples) is a LAB-ONLY seam behind the
-- same IdempotencyStore interface — do not depend on it until PG19 is GA (see §4.8).

CREATE TABLE inbound_files (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  file_sha256 bytea NOT NULL,
  msg_id text NOT NULL,                            -- ISO GrpHdr/MsgId
  sender_id uuid NOT NULL,                         -- [ADD] scopes uniqueness correctly
  message_type text NOT NULL,                      -- [ADD] pain.001 batch | other
  business_date date NOT NULL,                      -- [ADD]
  tenant_id uuid NOT NULL, branch_id uuid,
  status text NOT NULL DEFAULT 'ACCEPTED',         -- ACCEPTED|PROCESSING|PROCESSED|RESULT_SENT
  expected_count int, ok_count int, rejected_count int,
  result_file_id uuid,                             -- [ADD] set once §6.4 outbound_files row exists
  received_at timestamptz(3) NOT NULL,
  -- [CHANGE] scoped uniqueness: same MsgId from a DIFFERENT sender is not a duplicate
  UNIQUE (tenant_id, sender_id, msg_id, message_type)  -- same MsgId+different content, same sender → DUPL reject (G5)
);
CREATE TABLE inbound_file_items (
  file_id uuid NOT NULL REFERENCES inbound_files(id),
  item_seq int NOT NULL,
  status text NOT NULL,                            -- STAGED|REJECTED
  reason_codes text[],
  raw_item_ref text,                                -- [ADD] pointer into the raw file (line/offset)
  canonical_hash bytea,                             -- [ADD] hash of the mapped canonical item (support tracing)
  payment_id uuid,
  PRIMARY KEY (file_id, item_seq)                  -- P12: naturally clustered per file
);
```

### 4.3 Canonical payment (current-state) + ISO lineage + coded history (G4)

`[PATCH v2, deep-research applied]` The single biggest correction: `payments` was carrying ISO identifiers directly (`msg_id`, `end_to_end_id`, `orgnl_*`), which flattens **message identity** into **business state** — the report's sharpest finding. Real pacs.002/R-message correlation needs a *per-message-type* identifier record (a payment can be referenced by more than one source message type), not one flat set of columns. **Split:** `payments` stays the slim, hot current-state row; all ISO identifiers move to `iso.payment_iso_identifiers`. Status/reason also move from free `text`/`text[]` to coded references (full reference tables in the update-plan's §4.13 addition; the FK-bearing columns land here now).

```sql
CREATE SCHEMA IF NOT EXISTS iso;

CREATE TABLE payments (                            -- schema `payment`; slim, hot, current-state ONLY
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  tenant_id uuid NOT NULL, branch_id uuid,
  scheme_code text NOT NULL                         -- [ADD] SCT | SCT_INST | INTERNAL
    CHECK (scheme_code IN ('SCT','SCT_INST','INTERNAL')),
  service_level_code text NOT NULL,                 -- [ADD] e.g. SEPA / URGP
  instruction_scope text NOT NULL                    -- [ADD] the branch trichotomy, decided at routing
    CHECK (instruction_scope IN ('INTRA_BRANCH','INTER_BRANCH','INTERBANK')),
  acceptance_state text NOT NULL                     -- [ADD] separate from business/lifecycle status
    CHECK (acceptance_state IN ('RECEIVED','VALIDATED','ACCEPTED','REJECTED')),
  business_status_code text NOT NULL,               -- [CHANGE] was `status text`; FK to status_catalog (§4.13)
  settlement_basis text NOT NULL                     -- [ADD] drives the settlement strategy (§4.6/§4.9)
    CHECK (settlement_basis IN ('GROSS_INSTANT','NET_DEFERRED','INTERNAL_BOOK')),
  version int NOT NULL DEFAULT 0,                   -- optimistic lock
  amount_minor bigint NOT NULL CHECK (amount_minor > 0),
  currency char(3) NOT NULL,
  debtor_iban text NOT NULL, creditor_iban text NOT NULL,
  debtor_participant_id uuid NOT NULL,               -- [ADD] who owes — needed by ledger/routing joins
  creditor_participant_id uuid NOT NULL,             -- [ADD] who receives
  value_date date, requested_execution_date date,
  profile_id uuid,                                   -- set at routing
  -- finality & timing block — PFMI-aligned (ms precision; separate technical vs business vs legal time)
  accepted_at timestamptz(3),                        -- [ADD] business acceptance
  settled_at timestamptz(3),                         -- [ADD] money moved
  finality_at timestamptz(3),                        -- legal finality ≠ status; irreversible once set
  timeout_at timestamptz(3),                         -- [ADD] technical timeout ≠ business rejection
  technical_receipt_at timestamptz(3),               -- [ADD] transport-level receipt, before validation
  revocation_cutoff timestamptz(3),                  -- [ADD] PFMI: last moment a revocation request is valid
  finality_reason text,                              -- [ADD] why finality was reached (profile rule, manual, etc.)
  created_at timestamptz(3) NOT NULL,
  CONSTRAINT finality_only_final CHECK (finality_at IS NULL OR business_status_code IN ('SETTLED','RETURNED'))
) WITH (fillfactor = 85);
-- [CHANGE] the old `pay_corr` unique index on (tenant_id, end_to_end_id) is REMOVED — correlation now
-- lives entirely in iso.payment_iso_identifiers (below); a payment can be found by any ISO id it ever carried.
CREATE INDEX pay_status_recent ON payments(business_status_code, created_at DESC);
CREATE INDEX pay_debtor_recent ON payments(debtor_participant_id, created_at DESC);
CREATE INDEX pay_creditor_recent ON payments(creditor_participant_id, created_at DESC);

-- [CHANGE v4, ISO lineage] `iso.payment_iso_identifiers` is defined in the richer form in §4.3c below
-- (PK now includes `iso_message_id`; adds `pmt_inf_id`, `tx_id`, `uetr`, `orgnl_tx_id`, `cre_dt_tm`).
-- The earlier thin form (PK `(payment_id, source_message_type)`, no tx_id/uetr) is SUPERSEDED — see §4.3c.
-- Do not create it here; it belongs to the `iso` schema owned by `iso-adapter`.

CREATE TABLE payment_status_history (
  payment_id uuid NOT NULL,
  seq int NOT NULL,
  from_status text, to_status text NOT NULL,        -- kept for readability; business_status_code is canonical
  status_code text NOT NULL,                         -- FK-able to reference_data.status_catalog (§4.13)
  reason_code text,                                  -- FK-able to reference_data.iso_reason_codes (§4.13); NULL = none
  source_type text NOT NULL,                         -- INTERNAL | PACS002 | PACS004 | CAMT056 | CAMT029 | OPERATOR
  source_iso_message_id uuid,                        -- [CHANGE v4] typed FK → iso.iso_messages(id) (was loose source_message_id)
  raw_message_id uuid,                                -- → ingress.raw_inbound_messages, direct evidence link
  actor_type text NOT NULL DEFAULT 'SYSTEM',          -- SYSTEM | OPERATOR | SCHEDULER
  is_final boolean NOT NULL DEFAULT false,            -- drives outbox emission + finality checks
  event_type text NOT NULL, event_ref uuid,
  at timestamptz(3) NOT NULL,
  PRIMARY KEY (payment_id, seq)                      -- P4: timeline = one index range scan
);

CREATE TABLE payment_events (                        -- integration/event log, monthly partitions (unchanged this patch)
  id uuid NOT NULL DEFAULT uuidv7(),
  payment_id uuid NOT NULL,
  type text NOT NULL, payload jsonb NOT NULL,
  at timestamptz NOT NULL,
  PRIMARY KEY (at, id)
) PARTITION BY RANGE (at);
CREATE INDEX pe_brin ON payment_events USING brin(at);   -- P10
CREATE INDEX pe_payload_gin ON payment_events USING gin(payload jsonb_path_ops);
```
`[DECISION]` ISO identifiers are keyed **per source message + type**, not per payment — a payment referenced by a `pacs.008` (interbank leg) and a later `camt.056` (recall) carries one identifier row per ISO message. This is the "message lineage, not flattened state" model; the full table lives in §4.3c.

### 4.3b ISO 20022 Message Lineage & Versioning

`[ADD, ISO integration]` This makes ISO 20022 message lineage a first-class design element. `iso-adapter` owns parsing, versioning, mapping, identifier extraction and correlation; it makes **no** business decision, routing, settlement, finality or egress delivery (§3.8).

**Lineage principle `[ISO-LINEAGE]`.** Every ISO message is traceable across: `raw bytes → parsed ISO message → validation result → identifiers → canonical payment/status/case → settlement/egress artifact → evidence/replay`.

**Ten binding rules.** (1) raw inbound bytes archived before parsing; (2) parse/validation results recorded even when no payment is created; (3) ISO identifiers live in `iso.payment_iso_identifiers`, never in `payment.payments`; (4) message lineage lives in `iso.message_lineage`; (5) pacs.002 and R-message correlation produce explicit `iso.iso_message_correlation` records; (6) business status changes applied **only** by `payment-lifecycle`; (7) reason/status codes come from `reference_data`; (8) outbound ISO rendering is lineage (`iso.iso_outbound_artifacts`), delivery is `egress` transport; (9) replay records the parser/mapping/profile version used; (10) GraphQL exposes lineage read models only.

**Mapping flow — inbound:** `ingress.raw_inbound_messages` → `iso.iso_messages` → `iso.iso_message_validation_results` → `iso.payment_iso_identifiers` → `iso.message_lineage` → `payment.payments` → `payment.payment_status_history` → `payment.outbox_events` (§4.4, `ADR-N5` — the owning module's own outbox, not a shared table).
**Mapping flow — outbound `[P1]`:** `payment.status.reported`/`settlement.completed` → render-profile selection → `iso.iso_messages(direction=OUTBOUND)` → `iso.iso_outbound_artifacts` → `egress.outbound_messages` → `egress.transport_attempts` → `egress.delivery_receipts`.

**pacs.002 correlation order** (records `iso.iso_message_correlation`): (1) `OrgnlMsgId+OrgnlTxId`; (2) `OrgnlMsgId+OrgnlInstrId+OrgnlEndToEndId`; (3) `UETR` if unique; (4) `OrgnlMsgId+OrgnlEndToEndId`; (5) fallback `EndToEndId+amount+currency+participant+time-window`; (6) >1 candidate → `AMBIGUOUS`, no payment mutation; (7) 0 → `ORPHANED`, DLQ/operator queue; (8) duplicate message → `IGNORED_DUPLICATE`; (9) matched but late/weaker → `payment-lifecycle` FSM policy decides.

**Deterministic versioning:** `message_type + scheme_profile + direction + business_date/effective_at → iso_message_version`. Versioned concepts: ISO message version, SRU/release, EPC/KIR/STET/TIPS/STEP2-like scheme profile, validation profile, mapping profile, render profile, reason/status catalog version. `[DB-OWNERSHIP]` `reference_data` owns profiles/catalogs; `iso-adapter` selects and records exactly which version/profile was used.

**Validation boundary `[CHANGE]`.** ISO rejection = "the message cannot be parsed/mapped under the selected profile" (owner `iso-adapter`, no payment created). Business rejection = "the canonical payment exists but fails lifecycle/routing/settlement rules" (owners `payment-lifecycle`/`routing`/`settlement`). XML hardening: `ingress`+`iso-adapter`, rejects raw before parse.

### 4.3c ISO Schema DDL (`iso-adapter`-owned)

`[ADD]` `[MVP]` core = 7 tables; `[P1]` = `iso_outbound_artifacts`, `iso_message_replay_log`. Key rule: `iso_messages` stores **parsed metadata only — never raw bytes** (those stay in `ingress.raw_inbound_messages`, linked by `raw_message_id`).

```sql
-- SCHEMA iso already created in §4.3; owner role: iso_adapter_role
CREATE TABLE iso.iso_message_versions (             -- [MVP]
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  message_type text NOT NULL, iso_version text NOT NULL, sru text,
  scheme_profile_id uuid NOT NULL,                  -- → reference_data.scheme_profiles
  validation_profile_code text NOT NULL, mapping_profile_code text NOT NULL,
  valid_from date NOT NULL, valid_to date, is_active boolean NOT NULL DEFAULT true,
  CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX imv_type_profile_active ON iso.iso_message_versions(message_type, scheme_profile_id, is_active);

CREATE TABLE iso.iso_messages (                     -- [MVP] parsed METADATA only, no bytes
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  direction text NOT NULL CHECK (direction IN ('INBOUND','OUTBOUND')),
  message_type text NOT NULL,
  message_version_id uuid NOT NULL REFERENCES iso.iso_message_versions(id),
  raw_message_id uuid,                               -- → ingress.raw_inbound_messages (INBOUND)
  outbound_message_id uuid,                          -- → egress.outbound_messages (OUTBOUND)
  msg_id text, cre_dt_tm timestamptz(3), nb_of_txs int, ctrl_sum_minor bigint, currency char(3),
  tenant_id uuid NOT NULL, participant_id uuid,
  parse_status text NOT NULL CHECK (parse_status IN ('PARSED','PARSE_FAILED','SKIPPED')),
  created_at timestamptz(3) NOT NULL,
  CHECK ( (direction='INBOUND'  AND raw_message_id IS NOT NULL AND outbound_message_id IS NULL)
       OR (direction='OUTBOUND' AND outbound_message_id IS NOT NULL) )
);
CREATE INDEX iso_msg_type_msgid ON iso.iso_messages(message_type, msg_id);
CREATE INDEX iso_msg_raw ON iso.iso_messages(raw_message_id);
CREATE INDEX iso_msg_outbound ON iso.iso_messages(outbound_message_id);

CREATE TABLE iso.payment_iso_identifiers (          -- [MVP] richer form (supersedes §4.3 thin version)
  payment_id uuid NOT NULL, source_message_type text NOT NULL,
  iso_message_id uuid NOT NULL REFERENCES iso.iso_messages(id),
  msg_id text, pmt_inf_id text, instr_id text, end_to_end_id text, tx_id text, uetr text,
  orgnl_msg_id text, orgnl_instr_id text, orgnl_end_to_end_id text, orgnl_tx_id text,
  cre_dt_tm timestamptz(3),
  PRIMARY KEY (payment_id, source_message_type, iso_message_id)
);
CREATE INDEX pii_e2e ON iso.payment_iso_identifiers(end_to_end_id);
CREATE INDEX pii_txid ON iso.payment_iso_identifiers(tx_id);
CREATE INDEX pii_uetr ON iso.payment_iso_identifiers(uetr);
CREATE INDEX pii_orgnl ON iso.payment_iso_identifiers(orgnl_msg_id, orgnl_instr_id, orgnl_end_to_end_id, orgnl_tx_id);

CREATE TABLE iso.message_lineage (                  -- [MVP] causal chain, R-message roles ready now
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  iso_message_id uuid NOT NULL REFERENCES iso.iso_messages(id),
  raw_message_id uuid, outbound_message_id uuid,
  payment_id uuid, file_id uuid, case_id uuid,       -- case_id → "case".cases.id (case module, P1); nullable in MVP
  settlement_attempt_id uuid, settlement_cycle_id uuid,
  lineage_role text NOT NULL CHECK (lineage_role IN
    ('ORIGINAL_INSTRUCTION','INTERBANK_INSTRUCTION','STATUS_RESPONSE','RETURN',
     'RECALL_REQUEST','RECALL_RESPONSE','STATEMENT','OUTBOUND_STATUS','OUTBOUND_FORWARD','RESULT_FILE')),
  parent_lineage_id uuid REFERENCES iso.message_lineage(id),
  created_at timestamptz(3) NOT NULL
);
CREATE INDEX lineage_payment ON iso.message_lineage(payment_id);
CREATE INDEX lineage_iso ON iso.message_lineage(iso_message_id);

CREATE TABLE iso.iso_message_validation_results (   -- [MVP]
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  iso_message_id uuid REFERENCES iso.iso_messages(id), raw_message_id uuid,
  validation_type text NOT NULL CHECK (validation_type IN
    ('XML_HARDENING','XML_SCHEMA','ISO_STRUCTURAL','SCHEME_PROFILE','CANONICAL_MAPPING','CORRELATION')),
  result text NOT NULL CHECK (result IN ('PASS','FAIL','WARN')),
  status_code text, reason_code text,                -- FK → reference_data catalogs
  field_path text, details jsonb, created_at timestamptz(3) NOT NULL
);
CREATE INDEX ivr_msg ON iso.iso_message_validation_results(iso_message_id);

CREATE TABLE iso.iso_message_parse_errors (         -- [MVP] failures before mapping (XXE/schema)
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  raw_message_id uuid NOT NULL, message_type_guess text,
  error_code text NOT NULL, error_path text, error_message text NOT NULL, created_at timestamptz(3) NOT NULL
);

CREATE TABLE iso.iso_message_correlation (          -- [MVP] pacs.002/R-message matching result
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  iso_message_id uuid NOT NULL REFERENCES iso.iso_messages(id),
  correlation_type text NOT NULL CHECK (correlation_type IN
    ('PACS002_TO_PAYMENT','PACS004_TO_PAYMENT','CAMT056_TO_PAYMENT','CAMT029_TO_CASE','CAMT053_TO_STATEMENT')),
  matched_payment_id uuid, matched_case_id uuid,     -- matched_case_id → "case".cases.id (P1); CAMT029_TO_CASE
  matched_outbound_message_id uuid,
  status text NOT NULL CHECK (status IN ('MATCHED','ORPHANED','AMBIGUOUS','IGNORED_LATE','IGNORED_DUPLICATE')),
  score smallint CHECK (score BETWEEN 0 AND 100), ambiguity_reason text, matched_by text,
  created_at timestamptz(3) NOT NULL
);
CREATE INDEX imc_status ON iso.iso_message_correlation(status, created_at DESC);
CREATE INDEX imc_payment ON iso.iso_message_correlation(matched_payment_id);

-- [P1] iso.iso_outbound_artifacts (render lineage; see §6.7) and iso.iso_message_replay_log
-- (deterministic replay with profile snapshot) — schema-ready, land with EPIC-ISO-4 / replay work.
```

### 4.4 Outbox / inbox (transactional messaging) — per-schema ownership `[PATCH][ADR-N5][FREEZE]`

`[CHANGE, closes R-11 / DB-OWNERSHIP]` The single schema-flat `outbox_events`/`inbox_events` pair below is **retired**. It violated §3.6.1 rule 1 (a module writes only to its own schema) by being an unowned table every module wrote into. The replacement: **every module schema that publishes events owns its own `<schema>.outbox_events` table; every module schema that consumes events owns its own `<schema>.inbox_events` table.** A single shared-kernel **dispatcher role** (`outbox_dispatcher_role`) is granted narrow `SELECT`/`UPDATE` across every module's outbox table to claim and mark-published rows — and **nothing else**; it has no grant on any domain table and does not appear as a schema-owning row anywhere in §3.6.2. This is infrastructure (the same distinction §4.7 already draws between RLS-protected tenant tables and ownership-protected queue tables), not a business owner.

```sql
-- Pattern, repeated once per publishing schema (created with that module's own migration/iteration):
--   payment.outbox_events, settlement.outbox_events, egress.outbox_events,
--   reconciliation.outbox_events, case.outbox_events, ingress.outbox_events, iso.outbox_events, routing.outbox_events, ...
CREATE TABLE <schema>.outbox_events (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  aggregate_id uuid NOT NULL,                      -- Kafka key
  topic text NOT NULL, type text NOT NULL,         -- topic MUST be one this schema's module owns (§3.7 v2)
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL,
  published_at timestamptz
) WITH (fillfactor = 90);
CREATE INDEX <schema>_outbox_todo ON <schema>.outbox_events(created_at) WHERE published_at IS NULL;  -- P5
-- writer role (the owning module) gets INSERT/SELECT; dispatcher role gets SELECT/UPDATE (claim + mark-published) only:
REVOKE ALL ON <schema>.outbox_events FROM PUBLIC;
GRANT INSERT, SELECT ON <schema>.outbox_events TO <schema>_role;
GRANT SELECT, UPDATE (published_at) ON <schema>.outbox_events TO outbox_dispatcher_role;

-- Pattern, repeated once per consuming schema:
--   payment.inbox_events, settlement.inbox_events, egress.inbox_events, reconciliation.inbox_events, case.inbox_events, ...
CREATE TABLE <schema>.inbox_events (
  message_id uuid PRIMARY KEY,                     -- P6: the insert IS the dedupe
  consumer text NOT NULL,
  processed_at timestamptz NOT NULL
);
REVOKE ALL ON <schema>.inbox_events FROM PUBLIC;
GRANT INSERT, SELECT ON <schema>.inbox_events TO <schema>_role;
```

**Mandatory grant/behaviour tests (Iteration 0 CI gate, per ADR-N5):**
- a module's writer role **cannot** write another module's outbox table (per-pair grant test);
- `outbox_dispatcher_role` **cannot** write any domain table (`payments`, `journal_lines`, `outbound_artifacts`, etc. — a negative grant sweep, not a per-table allowlist);
- consumer inbox tables deduplicate on `message_id` (insert-or-conflict is the check, unchanged from the original design);
- Kafka redelivery does not duplicate a domain effect (replay-safe, asserted end-to-end).

The polling `@Scheduled` `OutboxDispatcher` (§2.5) now iterates N per-schema outbox tables instead of one global table — a mechanical change to its implementation, not an architectural one. Schemas are still created per module, per iteration (§3.6.2's existing rule); an `outbox_events`/`inbox_events` pair is created alongside each module's first migration, not upfront for all 17 modules.

### 4.5 Ledger (double-entry, append-only — the crown jewel)

`[PATCH v2, deep-research applied]` The report's second-sharpest finding: **`Σ(journal_lines.amount_minor)=0` cannot rely on the Java service + a nightly job.** PostgreSQL's own documentation is explicit that constraints spanning multiple rows must be expressed as `UNIQUE`/`EXCLUDE`/`FK` or a **trigger** — never assumed from application discipline alone. Correction: enforce the sum with a **deferred constraint trigger** that fires at `COMMIT`, so a multi-statement transaction can insert lines one at a time and still be checked atomically as a whole; **revoke `UPDATE`/`DELETE`** from application roles so the only way to correct a mistake is a new reversal entry; add snapshots so re-derivation stays fast.

```sql
CREATE TABLE liquidity_accounts (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  tenant_id uuid NOT NULL, branch_id uuid,
  participant_id uuid NOT NULL, currency char(3) NOT NULL,
  available_minor bigint NOT NULL, reserved_minor bigint NOT NULL DEFAULT 0,
  version int NOT NULL DEFAULT 0,
  CHECK (available_minor >= 0), CHECK (reserved_minor >= 0),
  UNIQUE (participant_id, currency)
) WITH (fillfactor = 80);                          -- P7: hot updates, keep HOT
-- [EDU note] named `synthetic_settlement_account` conceptually — this is NOT a central-bank DCA;
-- do not imply TIPS-equivalent central-bank-money settlement in docs or UI copy.

CREATE TABLE journal_entries (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  entry_type text NOT NULL                          -- RESERVE|POST|RELEASE|NETTING_BATCH|REVERSAL
    CHECK (entry_type IN ('RESERVE','POST','RELEASE','NETTING_BATCH','REVERSAL')),
  payment_id uuid, cycle_id uuid,
  business_date date NOT NULL,                       -- [ADD]
  entry_status text NOT NULL DEFAULT 'POSTED'        -- [ADD] POSTED | REVERSED
    CHECK (entry_status IN ('POSTED','REVERSED')),
  reversal_of_entry_id uuid REFERENCES journal_entries(id),  -- [ADD] the ONLY correction mechanism
  created_at timestamptz(3) NOT NULL
);

CREATE TABLE journal_lines (
  entry_id uuid NOT NULL REFERENCES journal_entries(id),
  line_no smallint NOT NULL,
  account_id uuid NOT NULL,
  currency char(3) NOT NULL,                         -- [ADD] must match account currency
  amount_minor bigint NOT NULL,                      -- signed; entry must sum to zero (now SQL-enforced, see below)
  at timestamptz(3) NOT NULL,
  PRIMARY KEY (at, entry_id, line_no)
) PARTITION BY RANGE (at) WITH (fillfactor = 100); -- append-only
CREATE INDEX jl_entry ON journal_lines(entry_id);
CREATE INDEX jl_account_at ON journal_lines(account_id, at DESC);

-- [CHANGE] SQL-level invariant: a deferred constraint trigger, not a Java convention.
-- Fires once per transaction at COMMIT, after all lines for one or more entries have been inserted.
-- (Kept schema-flat like the rest of this document — the update plan defers creating the full
--  14-schema layout to "per module, per iteration"; only `iso` above is a genuinely new physical
--  separation this patch requires. `ledger`/`settlement`/etc. schemas land when those iterations do.)
CREATE OR REPLACE FUNCTION check_entry_balance() RETURNS trigger AS $$
DECLARE unbalanced RECORD;
BEGIN
  SELECT entry_id, sum(amount_minor) AS total INTO unbalanced
  FROM journal_lines WHERE entry_id = NEW.entry_id
  GROUP BY entry_id HAVING sum(amount_minor) <> 0;
  IF FOUND THEN
    RAISE EXCEPTION 'journal_entry % does not balance (sum=%)', unbalanced.entry_id, unbalanced.total;
  END IF;
  RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_entry_balance
  AFTER INSERT ON journal_lines
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION check_entry_balance();

-- [ADD] immutability: application roles may INSERT only, never UPDATE/DELETE journal_lines.
REVOKE UPDATE, DELETE ON journal_lines FROM app_role;   -- app_role = the role LedgerService connects as
GRANT INSERT, SELECT ON journal_lines TO app_role;

CREATE TABLE balance_snapshots (                     -- [ADD] fast re-derivation without walking full history
  account_id uuid NOT NULL,
  snapshot_at timestamptz(3) NOT NULL,
  available_minor bigint NOT NULL, reserved_minor bigint NOT NULL,
  through_entry_id uuid NOT NULL,                    -- last journal_entries.id included in this snapshot
  PRIMARY KEY (account_id, snapshot_at)
);
```
`[DECISION]` balance is **stored current + provable from movements** (hybrid): `liquidity_accounts` holds the fast truth under row lock; reconciliation re-derives it from `journal_lines` (now accelerated by `balance_snapshots`) and any drift is a **defect, loudly**. The nightly Σ=0 assertion job from the prior version of this document is **demoted to a secondary cross-check** — the deferred trigger above is now the primary, transaction-level guarantee. **Corrections are always new `REVERSAL` entries** referencing `reversal_of_entry_id`; no application path may `UPDATE`/`DELETE` a posted line. `[ALT rejected]`: derive-always (too slow for P7), CHECK-only enforcement (PG docs: cross-row rules aren't expressible as `CHECK`), trigger-maintained balances (hides the invariant from the `[EDU]` surface — the trigger here *validates*, it does not silently fix).



### 4.6 Settlement (attempts, cycles, netting)

```sql
CREATE TABLE settlement_attempts (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  payment_id uuid NOT NULL, profile_id uuid NOT NULL,
  state text NOT NULL,                             -- INITIATED|RESERVED|POSTED|CONFIRMED|REJECTED|TIMED_OUT
  reserve_entry uuid, post_entry uuid,
  started_at timestamptz NOT NULL, finished_at timestamptz,
  UNIQUE (payment_id)                              -- one live attempt per payment (retries create history rows post-MVP)
);

CREATE TABLE settlement_cycles (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  profile_id uuid NOT NULL,
  business_date date NOT NULL, session_no smallint NOT NULL,
  state text NOT NULL,                             -- OPEN|CLOSING|CLOSED|NETTED|SETTLED|RECONCILED
  cutoff_at timestamptz NOT NULL,
  UNIQUE (profile_id, business_date, session_no)
);
CREATE TABLE settlement_items (
  cycle_id uuid NOT NULL, payment_id uuid NOT NULL,
  debtor_participant uuid NOT NULL, creditor_participant uuid NOT NULL,
  amount_minor bigint NOT NULL,
  PRIMARY KEY (cycle_id, payment_id)
);
-- G6: membership decided under lock —
--   SELECT state FROM settlement_cycles WHERE id=$1 FOR UPDATE;  -- OPEN → insert item; CLOSING/… → next cycle
-- P8 netting = one statement:
--   INSERT INTO settlement_positions
--   SELECT cycle_id, participant, sum(credit)-sum(debit) ... GROUP BY cycle_id, participant;
CREATE TABLE settlement_positions (
  cycle_id uuid NOT NULL, participant_id uuid NOT NULL,
  net_minor bigint NOT NULL,
  PRIMARY KEY (cycle_id, participant_id)
);
```

### 4.7 RLS — selective, not universal (G3, corrected)

`[PATCH v2, deep-research applied]` The prior version of this document applied RLS "to every tenant table," including queues and the ledger. The report's correction: RLS is **default-deny**, its owner-bypass and cross-table-referencing policies carry real race/leak subtleties, and applying it to hot queue/dispatcher tables and the ledger base tables adds cost and complexity where **table/schema ownership and service roles already solve the problem more simply**. (The DDL below stays schema-flat for now, per the deferred schema-creation decision in §4.5 — the ownership *principle* applies today via table-level `GRANT`/`REVOKE`; it becomes schema-level `GRANT USAGE ON SCHEMA ledger` once that schema is created with its own iteration.) Decision, table by table:

| Table category | Examples | RLS decision | Mechanism |
|---|---|---|---|
| Tenant-facing operational | `payments`, `raw_inbound_messages`, `inbound_files`, `outbound_messages`, `audit_log` | **Adopt (two-level)** | claim→GUC→policy on `tenant_id`+`branch_id`, per §4.7 example below |
| Queue / dispatcher | each module's own `<schema>.outbox_events`/`<schema>.inbox_events` (§4.4, `ADR-N5`), Spring Batch job tables | **Avoid RLS entirely** | Owned by that module's DB role (`INSERT/SELECT` only); `outbox_dispatcher_role` gets narrow `SELECT/UPDATE(published_at)` **across all outbox tables only**, no grant on any domain table — no policy, no GUC dependency |
| Ledger & reconciliation core | `liquidity_accounts`, `journal_entries`, `journal_lines`, `reconciliation_runs` | **Avoid RLS on base tables** | Protected by **table ownership** (`GRANT`/`REVOKE` per table now; schema-level once `ledger` schema lands) + narrow service views for any cross-tenant reporting need; app roles get `INSERT/SELECT` grants only (§4.5) |

```sql
-- Tenant-facing table: two-level RLS (unchanged pattern, now scoped correctly)
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_tenant_branch ON payments USING (
  tenant_id = current_setting('app.tenant_id')::uuid
  AND (current_setting('app.branch_id', true) IS NULL
       OR branch_id = current_setting('app.branch_id')::uuid));
-- Background workers that legitimately need cross-tenant read on THIS table get a narrow, explicit policy:
CREATE POLICY p_system_relay ON payments FOR SELECT USING (
  current_setting('app.role', true) = 'system_relay');

-- Queue table: NO RLS. Ownership + grants instead — simpler, faster, no GUC dependency for background workers.
-- Per-schema pattern (ADR-N5) — repeated once per owning module, e.g. payment.outbox_events:
REVOKE ALL ON payment.outbox_events FROM PUBLIC;
GRANT INSERT, SELECT ON payment.outbox_events TO payment_role;                    -- owner writes/reads only its own
GRANT SELECT, UPDATE (published_at) ON payment.outbox_events TO outbox_dispatcher_role;  -- dispatcher claims/marks-published only, never inserts
-- (same pattern for every module's own outbox_events; inbox_events grants owner-only INSERT/SELECT, no dispatcher grant needed)

-- Ledger base tables: NO RLS. Table ownership is the boundary; app_role gets INSERT/SELECT only (§4.5).
-- (Schema-flat for now, like the rest of this document — see §4.5 note on deferred schema creation.)
REVOKE ALL ON journal_lines, journal_entries, liquidity_accounts FROM PUBLIC;
GRANT SELECT, INSERT ON journal_lines, journal_entries TO app_role;   -- no UPDATE/DELETE, ever
GRANT SELECT, UPDATE ON liquidity_accounts TO app_role;               -- current-balance updates are legitimate here
```
**Consequence for G3 (background identity):** the *service-role identity* rule from §3.5 still applies, but it now has two different jobs depending on the table: on **RLS-protected tenant tables**, the worker's session sets `app.role` and a narrow `p_system_*` policy grants exactly what it needs (as above); on **queue/ledger tables**, there is no RLS to satisfy at all — the worker's DB role simply has (or lacks) a `GRANT`, checked at connection time, not per-row. This is *simpler* than the original "RLS everywhere" design, not an exception to it.



### 4.8 PG19 upgrade notes per table (the ready seams)
`[SELF-CORRECT, deep-research applied]` `idempotency_keys` → `ON CONFLICT DO SELECT` is **lab-only, not a near-term swap**: PG19 is beta, so the PG18 two-step (§4.2) is the MVP path behind `IdempotencyStore` indefinitely, until PG19 reaches GA and is re-evaluated — this is a demotion from how this document previously described it. Partitioned tables (`payment_events`, `journal_lines`, `audit_log`, `raw_inbound_messages`) → `MERGE/SPLIT PARTITIONS` in the maintenance runbook + `REPACK CONCURRENTLY` for bloat (also lab-only until GA). `reference-data` validity tables (`tstzrange` + gist exclusion today) → `FOR PORTION OF` after trigger/FK testing. Read models → `IGNORE NULLS` window funcs; replica reads → `WAIT FOR LSN`. Retest reconciliation timings with JIT-off default. BM25 (`pg_search`/ParadeDB) stays FTS-first, extension-later — not a core PostgreSQL feature at any version.

### 4.13 Reference data & catalogs (`reference-data`-owned)

`[ADD]` `reference-data` is the **only** writer of these catalogs; `iso-adapter`, `payment-lifecycle`, `routing`, `settlement`, `egress` **read** them (and `iso-adapter` records which version it used — it never owns them). Status/reason codes referenced from `payment_status_history` and `iso.iso_message_validation_results` are FK values into these tables, never re-catalogued elsewhere.

```sql
-- CREATE SCHEMA reference_data; owner role: reference_data_role  (created with this iteration)
CREATE TABLE reference_data.participants (            -- [MVP] bank/participant directory
  id uuid PRIMARY KEY DEFAULT uuidv7(), bic text, name text NOT NULL,
  scheme_membership text[], valid_from date NOT NULL, valid_to date );
CREATE TABLE reference_data.participant_accounts (    -- [MVP] read by ledger/routing
  participant_id uuid NOT NULL REFERENCES reference_data.participants(id),
  currency char(3) NOT NULL, account_ref text NOT NULL, PRIMARY KEY (participant_id, currency) );
CREATE TABLE reference_data.status_catalog (          -- [MVP] valid status codes per message/lifecycle stage
  status_code text NOT NULL, message_type text, lifecycle_stage text,
  description text, valid_from date NOT NULL, valid_to date, PRIMARY KEY (status_code, valid_from) );
CREATE TABLE reference_data.iso_reason_codes (        -- [MVP] reason codes per scheme/version/date
  reason_code text NOT NULL, family text, scheme_profile_id uuid, description text,
  valid_from date NOT NULL, valid_to date, PRIMARY KEY (reason_code, valid_from) );
CREATE TABLE reference_data.scheme_profiles (         -- [MVP] EPC/KIR/STET/TIPS/STEP2-like
  id uuid PRIMARY KEY DEFAULT uuidv7(), profile_code text NOT NULL,   -- e.g. TIPS_LIKE, STET_LIKE
  family text NOT NULL,                               -- GROSS_INSTANT | NET_DEFERRED
  service_level text, sla_seconds int, netting_mode text,
  valid_from date NOT NULL, valid_to date );
CREATE TABLE reference_data.business_calendars (      -- [P1] TARGET2 (EUR) + Polish (PLN) holidays; cut-off sessions
  calendar_code text NOT NULL, business_date date NOT NULL, is_business_day boolean NOT NULL,
  session_no smallint, cutoff_at timestamptz(3), PRIMARY KEY (calendar_code, business_date, session_no) );
CREATE TABLE reference_data.settlement_cutoff_calendar (  -- [P1] owned here (not settlement) — one source of cut-offs
  profile_id uuid NOT NULL, business_date date NOT NULL, session_no smallint NOT NULL,
  cutoff_at timestamptz(3) NOT NULL, PRIMARY KEY (profile_id, business_date, session_no) );
```
`[DB-OWNERSHIP]` deterministic ISO version selection reads here: `message_type + scheme_profile + direction + business_date → iso.iso_message_versions`. Cut-off queries (`settlement`, `ingress`) use the single primitive "next cut-off after(t)" over `settlement_cutoff_calendar` — no duplicated calendars.

#### 4.13a Validation / mapping / render profile catalogs `[ADD][R-09, closed]`

`[MVP]` `iso.iso_message_versions` (§4.3c) selects these by *code* (`validation_profile_code`, `mapping_profile_code`); the codes must resolve to something. These three catalogs close that gap. `[NO-CODE]` table summary only — full DDL lands with the iteration that implements ISO validation (Iteration 5, per the routing/ISO epics), not here.

| Table | Owner | Purpose | Key Columns | MVP/P1/P2 |
|---|---|---|---|---|
| `reference_data.validation_profiles` | `reference-data` | which XML-schema/structural/scheme-profile/mapping checks apply, per message type + version + date | `validation_profile_code` (PK), `message_type`, `applies_from`, `applies_to`, `rule_set_ref` | `[MVP]` |
| `reference_data.mapping_profiles` | `reference-data` | ISO→canonical field-mapping ruleset selected per message type + version + date | `mapping_profile_code` (PK), `message_type`, `applies_from`, `applies_to`, `mapper_ref` | `[MVP]` |
| `reference_data.render_profiles` | `reference-data` | canonical→ISO rendering ruleset for outbound artifacts (pacs.002/pain.002-style/camt.029/pacs.004) | `render_profile_code` (PK), `artifact_type`, `applies_from`, `applies_to`, `renderer_ref` | `[P1]` (needed once outbound ISO rendering beyond status-JSON lands) |

`[SYNTHETIC]` One row of each is seeded for `JSON_DIRECT` (§2.2a) at platform bootstrap: a trivial pass-through `validation_profile_code`/`mapping_profile_code` that `iso.iso_message_versions(message_type='JSON_DIRECT')` points at, so the JSON channel resolves through the exact same catalog lookup as every ISO channel.

### 4.10 Routing & participant reachability (`routing` + `reference-data`)

`[ADD]` Routing is a **route-resolution pipeline** (§3.9): `payment → candidate profiles → eligibility → per-profile reachability → amount/currency/service-level → cut-off/cycle (read from settlement) → liquidity precheck (P1) → route decision → settlement basis → egress mode (P1)`. It **selects a profile and records the decision** — no settlement, booking, finality or egress. `[SYNTHETIC]` access/reachability concepts (`DIRECT`/`INDIRECT`/`INTERNAL`/`REACHABLE_PARTY`/`ADDRESSABLE`) are synthetic abstractions of public patterns, **not** FMI replicas `[DO-NOT-COPY]`.

**Static (reference-data) vs runtime (routing/settlement):** eligibility, capabilities, allowed message set, settlement basis, timeout policy, fallback/priority config → **static, `reference-data`**. Reachability status, route decision → **runtime, `routing`**. Cut-off/cycle/liquidity state → **runtime, `settlement`/`ledger`** (routing **reads** via ports, never recomputes). **Fallback is explicit config only — no implicit instant→batch in MVP** `[REJECT]`.

```sql
-- reference-data eligibility catalogs (static; owner reference_data_role)
CREATE TABLE reference_data.participant_capabilities (   -- [MVP] access mode + supported profiles
  participant_id uuid NOT NULL, profile_id uuid NOT NULL,
  access_mode text NOT NULL CHECK (access_mode IN ('DIRECT','INDIRECT','INTERNAL','ADDRESSABLE','SERVICED')),
  PRIMARY KEY (participant_id, profile_id) );
CREATE TABLE reference_data.participant_eligibility_rules (  -- [MVP] objective per-profile rules
  profile_id uuid NOT NULL, rule_code text NOT NULL, rule_value text, PRIMARY KEY (profile_id, rule_code) );
CREATE TABLE reference_data.profile_fallback_rules (     -- [MVP] EXPLICIT ordered fallback
  profile_id uuid NOT NULL, fallback_profile_id uuid NOT NULL, priority smallint NOT NULL,
  condition text, PRIMARY KEY (profile_id, priority) );
CREATE TABLE reference_data.profile_route_priorities (   -- [MVP] candidate ordering
  scheme text NOT NULL, service_level text, currency char(3), profile_id uuid NOT NULL, priority smallint NOT NULL,
  PRIMARY KEY (scheme, service_level, currency, profile_id) );

-- routing runtime (owner routing_role)
CREATE TABLE routing.participant_reachability (          -- [MVP] runtime, PER-PROFILE (never global)
  participant_id uuid NOT NULL, profile_id uuid NOT NULL,
  status text NOT NULL CHECK (status IN ('REACHABLE','UNAVAILABLE','DEGRADED')),
  reachability_type text CHECK (reachability_type IN ('DIRECT','INDIRECT','REACHABLE_VIA_PARTICIPANT','ADDRESSABLE')),
  as_of timestamptz(3) NOT NULL, PRIMARY KEY (participant_id, profile_id) );
CREATE TABLE routing.route_decisions (                   -- [MVP] immutable
  id uuid PRIMARY KEY DEFAULT uuidv7(), payment_id uuid NOT NULL, tenant_id uuid NOT NULL,
  selected_profile_id uuid,
  outcome text NOT NULL CHECK (outcome IN ('ROUTE_SELECTED','FALLBACK_SELECTED','PARTICIPANT_UNREACHABLE',
    'PROFILE_NOT_ELIGIBLE','AMOUNT_LIMIT_EXCEEDED','CURRENCY_NOT_SUPPORTED','CUTOFF_REACHED','CYCLE_CLOSED',
    'LIQUIDITY_MODE_UNAVAILABLE','ROUTE_FAILED','ROUTE_PENDING_INVESTIGATION')),
  settlement_basis text CHECK (settlement_basis IN ('GROSS_INSTANT','NET_DEFERRED','INTERNAL_BOOK')),
  scope text CHECK (scope IN ('INTRA_BRANCH','INTER_BRANCH','INTERBANK')),
  fallback_applied boolean NOT NULL DEFAULT false, fallback_rule_id uuid,
  decision_reason_code text, decided_at timestamptz(3) NOT NULL );   -- no UPDATE/DELETE grant → immutable
CREATE INDEX rd_payment ON routing.route_decisions(payment_id);
CREATE TABLE routing.route_candidate_results (           -- [MVP] why each candidate passed/failed
  decision_id uuid NOT NULL REFERENCES routing.route_decisions(id), profile_id uuid NOT NULL, seq smallint NOT NULL,
  passed boolean NOT NULL, reject_reason text, PRIMARY KEY (decision_id, profile_id) );
CREATE TABLE routing.route_decision_explanations (       -- [MVP-BLOCKER] immutable snapshot
  decision_id uuid PRIMARY KEY REFERENCES routing.route_decisions(id),
  explanation jsonb NOT NULL,                             -- full pipeline trace: candidates, checks, cut-off read, fallback
  reference_data_version text NOT NULL, sim_scenario_id uuid, created_at timestamptz(3) NOT NULL );
```
`[MVP-BLOCKER]` every decision writes a `route_decision_explanations` snapshot (selected + candidates + per-candidate reasons + reachability + eligibility + amount/ccy/service + cut-off/cycle read + fallback + reason + timestamp + role + sim scenario); `payment.routed` carries its id. `[DB-OWNERSHIP]` routing writes only `routing.*`, reads `reference_data.*` + settlement cut-off/cycle via ports; **cut-off is not stored in routing** (no duplication). Outcomes: `ROUTE_SELECTED`/`FALLBACK_SELECTED`→proceed; `PARTICIPANT_UNREACHABLE`/`PROFILE_NOT_ELIGIBLE`/`AMOUNT_LIMIT_EXCEEDED`/`CURRENCY_NOT_SUPPORTED`/`CUTOFF_REACHED`/`CYCLE_CLOSED`→reject or (batch) next cycle; `ROUTE_FAILED`→`route.failed` Kafka; minor outcomes are internal Modulith events (no topic per profile). A routing outcome is a *decision*; the payment status change that follows is `payment-lifecycle`'s.

### 4.11 Settlement strategy & liquidity (`settlement` + `ledger` + `reference-data`)

`[ADD]` The binding chain: `route_decision → selected_profile → settlement_basis → liquidity_mode → strategy → LedgerPort → finality_rule`. **Strategy is resolved by `(settlement_basis, liquidity_mode)`, never by profile/CSM name** `[MVP-BLOCKER]` — a profile is config carrying a basis+mode+finality rule; TIPS-/RT1-/STEP2-/Euro-Elixir-like are **rows**, not classes. A `switch` on profile name is forbidden by ArchUnit test. `[REJECT]` per-CSM engines (`Tips/Rt1/Step2/Stet/KirSettlementEngine`).

**Strategy taxonomy:**

| Strategy | settlement_basis | Behaviour | Finality rule | Priority |
|---|---|---|---|---|
| `GrossInstantStrategy` | `GROSS_INSTANT` | reserve→post per payment, immediate | `ON_LEDGER_POST` | `[MVP]` |
| `NetDeferredStrategy` | `NET_DEFERRED` | assign cycle→net→settle positions | `ON_CYCLE_SETTLED` | `[MVP]` |
| `InternalBookStrategy` | `INTERNAL_BOOK` | internal post, no external rail | `ON_INTERNAL_BOOK_POST` | `[MVP]` |
| `FileBatchStrategy` | `ACH_FILE_BATCH` | file/cycle assignment, settle later | `ON_CYCLE_SETTLED` | `[MVP]` |
| `BulkCgsLikeStrategy` | `BULK_CGS_LIKE` | queue-until-cut-off, gross-settle when funded | `ON_LEDGER_POST` | `[P1]` |
| `PrefundedInstantStrategy` | `SIMULATED_PREFUNDED_INSTANT` | reserve against prefund/collateral | `ON_LEDGER_POST` after prefund | `[P1]` |

**Liquidity-mode taxonomy** (owned by `reference-data`, evaluated via `LedgerPort`): `[MVP]` `DCA_POOL` (gross), `ISOLATED_SUBACCOUNT` (deferred/file), `CENTRAL_BANK_MONEY_LIKE`/`COMMERCIAL_BANK_MONEY_LIKE` (risk labels), `NONE_INTERNAL` (internal book); `[P1]` `PREFUNDED_RESERVE`, `TECHNICAL_ACCOUNT_LIKE`, `LAB_SYNTHETIC`. `[SYNTHETIC]` all are educational labels; `CENTRAL_BANK_MONEY_LIKE` claims no legal central-bank-money equivalence `[DO-NOT-COPY]`.

**Attempt lifecycle:**
```
INITIATED → ACCEPTED (profile/basis/mode checks; ≠ finality)
  gross:    → RESERVED → POSTED → FINAL (per finality_rule)
            reserve fails → INSUFFICIENT_LIQUIDITY → reject (MVP) | queue (P1)
  deferred: → CYCLE_ASSIGNED → (cycle nets+settles) → FINAL
            cycle CLOSING/CLOSED → MOVED_TO_NEXT_CYCLE | REJECTED
  cgs[P1]:  → QUEUED → (funded) POSTED → FINAL | (cut-off) CANCELLED_AT_CUTOFF
  internal: → POSTED → FINAL (ON_INTERNAL_BOOK_POST)
  any:      → SETTLEMENT_FAILED_TECHNICAL | SETTLEMENT_PENDING_INVESTIGATION[P1]
```
Every transition writes `settlement_attempt_events` + audit; `FINAL` writes `settlement_finality_records` and sets `payment.finality_at` via the payment-lifecycle port.

**LedgerPort contract (the only money path)** `[MVP-BLOCKER]`: `reserve(account,amount)→reservationId|INSUFFICIENT` (row-lock, available→reserved, RESERVE entry); `post(...)` (balanced POST entry, deferred Σ=0 trigger, decrement reserved); `release(reservationId)` (pre-finality cancel); `reverse(entryId,reason)` (**internal booking-error correction before finality only — never a business return**). `settlement_role` has **no** write grant on `ledger.*`.

**Finality model** `[FINALITY-RISK]`: applied from the profile's `finality_rule` — `ON_LEDGER_POST` (gross/internal), `ON_CYCLE_SETTLED` (deferred/file), `ON_NET_POSITION_SETTLED` (P1), `ON_INTERNAL_BOOK_POST`. **Rejected:** `ON_CSM_ACCEPTED` (core), `NO_FINALITY_UNTIL_CONFIRMATION` (egress) `[DO-NOT-COPY]`. `SETTLEMENT_ACCEPTED`/`POSTED` and egress `DELIVERED`/`CONFIRMED` are **never** finality unless the rule makes the post the finality moment. **Insufficient liquidity** is not a uniform reject — outcome depends on `settlement_basis + liquidity_mode + cut-off/cycle state` (gross→reject or short-queue P1; deferred→next cycle; CGS→queue-until-cut-off P1).

**Config vs runtime** `[DB-OWNERSHIP]`: `settlement_basis`/`liquidity_mode`/`finality_rule`/`timeout_policy`/`cut-off calendar` → static, `reference-data`; `settlement_attempts`/`cycles`/`positions`/`queue`/cut-off runtime state → `settlement`; `liquidity_accounts`/`reservations`/`journal_*`/`balances` → `ledger`. Settlement freezes a `settlement_profile_snapshots` row per attempt (basis+mode+finality + reference-data version) so every attempt is replayable against the policy that applied at the time.

---

### 4.12 Reconciliation & exception handling (`reconciliation` + `reference-data`)

`[ADD]` Reconciliation is a **read-only detection-and-escalation module** (§3.12): `as_of snapshot → collect evidence → compare expected vs actual → classify mismatch → result → exception → severity → evidence bundle → escalate to operator/case/action-request`. It writes only `reconciliation.*` and never mutates `payment`/`settlement`/`ledger`/`egress`/`iso`/`case`. `[MVP-BLOCKER]` every run is anchored on a deterministic `as_of` watermark (one run = one `as_of`, one filter set, one snapshot reference) so runs are reproducible and auditable; a rerun references `original_run_id` and keeps results immutable.

**Reconciliation types:** `[MVP]` settlement-vs-ledger (`MISSING`/`DUPLICATE_LEDGER_POSTING`, `MONEY_MISMATCH`, `FINALITY_MISMATCH`), ledger-vs-balance-snapshot (`SILENT_MONEY_DRIFT`), payment-status-vs-finality (`STATUS_MISMATCH`, `FINALITY_MISMATCH`), settlement-cycle-vs-positions (`CYCLE_POSITION_MISMATCH`); `[P1]` egress-artifact-vs-outcome, delivery-receipt-vs-artifact, case-return-vs-return-payment, ISO-lineage-vs-payment/case, report/result-file-vs-state.

**Evidence bundle = pointers, not copies** `[RECON-RISK]`: each `evidence_pointer` is `(source_module, source_schema, source_table, source_id, as_of, captured_hash?)` — proving what was compared without duplicating or drifting from the source.

**Mismatch taxonomy + severity owned by `reference-data`** (versioned, snapshot per run): severity scale `INFO < WARNING < OPERATIONAL(4h) < FINANCIAL_RISK(60m) < FINALITY_RISK(30m) < LEDGER_RISK(30m, freeze-for-investigation-no-auto-fix) < COMPLIANCE_RISK < CRITICAL(immediate)`. The money-critical types `MISSING_LEDGER_POSTING`(LEDGER_RISK) and `FINALITY_MISMATCH`/`DUPLICATE_LEDGER_POSTING`/`SILENT_MONEY_DRIFT`(CRITICAL) escalate immediately, never auto-fix.

**Run lifecycle:** `REQUESTED→SNAPSHOT_CREATED→EVIDENCE_COLLECTED→COMPARED→RESULTS_RECORDED→EXCEPTIONS_CREATED→COMPLETED` (failure: `EVIDENCE_COLLECTION_FAILED→PARTIAL_RESULTS_RECORDED→RETRY_REQUIRED|MANUAL_INVESTIGATION`). **Exception lifecycle:** `DETECTED→CLASSIFIED→ASSIGNED→INVESTIGATING→ACTION_RECOMMENDED→ACTION_REQUESTED→RESOLVED→CLOSED` (alt: `SUPPRESSED_DUPLICATE`/`FALSE_POSITIVE`/`REOPENED`/`ESCALATED`). `[RECON-RISK]` `ACTION_REQUESTED` is a **request** to another module/operator (rerun egress, open case, verify posting) — never a source mutation; the target acts through its own normal path. Operator actions (assign/comment/resolve/false-positive/suppress) are role-gated REST/gRPC admin commands, never GraphQL mutations.

**Config vs runtime** `[DB-OWNERSHIP]`: reconciliation profile / mismatch taxonomy / severity rules / SLA-ageing / schedule / evidence-source-list / duplicate-suppression-rule → **static, `reference-data`**; run / result / exception / false-positive-decision / operator-assignment → **runtime, `reconciliation`**. Tables (`reconciliation` schema): `reconciliation_profiles_snapshot`, `reconciliation_runs`, `reconciliation_run_sources`, `reconciliation_results` (immutable), `reconciliation_exceptions`, `exception_events`, `evidence_bundles`, `evidence_pointers`; `[P1]` `action_requests`, `exception_comments`, `exception_assignments`. All `source_*` columns are read-only references. Events: `reconciliation.run.completed`/`run.failed`/`exception.detected` are Kafka; granular run/exception steps are internal Modulith events; no topic per reconciliation type.

### 4.14 Case R-message model (`case` + `reference-data`)

`[ADD]` Deepens the `case` module (§3.13) with the R-message domain. Pipeline: `incoming R-message/internal trigger → resolve case type → validate against payment/settlement/finality state (as_of) → collect evidence (pointers) → apply reference-data case rules → make decision → request outbound via egress / request return via normal path / link reconciliation → close/escalate/expire`.

**ISO message matrix:** `[MVP]` `pacs.002` RJCT→`PAYMENT_REJECT_CASE`, `camt.056`→`RECALL_REQUEST_CASE`, `camt.029`→resolution (positive→return, negative→close/escalate), `pacs.004` inbound→return received + original `RETURNED`; `[P1]` `pacs.028` (case-generated status request when a recall answer is missing), `pain.002` (customer status); `[P2]` `camt.027` (claim non-receipt), `camt.087` (request-to-modify). `[ISO-LINEAGE-RISK]` every R-message is parsed/correlated by `iso-adapter` (correlation types `CAMT056_TO_PAYMENT`/`CAMT029_TO_CASE`/`PACS004_TO_PAYMENT`, §4.3c); `case` consumes the correlation result and never parses ISO itself.

**Case type taxonomy** (reference-data catalog): `[MVP]` `PAYMENT_REJECT_CASE`, `RECALL_REQUEST_CASE`, `RETURN_INBOUND_CASE`; `[P1]` `RETURN_OUTBOUND_CASE`, `STATUS_REQUEST_CASE`, `DUPLICATE_R_MESSAGE_CASE`, `CONFLICTING_R_MESSAGE_CASE`, `RECONCILIATION_LINKED_CASE`; `[P2]` `INVESTIGATION_CASE`. One `Case` aggregate, `case_type` discriminator.

**Decision outcomes:** `CASE_ACCEPTED`, `CASE_REJECTED`, `CASE_DECISION_POSITIVE` (→ request return / pacs.004), `CASE_DECISION_NEGATIVE` (→ camt.029 RJCR), `RETURN_PAYMENT_REQUIRED` (→ request new payment), `OUTBOUND_RESPONSE_REQUIRED` [P1], `CASE_DUPLICATE_SUPPRESSED` [P1], `CASE_WAITING_FOR_COUNTERPARTY` [P1], `OPERATOR_REVIEW_REQUIRED` [P1], `RECONCILIATION_EXCEPTION_LINKED` [P1], `CASE_EXPIRED` [P2], `CASE_CLOSED`. Every money/ISO effect is a **request**, never a direct write.

**Duplicate / conflicting R-messages** `[P1]` `[CASE-RISK]`: duplicate (same R-message re-received) → `CASE_DUPLICATE_SUPPRESSED` (dedupe on original+type+reason, link to existing case); conflicting (e.g. a return and a reject for the same event) → `CONFLICTING_R_MESSAGE_CASE` → **always operator escalation, never auto-resolve**.

**Config vs runtime** `[DB-OWNERSHIP]`: case-type catalog / R-message-rule catalog / allowed reason+status codes / response-SLA+timeout / validation rules → **static, `reference-data`**; case instance / decision / evidence bundle / operator assignment / outbound-response request / return-payment request → **runtime, `case`**. Tables (`"case"` schema, per Case Module blueprint + this patch): `cases`, `case_events`, `case_decisions`, `case_evidence_bundles`, `case_evidence_pointers`, `case_r_message_links`; `[P1]` `case_action_requests`, `case_operator_assignments`, `case_comments`, `case_duplicate_links`. All cross-schema ids are read-only references; case writes only `"case".*`. Events: `case.opened`/`case.resolved`/`case.closed`/`case.escalated`/`case.return_payment_requested` are Kafka; granular steps internal; no topic per case type.

---

## 5. Clearing & Settlement mechanics (the "calculate → book → settle" chain, precisely)

The exact order of operations per family — this is the paragraph a story writer needs verbatim.

**GROSS_INSTANT (TIPS/Express-Elixir-like):** on `payment.routed`: (1) open TX; (2) `reserve(debtor, amount)` — row-lock account, check `available ≥ amount`, move to `reserved`, journal RESERVE entry; insufficient → journal RELEASE-nothing, attempt=REJECTED, status RJCT out, close TX; (3) `post(...)` — journal POST entry (debit debtor available/reserved, credit creditor available), attempt=POSTED; (4) write `payment.status.reported(ACSC)` to outbox; (5) commit. SLA clock checked at (4): breach emits `payment.sla.breached` (status still valid — SLA breach is telemetry, not rejection). Crash between any steps = TX rollback = nothing happened; the Kafka redelivery replays cleanly through the inbox gate. **Settlement finality:** `finality_at` set in step (4) — after commit, no application path may reverse; corrections are new compensating entries.

**`[DECISION]` Returns & recalls after finality = a new payment, never a reversal.** When a recall is accepted or a return arrives against a payment whose `finality_at` is set, the original settlement is **not** touched: the `case` module requests a **new, opposite-direction return payment** (original creditor → original debtor) that settles independently through this same path with its own `finality_at`; the original's `business_status_code` becomes `RETURNED` via the payment-lifecycle port, but its journal lines stay byte-identical. The ledger `REVERSAL` type is **only** for correcting our own booking errors *before* finality — never for a business return. (Pre-finality recall — `finality_at IS NULL` — instead cancels via lifecycle + `LedgerPort.release`.) Enforced: `case` has no `ledger.*` write grant and no reachable `LedgerPort.reverse` path (§3.6.3). Full design in the Case Module blueprint.

**`[ownership]` Finality is a settlement/payment concern, never an egress one.** `egress` delivering or confirming a status message (`DELIVERED`/`CONFIRMED`) is **transport state**, not settlement finality — a confirmed pacs.002 does not move `finality_at`, and a failed delivery does not un-settle a payment. `egress` may never write `payments.status` or `settlement_*`; the two state machines are independent by design (see §3.6.1 rule 5, §6.2b).

**NET_DEFERRED (STET/Elixir-like):** acceptance assigns item to the open cycle (under cycle-row lock, G6). At `cutoff_at` the scheduler flips OPEN→CLOSING (new arrivals roll to next cycle), drains in-flight assignments, flips CLOSED; **netting** = the single INSERT…GROUP BY into `settlement_positions` (P8); **settlement** = one journal batch entry per participant net (Σ of the batch = 0 by construction — asserted); cycle→SETTLED; per-payment ACSC statuses fan out via outbox; reconciliation run (REPEATABLE READ, G9) re-derives positions from `journal_lines` and compares — any drift = mismatch row with severity. Cycle→RECONCILED.

**Clearing vs settlement, named `[EDU]`:** clearing = everything through `settlement_positions` (information exchange + obligation computation); settlement = the journal batch (obligation discharge). The lab keeps them as two visibly distinct steps because conflating them is the most common domain misunderstanding.

---

## 6. Outbound: collecting and dispatching to bank / institution / clearing house / branch

Mirror-image of §2, Event-Stormed, with the **egress outbox** (G2) at the center.

### 6.1 What gets sent, when, to whom

| Trigger | Outbound artifact | Recipient | Mode |
|---|---|---|---|
| Instant settle/reject | pacs.002 (ACSC/RJCT) | submitting PSP/bank | per-message, immediate |
| Deferred cycle settled | pacs.002 fan-out per payment | each submitter | per-message, batched emission |
| File processed | **result file** (per-item pain.002-style statuses) | file submitter | per-file |
| We forward interbank CT | pacs.008 | CSM (simulated) / correspondent | per-message, signed |
| Cycle settled (CSM side) | net-position advice + statement (JSON MVP; camt.053 later) | participants | per-cycle |
| Recall etc. (post-MVP) | camt.056/029, pacs.004 | counterparty | per-message |

### 6.2 The egress pipeline (per-message rail)

```
[Event] payment.status.reported / settlement.completed (Kafka, internal)
  → EgressAssembler (@KafkaListener): decide artifact type + recipient + egress_profile; freeze egress_profile_snapshot
  → idempotent create: outbound_artifacts UNIQUE(trigger_event_id, artifact_type, recipient) → REQUESTED
  → ArtifactRenderPort (iso-adapter renderer) → RENDERED → SignaturePort → SIGNED
  → ONE TX: insert/advance outbound_messages + audit          ← the egress outbox (G2)
  → EgressDispatcher (@Scheduled, system-role): claim batch FOR UPDATE SKIP LOCKED → CLAIMED_FOR_DELIVERY
       → deliver via TransportAdapter (HTTP webhook / file drop / internal topic)
       → OK  → DELIVERED (+ receipt ref); recipient ACK → RECEIPT_RECEIVED → CLOSED
       → ERR → DELIVERY_FAILED → transport_attempts.attempt_no++, next_retry_at=backoff → RETRY_SCHEDULED
                attempts > max → DEAD_LETTERED + MANUAL_INTERVENTION + DLQ + alert
```
Lifecycle `REQUESTED→RENDERED→SIGNED→CLAIMED_FOR_DELIVERY→DELIVERED→RECEIPT_RECEIVED→CLOSED` (failure branch: `DELIVERY_FAILED→RETRY_SCHEDULED→DELIVERED|DEAD_LETTERED|MANUAL_INTERVENTION`). `[FINALITY-RISK]` **this lifecycle is transport-only** — no state implies settlement finality or payment status; `SIGNED` is cryptographic not legal, `DELIVERED` is "handed to transport", `RECEIPT_RECEIVED` is a transport ACK. `FOR UPDATE SKIP LOCKED` makes the dispatcher horizontally safe (two dispatchers never double-send) — a deliberate `[EDU]` PostgreSQL lesson.

### 6.3 The collector (batch rail): assembling files/advices for the deferred world

`OutboundBatchCollector` (`@Scheduled` per profile calendar, system-role): query all `outbound_messages` in state PENDING with `batch_group != null` for the (recipient, profile, business_date) → build one file (Prowide multi-document) → sign the FILE → single `outbound_files` row (state machine identical to §6.2) → deliver → per-item states flip with the file. Result files from §2.3 ride the same collector. `[DECISION]` per-message vs batched is a **profile flag** (`emissionMode: IMMEDIATE|BATCHED`), not code branching — consistent with profiles-not-engines.

### 6.4 Egress tables

```sql
CREATE TABLE outbound_messages (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  tenant_id uuid NOT NULL, branch_id uuid,
  recipient_id uuid NOT NULL, channel text NOT NULL,
  artifact_type text NOT NULL,                     -- PACS002|PACS008|RESULT_FILE_ITEM|ADVICE
  correlation_msg_id text NOT NULL,                -- our MsgId (their OrgnlMsgId)
  payment_id uuid, cycle_id uuid, file_id uuid,
  payload bytea NOT NULL, payload_sha256 bytea NOT NULL,
  signature bytea,
  state text NOT NULL DEFAULT 'PENDING',           -- PENDING|DELIVERED|CONFIRMED|FAILED|ABANDONED
  attempts smallint NOT NULL DEFAULT 0,
  next_retry_at timestamptz,
  batch_group text,                                -- null = immediate rail
  created_at timestamptz NOT NULL, delivered_at timestamptz
);
CREATE INDEX egress_todo ON outbound_messages(next_retry_at NULLS FIRST, created_at)
  WHERE state IN ('PENDING','FAILED');             -- P11
CREATE INDEX egress_corr ON outbound_messages(correlation_msg_id);
-- outbound_files mirrors inbound_files (sha256, msg_id, state, counts) — same shape, opposite direction
```

### 6.5 Branch routing on the way out
The routing trichotomy applies outbound too: intra-branch → no egress (internal booking only); inter-branch same bank → internal topic, no signature; interbank → full signed channel. Decided by `RoutingDecision.scope` recorded at routing time — egress only reads it.

### 6.6 Read models & GraphQL (read-only rule)

`[ADOPT]` **Write path = REST/gRPC/command APIs. Read path = GraphQL over read models. GraphQL is read-only in MVP** — no mutation resolver touches a domain repository; a dashboard/search view never becomes a domain write API. The module that owns the *source data* owns the *read-model definition*; `reporting` may host cross-module projections but only by **reading** events/read models, never by writing source tables. Dashboard live counters (egress pending/claimed/delivered/failed, cycle netting, mismatch severity, SLA p95) are projections, not authoritative state.

| Read model | Owner | Source | GraphQL query |
|---|---|---|---|
| `payment_timeline_view` | `payment-lifecycle`(+`reporting`) | `payment_status_history`, `payment_events`, `iso.payment_iso_identifiers` | `paymentTimeline(paymentId)` |
| `payment_detail_view` | `reporting` | `payments` + lineage + route decision + settlement attempt + egress state | `payment(id)` |
| `settlement_cycle_dashboard` | `settlement`(+`reporting`) | `settlement_cycles/positions/items` | `settlementCycle(id)` |
| `ledger_balance_view` | `ledger`(+`reporting`) | `liquidity_accounts`, `balance_snapshots`, `journal_lines` | `ledgerBalances(participantId)` |
| `reconciliation_mismatch_view` | `reconciliation` | `reconciliation_exceptions/runs` | `reconciliationExceptions` |
| `egress_delivery_dashboard` | `egress`(+`reporting`) | `outbound_messages`, `transport_attempts`, `delivery_receipts` | `egressDeliveries` |
| `csm_profile_dashboard` | `routing`(+`reference-data`) | `routing_profiles`, `participant_reachability`, `route_rules` | `routingProfiles` |
| `risk_signal_dashboard` | `risk` | `fraud_signals`, `anomaly_windows`, `vop_checks` | `riskSignals` |
| `simulation_replay_view` | `simulation` | `simulation_runs`, `generated_events`, payment events | `simulationRun(id)` |
| `participant_statement_view` | `reporting` | `statement_headers/lines`, settlement events | `statement(id)` |
| `message_lineage_timeline` | `iso-adapter`(+`reporting`) | `iso.message_lineage`, `iso.iso_messages`, `payment_status_history` | `messageLineage(paymentId)` |
| `payment_identifiers_panel` | `iso-adapter` | `iso.payment_iso_identifiers` | `paymentIdentifiers(paymentId)` |
| `raw_vs_parsed_evidence_view` | `evidence-audit`(+`iso-adapter`) | `raw_inbound_messages`, `iso.iso_messages`, validation tables | `messageEvidence(rawMessageId)` |
| `pacs002_correlation_view` | `iso-adapter` | `iso.iso_message_correlation`, identifiers | `pacs002Correlation(isoMessageId)` |
| `orphaned_message_queue` | `iso-adapter`(+`reporting`) | `iso.iso_message_correlation` where `ORPHANED` | `orphanedIsoMessages` |
| `iso_validation_error_view` | `iso-adapter` | `iso.iso_message_validation_results`, `iso.iso_message_parse_errors` | `isoValidationErrors` |
| `outbound_artifact_view` `[P1]` | `egress`(+`iso-adapter`) | `iso.iso_outbound_artifacts`, `outbound_messages` | `outboundArtifact(id)` |
| `replay_evidence_view` `[P1]` | `iso-adapter`(+`evidence-audit`) | `iso.iso_message_replay_log` | `messageReplay(runId)` |

`[ADD, ISO integration]` Replay, manual-correlation and reprocess are **REST/gRPC admin commands**, never GraphQL mutations.

### 6.7 Outbound ISO artifact lineage `[P1]`

`[ADD]` Split render from transport: `iso-adapter` renders (`iso.iso_outbound_artifacts`: `render_profile_id`, version snapshot, `payload_sha256`, `iso_message_id`) → `egress` delivers (`outbound_messages`: unified transport lifecycle §6.2, `REQUESTED→…→CLOSED`). FK links, **no field overlap**: rendering a pacs.002 / pain.002-style result file is lineage; delivering it is transport; the two are never the same row. Emit `iso.outbound.rendered` (internal event) then `egress.delivery.requested`. This closes the outbound half of the lineage chain (§4.3b) so a generated status message is traceable back to its source payment/settlement.

### 6.8 Egress profile model `[MVP]`

`[ADD]` Egress behaviour is selected by `egress_profile` (a `reference-data` catalog row), **never by CSM name** `[REJECT per-CSM engine]`. A profile carries `transport_type` (WEBHOOK|FILE_DROP|INTERNAL_TOPIC), `emission_mode` (IMMEDIATE|BATCHED — supersedes the old inline flag in §6.3), `retry_policy` (max attempts + backoff), `signing_required`, `allowed_artifact_types`, `receipt_expectation` (NONE|ASYNC_ACK|SYNC_ACK). Each artifact freezes an `egress.egress_profile_snapshots` row (profile + reference-data version) so delivery is replayable against the policy that applied — mirroring `settlement_profile_snapshots`. `[SYNTHETIC]` TIPS-/RT1-/STEP2-like transport behaviours are profile rows, not classes `[DO-NOT-COPY]`.

### 6.9 Outbound artifact taxonomy & triggers

| Artifact | Trigger (source event) | Renderer | Priority |
|---|---|---|---|
| `pacs.002` status report | `settlement.completed`/`failed`, `payment.status.reported` | iso-adapter | `[MVP]` |
| `pain.002`-style result file | `FileProcessed` (batch, §2.3) | iso-adapter + collector | `[MVP]` |
| status report (JSON, non-ISO) | ingress rejection, `payment.received`/`accepted` | egress templating | `[MVP]` |
| operator notification | `route.failed`, `egress.dead_lettered`, recon exception | egress templating | `[P1]` |
| outbound `pacs.008` (forward) | `payment.routed` (interbank forward) | iso-adapter | `[P1]` |
| outbound `camt.029` (recall resolution) | `case.resolved` | iso-adapter | `[P1]` |
| outbound `pacs.004` (return) | `ReturnInitiated` (case) | iso-adapter | `[P1]` |
| statement / `camt.053` | `settlement.completed` (cycle) | reporting + iso-adapter | `[P2]` |

`[MVP-BLOCKER]` artifact creation is **idempotent** on `UNIQUE(trigger_event_id, artifact_type, recipient_id)` — an event redelivered by Kafka produces the same one artifact, never a duplicate send. New egress tables: `outbound_artifacts`, `transport_attempts`, `egress_profile_snapshots`, `outbound_artifact_lineage` (→ `iso.iso_outbound_artifacts` + source), `manual_delivery_actions` (role-gated, audited operator resend/cancel — a REST/gRPC admin command, never a GraphQL mutation). All read-only references to `payment`/`settlement`/`ledger`/`iso`/`case`; egress writes only `egress.*`.

---

## 7. Business view (SLA, actors, failure cost — the prioritization input)

| Stage | Actor | SLA class | Cost of failure | Priority signal |
|---|---|---|---|---|
| Accept (REST) | PSP/bank system | <300 ms p95 | retry storms, duplicate risk | P0 — idempotency first |
| File accept | corporate/gov ops | <2 s | resubmission confusion | P0 |
| Instant settle | automated | <10 s e2e (profile-configurable) | SLA breach visible to counterparty | P0 |
| Status out (egress) | counterparty systems | <5 s from final status | counterparty timeout → investigation | **P0 — why G2 is High** |
| Cycle close+net | scheduler/operator | at cut-off ±30 s | whole session slips | P1 |
| Result file | file submitter | <15 min from processed | manual reconciliation at client | P1 |
| Reconciliation | operator | daily, before next session | silent money drift | P1 |
| Statements/advices | participants | end of business date | reporting complaints | P2 |

---

### 7.1 Observability inventory `[ADD][R-17, closed]`

`[HLD-GAP, closed]` The three-ID correlation model (`traceId`/`paymentTraceId`.`correlationId`) and the segmented latency budget were designed in prose across several sections but never consolidated into one inventory. This table is that inventory; it is the Iteration 0/1/2 wiring checklist.

| Area | Required IDs | Logs | Metrics | Traces | Alerts | MVP/P1/P2 |
|---|---|---|---|---|---|---|
| Ingress | `traceId`, `correlationId`, `paymentId`, `fileId` | structured JSON, verdicts | accept p95 (<300ms), rejects by reason, file throughput | span per stage | reject-rate spike | `[MVP]` |
| ISO lineage | `isoMessageId`, `paymentId` | parse/validation verdicts | parse/validation counters, orphan rate | consumer spans | orphan queue depth | `[MVP]` |
| Lifecycle | `paymentId`, `correlationId` | transition log | illegal-transition meter, status latency | consumer spans | illegal-transition > 0 | `[MVP]` |
| Segmented latency budget | `traceId`, `paymentId` | — | `ingress→validate→route→settle→pacs002→e2e` histograms | one continuous trace | e2e p95 breach | `[MVP]` (Iter 2) |
| Kafka | `traceId` (propagated via headers) | consumer logs | **lag per consumer group** (first-class), retry count, DLQ depth | header propagation | DLQ depth > 0 on `csm.response.received` and reconciliation topics | `[MVP]` (Iter 3) |
| Ledger | `ledgerJournalId`, `paymentId` | entry log | drift gauge (snapshot vs derived), reserve latency | — | drift ≠ 0 = CRITICAL | `[MVP]` |
| Settlement | `settlementAttemptId`, `paymentId` | cycle log | cycle-close ±30s, insufficiency rate | cycle span | close overdue | `[MVP]` |
| Egress | `outboundArtifactId`, `paymentId` | attempt log | pending/claimed/delivered/failed, retry depth, receipt lag | dispatch span | `egress.dead_lettered` > 0; status-out > 5s | `[MVP]` |
| Reconciliation | `reconciliationRunId`, `exceptionId` | run log | run duration, exceptions by severity, ageing vs SLA | run span | CRITICAL exception → immediate; `reconciliation.run.failed` | `[MVP]` |
| Case | `caseId`, `paymentId` | decision log | open-case ageing, escalations | — | expired cases | `[P1]` |
| Simulation | `scenarioRunId` | scenario log | injected-anomaly counts, run duration | scenario trace | — | `[MVP]` |
| Security / audit | `correlationId`, actor/role | authz-denied log | denied count | — | audit-write failure = page | `[MVP]` |

**Rule:** every entity id above is attached to every log line and event where that entity is present — a payment-related log line without `paymentId` is a defect. Consolidates and closes the observability fragmentation flagged in the review (R-17).

### 7.2 Read models and admin command inventory `[ADD][R-18][R-19][R-20, closed]`

**Missing read models, now added:**

- `operatorWorklist` — a single composite projection over ISO orphans, reconciliation exceptions, egress dead-letters, and case queues, so an operator has one worklist instead of four separate screens to check. Owned by `reporting`, read-only, `[P1]` (the underlying per-module queues are `[MVP]`; the composite view is scheduled with the case module).
- `inboundFile(fileId)` / `inboundFiles` — file-level status + item counts + result-file link, mirroring the outbound `outboundArtifact(id)` read model that already exists. Owned by `ingress`(+`reporting`), `[MVP]` (the file rail itself is `[MVP]`; this read model was the one missing piece).

**Admin command inventory** — every "role-gated REST/gRPC admin command, never GraphQL mutation" mentioned across this document, now listed in one place:

| Command | Module | REST Endpoint Sketch | Role | Idempotency-Key? `[ADD, security-review]` | Audit Required? | Iteration |
|---|---|---|---|---|---|---|
| Submit payment / file | `ingress` | `POST /api/v1/payments`, `POST /api/v1/files` | `payment_initiator` / integration client | **required** | yes | 1 |
| Launch simulation scenario | `simulation` | `POST /api/v1/simulations/{profile}` | `sim_operator` | recommended | yes | 3 |
| Close settlement cycle (manual override) | `settlement` | `POST /api/v1/settlement/cycles/{id}/close` | `cycle_operator` | recommended | yes | 4 |
| Assign / resolve / mark-false-positive / suppress reconciliation exception | `reconciliation` | `POST /api/v1/reconciliation/exceptions/{id}/assign`, `/resolve`, `/false-positive`, `/suppress` | `recon_operator` | recommended | yes | P1 |
| Resend / cancel / force-close outbound artifact | `egress` | `POST /api/v1/egress/artifacts/{id}/resend`, `/cancel` | `egress_operator` | **required** (resend — retries an external send) | yes | 2 (resend) / P1 (cancel/force-close) |
| Resolve recall (accept/reject), escalate, close case | `case` | `POST /api/v1/cases/{id}/resolve`, `/escalate`, `/close` | `recall_approver` / `case_supervisor` | recommended | yes | P1 |
| Manual ISO correlation / reprocess / replay | `iso-adapter` | `POST /api/v1/iso/messages/{id}/correlate`, `/replay` | ops_senior | recommended | yes | P1 |
| Reference-data CRUD (participants, codes, calendars, profiles) | `reference-data` | `POST/PUT /api/v1/reference-data/{catalog}` | `reference_data_admin` | not applicable (versioned, not replay-prone) | yes | 1 (thin) / P1 (full) |
| Approve / reject payment or batch `[ADD, persona-driven]` | `payment-lifecycle` | `POST /api/v1/payments/{id}/approve`, `/reject`, `POST /api/v1/payment-batches/{batchId}/approve`, `/reject` | `payment_approver` | **required** (§2.2b — reuses `idempotency_keys`) | yes | 1 (MVP — see §2.2b) |
| Release / reject / escalate fraud hold `[ADD, persona-driven]` | `payment-lifecycle` | `POST /api/v1/fraud-holds/{id}/release`, `/reject`, `/escalate` | `reconciliation_operator` + `ops_senior` | **required** | yes | 1 (MVP, thin — see §2.2c) |
| Override VoP mismatch `[ADD, persona-driven]` | `payment-lifecycle` | `POST /api/v1/payments/{id}/vop-override` | `payment_approver` / `ops_senior` + step-up | **required** | yes, same-TX with the override | 1 (MVP, thin — see §2.2c) |
| Update approval matrix / limit policy `[ADD, persona-driven]` | `reference-data` | `POST/PUT /api/v1/reference-data/approval-matrix`, `/limit-policies` | `reference_data_admin` + step-up | not applicable (versioned) | yes | 1 (MVP) |

**Rule (reaffirmed):** every row above is a REST (or gRPC, only if the routing-extraction exercise per `ADR-N2` ever needs it) admin command, role-gated, audited same-transaction — **never** a GraphQL mutation. GraphQL stays strictly read-only per §6.6. `[ADD, security-review]` **"Required" Idempotency-Key commands all reuse the single `idempotency_keys` store** (§2.2) — no second idempotency mechanism anywhere in the system; "recommended" rows are safe to retry by nature (assign/resolve/replay are naturally re-appliable) but gain a client-side safety net from the key without needing a new server-side table.

`[SECURITY-GAP, closed][ADD, persona-driven]` **Object-level authorization on the four new payment-decision endpoints above is not optional path-parameter trust.** Every one of `approve/reject/release/escalate/vop-override` re-validates server-side, inside the same transaction as the decision, via a custom `AuthorizationManager<MethodInvocation>` (Keycloak-26 blueprint §6) evaluated **before** the service method runs: (1) the `{id}` belongs to the caller's `tenant_id`/`branch_id` (RLS does this structurally, but the check is asserted explicitly here because these are exactly the endpoints OWASP API1:2023 (Broken Object Level Authorization) warns about — an approver ID guessing a sequential payment ID must never reach another tenant's row); (2) `checker_user_id != maker_user_id` (the DB constraint in §2.2b is the last line of defense, not the only one); (3) the approval queue read model (`payment_approvals` filtered to `status='PENDING_APPROVAL'`) is itself tenant/branch-scoped by the same RLS/GUC mechanism as every other queue in this document (§4.7) — an approver never sees, let alone can act on, a payment outside their scope, mirroring the exact pattern already frozen for the reconciliation exception queue.

`[ADD, security-review]` **Two further defenses, already true by construction, now stated explicitly:** (1) **non-predictable object IDs** — every primary key in this system is `uuidv7()` (§4, throughout), which is itself an OWASP-recommended BOLA mitigation (sequential IDs make ID-guessing trivial; UUIDs don't) — this was a data-modeling default, not a security afterthought, but it earns an explicit callout here. (2) **no mass assignment** — every REST command is a narrow, named request DTO (`SubmitPayment`, `ApprovePayment`, …), never a generic entity-binding `PUT`/`PATCH` of the full `Payment`/`Approval` object; a client cannot set fields (e.g. `checker_user_id`, `status`) that aren't part of the specific command's contract, closing the class of bug where an extra JSON field in the request body silently mutates a field the endpoint never intended to expose.

### 7.3 Patch notes — priority taxonomy, simulation, routing, HLD supersession `[ADD][R-02][R-05][R-07][R-23, closed]`

- **One priority taxonomy (`ADR-N6`):** `[MVP]` = everything scheduled inside Iterations 0–5; `[P1]` = first post-MVP wave; `[P2]` = second-wave breadth and labs. Any `[MVP]`/`[P1]`/`[P2]` tag elsewhere in this document is read against this scale, not against the older, now-retired dual taxonomy that let `simulation` and `reconciliation` carry contradictory priorities across documents.
- **`simulation` is `[MVP]`, Iteration 3** — re-tagged per `ADR-N6`. It is the determinism keystone ("the demo that sells the project," per the comprehensive review's business-lens advice) and must never be read as deferrable; `[EDU-ONLY]` on this module is a *nature* label, not a priority downgrade.
- **`routing` is an in-process Modulith module for MVP** (`ADR-N2`) — it consumes `payment.validated` and publishes `payment.routed`/`route.failed` exactly as §3.9/§4.10 already describe; no gRPC call is made to reach it in Iterations 0–5. The out-of-process gRPC extraction is a named `[P2]` educational exercise with its own G7 degraded-mode policy, written at extraction time. This resolves the contradiction between this section's event table (Kafka consumer) and older prose elsewhere describing routing as "the one out-of-process gRPC service" — the Kafka-consumer model is the frozen one for MVP.
- **HLD supersession:** the older `sepa-nexus-hld-and-implementation-plan.md` document contains material partially superseded by the CPC-SP re-topology and the patches applied to this blueprint (its 18-module map, six-gRPC-service design, 13-role security matrix, and Epic 9/12/14/15 scope in particular are superseded). Its Tier 0/Phase 0/TS-01…TS-17 foundation material and its DoR/DoD/anti-overengineering doctrine (§14–§16 of that document) remain valid inputs to the Iteration 0 plan and are not superseded.

---

## 8. Backlog seed (epics → stories → task types) — the starting point requested

**EPIC-IN-1 Ingress staging pipeline** — stories: S1 REST JSON submit + idempotency (tasks: controller, IdempotencyStore PG18 two-step impl + contract test, IngestionService TX, outbox write, Playwright happy path); S2 signature-before-parse filter chain (raw cache filter, verify filter, ordering test); S3 XML hardening config (factory bean, XXE/bomb negative fixtures); S4 REST XML pain.001 (iso-adapter map, 422 taxonomy).
**EPIC-IN-2 File rail** — S1 file accept + scoped file idempotency (sender-scoped MsgId uniqueness tests, not sha-unique-archive); S2 Spring Batch job (reader/processor/writer, SkipPolicy, restart test); S3 partial-accept result data (file_items, counts); S4 result-file render+deliver (depends EPIC-OUT-1).
**EPIC-CORE-1 Lifecycle FSM** — S1 transition table + guards (unit matrix); S2 Kafka consumer + inbox (duplicate/ordering tests); S3 status-inbound correlation (G4: `iso.payment_iso_identifiers` lookup + orphan→DLQ).
**EPIC-CORE-2 Identifier refactor** — S1 `iso.payment_iso_identifiers` schema + migration off `payments`; S2 correlation queries repointed; S3 pacs.002/R-message lineage test per source_message_type.
**EPIC-MONEY-1 Ledger** — S1 accounts+journal DDL + table ownership grants (no RLS — §4.7); S2 reserve/post/release with invariants (concurrency no-double-reserve test); S3 deferred constraint trigger (unbalanced-entry-at-COMMIT test) + immutability grants (mutation-denied test); S4 reversal entry flow; nightly Σ=0 job demoted to secondary check.
**EPIC-MONEY-2 Instant settlement** — S1 GrossInstantStrategy one-TX flow; S2 insufficient-liquidity path; S3 SLA timer + breach event; S4 finality/timeout attributes (`revocation_cutoff`, `timeout_at` ≠ business rejection).
**EPIC-MONEY-3 Deferred settlement** — S1 cycle FSM + G6 lock semantics (race test!); S2 netting SQL + positions; S3 batch journal + fan-out statuses; S4 reconciliation run (REPEATABLE READ) + mismatch severities.
**EPIC-OUT-1 Egress rail** — S1 outbound_messages + dispatcher SKIP LOCKED (double-dispatcher test); S2 renderer (Prowide) + signer; S3 retry/backoff/ABANDONED+DLQ; S4 batch collector + outbound files; S5 delivery-confirmation correlation.
**EPIC-XCUT-1 Identity & time** — S1 claim→GUC filter + selective two-level RLS (tenant tables only, §4.7); S2 queue/ledger ownership grants (no-RLS path) + zero-rows-on-empty-session test for RLS tables (G3); S3 ClockPort everywhere (arch test banning `Instant.now()`).

*Ownership epics (from §3.6/§3.7 — the domain-ownership integration):*
**EPIC-OWN-1 Domain & schema ownership** — S1 one-DB-role-per-module + grants/revokes (SQL ownership tests); S2 Flyway folder-per-module + repository package boundaries; S3 Modulith `allowedDependencies` + `verify()` gate; S4 ArchUnit ownership rules (repository/GraphQL/`Instant.now()`).
**EPIC-OWN-2 ISO lineage** — S1 `iso.payment_iso_identifiers` + `message_lineage` (raw→ISO→canonical→payment map); S2 pacs.002 correlation via lineage (orphan→DLQ); S3 iso-adapter-makes-no-business-decision arch test.
**EPIC-OWN-3 Payment slim row** — S1 `payment-lifecycle` sole writer of `payment.*`; S2 coded status/reason from `reference_data` (FK); S3 God-Module guard test.
**EPIC-OWN-4 Reference-data catalogs** — S1 `participants`/`participant_accounts`; S2 `iso_reason_codes`/`status_catalog` + loaders; S3 `business_calendars`/`service_levels`/`scheme_profiles`/`settlement_cutoff_calendar`; S4 reference-data-owns-catalogs SQL grant test.
**EPIC-OWN-5 Ledger ownership** — S1 `ledger_role` sole writer; S2 `LedgerPort` (`reserve/post/release/reverse`); S3 settlement-has-no-ledger-grant test; S4 deferred trigger + immutability + reversal tests.
**EPIC-OWN-6 Egress boundary** — S1 `CLAIMED` + `transport_attempts`/`delivery_receipts`; S2 egress-cannot-write-payment-status test; S3 delivered≠final assertion; S4 egress delivery dashboard.
**EPIC-OWN-7 Kafka topic ownership** — S1 topic-owner matrix + AsyncAPI contracts; S2 producer-owns-topic test; S3 ordering + inbox-dedupe tests; S4 DLQ per consumer.
**EPIC-OWN-8 Read models & GraphQL read-only** — S1 read-model ownership per source module; S2 GraphQL read-only enforcement (no domain-repo dependency); S3 dashboard projection refresh; S4 Playwright dashboard ownership tests.
**EPIC-OWN-9 Simulation path enforcement** — S1 simulation uses public commands/`csm.response.received` only; S2 no-domain-write-grant test; S3 deterministic-seed replay view.

*ISO lineage epics (from §3.8/§4.3b/§4.3c — the ISO message-lineage integration):*
**EPIC-ISO-1 Message lineage core `[MVP]`** — S1 `iso.iso_messages`+`iso_message_versions` (parsed metadata only, no bytes; deterministic version selection); S2 `iso.message_lineage` (raw→ISO→canonical→payment map, R-message roles ready); S3 identifier extraction → richer `iso.payment_iso_identifiers`; S4 payment-detail GraphQL lineage timeline + identifiers panel.
**EPIC-ISO-2 Correlation engine `[MVP]`** — S1 pacs.002 identifier extraction; S2 9-step correlation → `iso.iso_message_correlation` (MATCHED/AMBIGUOUS/ORPHANED); S3 duplicate (`IGNORED_DUPLICATE`)/out-of-order (FSM policy) tests; S4 orphan DLQ + operator read model; **binding: adapter correlates, payment-lifecycle transitions.**
**EPIC-ISO-3 Validation boundaries `[MVP]`** — S1 XML hardening result (`iso_message_parse_errors`); S2 schema/structural/profile/mapping validation results; S3 ISO-reject-vs-business-reject hard split; S4 reason/status catalog FK integration (`reference_data`).
**EPIC-ISO-4 Outbound artifact lineage `[P1]`** — S1 `iso.iso_outbound_artifacts`; S2 render-profile/version snapshot; S3 outbound pacs.002/result-file lineage split from transport; S4 Playwright outbound evidence panel.
**EPIC-ISO-5 R-message lineage `[P1]`** — S1 `pacs.004` return correlation; S2 `camt.056` recall → case link; S3 `camt.029` resolution link; S4 investigation dashboard. *(unblocked: consumes the `case` module from EPIC-CASE-1.)*

*Case epics (from the Case Module blueprint + R-messages integration — decision-and-coordination, R-message depth):*
**EPIC-CASE-1 R-message catalog & case-type resolution `[MVP]`** — S1 `case` schema + ownership (grant tests); S2 reference-data case-type + R-message-rule catalogs; S3 `CaseTypeResolver`; S4 consume `iso.message.correlated` for R-messages.
**EPIC-CASE-2 Reject / return / recall rules `[MVP]`** — S1 four-concept rules (reject/return/recall/reversal); S2 timing matrix + `PaymentStateValidator` (`as_of`); S3 ResolveRecall role-gated; S4 **finality-correct return-as-new-payment** (flagship test: NEW payment, original journal lines byte-identical, no `ledger.*` write / `reverse` path); S5 pacs.004 inbound → original `RETURNED`.
**EPIC-CASE-3 Case evidence bundles `[MVP]`** — S1 `case_evidence_bundles` + `case_evidence_pointers` (pointer model); S2 `case_r_message_links`; S3 case FSM + `case_events`.
**EPIC-CASE-4 Decisions & action requests `[MVP]`/`[P1]`** — S1 decision outcomes; S2 `case_action_requests` request-only (return/outbound/operator); S3 role-gated admin commands (not GraphQL).
**EPIC-CASE-5 Egress / ISO outbound responses `[P1]`** — S1 request egress to render+deliver camt.029/pacs.004/pacs.028; S2 outbound artifact lineage; S3 case never renders/sends (boundary test).
**EPIC-CASE-6 Duplicate & conflicting R-messages `[P1]`** — S1 duplicate-suppress; S2 conflict-escalate (never auto-resolve); S3 `case_duplicate_links`.
**EPIC-CASE-7 Reconciliation-linked cases `[P1]`** — S1 `RECONCILIATION_LINKED_CASE` from a recon exception (request-only, read-only link).
**EPIC-CASE-8 Case observability & test lab `[MVP]`/`[P1]`** — S1 read models; S2 Playwright case dashboard (R-message chain + return link) + admin-command resolution; S3 simulation case trace; S4 pacs.028/pain.002 (P1).

*Routing epics (from the routing & reachability integration — routing as a testable pipeline):*
**EPIC-ROUTE-1 Candidate profile resolution `[MVP]`** — S1 resolve candidates from scheme/service/currency; S2 filter by profile active window; S3 filter by message-set compatibility; S4 candidates in read model.
**EPIC-ROUTE-2 Eligibility & reachability `[MVP]`** — S1 `reference_data.participant_capabilities`; S2 `participant_eligibility_rules`; S3 profile-specific `routing.participant_reachability` runtime; S4 reachability dashboard.
**EPIC-ROUTE-3 Route decision & explanation `[MVP]`** — S1 immutable `route_decisions`; S2 `route_candidate_results`; S3 **immutable explanation snapshot** (MVP-blocker); S4 GraphQL route explanation.
**EPIC-ROUTE-4 Fallback rules `[MVP]`/`[P1]`** — S1 explicit `profile_fallback_rules`; S2 `FALLBACK_SELECTED` outcome; S3 **no-implicit instant→batch test**; S4 multi-CSM fallback `[P1]`.
**EPIC-ROUTE-5 Cut-off / cycle / liquidity precheck `[MVP]`/`[P1]`** — S1 `CutoffStateReader` port (reads settlement); S2 `CUTOFF_REACHED`/`CYCLE_CLOSED` outcomes; S3 coarse `LiquidityModePrecheckPort` read-only `[P1]`; S4 cycle-closed routing behavior.
**EPIC-ROUTE-6 Routing test lab `[MVP]`** — S1 deterministic routing fixtures; S2 pairwise profile tests; S3 boundary amount/currency tests; S4 Playwright routing dashboard + simulation failure routing scenarios.

*Settlement/liquidity epics (from the settlement & liquidity integration — strategy-by-basis, not per-CSM engine):*
**EPIC-SETTLE-1 Strategy resolver & basis/mode `[MVP]`** — S1 `SettlementStrategyResolver(settlement_basis, liquidity_mode)`; S2 profile-name-switch forbidden (ArchUnit); S3 `settlement_profile_snapshots` frozen per attempt.
**EPIC-SETTLE-2 Gross instant + LedgerPort `[MVP]`** — S1 `GrossInstantStrategy` one-TX reserve→post→FINAL; S2 `LedgerPort.reserve/post/release`; S3 `settlement_liquidity_checks`; S4 `settlement_role` has no `ledger.*` write (grant test).
**EPIC-SETTLE-3 Deferred net + cycles `[MVP]`** — S1 `NetDeferredStrategy`; S2 cycle FSM (G6 lock); S3 netting→positions; S4 `ON_CYCLE_SETTLED`.
**EPIC-SETTLE-4 Internal book + file batch `[MVP]`** — S1 `InternalBookStrategy` (`ON_INTERNAL_BOOK_POST`); S2 basic `FileBatchStrategy` (cycle/file assignment).
**EPIC-SETTLE-5 Finality model `[MVP]`** — S1 `finality_rule` catalog; S2 `FinalityPolicy` + `settlement_finality_records`; S3 accepted/posted≠final tests; S4 delivery≠finality test.
**EPIC-SETTLE-6 Insufficient-liquidity outcomes `[MVP]`/`[P1]`** — S1 reject (MVP); S2 outcome by basis+mode+cut-off; S3 `settlement_queue_items` + next-cycle (P1).
**EPIC-SETTLE-7 CGS & prefunded `[P1]`** — S1 `BulkCgsLikeStrategy` (queue-until-cut-off, `CANCELLED_AT_CUTOFF`); S2 `PrefundedInstantStrategy`; S3 `PREFUNDED_RESERVE`/`TECHNICAL_ACCOUNT_LIKE`.
**EPIC-SETTLE-8 Return vs reversal `[MVP]`** — S1 reaffirm return=new payment; S2 reversal pre-finality only; S3 explanation read model + Playwright.

*Egress epics (from the egress & delivery integration — profile-driven, idempotent transport layer):*
**EPIC-EGRESS-1 Egress profile & artifact taxonomy `[MVP]`** — S1 `egress_profile` catalog + `egress_profile_snapshots`; S2 `outbound_artifacts` + idempotency key (`UNIQUE(trigger_event_id, artifact_type, recipient)`); S3 artifact-type/trigger map (pacs.002/pain.002/status).
**EPIC-EGRESS-2 Outbound message lifecycle `[MVP]`** — S1 unified lifecycle REQUESTED→…→CLOSED (§6.2); S2 render→sign→deliver; S3 `SKIP LOCKED` dispatcher; S4 transport-only assertion (no finality/status mutation).
**EPIC-EGRESS-3 Delivery attempts & retry `[MVP]`** — S1 `transport_attempts`; S2 profile retry policy + dead-letter; S3 failed-delivery-doesn't-touch-upstream test.
**EPIC-EGRESS-4 Delivery receipts & five-status `[MVP]`/`[P1]`** — S1 `delivery_receipts` + receipt→artifact correlation; S2 `transport_receipts_in` (P1); S3 five-status-separation tests (delivery≠finality≠business status≠ISO status≠receipt).
**EPIC-EGRESS-5 Batch result file delivery `[MVP]`** — S1 collector; S2 pain.002-style result file, one artifact per file; S3 Playwright result-file dashboard.
**EPIC-EGRESS-6 Case/R-message outbound `[P1]`** — S1 camt.029/pacs.004 rendered from case events; S2 `outbound_artifact_lineage`; S3 case-outbound dashboard.
**EPIC-EGRESS-7 Egress observability & test lab `[MVP]`/`[P1]`** — S1 read models; S2 `manual_delivery_actions` resend command (role-gated, audited, not GraphQL); S3 simulation egress-failure trace; S4 failed-delivery + manual-resend dashboards.

*Reconciliation epics (from the reconciliation & exception-handling integration — read-only detection-and-escalation):*
**EPIC-RECON-1 Profiles & snapshot model `[MVP]`** — S1 `reconciliation_profile` (reference-data); S2 `reconciliation_profiles_snapshot`; S3 deterministic `as_of` + `reconciliation_run_sources`; S4 run lifecycle.
**EPIC-RECON-2 Evidence collection & bundles `[MVP]`** — S1 read-only `EvidenceCollector` per source; S2 `evidence_bundles` + `evidence_pointers` (pointer model); S3 no-source-write grant test.
**EPIC-RECON-3 Settlement-vs-ledger `[MVP]`** — S1 comparison engine; S2 `MISSING`/`DUPLICATE_LEDGER_POSTING`, `MONEY_MISMATCH`, `FINALITY_MISMATCH`; S3 immediate escalation, no auto-fix.
**EPIC-RECON-4 Balance drift detection `[MVP]`** — S1 ledger-vs-balance-snapshot; S2 `SILENT_MONEY_DRIFT`; S3 status-vs-finality.
**EPIC-RECON-5 Mismatch taxonomy & severity `[MVP]`** — S1 taxonomy + severity in reference-data; S2 deterministic classifier + severity policy tests.
**EPIC-RECON-6 Exception lifecycle & operator actions `[MVP]`/`[P1]`** — S1 exception lifecycle + `exception_events`; S2 assign/comment/resolve (P1); S3 action-request request-only (P1); S4 case-escalation port (P1).
**EPIC-RECON-7 Egress / ISO / case reconciliation `[P1]`** — S1 egress-artifact + delivery-receipt; S2 ISO-lineage-orphan; S3 case-return-vs-return-payment; S4 dashboards.
**EPIC-RECON-8 Observability & test lab `[MVP]`/`[P1]`** — S1 read models; S2 Playwright dashboards (P1); S3 simulation reconciliation trace; S4 duplicate-suppression / false-positive (P1).

*ISO lineage test additions (fold into each epic's DoR):* XML hardening rejected pre-map · invalid pain.001 → `validation_results=FAIL`, no payment · mapping golden fixtures · identifier extraction · pacs.002 exact correlation (`OrgnlMsgId+OrgnlEndToEndId`→MATCHED) · duplicate no-op · out-of-order FSM policy · orphan→DLQ/operator · ambiguous→no mutation · PG CHECK/enum constraint tests (direction/ref, correlation status) · 4 ISO-topic Kafka contract tests · Playwright lineage dashboard (raw→ISO→identifiers→status→egress) · GraphQL read-only (no ISO mutation).

Task types per story (uniform): DDL+migration · component code · unit tests · Testcontainers integration · contract (OpenAPI/AsyncAPI/proto) · Playwright (where user-visible) · observability (metrics+trace) · docs (module canvas row).

---

*End of blueprint. Method: Event Storming → Hexagonal/Spring mapping → access-path-driven schema design. Implements/closes G1–G6 (design level) and G8/G9 rules; consistent with Master Architecture v2026-07-05. **v2 patch applied (2026-07-05):** four P0 corrections from the deep-research DBA review — (1) `payments` split into slim current-state + `iso.payment_iso_identifiers` message lineage, coded status/reason history, ms-precision finality timestamps; (2) ledger invariant moved to a SQL deferred constraint trigger + immutability grants + reversal model + balance snapshots; (3) RLS made selective (tenant/evidence tables only; queues and ledger protected by table/schema ownership instead); (4) ingress hardened — raw archive de-duplication removed, file/idempotency uniqueness scoped, PG19 `ON CONFLICT DO SELECT` demoted to lab-only. **v3 patch applied (2026-07-05):** domain-ownership integration — new §3.6 (ownership principle, bounded-context map, module→schema→tables→aggregates, allowed/forbidden dependencies, config-vs-decision split, architectural tests) and §3.7 (Kafka topic ownership, 15 topics); LedgerPort boundary made explicit in §3.3; ISO-owns-lineage-not-decisions rule in §2.2; finality≠delivery rule in §5; new §6.6 (read-model ownership + GraphQL read-only); EPIC-OWN-1..9 added to §8. **v4 patch applied (2026-07-05):** ISO 20022 message-lineage integration — §2.2 pain.001 flow expanded into the raw→parse→validate→identifiers→lineage→payment persistence sequence; §2.4 pacs.002 flow replaced with the 9-step correlation policy (MATCHED/AMBIGUOUS/ORPHANED/IGNORED_DUPLICATE); new §3.8 (ISO adapter responsibilities boundary); §4.3 thin `payment_iso_identifiers` superseded and `source_message_id`→`source_iso_message_id` FK; new §4.3b (lineage & versioning) + §4.3c (9-table `iso` schema DDL, parsed-metadata-only rule); new §4.13 (reference-data catalogs incl. scheme/validation/mapping/render profiles); four `iso.message.*` topics added to §3.7 (three kept internal, `pacs002.correlated` rejected as alias); eight lineage read models added to §6.6; new §6.7 (outbound artifact lineage, P1); EPIC-ISO-1..5 + ISO test suite added to §8. **v5 patch applied (2026-07-05):** Case module (recall/return/investigation) integrated to close the ISO-lineage P1-blocker — `case` added to §3.6.2 ownership map and §3.6.3 dependency + forbidden-access lists; three `case.*` topics added to §3.7; dangling `matched_case_id`/`case_id` in §4.3c documented as resolving to `"case".cases.id`; the **finality-correct return model** (a return/accepted-recall after finality is a NEW opposite-direction payment, never a ledger reversal) added to §5; EPIC-ISO-5 unblocked and EPIC-CASE-1/2 added to §8. Full design in the Case Module blueprint. **v6 patch applied (2026-07-05):** Routing & participant reachability integrated — new §3.9 (routing responsibilities + 9 ports, pipeline-not-engine); new §4.10 (route-resolution pipeline, synthetic reachability/access model, static-config-vs-runtime split, outcome taxonomy, explicit-only fallback, immutable route-explanation snapshot) with routing + reference-data eligibility DDL; `route.failed` added and `payment.routed` carries the route decision snapshot id in §3.7 (minor outcomes kept internal, no topic per profile); `routing` owned tables expanded in §3.6.2; EPIC-ROUTE-1..6 added to §8. Routing reads cut-off/cycle from settlement (never recomputes), does only coarse read-only liquidity precheck, and never writes money/finality/status/egress. **v7 patch applied (2026-07-05):** Settlement strategy & liquidity integrated — new §3.10 (settlement responsibilities + ports, strategy-selecting-orchestrator-not-RTGS) and §4.11 (binding chain `route_decision→settlement_basis→liquidity_mode→strategy→LedgerPort→finality_rule`; 6-strategy taxonomy MVP+P1; liquidity-mode taxonomy; attempt lifecycle; LedgerPort contract `reserve/post/release/reverse`; explicit finality model with accepted/posted/delivered≠final; insufficiency-by-basis+mode+cut-off); `SettlementOrchestrator` now resolves strategy by `(settlement_basis, liquidity_mode)` never by profile name (§3.3); settlement/ledger owned tables expanded in §3.6.2 (+`settlement_profile_snapshots`, `settlement_finality_records`, `settlement_liquidity_checks`, `liquidity_reservations`); EPIC-SETTLE-1..8 added to §8. Strategy-by-basis (not per-CSM engine), finality-is-an-explicit-profile-rule, and reversal-is-internal-correction-only frozen as ADRs. **v8 patch applied (2026-07-05):** Egress / delivery / confirmation consolidated and completed — new §3.11 (egress responsibilities + ports + the five-status separation, profile-driven idempotent transport layer); §6.2 lifecycle unified to `REQUESTED→RENDERED→SIGNED→CLAIMED_FOR_DELIVERY→DELIVERED→RECEIPT_RECEIVED→CLOSED` (transport-only, replacing the two prior vocabularies); new §6.8 (egress profile model + snapshot) and §6.9 (outbound artifact taxonomy + triggers + idempotent creation); egress owned tables expanded in §3.6.2 (+`outbound_artifacts`, `egress_profile_snapshots`, `outbound_artifact_lineage`, `manual_delivery_actions`); EPIC-EGRESS-1..7 added to §8. Egress-owns-transport-only, delivery≠finality, technical-ACK≠business-status, idempotent-artifact-creation, failed-delivery-never-reverses-settlement, and egress-behaviour-by-profile-not-per-CSM frozen as ADRs. **v9 patch applied (2026-07-05):** Reconciliation & exception handling consolidated and completed — new §3.12 (reconciliation responsibilities + ports, read-only detection-and-escalation, never mutates source modules) and §4.12 (deterministic `as_of` snapshot, reconciliation-type catalogue, evidence-bundle pointer model, mismatch taxonomy + severity in reference-data, run + exception lifecycles, `ACTION_REQUESTED`-is-a-request-not-a-mutation); reconciliation owned tables expanded/promoted in §3.6.2 (`[P1]`→`[MVP]` core + evidence bundles/pointers/profiles-snapshot); EPIC-RECON-1..8 added to §8. Reconciliation-is-read-only-detection-and-escalation, deterministic-`as_of`-snapshot, evidence-bundle-stores-pointers-not-copies, and mismatch-taxonomy-and-severity-in-reference-data frozen as ADRs. **v10 patch applied (2026-07-05):** R-messages / case rules deepened the case module — new §3.13 (case responsibilities: reject/return/recall four-concept clarity + case timing matrix gating action legality by `as_of` finality state + ports) and §4.14 (ISO message matrix pacs.002/camt.056/camt.029/pacs.004 MVP + pacs.028/pain.002 P1 + camt.027/camt.087 P2, case-type taxonomy, decision outcomes, duplicate/conflict handling, config-vs-runtime); two case topics added (`case.escalated`, `case.return_payment_requested`); EPIC-CASE refined into 1..8. Case-is-decision-and-coordination-only, action-legality-gated-by-timing-matrix, return-after-finality-is-a-new-payment, internal-reversal-is-ledger-only, all-outbound-R-messages-through-egress, and duplicate-suppresses/conflict-escalates frozen as ADRs. **v11 patch applied (decision-gate paper pass):** closed the blueprint-review findings gated by `ADR-N1…N8` — §3.7 rewritten as the Kafka Topic Catalog v2 / sole AsyncAPI source (adds `egress.dead_lettered`, `egress.manual_intervention_required`; renames the reconciliation topics; `ADR-N8`); new §2.2a defines the `JSON_DIRECT` pseudo message-version so direct-JSON payments record identifiers/lineage without weakening the `iso_message_id NOT NULL` contract (`ADR-N7`); §4.4 rewritten from a single unowned `outbox_events`/`inbox_events` pair to per-schema tables with a narrow shared-kernel dispatcher role, with matching fixes in §2.5/§3.5/§4.3b/§4.7 (`ADR-N5`); new §4.13a adds the `validation_profiles`/`mapping_profiles`/`render_profiles` catalog summary; new §7.1 consolidates the observability inventory; new §7.2 adds the `operatorWorklist`/`inboundFile` read models and the admin-command REST inventory; new §7.3 freezes the one-priority-taxonomy (`ADR-N6`, re-tagging `simulation` `[MVP]` Iteration 3), the routing-in-process decision (`ADR-N2`), and marks the HLD's superseded sections. DDL is a design sketch (PG18), not a migration; no production code; no claim of production SEPA/KIR compliance.*
