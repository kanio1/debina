# Updated SEPA Nexus Message Flow & Data Blueprint

**What this is.** The integration of `sepa-nexus-domain-model-module-ownership-blueprint.md` (the domain-ownership source) into the main blueprint (`sepa-nexus-message-flow-and-data-blueprint.md`). Sections 2/4/6/7/8/9 below are **ready-to-paste content** for the main blueprint; sections 1/3/10 are the plan and integration notes. The main blueprint has also been patched in place (see §10 — the new §3.6, §3.7, ownership annotations on §4, and the read-model/GraphQL/test additions are live in it).
**Convention:** `[ADOPT] [CHANGE] [ADD] [DEFER] [REJECT]` + `[MVP] [P1] [P2] [ADVANCED] [EDU-ONLY]` + `[ARCH-RISK] [DB-OWNERSHIP] [HLD-GAP] [MVP-BLOCKER]`.
**Consistency:** builds directly on the v2 data-model patch (payments split, iso lineage, SQL ledger invariant, selective RLS) already applied to the main blueprint.

---

## 1. Change Summary

| Source Finding from Ownership Markdown | Main Blueprint Section | Action | Priority |
|---|---|---|---|
| One-writer-per-schema principle | new §3.6.1 | `[ADD]` ownership principle block | `[MVP-BLOCKER]` |
| Bounded context map (12+ contexts, core/supporting/generic) | new §3.6 intro + map | `[ADD]` full context map | `[MVP]` |
| Module → schema → tables → aggregates | new §3.6.2 | `[ADD]` full ownership table | `[MVP]` |
| Allowed / forbidden dependency rules | new §3.6.3 | `[ADD]` dependency + forbidden-access tables | `[MVP-BLOCKER]` |
| Kafka topic ownership (15 topics) | §2.5/§6 → new §3.7 | `[ADD]` topic-ownership table replacing loose mentions | `[MVP]` |
| Read-model ownership + GraphQL read-only rule | new §6.6 | `[ADD]` read-model ownership + GraphQL rule | `[MVP]` |
| `payments` slim + ISO lineage in `iso-adapter` | §4.3 (already split) | `[CHANGE]` add explicit ownership annotation | done + annotate |
| `iso-adapter` owns lineage, makes no business decisions | §2.2 + new §4.3b | `[ADD]` ISO/lineage ownership + no-business-decision rule | `[MVP]` |
| `ledger` sole journal writer; `settlement` via `LedgerPort` | §3.3 + §4.5 | `[CHANGE]` make the port boundary explicit in prose + DDL grants | `[MVP-BLOCKER]` |
| `egress` owns transport only; delivered ≠ final | §6 + new §6.2b | `[ADD]` egress-not-status-owner rule; `CLAIMED` state | `[MVP]` |
| `reference-data` owns catalogs (status/reason/participants/calendars) | new §4.13 | `[ADD]` reference-data ownership tables | `[MVP]` |
| `risk`/`VoP` advisory-only | §3.6.1 + new §4.12 note | `[ADD]` advisory-only rule | `[P2]` |
| `simulation` through normal paths only | §3.6.1 + §3.6.3 | `[ADD]` simulation-path rule + forbidden direct writes | `[MVP]` |
| Architectural tests (Modulith, ArchUnit, SQL grants, Kafka, Playwright) | §? test strategy → new §8 test block | `[ADD]` ownership test suite | `[MVP]` |
| Backlog: ownership/schema/ISO-split/reference/ledger/egress/Kafka/read-model/arch-tests/GraphQL/simulation | §8 backlog | `[CHANGE]` add ownership epics | `[MVP]` |
| Don't create all schemas upfront | §3.6.2 + §4 priorities | `[DEFER]` schema-per-iteration | `[MVP]` |
| `routing_profiles` ownership ambiguity (reference-data config vs routing decisions) | §3.6.2 + §4.9 | `[CHANGE]` split config (reference-data) from decisions (routing) | `[P1]` |
| `settlement_cutoff_calendar` duplicated | §4.6/§4.13 | `[CHANGE]` calendar owned by reference-data, settlement reads | `[P1]` |

---

## 2. Updated / Added Sections (ready to paste)

The full content of the new/added sections is produced in §4 (§3.6 domain ownership), §5 (PostgreSQL ownership tables), §6 (Kafka), §7 (read models/GraphQL), §8 (testing), §9 (backlog) below. Prose-level insertions into existing sections:

**Into §2.2 (ISO adapter role), append:** `[ADD]` *ISO Adapter is the owner of message lineage, not of business decisions. It parses, maps, version-validates and records `iso.payment_iso_identifiers` + `iso.message_lineage`; it never sets `payments.business_status_code`, never routes, never settles, never decides finality. Reason and status codes it applies come from `reference_data`, not from its own logic.*

**Into §3.3 (settlement + ledger money path), append:** `[CHANGE]` *`ledger` is the only writer of `journal_entries`, `journal_lines`, `liquidity_accounts`, `balance_snapshots`. `settlement` never issues SQL against `ledger.*`; it calls `LedgerPort` (`reserve/post/release/reverse`). This is enforced three ways: Spring Modulith `allowedDependencies` (no settlement→ledger repository), a DB grant test (settlement's DB role has no write on `ledger.*`), and an ArchUnit rule (no `ledger` repository referenced outside the `ledger` module). Corrections are `REVERSAL` entries via the port — never mutation.*

**Into §5 (clearing/settlement mechanics), append:** `[ADD]` *Finality is a settlement/payment concern. `egress` delivering or confirming a status message (`DELIVERED`/`CONFIRMED`) is transport state, **not** settlement finality — a confirmed pacs.002 does not move `finality_at`, and a failed delivery does not un-settle a payment. The two state machines are independent by design.*

---

## 3. Full Patch Plan

| Main Blueprint Section | Patch Type | Exact Change | Reason | Dependencies |
|---|---|---|---|---|
| §2.2 ISO adapter row | `[ADD]` prose | ISO-owns-lineage-not-decisions paragraph | Prevents `iso-adapter` God-mapper | §4.3b, §4.13 (reason codes) |
| §2.5 Spring reception table | `[CHANGE]` | Add row pointing to §3.7 topic ownership; keep PG18 idempotency row | Topic contracts must be discoverable | §3.7 |
| §3 (new §3.6) | `[ADD]` section | Full domain ownership (principle, context map, module→schema, deps, forbidden, tests) | Core of this integration | §4, §6, §8 |
| §3 (new §3.7) | `[ADD]` section | Kafka topic ownership table (15 topics) | Freeze producer/consumer/key/DLQ | §6.4 events |
| §3.3 money path | `[CHANGE]` prose | LedgerPort-only + triple enforcement | Money-correctness boundary | §4.5 grants, §8 tests |
| §4 (each table block) | `[CHANGE]` annotate | Add `-- owner: <module> [MVP|P1|P2]` header comment per table group | Ownership visible at the DDL | §3.6.2 |
| §4.3 payments | `[ADD]` annotation | Confirm `payment-lifecycle` owner; lineage owned by `iso-adapter` | Split already done; label it | §4.3b |
| §4 (new §4.3b) | `[ADD]` | ISO/message-lineage section (`payment_iso_identifiers`, `message_lineage`, raw→ISO→canonical→payment map, pacs.002 correlation) | Lineage is a first-class concept | §4.13 |
| §4.5 ledger | `[CHANGE]` | Add explicit `ledger_reversals` note + DB grant block referencing owner role | Immutability + single writer | §8 tests |
| §4.6 settlement | `[CHANGE]` | `settlement_cutoff_calendar` → reference-data owned, settlement reads | Avoid duplicated calendars | §4.13 |
| §4 (new §4.13) | `[ADD]` | Reference-data catalogs (participants, accounts, reason/status catalog, calendars, service levels, scheme/route profiles) | Catalog ownership | §4.9 routing |
| §4.7 RLS | `[ADOPT]` | Already selective; cross-reference §3.6.2 RLS column | Consistency | done |
| §6 egress (new §6.2b) | `[ADD]` | Egress-owns-transport-only + `CLAIMED` + receipts + delivery ≠ finality | Domain correctness | §5 mechanics |
| §6 (new §6.6) | `[ADD]` | Read-model ownership + GraphQL read-only rule | Read/write separation | §7 |
| Test strategy | `[ADD]` | Ownership test suite (Modulith/ArchUnit/SQL/Kafka/Playwright) | Enforcement | §8 |
| §8 backlog | `[CHANGE]` | Add ownership/arch-test/reference/read-model epics | Delivery | all above |

---

## 4. Updated §3.6 Domain Model & Schema-per-Module Ownership (full section)

`[ADD]` This section freezes the domain ownership model. SEPA Nexus is **one Spring Modulith deployable**, but every module owns its own business boundary, PostgreSQL schema, aggregate roots, repositories, migrations, event contracts and write permissions.

### §3.6.1 Ownership Principle

`[DB-OWNERSHIP]` Every table, aggregate, event, topic and read model has **exactly one owner**. Ten binding rules:

1. A module writes **only** to its own PostgreSQL schema. Cross-schema writes are an architecture violation `[REJECT]`.
2. Other modules read through one of: domain port, query service, service view, read model, or event projection — never a foreign repository or a foreign table.
3. `ledger` is the **only** writer of `journal_entries`, `journal_lines`, `liquidity_accounts`, `balance_snapshots`, `ledger_reversals`.
4. `settlement` requests ledger booking **only** through `LedgerPort` (`reserve/post/release/reverse`); it never issues SQL against `ledger.*`.
5. `egress` owns transport/delivery state **only**; it must not mutate `payments.*` or `settlement_*`.
6. `iso-adapter` owns message lineage and mapping **only**; it makes no business decision (no route, no reject-for-liquidity, no finality).
7. `reference-data` owns catalogs (status, reason, participants, calendars, service levels, scheme profiles) — **never** transactional state.
8. `risk` and `VoP` are **advisory-only** in MVP; they may not block or reject settlement.
9. `simulation` exercises the **same public ports and Kafka topics** as real traffic; it never inserts directly into domain tables.
10. **GraphQL is read-only** in MVP; the write path is REST/gRPC/command APIs.
11. `[ADD, R-13, closed]` **`signature` owns raw-byte signature verification and signing only.** It exposes `SignaturePort` (verify/sign), records verification verdicts as evidence, and owns a synthetic key registry. It does **not** parse ISO messages, decide payment status, deliver outbound artifacts, or own Keycloak — every domain module reaches it through a port, never a repository.

`[ARCH-RISK]` The five highest-risk violations to guard against: `payment-lifecycle` becoming a God Module (absorbing routing/settlement/egress decisions); `settlement` writing `journal_lines` directly; `iso-adapter` embedding business logic; `egress` treating "delivered" as "settled/final"; **`signature` or its callers treating a verification verdict as a business decision — a `FAILED` verdict causes a rejection at `ingress`, it is never itself the rejection.**

#### `signature` ownership summary `[ADD, R-13, closed]`

`[MVP-BLOCKER, closed]` The `signature` module was named throughout the message-flow blueprint (`MessageSignatureVerificationFilter`, `SignaturePort` in egress, G1's verify-before-parse rule) but had no ownership row anywhere — a module with no owner is an architecture violation by the case-module blueprint's own stated rule. This closes it.

| Module | Schema | Owns | May Read | Must Not |
|---|---|---|---|---|
| `signature` | `signature` | Raw-byte signature/checksum verification (`verify` port); outbound artifact signing (`sign` port); verification verdicts (`VERIFIED`/`FAILED`/`N/A`); synthetic key registry (`keys`) | Nothing beyond its own schema; verdicts are pushed to callers via the port return value, not read by other modules from `signature.*` directly | Parse ISO messages · decide payment status · write `payment`/`settlement`/`ledger`/`egress`/`iso`/`case`/`reconciliation` · deliver outbound artifacts (that is `egress`'s job — `signature` only signs, `egress` sends) · own or configure Keycloak |

**Callers and the boundary they must respect:** `ingress` calls `signature.verify()` on raw bytes **before** any XML parse (the `RawBodyCachingFilter` → `MessageSignatureVerificationFilter` → controller order, pinned by `@Order`, is now also an ArchUnit/ordering test, not just documentation — §3.6.5). `iso-adapter` parses **only after** the verify verdict is recorded — it never calls `signature` itself; the verdict is already on the archived raw message by the time `iso-adapter` sees it. `egress` calls `signature.sign()` through `SignaturePort` when a profile's `signing_required` is true; `signature` never delivers — that boundary stays with `egress`'s own transport layer.

### §3.6.2 Bounded Context Map

`[CHANGE][R-03][R-04][R-24, closed]` Module-naming and folding decisions applied to this map: **`live-analytics` folds into `reporting`** (it has no aggregate of its own — only a thin SSE aggregation responsibility, per `ADR-N4`); **`vop` folds into `risk`** as an advisory-only concern (VoP checks are exactly the kind of non-blocking signal `risk` already owns); **`batch` is not a domain module** — Spring Batch job-repository tables are a schema owned by `ingress` (the module that launches the jobs), not a 21st bounded context; the module is named **`identity-access`**, and it owns the **`security`** schema — the pair is stated explicitly here so the two names are never mistaken for two different things.

| Bounded Context | Responsibility | Class | Main Aggregates | Owns Schema |
|---|---|---|---|---|
| Ingress | Intake, raw archive, idempotency, staging, Spring Batch job repository | Supporting | `InboundMessage`, `InboundFile`, `IdempotencyRecord` | `ingress` (+ `batch` schema, job metadata only) |
| ISO Adapter / Lineage | ISO parse/map/version, identifiers, lineage | Supporting | `IsoMessageLineage`, `IsoParsedMessage` | `iso` |
| Payment Lifecycle | Canonical current state, FSM, status history, **maker-checker approval gate (§2.2b, prefix to the FSM)** `[CHANGE, persona-driven]` | **Core** | `Payment`, `PaymentLifecycle`, `PaymentApproval` | `payment` |
| Routing | Profile-driven route/reachability decisions (in-process module, `ADR-N2`) | **Core** | `RoutingProfile`, `RouteDecision` | `routing` |
| Settlement | Instant/deferred attempt, cycle, positions, finality | **Core** | `SettlementAttempt`, `SettlementCycle`, `SettlementPosition` | `settlement` |
| Ledger | Money correctness, accounts, journal, reversals | **Core** | `LedgerAccount`, `JournalEntry`, `BalanceSnapshot` | `ledger` |
| Reconciliation | Expected vs actual, mismatch, exception | **Core** | `ReconciliationRun`, `ReconciliationException` | `reconciliation` |
| Egress | Outbound render/dispatch/receipts/confirmations | **Core** | `OutboundMessage`, `DeliveryReceipt`, `TransportAttempt` | `egress` |
| **Signature** `[ADD, R-13, closed]` | Raw-byte message/file signature verification + signing; synthetic key registry | Supporting | `MessageSignature`, `SignatureKey` | `signature` |
| Reference Data | Statuses, reason codes, participants, calendars, profiles, validation/mapping/render profile catalogs | Supporting | `Participant`, `ReasonCodeCatalog`, `StatusCatalog`, `BusinessCalendar` | `reference_data` |
| Risk (absorbs VoP) | Anomaly advisory signals `[P2]` + VoP payee-name↔IBAN checks `[CHANGE: P2→MVP, thin, closed]`, advisory-only | Supporting | `RiskSignal`, `VopCheck` | `risk` |
| Simulation | Deterministic traffic/failure/CSM (`[MVP]` Iteration 3, `ADR-N6`) | Supporting | `SimulationScenario`, `FailureProfile` | `simulation` |
| Reporting (absorbs Live Analytics) | Statements, dashboard projections, SSE live-feed aggregation (`ADR-N4`) | Supporting | `Statement`, `StatementLine` | `reporting` |
| Case | R-message decision-and-coordination (recall/return/reject) | Supporting | `Case`, `CaseDecision` | `case` |
| Security / Identity (module `identity-access`) | Tenant/branch/service-role mapping, Keycloak claim→GUC bridge | Generic | `SecurityPrincipal`, `ServiceRole` | `security` |
| Evidence / Audit | Raw evidence, immutable audit | Generic | `EvidenceRecord`, `AuditEntry` | `evidence`, `audit` |

### §3.6.3 Module → Schema → Tables → Aggregates (with priority)

`[CHANGE][ADR-N6, closed]` Priorities below use the **one frozen taxonomy**: `[MVP]` = Iterations 0–5, `[P1]` = wave 1, `[P2]` = wave 2/labs. This corrects two contradictions the review found: `simulation` moves from `[P1]`/`[EDU-ONLY]`-as-priority to **`[MVP]`, Iteration 3** (it is the determinism keystone, not deferrable); `reconciliation` core moves from `[P1]` to **`[MVP]`, Iteration 4**, matching the v9 patch already applied to the main blueprint — this row was stale here.

| Module | Owned Schema | Owned Tables | Main Aggregates | Priority |
|---|---|---|---|---|
| `ingress` | `ingress` (+ `batch`) | `raw_inbound_messages`, `idempotency_keys`, `inbound_files`, `inbound_file_items`, `ingress.outbox_events`, `ingress.inbox_events` (`ADR-N5`); `batch.*` Spring Batch job repository | `InboundMessage`, `InboundFile`, `IdempotencyRecord` | `[MVP]` |
| `iso-adapter` | `iso` | `payment_iso_identifiers`, `iso_messages`, `message_lineage`, `iso.outbox_events`, `iso.inbox_events` | `IsoMessageLineage`, `IsoParsedMessage` | `[MVP]` |
| `payment-lifecycle` | `payment` | `payments`, `payment_status_history`, `payment_events`, `payment_approvals` `[ADD, persona-driven]`, `payment.outbox_events`, `payment.inbox_events` | `Payment`, `PaymentLifecycle`, `PaymentApproval` | `[MVP]` |
| `signature` `[ADD, R-13, closed]` | `signature` | `message_signatures`, `keys` | `MessageSignature`, `SignatureKey` | `[MVP]` (verify: Iter 2; sign: Iter 5) |
| `routing` | `routing` | `route_decisions`, `route_candidate_results`, `route_decision_explanations`, `routing.outbox_events`, `routing.inbox_events` (`routing_profiles`/`participant_reachability`/`route_rules`/`fallback_rules` — config co-owned, see §3.6.4) | `RouteDecision`, `RoutingProfile` | `[MVP]` (decisions, Iter 5) |
| `settlement` | `settlement` | `settlement_attempts`, `settlement_cycles`, `settlement_items`, `settlement_positions`, `settlement.outbox_events`, `settlement.inbox_events` | `SettlementAttempt`, `SettlementCycle`, `SettlementPosition` | `[MVP]` |
| `ledger` | `ledger` | `liquidity_accounts`, `journal_entries`, `journal_lines`, `balance_snapshots`, `ledger_reversals` (no outbox — ledger changes are read via settlement's events, not published directly) | `LedgerAccount`, `JournalEntry` | `[MVP]` |
| `reconciliation` | `reconciliation` | `reconciliation_runs`, `reconciliation_results`, `reconciliation_exceptions`, `reconciliation.outbox_events`, `reconciliation.inbox_events` | `ReconciliationRun`, `ReconciliationException` | `[MVP]` (re-tagged, was stale `[P1]`) |
| `egress` | `egress` | `outbound_messages`, `outbound_files`, `transport_attempts`, `delivery_receipts`, `transport_receipts_in`, `egress.outbox_events`, `egress.inbox_events` | `OutboundMessage`, `DeliveryReceipt` | `[MVP]` (files/receipts-in `[P1]`) |
| `case` | `case` | `cases`, `case_decisions`, `case_r_message_links`, `case_evidence_bundles`, `case.outbox_events`, `case.inbox_events` | `Case`, `CaseDecision` | `[P1]` (MVP-of-P1 per case blueprint) |
| `reference-data` | `reference_data` | `participants`, `participant_accounts`, `iso_reason_codes`, `status_catalog`, `business_calendars`, `service_levels`, `scheme_profiles`, `settlement_cutoff_calendar`, `validation_profiles`, `mapping_profiles`, `render_profiles` | `Participant`, `ReasonCodeCatalog`, `StatusCatalog` | `[MVP]` (calendars `[P1]`) |
| `risk` (absorbs `vop`) | `risk` | `vop_checks` `[CHANGE: MVP, thin]`, `fraud_signals`, `anomaly_windows`, `corridor_stats` (all `[P2]`) | `VopCheck`, `RiskSignal` | `[MVP]` (VoP check only) / `[P2]` (fraud/anomaly scoring) |
| `simulation` | `simulation` | `simulation_scenarios`, `simulation_runs`, `failure_profiles`, `generated_events` | `SimulationScenario`, `SimulationRun` | `[MVP]`, Iteration 3 (re-tagged, was stale `[P1]`) |
| `reporting` (absorbs `live-analytics`) | `reporting` | `statement_headers`, `statement_lines`, `dashboard_snapshots`, `operator_worklist` (projection, `[P1]`) | `Statement`, `StatementLine` | `[P2]` (thin SSE aggregation `[MVP]`) |
| `identity-access` | `security` | `tenants`, `branches`, `service_roles`, `role_permissions` | `SecurityPrincipal`, `ServiceRole` | `[MVP]` |
| `evidence-audit` | `evidence`, `audit` | `evidence_records`, `audit_log`, `payload_hashes` | `EvidenceRecord`, `AuditEntry` | `[MVP]` |

`[DEFER]` **One-writer-per-schema rule.** Create each schema with its module and first migration — not all upfront. Earliest-needed schemas: `ingress` (+`batch`), `iso`, `payment`, `signature`, `ledger`, `settlement`, `egress`, `reference_data`, `evidence`/`audit`, `security`. Everything else (`routing` beyond decisions, `reconciliation`, `risk`, `reporting`, `simulation`, `case`) lands with its iteration. `[CHANGE][ADR-N5]` **`outbox_events`/`inbox_events` are no longer a global unowned pair** — each publishing/consuming schema above owns its own copy, created alongside that schema's first migration; a single shared-kernel `outbox_dispatcher_role` claims/marks-published across all of them via narrow grants, with **no** grant on any domain table. Full pattern and grant DDL: main blueprint §4.4.

### §3.6.4 Allowed & Forbidden Dependencies

| From Module | Allowed (port / read-model / event) | Forbidden |
|---|---|---|
| `ingress` | `iso-adapter` port, `signature` port, `security`, `reference-data`, `evidence-audit` | direct `payment`/`settlement`/`ledger`/`egress` calls; `signature.*` repository access |
| `signature` `[ADD, R-13, closed]` | `evidence-audit` port (verdict recorded as evidence) | writing `payment`/`settlement`/`ledger`/`egress`/`iso`/`case`/`reconciliation`; parsing ISO; deciding payment status; delivering outbound artifacts; owning Keycloak |
| `iso-adapter` | `reference-data` (reason/status/versions), `evidence-audit` | changing `payment.status`; routing; settling |
| `payment-lifecycle` | `iso-adapter` query port, `reference-data` | writing `ledger`/`settlement`/`egress`/`routing` |
| `routing` | `payment` query, `reference-data` (profiles/participants) | settling; posting ledger; dispatching |
| `settlement` | `routing` read model, **`LedgerPort`**, `reference-data` | direct `ledger.*` table writes |
| `ledger` | `reference-data` (participant accounts/currency) | depending on `settlement`/`payment`; consuming routing events |
| `reconciliation` | `ledger`/`settlement`/`egress` **read models only** | mutating any source module |
| `egress` | `payment` read model, `routing` profile, `reference-data`, `iso-adapter` renderer, **`signature` port (sign, never verify — verify happens at ingress)** | updating `payments.status` / `settlement_*`; calling `signature` internals directly (port only) |
| `risk` | `payment` events/read model, `reference-data` | blocking/rejecting settlement in MVP |
| `simulation` | **public command ports** of `ingress`, CSM response topic | direct inserts into `payment`/`settlement`/`ledger`/`egress` |
| `reporting` | events/read models (payment, settlement, ledger, egress, reconciliation) | becoming a domain write path |
| `reference-data` | admin/security | owning transactional state |
| `outbox_dispatcher_role` `[ADD, ADR-N5, closed]` | narrow `SELECT`/`UPDATE(published_at)` grant on every module's `<schema>.outbox_events` | **any** write to a domain table in any schema; this is a shared-kernel infrastructure role, not a module, and appears here only for completeness of the grant matrix |

**Forbidden-access list `[REJECT]`:** `settlement`→direct `ledger.journal_*` write · `egress`→`payments.status` update · `risk`→block settlement (MVP) · `simulation`→direct domain-table insert · GraphQL mutation→domain write · `reference-data`→transactional state · `iso-adapter`→business decision (route/reject/finality) · **`signature`→any domain table write (payment/settlement/ledger/egress/iso/case/reconciliation)** `[ADD, R-13]` · **`ingress`/`iso-adapter`→`signature.*` repository access (ports only)** `[ADD, R-13]` · **`iso-adapter` parses only after `signature`'s verify verdict is recorded** `[ADD, R-13]` — this is the verify-before-parse rule (G1) made an explicit dependency-graph rule, not just a filter-ordering note · **`outbox_dispatcher_role`→any domain table write** `[ADD, ADR-N5]`.

### §3.6.4a Controller / Service / Repository Layering `[ADD, security-review]`

`[FREEZE]` **Never formally stated until this pass, though every patch in this document already assumed it.** Three layers, one rule each, enforced by ArchUnit alongside the module-boundary tests already in §3.6.5:

- **Controllers** parse/validate the HTTP request into a command DTO and call exactly one service method — **no business rule, no security decision, no repository access** in a controller. A controller that branches on `payment.status` or checks a role beyond the method-security annotation is a controller doing a service's job.
- **Services** own both the business rule *and* the security decision together (the object-level `AuthorizationManager` check, Keycloak-26 blueprint §6, runs **before** the service method body — the service method itself trusts that it already ran, it does not re-derive authorization from scratch) — a service is where "is this a legal transition" and "is this caller allowed to cause it" meet, in one place, not split across a filter and a `switch` statement.
- **Repositories** are thin Spring Data/JPA interfaces scoped to their owning module's schema (§3.6.1 rule 1) — **no repository method builds a query that could cross a tenant boundary**, because it doesn't need to: every connection is already GUC-scoped by the time a repository method runs (Keycloak-26 blueprint §9), so a repository's job is data access, not access control. A repository is not a second place tenant isolation is re-checked — RLS already did that at the connection level.

`[ADD, security-review]` **JPA/Hibernate data-access note:** entities map columns, not access rules — **no `@TenantId`, no Hibernate-level tenant filter anywhere in this codebase** (Keycloak-26 blueprint §9 states why: RLS is the sole enforcement layer, and a second ORM-level filter could silently disagree with it). GraphQL read models and REST responses are built from **DTOs/projections**, never a serialized entity graph — this is not a performance optimization first, it is what keeps a field added to an entity for internal use (e.g. a raw signing key reference) from silently reaching a response just because someone forgot a `@JsonIgnore`. Transaction boundaries are one-per-command (§2.2 "ONE DB TX," reaffirmed for every command added since, including approve/reject/release/escalate) — a service method is one transaction, not a sequence of several, so a partial failure never leaves a payment half-decided.

### §3.6.5 Architectural Tests (mandatory)

Spring Modulith `verify()` (allowed dependencies) · ArchUnit: no repository outside owning package, no `settlement`→`ledger` repository, no `Instant.now()` outside `ClockPort`, GraphQL package cannot depend on command services, **no domain module depends on `signature` internals — only its `SignaturePort`/verify-verdict port `[ADD, R-13]`**, **no direct `signature.*` repository access from any non-`signature` module `[ADD, R-13]`**, **no controller class references a `Repository` type `[ADD, security-review]`**, **no `@TenantId`/Hibernate tenant-filter annotation anywhere in the entity model `[ADD, security-review]`** · SQL/Testcontainers: module DB role cannot write foreign schema, `journal_lines` rejects unbalanced entry at commit, `journal_lines` update/delete denied, queue tables not RLS, tenant/evidence tables enforce RLS, **non-`signature` roles cannot write `signature.*` `[ADD, R-13]`**, **`outbox_dispatcher_role` cannot write any domain table — a negative sweep across every schema, not a per-table allowlist `[ADD, ADR-N5]`**, **a module's writer role cannot write another module's `<schema>.outbox_events` `[ADD, ADR-N5]`**, **two `payment_approver`s racing to decide the same payment — exactly one commits, the other gets a conflict, never a silent double-decision `[ADD, security-review]`** · Kafka: topic producer schema matches contract (per §3.7 v2 of the main blueprint, `ADR-N8`), per-`payment_id` ordering, duplicate CSM response ignored by inbox · Ingress flow ordering: **signature verification occurs before XML parse in the ingestion flow — asserted as an ordering test on the filter chain, not just documented `[ADD, R-13]`** · Playwright: payment detail shows ISO identifiers from `iso.payment_iso_identifiers`, egress dashboard shows `PENDING→CLAIMED→DELIVERED/CONFIRMED`, reconciliation dashboard shows mismatch severity + source run.

### §3.6.6 Decision Summary

| Decision | Status |
|---|---|
| One-writer-per-schema | `[ADOPT]` |
| `payments` slim current-state; lineage in `iso` | `[ADOPT]` |
| Ledger-only journal writer; settlement via `LedgerPort` | `[ADOPT]` |
| Selective RLS | `[ADOPT]` |
| Risk/VoP advisory-only (MVP) | `[ADOPT]` |
| GraphQL read-only (MVP) | `[ADOPT]` |
| Simulation through normal paths | `[ADOPT]` |
| Full ISO party/attribute normalization | `[DEFER]` |
| All schemas upfront | `[DEFER]` |
| Cross-schema writes | `[REJECT]` |

---

## 5. Updated PostgreSQL Ownership Sections (full tables)

`[DB-OWNERSHIP]` Every table now carries an owner and a priority. To paste as the ownership column set alongside the existing §4 DDL blocks.

| Schema | Table | Owner | Write from | Read from (via port/view/event) | RLS | Priority |
|---|---|---|---|---|---|---|
| `ingress` | `raw_inbound_messages` | `ingress` | `ingress` only | `evidence-audit`, `iso-adapter` | tenant/evidence | `[MVP]` |
| `ingress` | `idempotency_keys` | `ingress` | `ingress` only | operational view only | service-only | `[MVP]` |
| `ingress` | `inbound_files` / `inbound_file_items` | `ingress` | `ingress` (batch writer for items) | `egress`, `reporting` | yes | `[MVP]` |
| `ingress` | `outbox_events` / `inbox_events` `[ADD, ADR-N5]` | `ingress` | `ingress` only (+ `outbox_dispatcher_role` claim/mark-published on outbox) | Kafka relay | no | `[MVP]` |
| `batch` `[CHANGE, R-22, closed]` | Spring Batch job repository | **`ingress`** (not a separate module — see §3.6.2) | `ingress` (job launcher) only | ops dashboard | no | `[MVP]` |
| `iso` | `payment_iso_identifiers` | `iso-adapter` | `iso-adapter` only | `payment-lifecycle`, `egress`, `reconciliation` | selective | `[MVP]` |
| `iso` | `message_lineage` | `iso-adapter` | `iso-adapter` only | audit, reconciliation | selective | `[MVP]` |
| `iso` | `iso_messages` | `iso-adapter` | `iso-adapter` only | evidence, search, reporting | yes | `[MVP]` |
| `iso` | `outbox_events` / `inbox_events` `[ADD, ADR-N5]` | `iso-adapter` | `iso-adapter` only (+ dispatcher) | Kafka relay | no | `[MVP]` |
| `signature` `[ADD, R-13, closed]` | `message_signatures` | `signature` | `signature` only | `ingress` (verdict via port), `evidence-audit` (recorded as evidence) | selective | `[MVP]` |
| `signature` `[ADD, R-13, closed]` | `keys` | `signature` | `signature` only | `egress` (sign via port only, never direct read) | service-only | `[MVP]` |
| `payment` | `payments` | `payment-lifecycle` | `payment-lifecycle` only | routing, settlement, egress, reporting | yes | `[MVP]` |
| `payment` | `payment_status_history` | `payment-lifecycle` | `payment-lifecycle` only | egress, reporting, reconciliation | yes | `[MVP]` |
| `payment` | `payment_events` | `payment-lifecycle` | `payment-lifecycle` only | dashboard, reporting | selective | `[MVP]` |
| `payment` | `payment_approvals` `[ADD, persona-driven]` | `payment-lifecycle` | `payment-lifecycle` only (writer role); `payment_approver` role reads its queue via RLS-scoped view | approver queue (own tenant/branch only), auditor, reporting | yes | `[MVP]` |
| `payment` | `fraud_holds` `[ADD, persona-driven]` | `payment-lifecycle` | `payment-lifecycle` only | `reconciliation_operator`/`ops_senior` decision queue, auditor | yes | `[MVP]`, thin |
| `payment` | `outbox_events` / `inbox_events` `[ADD, ADR-N5]` | `payment-lifecycle` | `payment-lifecycle` only (+ dispatcher) | Kafka relay | no | `[MVP]` |
| `routing` | `route_decisions` | `routing` | `routing` only | settlement, egress, reporting | no | `[MVP]` |
| `routing` | `routing_profiles` / `route_rules` / `fallback_rules` | `reference-data` (catalog) + `routing` (operational) | see §3.6.4 split | settlement, egress | no | `[P1]` |
| `routing` | `participant_reachability` | `routing` | `routing` only | settlement, dashboard | no | `[P1]` |
| `routing` | `outbox_events` / `inbox_events` `[ADD, ADR-N5]` | `routing` | `routing` only (+ dispatcher) | Kafka relay | no | `[MVP]` |
| `settlement` | `settlement_attempts` | `settlement` | `settlement` only | payment-lifecycle, egress, reconciliation | no | `[MVP]` |
| `settlement` | `settlement_cycles` / `settlement_items` / `settlement_positions` | `settlement` | `settlement` only | reconciliation, reporting, ledger (positions) | no | `[MVP]` |
| `settlement` | `outbox_events` / `inbox_events` `[ADD, ADR-N5]` | `settlement` | `settlement` only (+ dispatcher) | Kafka relay | no | `[MVP]` |
| `ledger` | `liquidity_accounts` | `ledger` | **`ledger` only** | settlement via port, reporting via view | no base RLS; service views | `[MVP]` |
| `ledger` | `journal_entries` / `journal_lines` | `ledger` | **`ledger` only** | reconciliation, reporting via views | no base RLS | `[MVP]` |
| `ledger` | `balance_snapshots` / `ledger_reversals` | `ledger` | `ledger` only | reporting, dashboard | no | `[MVP]`/`[P1]` |
| `reconciliation` | `reconciliation_runs` / `_results` / `_exceptions` | `reconciliation` | `reconciliation` only | reporting, investigation, dashboard | no | `[MVP]` (re-tagged, `ADR-N6` — was stale `[P1]`) |
| `reconciliation` | `outbox_events` / `inbox_events` `[ADD, ADR-N5]` | `reconciliation` | `reconciliation` only (+ dispatcher) | Kafka relay | no | `[MVP]` |
| `egress` | `outbound_messages` | `egress` | `egress` only | audit, reporting, dashboard | selective | `[MVP]` |
| `egress` | `transport_attempts` / `delivery_receipts` / `transport_receipts_in` | `egress` | `egress` only | audit, payment/reporting via event | no | `[MVP]` |
| `egress` | `outbound_files` | `egress` | `egress` only | reporting, audit | selective | `[P1]` |
| `egress` | `outbox_events` / `inbox_events` `[ADD, ADR-N5]` | `egress` | `egress` only (+ dispatcher) | Kafka relay | no | `[MVP]` |
| `case` | `cases` / `case_decisions` / `case_r_message_links` | `case` | `case` only | egress, payment-lifecycle, reporting | selective | `[P1]` |
| `case` | `outbox_events` / `inbox_events` `[ADD, ADR-N5]` | `case` | `case` only (+ dispatcher) | Kafka relay | no | `[P1]` |
| `evidence` | `evidence_records` | `evidence-audit` | all via evidence port only | audit, search, dashboard | yes | `[MVP]` |
| `audit` | `audit_log` / `payload_hashes` | `evidence-audit` | all via audit port only | audit, search, dashboard | yes | `[MVP]` |
| `reference_data` | `participants` / `participant_accounts` | `reference-data` | `reference-data` only | all via read model (ledger/routing for accounts) | no | `[MVP]` |
| `reference_data` | `iso_reason_codes` / `status_catalog` | `reference-data` | `reference-data` only | payment, iso, egress, dashboard | no | `[MVP]` |
| `reference_data` | `validation_profiles` / `mapping_profiles` / `render_profiles` `[ADD, R-09]` | `reference-data` | `reference-data` only | `iso-adapter` (version selection), `egress` (rendering) | no | `[MVP]`/`[P1]` (render `[P1]`) |
| `reference_data` | `approval_matrix_rules` / `limit_policies` `[ADD, persona-driven]` | `reference-data` | `reference-data` only (`reference_data_admin` + step-up) | `payment-lifecycle` (matrix evaluation at submission) | no | `[MVP]` |
| `reference_data` | `business_calendars` / `service_levels` / `scheme_profiles` / `settlement_cutoff_calendar` | `reference-data` | `reference-data` only | settlement, routing, egress | no | `[P1]` |
| `risk` | `vop_checks` / `fraud_signals` / `anomaly_windows` / `corridor_stats` | `risk` (absorbs `vop`) | `risk` only | payment detail, dashboard, security | selective | `[P2]` |
| `simulation` | `simulation_scenarios` / `simulation_runs` / `failure_profiles` / `generated_events` | `simulation` | `simulation` only | dashboard, replay | no | `[MVP]`, Iteration 3 (re-tagged, `ADR-N6` — was stale `[P1]`; `[EDU-ONLY]` is a nature label, not a priority) |
| `reporting` | `statement_headers` / `statement_lines` | `reporting` (absorbs `live-analytics`) | `reporting` only | egress, dashboard | selective | `[P2]` |
| `reporting` | `dashboard_snapshots` / `operator_worklist` | `reporting` | `reporting` only | GraphQL | no | thin `[MVP]` / worklist `[P1]` |
| `security` | `tenants` / `branches` / `service_roles` / `role_permissions` | `identity-access` | `identity-access` only | all via identity port | no | `[MVP]` |

`[CHANGE, ADR-N5]` **`outbox_events`/`inbox_events` are per-schema, one pair per owning module** (rows above), not the single global pair this table previously implied by omission. `outbox_dispatcher_role` is granted narrow `SELECT`/`UPDATE(published_at)` across every outbox row above — and nothing else; it never appears as an "owner" because it is shared-kernel infrastructure, not a business boundary. Full grant DDL: main blueprint §4.4.

**Ownership DDL enforcement pattern (per owned schema):**
```sql
-- one DB role per module; only the owner role may write its schema
REVOKE ALL ON ALL TABLES IN SCHEMA ledger FROM PUBLIC;
GRANT USAGE ON SCHEMA ledger TO ledger_role;
GRANT SELECT, INSERT ON ledger.journal_lines, ledger.journal_entries TO ledger_role;  -- no UPDATE/DELETE
GRANT SELECT ON ledger.journal_lines TO reconciliation_role, reporting_role;           -- read-only for consumers
-- settlement_role deliberately gets NO grant on ledger.* → LedgerPort is the only path
```

---

## 6. Updated Kafka / Eventing Section (new §3.7) — superseded by §3.7 v2 `[CHANGE][ADR-N8, closed]`

`[CHANGE]` This section previously carried its own topic table, independent of the main blueprint's §3.7. That duplication is exactly what produced the topic-naming drift the review found (R-10): this table still showed `reconciliation.completed`/`reconciliation.mismatch.detected` and never gained `egress.dead_lettered`/`egress.manual_intervention_required`, while the main blueprint and the egress/reconciliation patches moved ahead independently. Per `ADR-N8`, **the main blueprint's §3.7 "Kafka Topic Catalog v2 / AsyncAPI Source of Truth" is now the sole topic table in the entire document set.** This section is retired as a duplicate; the rules it stated are reaffirmed but no longer restated with their own topic list:

- topic name = producing module's concern; a module never produces onto another module's topic;
- `csm.response.received` is the **only** topic where the simulator's output enters — proving simulation uses the real status path, not a back door;
- ordering is guaranteed by single-partition-per-key;
- every consumer is inbox-gated (per-schema `inbox_events`, `ADR-N5`) so redelivery is a no-op;
- DLQ per consumer group, except the three terminal-signal topics noted in §3.7 v2 (`egress.dead_lettered`, `egress.manual_intervention_required`, `reconciliation.run.failed`), which are themselves the DLQ/terminal signal.

**For the current topic list, owners, keys, ordering, DLQ rules, contract owners, and MVP/P1/P2 tags: see the main blueprint, §3.7 v2.** Do not re-list topics here or in any future integration patch — add new topics to §3.7 v2 directly.

---

## 7. Updated Read Models / GraphQL Section (new §6.6)

`[ADOPT]` **Write path = REST/gRPC/command APIs. Read path = GraphQL over read models. GraphQL is read-only in MVP** — no mutation resolver touches a domain repository. A dashboard or search view must never become a domain write API.

| Read Model | Owner | Source | GraphQL query | Refresh |
|---|---|---|---|---|
| `payment_timeline_view` | `payment-lifecycle` (+`reporting` projection) | `payment_status_history`, `payment_events`, `iso.payment_iso_identifiers` | `paymentTimeline(paymentId)` | event projection / SQL view |
| `payment_detail_view` | `reporting` | `payments` + ISO lineage + route decision + settlement attempt + egress state | `payment(id)` | materialized, event-refreshed |
| `settlement_cycle_dashboard` | `settlement` (+`reporting`) | `settlement_cycles`, `settlement_positions`, `settlement_items` | `settlementCycle(id)` | view + refresh on cycle events |
| `ledger_balance_view` | `ledger` (+`reporting`) | `liquidity_accounts`, `balance_snapshots`, `journal_lines` | `ledgerBalances(participantId)` | ledger-owned view |
| `reconciliation_mismatch_view` | `reconciliation` | `reconciliation_exceptions`, `reconciliation_runs` | `reconciliationExceptions` | event update after run |
| `egress_delivery_dashboard` | `egress` (+`reporting`) | `outbound_messages`, `transport_attempts`, `delivery_receipts` | `egressDeliveries` | view + live projection |
| `csm_profile_dashboard` | `routing` (+`reference-data`) | `routing_profiles`, `participant_reachability`, `route_rules` | `routingProfiles` | read-only view |
| `risk_signal_dashboard` | `risk` | `fraud_signals`, `anomaly_windows`, `vop_checks` | `riskSignals` | event projection |
| `simulation_replay_view` | `simulation` | `simulation_runs`, `generated_events`, payment events | `simulationRun(id)` | scenario-run projection |
| `participant_statement_view` | `reporting` | `statement_headers`, `statement_lines`, settlement events | `statement(id)` | generated after `settlement.completed` |

**Ownership rules `[ADD]`:** the module that owns the *source data* owns the *read model definition*; `reporting` may host cross-module projections but only by **reading** events/read models, never by writing source tables. Each dashboard's live counters (egress pending/claimed/delivered/failed, cycle netting, mismatch severity, SLA p95) are projections, not authoritative state — the authoritative state stays in the owning schema.

---

## 8. Updated Testing Strategy (ownership enforcement suite)

`[ADD]` Layered on top of the existing algorithm/flow tests. These specifically enforce ownership; each is a blocking CI gate.

**Architecture (Spring Modulith + ArchUnit):**
- Modulith `ApplicationModules.verify()` — allowed dependencies hold; no cycles.
- ArchUnit: no repository referenced outside its owning module package.
- ArchUnit: no dependency from `settlement` to any `ledger` repository/entity (LedgerPort only).
- ArchUnit: no `Instant.now()` / `LocalDate.now()` outside `ClockPort`.
- ArchUnit: GraphQL package depends on read-model/query services only — never on command services or domain repositories.

**Database (SQL / Testcontainers, one DB role per module):**
- Module role cannot INSERT/UPDATE another schema (grant test, per pair).
- `settlement_role` has **no** write grant on `ledger.*` (the LedgerPort proof).
- `journal_lines` rejects an unbalanced entry at COMMIT (deferred trigger).
- `journal_lines` UPDATE/DELETE denied for app roles; reversal is the only correction.
- Queue tables (`outbox_events`, `inbox_events`) are **not** RLS-protected; tenant user cannot read them but the dispatcher role can.
- Tenant/evidence tables **enforce** two-level RLS (cross-tenant read returns zero rows).
- Raw evidence + audit tables deny UPDATE/DELETE.

**Kafka:**
- Contract test: each producer's payload matches the topic's AsyncAPI/protobuf contract.
- Ordering test: events for one `payment_id` are consumed in production order.
- Dedupe test: a duplicate `csm.response.received` is a no-op via the inbox.
- Ownership test: no module produces onto a topic it does not own.

**Playwright (dashboard ownership):**
- Payment detail renders ISO identifiers sourced from `iso.payment_iso_identifiers`.
- Egress dashboard shows `PENDING → CLAIMED → DELIVERED/CONFIRMED` transitions.
- Reconciliation dashboard shows mismatch severity + source run.
- GraphQL read-only: attempting a mutation against a domain type is rejected/absent.

**Simulation-path:**
- Integration test: the `simulation` DB role has **no** write grant to `payment`/`settlement`/`ledger`/`egress`; a scenario must move a payment only via `payment.received`/`csm.response.received`. If the domain path is broken, the simulation test fails too — proving it exercises the real system.

---

## 9. Updated Backlog (ownership epics)

`[CHANGE]` Added to the main blueprint's §8 seed. Each story carries its source ownership rule.

**EPIC-OWN-1 Domain & schema ownership** — S1 one-DB-role-per-module + grants/revokes (SQL ownership tests); S2 Flyway folder-per-module + repository package boundaries; S3 Modulith `allowedDependencies` config + `verify()` gate; S4 ArchUnit ownership rules (repository/GraphQL/`Instant.now()`).
**EPIC-OWN-2 ISO lineage split** — S1 `iso.payment_iso_identifiers` + `message_lineage` (raw→ISO→canonical→payment map); S2 pacs.002 correlation via lineage (orphan→DLQ); S3 iso-adapter-makes-no-business-decision arch test.
**EPIC-OWN-3 Payment slim row** — S1 confirm `payment-lifecycle` sole writer of `payment.*`; S2 coded status/reason from `reference_data` (FK); S3 God-Module guard test (no settlement/routing/egress write from payment-lifecycle).
**EPIC-OWN-4 Reference-data catalogs** — S1 `participants`/`participant_accounts`; S2 `iso_reason_codes`/`status_catalog` + loaders; S3 `business_calendars`/`service_levels`/`scheme_profiles`; S4 reference-data-owns-catalogs SQL grant test.
**EPIC-OWN-5 Ledger ownership enforcement** — S1 `ledger_role` sole writer; S2 `LedgerPort` (`reserve/post/release/reverse`); S3 settlement-has-no-ledger-grant test; S4 deferred trigger + immutability + reversal tests.
**EPIC-OWN-6 Egress confirmation & boundary** — S1 `CLAIMED` state + `transport_attempts`/`delivery_receipts`; S2 egress-cannot-write-payment-status test; S3 delivered≠final assertion; S4 egress delivery dashboard.
**EPIC-OWN-7 Kafka topic ownership** — S1 topic-owner matrix + AsyncAPI contracts, generated from main blueprint §3.7 v2 (`ADR-N8`) — no locally-restated topic table; S2 producer-owns-topic test; S3 ordering + inbox-dedupe tests; S4 DLQ per consumer group, incl. the three terminal-signal topics (`egress.dead_lettered`, `egress.manual_intervention_required`, `reconciliation.run.failed`).
**EPIC-OWN-8 Read models & GraphQL read-only** — S1 read-model ownership per source module; S2 GraphQL read-only enforcement (no domain-repo dependency); S3 dashboard projection refresh; S4 Playwright dashboard ownership tests.
**EPIC-OWN-9 Simulation path enforcement** — S1 simulation uses public commands/`csm.response.received` only; S2 no-domain-write-grant test; S3 deterministic-seed replay view.
**EPIC-OWN-10 Signature module boundary `[ADD, R-13, closed]`** — S1 `signature` schema + `message_signatures`/`keys` DDL; S2 `SignaturePort` (verify/sign) + no-repository-outside-signature ArchUnit test; S3 verify-before-parse ordering test on the `ingress` filter chain (G1, now enforced not just documented); S4 non-`signature` roles cannot write `signature.*` grant test.
**EPIC-OWN-11 Per-schema outbox/inbox ownership `[ADD, ADR-N5, closed]`** — S1 per-module `<schema>.outbox_events`/`<schema>.inbox_events` DDL, created with each module's first migration; S2 `outbox_dispatcher_role` grant (claim/mark-published across all outbox tables, zero domain-table grants) + negative sweep test; S3 cross-module outbox-write-denied test (module A cannot write module B's outbox); S4 inbox dedupe + replay-does-not-duplicate-domain-effect test.

---

## 10. Final Blueprint Integration Notes

**Rewrite (replace existing content):**
- §2.5 Spring reception table — add the §3.7 pointer row; keep the PG18-idempotency row from v2.
- §3.3 money path — rewrite the `LedgerService` bullet into the explicit `LedgerPort`-only + triple-enforcement paragraph (§2 above).
- Scattered Kafka topic mentions in §2/§6 — replace with a pointer to the new §3.7 table.

**Extend (add to existing, don't remove):**
- §2.2 — append the ISO-owns-lineage-not-decisions paragraph.
- §4 — annotate every DDL block with `-- owner: <module> [priority]`; add §4.3b (ISO lineage) and §4.13 (reference-data catalogs); add `ledger_reversals` note to §4.5; move `settlement_cutoff_calendar` ownership to reference-data in §4.6.
- §5 — append the finality≠delivery paragraph.
- §6 — add §6.2b (egress transport-only + `CLAIMED`) and §6.6 (read models + GraphQL read-only).
- Test strategy — append the ownership enforcement suite (§8 above).
- §8 backlog — add EPIC-OWN-1..9.

**Leave unchanged:**
- §1 method statement.
- §2.2–§2.4 flow narratives (except the one ISO paragraph), §2.3 file rail.
- §4.1 access paths, §4.2 ingress DDL, §4.7 RLS (already selective — only cross-reference §3.6.3), §4.8 PG19 notes.
- §5 mechanics body, §7 business view.

**Must be done before implementation `[MVP-BLOCKER]`:**
1. Freeze §3.6 (this section) as the ownership contract — it gates every migration and module.
2. Create the DB-role-per-module + grant/revoke matrix (§5 pattern) — it is what makes ownership real rather than documented.
3. Wire the four architecture gates (Modulith verify, ArchUnit ownership, SQL grant tests, no-cross-schema-write) into CI **before** the first domain table is written, so a violation can never land green.
4. Confirm the `routing_profiles` config-vs-decision split (reference-data owns catalog, routing owns `route_decisions`) — otherwise ownership drifts on the very first routing story.

`[HLD-GAP]` closed by this integration: module ownership, schema ownership, one-writer rule, allowed/forbidden dependencies, Kafka topic ownership, read-model ownership, GraphQL read-only, ledger-writer boundary, egress-transport boundary, iso-lineage boundary, reference-data catalog ownership, simulation-path rule, and the architectural test suite that enforces all of them.

---

*End of ownership integration. Converts the domain-ownership blueprint into concrete additions to the main blueprint (new §3.6, §3.7, §4.3b, §4.13, §6.2b, §6.6, ownership test suite, EPIC-OWN-1..9). Consistent with the v2 data-model patch (payments split, iso lineage, SQL ledger invariant, selective RLS) and Master Architecture v2026-07-05. No production code; no claim of production SEPA/KIR compliance.*
