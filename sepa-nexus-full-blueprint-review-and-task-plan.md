# SEPA Nexus — Full Blueprint Review & Task Plan

**What this is.** A surgical, cross-document audit of all current SEPA Nexus Markdown artifacts (15 documents, ~6,500 lines), treated as one system — followed by a concrete, prioritized build plan: modules, flows, features, seam gaps, epics, stories, tasks, tests, ADRs, roadmap, and the React + Next.js frontend/dashboard design. **No source document is modified by this review.**
**Truth hierarchy applied:** newest integration patches (routing, settlement/liquidity, egress, reconciliation, R-messages/case, ISO lineage, case module) → Master Architecture & State → Comprehensive Architecture Review → Main Blueprint v8/v10 → older HLD / implementation plan / algorithm synthesis / update plan. Where the hierarchy still leaves a conflict, it is marked `[HLD-GAP]` with a proposed decision — never guessed away.
**Markers:** `[ADOPT] [CHANGE] [ADD] [DEFER] [REJECT]` · `[MVP] [P1] [P2]` · `[OVERENGINEERING] [ARCH-RISK] [BUSINESS-RISK] [DB-OWNERSHIP] [EVENT-RISK] [FLOW-GAP] [SEAM-GAP] [DB-GAP] [API-GAP] [UI-GAP] [SECURITY-GAP] [TEST-GAP] [HLD-GAP] [ROADMAP-GAP] [MVP-BLOCKER] [EDU-VALUE] [LOW-VALUE]`.

---

## 1. Executive Verdict

**Yes — the current document set forms a coherent, implementable, educationally valuable and testable system that can be decomposed into modules, features, epics, stories and concrete tasks for the chosen stack. But it is not yet ready to *start coding* on three fronts, and its roadmap has one structural defect.**

**What is genuinely settled (verified, not assumed).** The CPC-SP topology has held through eight independent review passes without a single ownership reversal: one Modulith deployable, one Payment Lifecycle spine, one Settlement Profile Engine keyed by `(settlement_basis, liquidity_mode)` never by CSM name, one append-only Ledger behind a triple-enforced `LedgerPort`, one Simulation engine entering only through public paths, profiles-not-engines everywhere. The one-writer-per-schema contract (§3.6 of the main blueprint) is complete for 16 modules and enforced three ways (Modulith `allowedDependencies`, ArchUnit, SQL grants). The five-status separation (business ≠ ISO ≠ finality ≠ transport ≠ receipt), the finality-correct return-as-new-payment model, the deterministic `as_of` reconciliation snapshot, the case timing matrix, and the immutable route-explanation snapshot are all frozen and mutually consistent across every patch. G1–G6, G8 and G9 from the Gap Register are **closed at design level** in the v8/v10 blueprint. This is an unusually disciplined document set; the convergence of independent deep-research passes onto the same shape is a real trust signal, not an artifact of copy-paste.

**What is not ready — the three fronts:**

1. **`[ROADMAP-GAP]` `[MVP-BLOCKER]` Iteration 0 does not exist in the current roadmap.** The Master document's consolidated 5-iteration MVP starts at "Iteration 1 — Spine", which mixes platform foundation work (Keycloak realm, RLS plumbing, CI gates, Testcontainers harness, compose stack, contract folders) with the first business vertical slice. The *old* HLD had this right (Tier 0 / Phase 0 / TS-01…TS-17 / "First 5 Steps") — but that material was never carried into the consolidated roadmap, and the HLD sits at the bottom of the truth hierarchy with much of its module content superseded. Result: the only document that defines the technical skeleton is the least authoritative one. **Correction (§12):** re-establish Iteration 0 (Platform Skeleton) as a first-class phase, ported from HLD Tier 0/Phase 0 and updated to the current stack (Maven, Spring Boot 4.1, Modulith 2.1, PG18, pnpm, Podman); Iteration 1 becomes a pure business vertical slice.
2. **`[UI-GAP]` The React + Next.js layer is the widest under-designed surface.** Read models and GraphQL queries exist (30+), Playwright assertions are named per module — but there is **no screen inventory, no navigation/routing model, no role→screen matrix, no component inventory, no deep-link scheme, no test-id convention**, and the BFF-vs-SPA token ADR (S15) is recommended (BFF) but never frozen. The frontend cannot be decomposed into stories today. §14 closes this on paper.
3. **`[SECURITY-GAP]` + `[DB-GAP]` The `signature` module is named everywhere and designed nowhere.** It appears in the master module map (Gap ✗), in the ingress filter chain (`MessageSignatureVerificationFilter`), and as `SignaturePort` in egress — but it has no responsibilities section, no owned schema, no `message_signatures` DDL, no key-management sketch. Likewise the Keycloak adoption plan (Organizations claims → two-level GUC, FGAP v2 admin plane, FAPI-2 client profile) exists only as a recommendation table in the master doc, not as an implementable design. Both are Iteration-5-facing, so they don't block Iteration 0/1 — but they must be papered before Iteration 2 (egress status-out assumes signing exists as a port) hardens.

**Beyond those three:** the review found **24 concrete inconsistencies/gaps (§9)** — a stale reconciliation priority in the ownership integration, Kafka topic-catalog drift between patches and the frozen §3.7 table, an unresolved routing invocation-style contradiction (out-of-process gRPC vs Kafka consumer — a real ADR to freeze), two priority taxonomies (Master's Iterations 1–5 vs the blueprint's MVP/P1/P2) never mapped to each other, the direct-JSON-payment identifier path that violates the `iso.payment_iso_identifiers` NOT NULL contract as written, G7 (routing-down failure policy) still open, and undefined ownership of the shared `outbox_events` table. **None is architectural; all are paper-fixable; roughly a third are `[MVP-BLOCKER]`-adjacent because they sit on the Iteration 0/1 path.**

**Verdict in one line:** *architecture 9/10, data design 9/10, seams 8/10 after the patches, roadmap 6/10, frontend 4/10 — fix the roadmap split, paper the frontend and signature, freeze five ADRs (§18), then build Iteration 0.*

---

## 2. Architecture Health Score

Scores are earned against the current document set, not graded on a curve. Reference: the Comprehensive Review's earlier scorecard (design maturity 8.3 pre-patches) — the v3–v10 patches materially moved the seam and data axes.

| Area | Score /10 | Delta vs pre-patch review | One-line judgment |
|---|---:|---|---|
| Core topology (CPC-SP) | 9.5 | +0.5 | Eight review passes, zero ownership reversals; profiles-not-engines held under pressure. |
| Module completeness & ownership | 9 | +0.5 | 16-module ownership contract complete; `signature` is the one named-but-undesigned module. |
| Seam design | 8 | +1.5 | G1–G6/G8/G9 closed in DDL/flows; G7 (routing-down) and routing-invocation-style remain open. |
| Data architecture (PG18) | 9 | = | Access-path-driven, SQL-enforced ledger invariant, selective RLS, lineage split — production-like. Minor DDL debt (§9). |
| Eventing / Kafka | 8 | -0.5 | Topic ownership frozen, but the catalog has drifted across patches (`egress.dead_lettered`, `reconciliation.run.failed` missing from §3.7) `[EVENT-RISK]`. |
| ISO 20022 / domain realism | 9 | +1 | Lineage, correlation, R-message, finality and file-rail semantics now veteran-grade; direct-JSON identifier path has one contract hole. |
| Security architecture | 6.5 | -1 | Selective RLS is right; but signature undesigned, Keycloak Organizations/FGAP/FAPI-2 un-papered, role→command matrix fragmented across 4 docs. |
| Frontend / dashboards | 4 | -3 | Read models exist; screens, navigation, roles-per-screen, token model do not. The widest gap. |
| Testability / determinism | 9.5 | = | Per-module test labs, flagship boundary tests, seed-driven determinism — best-in-class for a lab. |
| Observability / operations | 6.5 | -0.5 | Three-ID correlation designed; no consolidated logs/metrics/traces/alerts/DLQ table exists anywhere (§17 fills it). |
| Roadmap / delivery structure | 6 | -2 | Iteration 0 missing from the consolidated roadmap; two priority taxonomies unmapped `[ROADMAP-GAP]`. |
| Documentation coherence | 8 | — | Truth hierarchy works; two integration docs carry stale rows superseded by later patches (flagged, not dangerous). |
| **Overall build readiness** | **7.8** | | **Ready for backlog decomposition after the §9 corrections and §18 ADR freeze; not ready for code until Iteration 0 is defined and started.** |

---

## 3. Business / Domain Review

**Purpose fitness `[EDU-VALUE]`.** The system is judged against its actual purpose: a synthetic, deterministic SEPA/ISO 20022 laboratory and SDET-architecture portfolio piece — not a bank. Against that purpose the domain content is exceptional: the six problems that make a payments lab worth building (correlation, duplicate, orphan, out-of-order, replay, evidence) are all first-class, testable artifacts; the four-concept reject/return/recall/reversal clarity and the finality timing matrix are the kind of domain precision most production teams get wrong; the intentional-imperfection catalog (`[EDU]` anomalies driven by the seeded simulator) is the direct feed for the Charter Loop synthetic-PR corpus.

**Domain boundaries hold under business scrutiny:**
- **Statuses:** business status (`payment-lifecycle`) vs technical/transport status (`egress`) vs ISO message status (`iso-adapter`) vs acceptance state vs finality — five separated axes, each with an owner and a test. No document confuses them post-v8. `[ADOPT]`
- **Finality ≠ delivery:** frozen in three places (§5 mechanics, §3.11 egress boundary, ownership rule 5) with a named test. `[ADOPT]`
- **Return ≠ reversal:** the finality-correct return model (new opposite-direction payment; original journal lines byte-identical; `REVERSAL` = pre-finality booking-error only) is the crown-jewel decision and is consistently enforced (no `case`→`LedgerPort.reverse` path). `[ADOPT]`
- **Reconciliation detects-and-escalates, never repairs:** `ACTION_REQUESTED` is a request row; grant tests prove no source write. `[ADOPT]`
- **Case decides-and-coordinates, never mutates:** every money/status/ISO effect is a request through the owning module. `[ADOPT]`
- **ISO adapter records, never rules:** correlation is the adapter's; transition is the lifecycle's. `[ADOPT]`

**Business flow completeness.** All sixteen flows the review scope demands exist at design level (§6 matrix). The two thinnest business surfaces are: (a) **manual/operator investigation** — the pieces exist (orphan queues, exception lifecycle, case escalation, manual resend) but no document draws the *operator's* unified worklist across them `[FLOW-GAP, minor]`; (b) **cut-off/cycle business calendars** — correctly designed (`reference_data.business_calendars`, TARGET/PL holidays) but tagged `[P1]`, which means Iteration 4's deferred settlement runs on a single synthetic cut-off until then — acceptable, but state it in the Iteration 4 DoR.

**Deliberate business exclusions — reaffirmed as correct, not gaps `[ADOPT]`:** direct debit (SDD / polecenie zapłaty) + mandates (a second lifecycle, named and parked); real AML/sanctions; camt.052/053/054 statement depth (P2); DORA incident module, VoP, conformance (post-MVP with named homes); production compliance claims (never made). `[BUSINESS-RISK]` unchanged from prior reviews: breadth appetite vs solo capacity — mitigated only by iteration gates, which is exactly why the Iteration 0/1 split (§12) matters.

**`[LOW-VALUE]` candidates confirmed for rejection:** regulator workspace (HLD Epic 14), backup/restore control center as a domain module (HLD Epic 15 — keep as an ops drill script, not a module), Three.js map before an SSE data feed asserts on data, six-simulator source lab breadth (one seeded simulator + profiles suffices).

---

## 4. Technical Review

**Stack verdict: internally consistent, version-honest, one protocol per purpose.** JDK 25 / Spring Boot 4.1 / Framework 7 / Modulith 2.1 / Maven / Spring Kafka 4.1 / Spring gRPC 1.1.0 / Spring for GraphQL 2.0.4 / PG18 baseline (PG19 lab-only) / Keycloak 26.x / Next.js + React + TS6 + pnpm / Playwright 1.61 / Testcontainers / Podman. The PG19 demotion (`ON CONFLICT DO SELECT` → lab seam; PG18 two-step behind `IdempotencyStore`) is correctly propagated everywhere post-v2. `[ADOPT]`

**Findings by layer:**

- **Spring Modulith.** The 16-module map with `allowedDependencies` + `verify()` as a blocking CI gate is right. Two technical loose ends: (1) `batch` is listed as a module but is really Spring Batch metadata used *inside* `ingress` — keep the schema, drop the pretense of a module `[CHANGE, cosmetic]`; (2) the shared `outbox_events`/`inbox_events` tables have **no owner** in §3.6.2 while §3.2 says "module-local outbox writer, shared relay" — one shared table with a shared-kernel dispatcher role, or per-schema outbox tables? Must be decided before Flyway folder-per-module lands `[DB-OWNERSHIP]` `[MVP-BLOCKER]` (proposed decision in §9, R-14).
- **Routing invocation style `[HLD-GAP]` `[ARCH-RISK]`.** Master + CPC-SP + algorithm synthesis all say routing is *the one out-of-process gRPC service*; the blueprint's §3.7 has `routing` as a Kafka consumer of `payment.validated` inside the Modulith, and §4.10 models it as an in-process pipeline. These are two different systems. **Proposed decision:** routing is an **in-process Modulith module consuming `payment.validated`** for MVP (simpler, consistent with the pipeline model and the immutable-decision design); the out-of-process gRPC variant becomes the *educational extraction exercise* of a later iteration, honoring "≤1 gRPC" as a ceiling, not a requirement. This also dissolves G7's urgency for MVP (no network hop to fail) while the degraded-mode policy is still written for the extracted variant. Freeze as ADR (§18, ADR-N2).
- **PostgreSQL.** The deferred constraint trigger for Σ=0, immutability grants, reversal model, selective RLS, UUIDv7, partition strategy, partial queue indexes, covering idempotency index — all sound. DDL debt found: `validation_profiles`/`mapping_profiles`/`render_profiles` are referenced by FK-like codes from `iso.iso_message_versions` but exist only as a comment in §4.13 `[DB-GAP]`; `settlement_attempts UNIQUE(payment_id)` contradicts "retries create history rows post-MVP" (needs a partial unique on live states when retries land) `[DB-GAP, deferred]`; direct-JSON payments cannot satisfy `payment_iso_identifiers(iso_message_id NOT NULL)` unless a `parse_status='SKIPPED'` `iso_messages` row with a defined JSON pseudo-version is created — the flow narrative promises identifiers+lineage for JSON, the DDL doesn't support it as written `[DB-GAP]` `[MVP-BLOCKER]` (it's on the Iteration 1 hot path).
- **Kafka.** Topic ownership table is the right instrument, but it is already stale: the egress patch adds `egress.dead_lettered` + `egress.manual_intervention_required` (ops-visible Kafka) and the reconciliation patch renames/extends to `reconciliation.run.completed`/`run.failed`/`exception.detected` — none reflected in §3.7 `[EVENT-RISK]`. One reconciliation topic-name drift (`reconciliation.completed` vs `reconciliation.run.completed`; `mismatch.detected` vs `exception.detected`). **Correction:** one reconciled §3.7 v2 table is the AsyncAPI source of truth; patch documents point at it.
- **Security.** Selective RLS matrix + service-role tiers: correct and tested. Open: `signature` design (Exec Verdict #3); Keycloak Organizations claim→GUC mapping design; consolidated role→capability matrix for the *current* module set (recall_approver, case_supervisor, egress manual-resend role, recon operator, cycle-close role, reference-data admin) — currently spread over 4 documents with the HLD's 13-role matrix partly superseded `[SECURITY-GAP]` (§15 consolidates).
- **GraphQL / REST / admin commands.** GraphQL read-only rule is consistently enforced, incl. the pattern "operator actions are role-gated REST/gRPC admin commands, never GraphQL mutations." Loose end: those admin commands (resolve recall, resend artifact, assign exception, manual correlation, replay) have **no REST contract inventory** anywhere `[API-GAP]` (§13 lists them).
- **Observability.** Designed in fragments (three IDs, segmented latency budget, lag as first-class, per-story observability task type) but never consolidated; no alert or DLQ-monitoring table exists `[HLD-GAP]` (§17 fills).
- **Testing.** The strongest layer. Per-module test labs with flagship boundary tests (return-after-finality byte-identical journal; action-request-no-mutation; no-implicit-fallback; delivered≠final; unbalanced-entry-at-COMMIT). §16 only re-organizes and adds the missing Iteration-0 and frontend layers.

---

## 5. Seam Gap Review

Status of every named seam gap after the v2–v10 patches. "Current evidence" cites where the closure (or hole) lives.

| Gap | Current Evidence | Risk | Owner Module | Required Decision | Priority |
|---|---|---|---|---|---|
| XML threat model (XXE, entity bombs, size limits, parse timeout, signature-before-parse) | Closed on paper: §2.2 flow, §2.5 hardened factory bean, `RawBodyCachingFilter`→`MessageSignatureVerificationFilter` order pinned, `iso_message_parse_errors`, negative fixtures in EPIC-IN-1 | Low (design) / High (if skipped in build) | `ingress`+`iso-adapter`+`signature` | `[ADOPT]` — carry the negative-fixture suite into Iteration 1 DoD even for the JSON-only slice (factory bean lands early) | `[MVP]` |
| Egress delivery outbox | Closed: v8 unified lifecycle `REQUESTED→…→CLOSED`, `SKIP LOCKED` dispatcher, `transport_attempts`, retry/dead-letter, idempotent artifact `UNIQUE(trigger_event_id, artifact_type, recipient_id)` | Low | `egress` | `[ADOPT]` | `[MVP]` (Iter 2) |
| Service-role RLS tier for background workers | Closed & simplified: §3.5 + §4.7 selective model (RLS-table `p_system_*` policies; queue/ledger by grants); zero-rows-on-empty-session test | Low | `security`/`identity-access` | `[ADOPT]`; keep both Testcontainers assertions as Iteration 0/1 gates | `[MVP]` |
| pacs.002 / Orgnl* correlation keys | Closed: `iso.payment_iso_identifiers` (rich form), 9-step correlation policy, `iso_message_correlation`, orphan→DLQ | Low | `iso-adapter` | `[ADOPT]`; fix the direct-JSON identifier hole first (§9 R-08) | `[MVP]` |
| Batch partial-accept / result-file contract | Closed: §2.3 file rail (scoped `UNIQUE(tenant,sender,msg_id,type)`, SkipPolicy item-level accept, result-file via egress collector) | Low | `ingress`+`egress`+`iso-adapter` | `[ADOPT]` | `[MVP]` (files P1 for outbound `outbound_files`) |
| Cut-off race transactionality | Closed: cycle-row `FOR UPDATE`, `CLOSING` grace state, `MOVED_TO_NEXT_CYCLE`; **business calendars remain `[P1]`** | Low-Med | `settlement`+`reference-data` | `[ADOPT]`; state in Iteration 4 DoR that MVP runs one synthetic calendar | `[MVP]`/cal `[P1]` |
| Routing-down failure policy (G7) | **Open.** No `@Retryable`/degraded-mode/queue-vs-reject decision anywhere in the blueprint | Med (or Low if routing stays in-process) | `routing` seam | `[CHANGE]` — resolve via ADR-N2 (§4): in-process for MVP dissolves the network seam; write the degraded-mode policy as part of the future extraction exercise | `[P1]` `[SEAM-GAP]` |
| Virtual Clock ownership (G8) | Closed: `ClockPort` in every port list; ArchUnit ban on `Instant.now()` | Low | shared kernel | `[ADOPT]` — wire from Iteration 0 skeleton | `[MVP]` |
| Ledger snapshot isolation for reconciliation (G9a) | Closed & upgraded: deterministic `as_of` watermark, `reconciliation_run_sources`, immutable results, rerun-by-new-run | Low | `reconciliation` | `[ADOPT]` | `[MVP]` |
| Audit same-transaction rule (G9b) | Closed in flows ("ONE DB TX … + audit"); **hash-chaining still open (Low-Med, parked)** | Low | `evidence-audit` | `[ADOPT]`; hash-chain `[DEFER P1]` | `[MVP]`/chain `[P1]` |
| BFF vs SPA token model (S15) | **Open as ADR.** BFF recommended in master §10.4 + comprehensive §6; never frozen; frontend design absent | Med — blocks all frontend stories | frontend + `security` | `[CHANGE]` — freeze ADR-N3: BFF (Next.js server session, HttpOnly cookie) default; DPoP-SPA documented alternative | `[MVP-BLOCKER]` for §14 |
| Keycloak Organizations / FGAP v2 / FAPI-2 / DPoP adoption | Recommendation table only (master §10.4); no claim-mapping or realm design; two-level RLS DDL exists but the claim source doesn't | Med | `security` | `[ADD]` — EPIC-SEC-KC (§12): realm design doc + Organizations claims → `app.tenant_id`/`app.branch_id` GUCs; FGAP v2 for admin commands; FAPI-2 profile with `signature` in Iteration 5 | `[MVP]` (Org claims) / `[P1]` (FAPI-2, passkeys) |
| `signature` module design | **Open.** Named in filters/ports; no schema, no `message_signatures` DDL, no signer service, no key model | Med-High for Iterations 2/5 | `signature` | `[ADD]` — EPIC-SIG (§12): boundary section + DDL + verify/sign ports + HSM-like signer stub; MVP = verdict recording + detached-signature verify on raw bytes | `[MVP-BLOCKER]` before Iter 2 status-out signing |

**Seam verdict:** 9 of 13 closed at design level; the 4 open items (routing-down/invocation, BFF, Keycloak adoption detail, signature) are exactly the ones this plan turns into ADRs + epics. Nothing re-opens CPC-SP.

---

## 6. End-to-End Flow Coverage Matrix

Legend: ✅ designed with owner+tables+events · ◐ partially designed · ✗ missing. "Dashboard" = a read model exists; **no flow has an actual screen design yet** — that single systemic `[UI-GAP]` is not repeated per row (fixed in §14). "Keycloak" names the role that must exist; ◐ means the role is implied but not in any consolidated matrix (`[SECURITY-GAP]`, fixed in §15).

| Flow | Business Step | Backend Module | Database Tables | Kafka/Internal Event | REST/API | GraphQL Read Model | React/Next.js Dashboard | Keycloak/Security | Tests | Gap? |
|---|---|---|---|---|---|---|---|---|---|---|
| Inbound payment message (REST JSON/XML) | receive→verify→archive→idempotency→parse→map→persist | `ingress`,`signature`,`iso-adapter`,`payment-lifecycle` | `raw_inbound_messages`,`idempotency_keys`,`iso.iso_messages`,`iso.payment_iso_identifiers`,`iso.message_lineage`,`payments`,`payment_status_history`,`outbox_events` | `payment.received`, `iso.message.parsed/rejected` | `POST /api/v1/payments`, `/api/v1/iso/pain001` | `payment(id)`, `paymentTimeline`, `messageLineage`, `messageEvidence` | payment list/detail/timeline (read models ✅, screens ✗) | PAYMENT_INITIATOR / integration client ◐ | idempotency replay/conflict, XML hardening negatives, lineage, Playwright happy path ✅ | ◐ JSON identifier DDL hole (§9 R-08); signature module undesigned |
| Inbound payment file (batch) | file accept→file idempotency→Spring Batch→item partial accept | `ingress`(+`batch`),`iso-adapter` | `inbound_files`,`inbound_file_items`, staged `payments` | `FileAccepted`,`FileProcessed`(internal), per-item `payment.received` | `POST /api/v1/files` | file processing view (named in §6.6? — **not present**) | file dashboard ✗ | file submitter role ◐ | SkipPolicy, restart, scoped-dup, counts ✅ | ◐ `[UI-GAP]`+missing `inbound_file_dashboard` read model `[API-GAP]` |
| Outbound response / result file | trigger→idempotent artifact→render→sign→collect→deliver | `egress`,`iso-adapter`,`signature` | `outbound_artifacts`,`outbound_messages`,`outbound_files`,`egress_profile_snapshots`,`iso.iso_outbound_artifacts`(P1) | `egress.delivery.requested/confirmed` | manual resend admin command | `egressDeliveries`, `result_file_dashboard`, `outboundArtifact`(P1) | egress dashboard (read models ✅) | egress operator/resend role ◐ | idempotent creation, five-status, result-file ✅ | ◐ signing depends on `signature` design |
| Routing decision | candidates→eligibility→reachability→cutoff read→decision+explanation | `routing`,`reference-data` | `route_decisions`,`route_candidate_results`,`route_decision_explanations`,`participant_reachability`, RD catalogs | `payment.routed`(+snapshot id), `route.failed` | — (event-driven) | `routeExplanation`,`reachabilityMatrix`,`routingFailures` | routing viewer (read models ✅) | ops read role ◐ | pipeline, per-profile reachability, no-implicit-fallback, snapshot immutability ✅ | ◐ invocation style ADR-N2; G7 policy |
| Settlement success (instant & deferred) | strategy by (basis,mode)→LedgerPort→finality rule | `settlement`,`ledger` | `settlement_attempts/_events/_profile_snapshots/_finality_records/_liquidity_checks`, `journal_entries/lines`,`liquidity_accounts/reservations` | `settlement.attempted/completed`, `payment.status.reported` | — | `settlement_attempt_timeline`,`settlementCycle`,`ledgerBalances`,`finality_evidence_view` | settlement timeline + cycle dashboard ✅ | cycle-close role ◐ | Σ=0 at COMMIT, no-double-reserve, finality rules, grant tests ✅ | — |
| Settlement failure (technical) | attempt→`SETTLEMENT_FAILED_TECHNICAL`→status out | `settlement`,`egress` | attempts/events + outbox | `settlement.failed` | — | attempt timeline | same | ops role ◐ | failure path, RJCT out ✅ | ◐ `SETTLEMENT_PENDING_INVESTIGATION` is P1 — MVP failure ends at status; state in DoR |
| Liquidity shortage | reserve→INSUFFICIENT→outcome by basis+mode+cutoff | `settlement`,`ledger` | `settlement_liquidity_checks`,`liquidity_reservations` | internal `settlement.insufficient_liquidity` → `settlement.failed` | — | `liquidity_check_view`,`ledgerBalances` | liquidity dashboard (read model ✅) | treasury/ops read ◐ | reserve-fail, release-after-reject, queue P1 ✅ | — (queue `[P1]`) |
| Rejected payment (ISO or business) | ISO reject (no payment) vs business reject (RJCT) | `iso-adapter` / `payment-lifecycle`+`settlement` | `iso_message_validation_results`,`iso_message_parse_errors` / `payment_status_history` | `iso.message.rejected` / `payment.status.reported(RJCT)` | 422 taxonomy | `isoValidationErrors`, timeline | validation error view ✅ | — | ISO-vs-business split tests ✅ | — |
| Recall / request for cancellation (camt.056) | correlate→case→timing matrix→decide→camt.029 via egress | `iso-adapter`,`case`,`egress` | `"case".cases/_decisions/_r_message_links/_evidence_*` | `iso.message.correlated`,`case.opened/resolved/closed/escalated` | ResolveRecall admin command (role-gated) | `case(id)`,`caseQueue`,`recall_dashboard` | case dashboard (read models ✅) | recall_approver, case_supervisor ◐ | timing matrix, role-gate, camt.029 render ✅ | — (`[P1]` module by design) |
| Return after finality (pacs.004) | correlate→case→NEW return payment via normal intake→original RETURNED | `case`,`payment-lifecycle`,normal path | `case_return_links`, new `payments` row, lineage `RETURN` | `case.return_payment_requested`; return rides normal topics | return via payment intake port | `return_dashboard`,`return_vs_reversal_explanation_view` | case/return dashboard ✅ | recall_approver ◐ | **flagship**: new payment + byte-identical original journal ✅ | — |
| Egress delivery failure | attempt fail→retry backoff→dead-letter→manual intervention | `egress` | `transport_attempts`,`manual_delivery_actions`(P1) | `egress.dead_lettered`,`egress.manual_intervention_required` (**not in §3.7 table**) | resend/cancel admin command | `failed_delivery_queue`,`manual_resend_dashboard` | failed-delivery dashboard ✅ | egress operator ◐ | retry policy, upstream-untouched, dead-letter ✅ | ◐ `[EVENT-RISK]` topic-table drift (§9 R-10) |
| Reconciliation mismatch | as_of→collect→compare→classify→exception→escalate | `reconciliation`,`reference-data` | `reconciliation_runs/_run_sources/_results/_exceptions`,`exception_events`,`evidence_bundles/_pointers` | `reconciliation.run.completed/failed`,`reconciliation.exception.detected` (naming drift vs §3.7) | assign/resolve/false-positive admin commands (P1) | `exception_queue`,`exception_severity_dashboard`,`ledger_drift_dashboard` | recon dashboards ✅ | recon operator ◐ | deterministic as_of, no-source-write, severity, no-auto-fix ✅ | ◐ topic naming drift (§9 R-10) |
| Manual / operator investigation | orphans + exceptions + dead-letters + cases in one worklist | `iso-adapter`,`reconciliation`,`egress`,`case` | (queries over the four modules' queues) | — | the scattered admin commands | 4 separate queue read models exist; **no unified operator worklist** | operator dashboard ✗ | operator role ◐ | per-queue tests ✅ | ◐ `[FLOW-GAP]` unified worklist read model + screen (§14, S-01) |
| Duplicate message/file | inbox dedupe / idempotency replay / DUPL reject / IGNORED_DUPLICATE / case dedupe | `ingress`,`iso-adapter`,`case` | `inbox_events`,`idempotency_keys`,`inbound_files`,`iso_message_correlation` | no-op by design | 202 replay / 409 / DUPL | correlation view | evidence views ✅ | — | duplicate suite ✅ (best-covered flow) | — |
| Cut-off / cycle handling | OPEN→CLOSING→CLOSED→NETTED→SETTLED→RECONCILED under lock | `settlement`,`reference-data` | `settlement_cycles/items/positions`,`settlement_cutoff_calendar`(P1) | `settlement.completed(cycle)` | — | `settlement_cycle_dashboard`,`cutoffEligibility` | cycle dashboard ✅ | cycle-close role ◐ | G6 race test, netting SQL ✅ | ◐ calendars `[P1]` |
| Simulation failure scenario | seed→scenario→normal public paths→deterministic anomaly | `simulation` | `simulation_scenarios/runs`,`failure_profiles`,`generated_events` | `simulation.event.generated`→`csm.response.received` only | `POST /simulations/...` | `simulationRun(id)`, per-module `simulation_*_trace` views | simulation dashboard ✅ | sim operator ◐ | same-seed determinism, no-domain-write grant ✅ | ◐ priority conflict: blueprint `[P1]` vs master Iteration 3 (§9 R-05) |

**Matrix verdict:** 16/16 flows have an owner, persistence, lifecycle, and events; 14/16 have read models. Systemic gaps concentrate in exactly three columns — **screens (`[UI-GAP]`), consolidated roles (`[SECURITY-GAP]`), and two event-catalog drifts (`[EVENT-RISK]`)** — plus one missing read model (inbound file dashboard) and one missing composite (operator worklist).

---

## 7. File Processing Coverage

| File Scenario | Business Purpose | Owner Module | Required Tables | Required Events | API / React Next.js UI | Required Tests | Priority | Gap? |
|---|---|---|---|---|---|---|---|---|
| Inbound valid payment file | KIR-style file-in; item-level staging | `ingress` | `inbound_files`,`inbound_file_items`, staged `payments` | `FileAccepted`(int), per-item `payment.received` | `POST /api/v1/files`; file detail screen ✗ | accept<2s, chunked job, counts | `[MVP]` | ◐ UI + read model |
| Inbound invalid file (signature/schema) | evidence-before-rejection | `ingress`,`signature`,`iso-adapter` | `raw_inbound_messages`(verdict),`iso_message_parse_errors` | `iso.message.rejected` | 4xx + reason; evidence screen ✗ | verify-before-parse, XXE/bomb fixtures | `[MVP]` | ◐ signature design |
| Duplicate file (same sha256) | idempotent replay of original result reference | `ingress` | `inbound_files` | none (replay) | same 202 + original `result_file_id` | dup-file replay test | `[MVP]` | — |
| Same MsgId, different content, same sender | DUPL reject (G5) | `ingress` | scoped `UNIQUE(tenant,sender,msg_id,type)` | `DuplicateFileRejected`(int) | DUPL response | constraint + semantics test | `[MVP]` | — |
| Partially accepted file | item-level accept, SkipPolicy | `ingress` | `inbound_file_items(status,reason_codes,raw_item_ref)` | `FileProcessed(ok,rejected)` | file detail with item statuses ✗ | 500-item mixed fixture, restart | `[MVP]` | ◐ UI |
| Generated result file (pain.002-style) | file-out mirror (zbiory wynikowe) | `egress`(collector)+`iso-adapter` | `outbound_artifacts`,`outbound_files` | `egress.delivery.requested` | `result_file_dashboard` ✅/screen ✗ | one-artifact-per-file, render fixture | `[MVP]` (files table `[P1]`) | ◐ |
| Outbound status file / batch advices | BATCHED emission per profile | `egress` | `outbound_files`,`egress_profile_snapshots` | same | egress dashboard | collector per (recipient,profile,date) | `[P1]` | — |
| Outbound return artifact (pacs.004) | case-driven return rendering | `egress`+`iso-adapter`+`case` | `outbound_artifact_lineage` | `case.return_payment_requested`→render | case-outbound view | case→egress boundary test | `[P1]` | — |
| Outbound case response (camt.029) | recall resolution out | same | same | `case.resolved`→render | `outbound_response_status_view` | render+deliver, case-never-sends | `[P1]` | — |
| Delivery failure + retry | transport-level retry, dead-letter | `egress` | `transport_attempts` | `egress.dead_lettered` | failed-delivery queue | backoff, max→DLQ, upstream untouched | `[MVP]` | ◐ topic drift |
| Delivery receipt in | counterparty ACK correlation | `egress` | `delivery_receipts`,`transport_receipts_in`(P1) | `egress.delivery.confirmed` | receipt view | receipt≠finality test | `[MVP]`/`[P1]` | — |
| File-level reconciliation | result-file-vs-state | `reconciliation` | recon tables + pointers | `reconciliation.exception.detected` | recon dashboards | report/result-file-vs-state type | `[P1]` | — |
| Item-level reconciliation | file items vs payments vs statuses | `reconciliation` | same | same | same | counts + per-item type (P1 catalogue) | `[P1]` | — |

**File-rail verdict:** inbound half `[MVP]`-complete; outbound half correctly `[MVP]` for result files and `[P1]` for the rest; the misses are UI (`file processing dashboard` screen + `inbound_file_dashboard` read model `[ADD]`) and the `signature` dependency.

---

## 8. Stack Impact Matrix

Per module: what it demands from each stack layer. (RD = reference-data; RM = read model; int = internal Modulith event.)

| Module / Feature | Spring Modulith | PostgreSQL | Kafka | REST | GraphQL | gRPC/Protobuf | Keycloak | React + Next.js | Tests | Observability |
|---|---|---|---|---|---|---|---|---|---|---|
| `ingress` | controllers, filters (order-pinned), `IngestionService` @Transactional, Spring Batch job | `ingress` schema: raw archive (partitioned), idempotency (covering idx), files/items | produces `payment.received` | `POST /payments`, `/iso/pain001`, `/files` | — | — | initiator + integration client; tenant/branch claims | submit is API-only; file detail screen | replay/conflict, hardening, batch restart | accept p95, ingress→validate segment, file throughput |
| `iso-adapter` | parse/version/map/correlate services; renderer port | `iso` schema (7 MVP tables) + profile catalogs FK | 4 `iso.*` topics; consumes `csm.response.received` | replay/manual-correlation admin cmds `[API-GAP: contract]` | 8 lineage RMs | — | ops read | lineage viewer, identifiers panel, orphan queue | 15-test ISO suite | parse/validation counters, orphan rate |
| `payment-lifecycle` | `TransitionTable` FSM, inbox-gated listener, @Version | `payment` schema: slim payments, coded history, partitioned events | produces `payment.validated`,`payment.status.reported`; consumes received/correlated/settlement.* | — | `paymentTimeline`,`payment(id)` | — | viewer roles | payment list/detail/timeline | transition matrix, late/dup policy | status-latency, illegal-transition meter |
| `routing` | pipeline services + 9 ports (in-process per ADR-N2) | `routing` schema (4 tables, immutable) + RD eligibility catalogs | produces `payment.routed`(+snapshot id),`route.failed`; consumes `payment.validated` | — | 8 routing RMs | **extraction exercise only (P2)** | ops read | routing decision viewer | routing test lab | decision latency, ROUTE_FAILED rate |
| `settlement` | resolver(basis,mode), 4 MVP strategies, CycleScheduler (system role) | `settlement` schema (9 tables) | produces attempted/completed/failed; granular = int | — | attempt/cycle/finality RMs | — | cycle-close role (FGAP v2) | settlement timeline, cycle dashboard | strategy/finality/G6 suites | cycle-close ±30s, insufficiency rate |
| `ledger` | `LedgerPort` impl; sole writer | `ledger` schema: deferred Σ=0 trigger, immutability grants, snapshots, reservations | consumed via settlement events only | — | `ledgerBalances` | — | read-only reporting role | ledger journal read-only view | invariant + grant suites | drift alert, reserve latency |
| `egress` | assembler, dispatcher (SKIP LOCKED), collector, ports | `egress` schema (9 tables) | delivery.requested/confirmed + dead_lettered/manual (add to §3.7) | manual resend/cancel admin cmds | 9 egress RMs | — | resend role | egress + failed-delivery + result-file dashboards | egress suite (5-status etc.) | pending/claimed/delivered/failed counters, retry depth |
| `reconciliation` | as_of service, collectors, classifier | `reconciliation` schema (8 MVP tables) | run.completed/failed, exception.detected | assign/resolve/FP admin cmds (P1) | 7 recon RMs | — | recon operator | run timeline, exception queue, drift dashboard | recon suite (no-mutation flagship) | run duration, exception-by-severity |
| `case` | typed FSM, `PaymentStateValidator`, request ports | `"case"` schema (6 MVP tables) | opened/resolved/closed/escalated/return_requested | ResolveRecall etc. admin cmds | 9 case RMs | — | recall_approver, case_supervisor | case dashboard, recall/return views | case suite (timing matrix flagship) | open-case ageing, escalations |
| `reference-data` | catalog services, loaders, "next cut-off after(t)" | `reference_data` schema (13+ tables; add validation/mapping/render profile DDL) | — | admin CRUD (role-gated) | catalog RMs | — | reference-data admin (FGAP v2) | admin/reference-data screens | catalog grant + versioning tests | catalog version gauge |
| `simulation` | scenario runner, seeded RNG, public-path client | `simulation` schema (4 tables) | `simulation.event.generated`→`csm.response.received` only | `POST /simulations/*` | `simulationRun`, trace views | — | sim operator | scenario launcher + replay view | determinism + no-write-grant | scenario duration, injected-anomaly counts |
| `security` (identity-access) | claim→GUC filter, `SystemSessionInitializer`, method security | `security` schema; RLS policies on tenant tables | — | — | — | — | realm, Organizations, FGAP v2, clients | login/session (BFF) | two-token negative, empty-GUC-zero-rows | authz-denied counter |
| `evidence-audit` | audit port (same-TX) | `evidence`,`audit` schemas (append-only, partitioned) | `audit.evidence.recorded` | — | evidence RMs | — | auditor read | evidence viewer | exactly-once, immutability | audit-lag=0 assertion |
| `signature` `[ADD]` | verify filter impl, `SignaturePort`, signer stub | `signature` schema: `message_signatures`, key registry `[DB-GAP fill]` | — | — | verdict on evidence RM | — | key-admin role | verdict surfaced on evidence screen | verify-before-parse, tamper fixtures | verify failures counter |
| `reporting` | cross-module projections (read-only) | `reporting` schema (P2 statements; `dashboard_snapshots`) | consumes events | — | composite RMs (`payment_detail_view`) | — | per-role field scoping | composite dashboards | projection freshness | staleness gauge |
| `risk`/`vop` (P2) | advisory listeners | `risk` schema | `risk.signal.detected` | — | `riskSignals` | — | risk analyst | risk panel | advisory-only (no-block) test | signal rate |

`gRPC/Protobuf` column verdict: with ADR-N2, gRPC has **zero MVP consumers** — it survives only as (a) the documented routing-extraction exercise `[P2]` `[EDU-VALUE]` and (b) an *option* for admin commands (REST is sufficient; adding gRPC for them is `[OVERENGINEERING]` — `[REJECT]` unless the extraction exercise happens).

---

## 9. Inconsistency and Gap Matrix

The concrete findings of the surgical pass. IDs `R-xx` are referenced throughout this document. "Evidence" cites the exact source locations.

| ID | Area | Inconsistency / Gap | Evidence from Docs | Risk | Required Correction | Priority |
|---|---|---|---|---|---|---|
| R-01 | Roadmap | **Iteration 0 absent** from the consolidated roadmap; foundation and first business slice mixed | Master §11 "Iteration 1 — Spine" includes `identity-access`+`evidence-audit`+UI+CI concerns; CPC-SP Iteration 1 includes compose/Keycloak/login; only the superseded HLD (Tier 0/Phase 0/TS-01..17) separates them | High — foundation work hidden inside a business iteration; no exit criteria for the skeleton | `[CHANGE]` §12: Iteration 0 defined; Iteration 1 re-cut to pure vertical slice | `[ROADMAP-GAP]` `[MVP-BLOCKER]` |
| R-02 | Priorities | **Two priority taxonomies never mapped**: Master's Iterations 1–5 (all "MVP") vs blueprint's `[MVP]/[P1]/[P2]` tags | e.g. `simulation`: Master Iteration 3 (core-for-a-lab) vs blueprint §3.6.2 `[P1] [EDU-ONLY]`; `reconciliation` Master Iter 4 vs blueprint `[MVP]` | Med — planning ambiguity, scope disputes | `[CHANGE]` one mapping (§11): `[MVP]` = Iterations 1–5 content; `[P1]` = post-MVP wave 1; `[P2]` = wave 2/labs. Re-tag simulation `[MVP]` (Iter 3) | `[HLD-GAP]` |
| R-03 | Modules | Module count drift: Master "20 modules", blueprint §3.6.2 lists 16; `live-analytics`, `search`, `vop`, `resilience-incident`, `conformance`, `signature` have no ownership row | Master §5 vs blueprint §3.6.2 | Low-Med — "module with no owner = violation by construction" (case blueprint's own rule) | `[CHANGE]` §10 map: fold `live-analytics`→`reporting`(+SSE), `vop`→`risk`; add `signature` row now; park `resilience-incident`/`conformance`/`search` with explicit "no schema until built" note | `[HLD-GAP]` |
| R-04 | Naming | `identity-access` (Master) vs `security` (blueprint schema/module) | Master §5.3 vs blueprint §3.6.2 | Low | `[CHANGE]` one name: module `identity-access`, schema `security` — state the pair explicitly | cosmetic |
| R-05 | Simulation | Priority conflict (see R-02) plus `[EDU-ONLY]` tag on a module the Master calls a core engine | blueprint §3.6.2 vs Master §5.1/§11 | Med — risk of deferring the determinism keystone | `[CHANGE]` simulation = `[MVP]`, Iteration 3, core engine; keep `[EDU-ONLY]` as a *nature* label, not a priority | `[HLD-GAP]` |
| R-06 | Reconciliation | Stale priority in ownership-integration doc (`[P1]`) vs v9 blueprint (`[MVP]` core) | ownership integration §3.6.3 vs blueprint §3.6.2 | Low — hierarchy resolves it | `[ADOPT]` blueprint; annotate the older doc as superseded on this row | note |
| R-07 | Routing | **Invocation style contradiction**: "the one out-of-process gRPC (Routing)" vs `routing` as in-process Kafka consumer of `payment.validated` | Master §4/§5.2/CPC-SP/synthesis vs blueprint §3.7 + §4.10 pipeline | Med-High — two different systems; affects G7, contracts, tests | `[CHANGE]` ADR-N2 (§18): in-process consumer for MVP; gRPC extraction = P2 educational exercise; rewrite G7 policy for the extracted variant | `[ARCH-RISK]` `[HLD-GAP]` |
| R-08 | Data/ISO | **Direct-JSON payments can't record identifiers as designed**: §2.2 promises "identifiers + lineage" for JSON, but `iso.payment_iso_identifiers.iso_message_id` is NOT NULL PK→`iso.iso_messages`, and `iso_messages.message_version_id` is NOT NULL — no version exists for JSON | blueprint §2.2 note vs §4.3c DDL | High — Iteration 1 hot path | `[CHANGE]` define a `JSON_DIRECT` pseudo message-type + one seeded `iso_message_versions` row; JSON path creates `iso_messages(parse_status='SKIPPED')` linked to the raw row; identifiers (`end_to_end_id` from payload) recorded against it | `[DB-GAP]` `[MVP-BLOCKER]` |
| R-09 | Data/RD | `validation_profiles`/`mapping_profiles`/`render_profiles` referenced by `iso_message_versions` codes but exist only as a §4.13 comment — no DDL | blueprint §4.13 comment; §4.3c FK-ish codes | Med | `[ADD]` three small versioned catalog tables in §4.13 (EPIC-OWN-4 S3 extension) | `[DB-GAP]` `[MVP]` |
| R-10 | Kafka | **Topic catalog drift**: `egress.dead_lettered`, `egress.manual_intervention_required` (egress patch §7) and `reconciliation.run.completed/run.failed/exception.detected` (recon patch §8) not in / misnamed vs blueprint §3.7 (`reconciliation.completed`, `reconciliation.mismatch.detected`) | egress patch §7, recon patch §8 vs blueprint §3.7 | Med — AsyncAPI source-of-truth is stale | `[CHANGE]` reconcile §3.7 v2 as the single catalog: add the two egress ops topics; pick recon names (`reconciliation.run.completed`, `reconciliation.run.failed`, `reconciliation.exception.detected`) and alias-note the old ones | `[EVENT-RISK]` `[MVP]` |
| R-11 | Outbox | **`outbox_events`/`inbox_events` have no owner**; "module-local outbox writer, shared relay" vs one schema-flat table | blueprint §3.2 vs §3.6.2 (absent) §4.4 | Med — blocks Flyway folder-per-module and grant tests | `[CHANGE]` decision: **one `outbox_events` table per owning schema** (writer = owner role) + one shared-kernel dispatcher role with SELECT/UPDATE across them (or a `messaging` schema with per-module partitions). Recommend per-schema tables — consistent with one-writer; dispatcher role granted explicitly. `inbox_events` per consuming module's schema | `[DB-OWNERSHIP]` `[MVP-BLOCKER]` |
| R-12 | Settlement | `settlement_attempts UNIQUE(payment_id)` vs "retries create history rows post-MVP" — mechanism undefined | blueprint §4.6 + settlement patch §5 | Low (post-MVP) | `[DEFER]` note: partial unique `WHERE state NOT IN (terminal)` when retries land | `[DB-GAP]` `[P1]` |
| R-13 | Signature | Module named in filters/ports; zero design (no schema, DDL, key model, signer) | Master §5.3 Gap ✗; blueprint §3.1/§3.11 ports | Med-High for Iter 2/5 | `[ADD]` EPIC-SIG (§12) + §3.6.2 row + `signature` schema DDL | `[SECURITY-GAP]` `[DB-GAP]` `[MVP-BLOCKER]` before Iter 2 |
| R-14 | Keycloak | Organizations/FGAP-v2/FAPI-2 adoption exists as a recommendation table only; no realm design, claim mapping, or seed | Master §10.4 / comprehensive §6 | Med | `[ADD]` EPIC-SEC-KC: realm design doc + seed export + claim mappers → GUCs; FGAP v2 permission model for the admin commands inventory (§13) | `[SECURITY-GAP]` `[MVP]` (claims) |
| R-15 | Frontend | No screen inventory / navigation / role→screen matrix / test-id convention / token ADR | absence across all docs; only read models + Playwright assertions exist | High for delivery | `[ADD]` §14 + EPIC-FE-0/1; freeze ADR-N3 (BFF) | `[UI-GAP]` `[MVP-BLOCKER]` for FE stories |
| R-16 | Frontend stack | Master lists Three.js in the stack row; everything else defers 3D; pnpm appears only in project context, not in any doc's stack table | Master §4 vs CPC-SP/HLD | Low | `[CHANGE]` stack row: pnpm added; Three.js moved to "experimental tracks" where it already lives in prose | cosmetic |
| R-17 | Observability | No consolidated logs/metrics/traces/dashboards/alerts/DLQ-monitoring inventory; segmented latency budget designed but unassigned | Master §10.6; per-story "observability" task type | Med | `[ADD]` §17 table; EPIC-OBS in Iteration 0/1 | `[HLD-GAP]` `[MVP]` |
| R-18 | Operator UX | Four separate operator queues (ISO orphans, recon exceptions, egress dead-letters, case queue) with no unified worklist read model | §6 matrix row "manual investigation" | Med `[EDU-VALUE]` | `[ADD]` `operator_worklist_view` (reporting projection) + screen S-01 (§14) | `[FLOW-GAP]` `[P1]` |
| R-19 | File UI | No `inbound_file_dashboard` read model despite full file rail | §6.6 read-model table (absent) | Low-Med | `[ADD]` read model + `fileProcessing(fileId)` query + screen S-06 | `[API-GAP]` `[UI-GAP]` `[MVP]` |
| R-20 | Admin API | Admin commands (resolve recall, resend, assign/resolve exception, manual correlation, replay, reference-data CRUD, cycle close) have no REST contract inventory | scattered "REST/gRPC admin command" mentions | Med | `[ADD]` §13.3 admin-command contract list → OpenAPI in Iteration owning each command | `[API-GAP]` |
| R-21 | G7 | Routing-down failure policy never written | comprehensive review G7; blueprint silent | Low after ADR-N2 (in-process) | `[DEFER]` to the extraction exercise; policy text = `@Retryable(backoff)` then deterministic queue-and-hold (never silent static reroute for instant) | `[SEAM-GAP]` `[P2]` |
| R-22 | Batch module | `batch` listed as a module; it is Spring Batch metadata used inside `ingress` | blueprint §3.6.2 | Low | `[CHANGE]` keep `batch` schema; owner = `ingress` (job launcher); drop the module fiction | cosmetic |
| R-23 | HLD residue | HLD content partially superseded (M4 VoP gRPC, M5 rulebook engine, M12 regulator workspace, M15 backup center, 6 gRPC services, 13-role matrix) but the doc is not annotated | HLD §2.4/§3/§6 vs CPC-SP/Master | Low-Med — a newcomer could implement dead designs | `[CHANGE]` add a supersession header to the HLD pointing to Master+blueprint; harvest only Tier 0/TS-xx/DoR-DoD/§16 doctrine (done in §12 here) | doc hygiene |
| R-24 | Live analytics / SSE | `live-analytics` module (Master, Designed ✅, Iter 4) has no blueprint counterpart; SSE-vs-GraphQL-subscription ADR (S14) open | Master §5.2 vs blueprint | Med | `[CHANGE]` fold into `reporting` + an SSE endpoint; freeze ADR-N4: SSE for MVP live feed | `[HLD-GAP]` `[P1]` |

**Summary:** 24 findings — 0 architectural, 5 `[MVP-BLOCKER]`-class (R-01, R-08, R-11, R-13, R-15), 3 cosmetic. All paper-fixable in days.

---

## 10. Module and Feature Map

The target map after applying §9 corrections. 17 buildable modules + 3 parked. (Screens reference §14 IDs.)

| Module | Responsibility | Key Features | Key Tables | Key Events | APIs / Read Models | React/Next.js Screens | Priority |
|---|---|---|---|---|---|---|---|
| `ingress` | intake, staging pipeline, idempotency, raw evidence, file rail | REST/XML/file channels; PG18 two-step idempotency; Spring Batch partial accept | `raw_inbound_messages`, `idempotency_keys`, `inbound_files/_items` | `payment.received` | submit/file APIs; file RM (R-19) | S-06 file dashboard | `[MVP]` Iter 1 |
| `iso-adapter` | parse, version, map, validate, identifiers, correlation, render | 9-step pacs.002 correlation; lineage; JSON_DIRECT pseudo-version (R-08) | `iso.*` (7 MVP) | 4 `iso.*` topics | 8 lineage RMs; replay/correlation admin cmds | S-05 lineage viewer, S-13 orphan queue | `[MVP]` Iter 1/5 |
| `payment-lifecycle` | canonical payment, FSM, status history, timeline | transition table as data; late/dup policy; finality flag via port | `payment.*` (3) | `payment.validated`, `payment.status.reported` | `payment(id)`, `paymentTimeline` | S-03 payment list, S-04 payment timeline | `[MVP]` Iter 1 |
| `routing` | route-resolution pipeline → immutable decision + explanation | per-profile reachability; explicit fallback; cutoff read | `routing.*` (4) + RD catalogs | `payment.routed`, `route.failed` | 8 routing RMs | S-07 routing viewer | `[MVP]` Iter 5 (decisions), config `[P1]` |
| `settlement` | strategy-by-(basis,mode), attempts, cycles, finality rule | 4 MVP strategies; cycle FSM (G6); profile snapshots | `settlement.*` (9) | attempted/completed/failed | attempt/cycle/finality RMs; cycle-close cmd | S-08 settlement timeline, S-09 liquidity | `[MVP]` Iter 2/4 |
| `ledger` | money correctness; sole journal writer; LedgerPort | Σ=0 deferred trigger; immutability; reservations; snapshots | `ledger.*` (6) | (via settlement) | `ledgerBalances` | S-10 ledger journal (read-only) | `[MVP]` Iter 2 |
| `egress` | profile-driven idempotent transport; render→sign→deliver | unified lifecycle; SKIP LOCKED dispatcher; result-file collector; receipts | `egress.*` (9) | delivery.requested/confirmed, dead_lettered, manual_intervention | 9 egress RMs; resend cmd | S-11 egress dashboard, S-12 delivery/retry | `[MVP]` Iter 2 (files/receipts-in `[P1]`) |
| `reconciliation` | as_of detection-and-escalation | 4 MVP recon types; taxonomy+severity; evidence pointers | `reconciliation.*` (8) | run.completed/failed, exception.detected | 7 recon RMs; exception admin cmds (P1) | S-14 recon runs, S-13 exception queue | `[MVP]` Iter 4 |
| `case` | R-message decision-and-coordination | timing matrix; return-as-new-payment; recall/return/reject | `"case".*` (6) | opened/resolved/closed/escalated/return_requested | 9 case RMs; ResolveRecall cmd | S-15 case dashboard | `[P1]` (MVP-of-P1 per case blueprint) |
| `reference-data` | static catalogs, calendars, profiles, codes | participants, status/reason catalogs, scheme/egress/recon/case profiles, eligibility, cutoff calendar, validation/mapping/render profiles (R-09) | `reference_data.*` (16+) | — | catalog RMs; admin CRUD | S-16 admin/reference-data | `[MVP]` (calendars `[P1]`) |
| `simulation` | deterministic traffic + failure injection via public paths | persisted seed; failure profiles; CSM responses | `simulation.*` (4) | `simulation.event.generated` → `csm.response.received` | `simulationRun`; launch API | S-17 simulation dashboard | `[MVP]` Iter 3 (R-05) |
| `identity-access` (`security` schema) | Keycloak integration, claim→GUC, service roles | Organizations claims; FGAP v2 admin plane; BFF session | `security.*` (4) | — | — | login/shell (S-00) | `[MVP]` Iter 0/1 |
| `evidence-audit` | append-only audit + evidence, same-TX rule | payload hashes; hash-chain `[P1]` | `evidence.*`, `audit.*` | `audit.evidence.recorded` | evidence RMs | S-18 evidence viewer (P1) | `[MVP]` Iter 1 |
| `signature` `[ADD]` | message/file signature verify+sign (Szafir-like, synthetic) | verify-on-raw-bytes; verdict recording; signer stub; key registry | `signature.message_signatures`, `signature.keys` | — | verdict on evidence RM | verdict badge on S-18/S-05 | `[MVP]` verify (Iter 2), sign Iter 5 |
| `reporting` (absorbs `live-analytics`) | cross-module projections, statements (P2), SSE live feed | composite `payment_detail_view`; operator worklist (R-18); SSE counters | `reporting.*` | consumes events | composite RMs; SSE endpoint | S-01 operator worklist, S-02 ops overview | `[MVP]` thin / `[P1]` rich |
| `risk` (absorbs `vop`) | advisory signals only | EWMA/MAD/z-score; VoP checks | `risk.*` (4) | `risk.signal.detected` | `riskSignals` | risk panel on S-02 | `[P2]` |
| `batch` (schema, owner=`ingress`) | Spring Batch metadata | job repository | Spring Batch tables | — | ops view | — | `[MVP]` |
| *parked:* `search` / `resilience-incident` / `conformance` / `direct-debit`+`mandate` | named homes, no schemas until built | FTS-first; DORA timers; onboarding gate; DD lifecycle | — | — | — | — | `[P2]`/deferred |

---

## 11. MVP / P1 / P2 Feature Selection

**Priority-taxonomy mapping (resolves R-02) `[DECISION]`:** `[MVP]` = everything inside Iterations 0–5; `[P1]` = first post-MVP wave (case full depth, egress files/receipts-in, calendars, recon P1 types, operator worklist, hash-chain, CGS/prefunded, FAPI-2/passkeys); `[P2]` = labs & breadth (risk/vop, search, statements, PG19 promotions, routing extraction, 3D). Not everything is selected — the table below is the *chosen* set; §19 lists what was consciously not chosen.

| Feature | Business Value | Technical Value | Testing Value | Frontend Value | Complexity | Priority |
|---|---|---|---|---|---|---|
| Platform skeleton (Iteration 0) | enables everything | Modulith+CI+contracts+infra | CI gates are the product | app shell + login | M | `[MVP]` Iter 0 |
| Payment spine + idempotency + timeline | first e2e proof | staging pipeline, outbox | replay/conflict, FSM matrix | list+timeline | M | `[MVP]` Iter 1 |
| ISO lineage core + JSON_DIRECT fix | evidence chain | schema-per-module in anger | 15-test ISO suite | lineage viewer | M-L | `[MVP]` Iter 1/5 |
| Ledger + GrossInstant + finality | money correctness | Σ=0 trigger, LedgerPort, grants | crown-jewel invariants | settlement timeline, ledger view | L | `[MVP]` Iter 2 |
| Egress rail + result files + signature-verify | file-out realism | SKIP LOCKED, idempotent artifacts | five-status suite | egress dashboard | L | `[MVP]` Iter 2 |
| Simulation + seed + failure profiles | the demo that sells | determinism keystone | kills flake at source | scenario dashboard | M | `[MVP]` Iter 3 |
| NetDeferred + cycles + netting | second settlement family | G6 lock, one-SQL netting | race + netting tests | cycle dashboard | L | `[MVP]` Iter 4 |
| Reconciliation core (4 types) | silent-drift detection | as_of snapshot, pointers | no-mutation flagship | exception queue | M-L | `[MVP]` Iter 4 |
| Routing pipeline + explanation | explainable decisions | immutable snapshot, catalogs | routing test lab | routing viewer | M | `[MVP]` Iter 5 |
| Keycloak Organizations claims + two-level RLS | KIR hierarchy realism | claim→GUC→policy | two-token negatives | role-scoped UI | M | `[MVP]` Iter 1/5 |
| Frontend baseline (BFF, shell, S-00..S-05, S-08, S-17) | the visible product | codegen types, SSE later | Playwright per screen | — | L | `[MVP]` spread over Iter 1–5 |
| Case module (recall/return/reject MVP-of-P1) | R-message lesson | timing matrix, request-only ports | return-after-finality flagship | case dashboard | L | `[P1]` |
| Business calendars + rollover | scheduling realism | RD calendars, next-cutoff-after(t) | temporal tests | cutoff view | M | `[P1]` |
| Operator worklist + exception ops | ops realism | composite projection | assignment/FP flows | S-01 | M | `[P1]` |
| Egress P1 (outbound files table, receipts-in, camt.029/pacs.004) | outbound completeness | lineage split | case-outbound suite | delivery views | M | `[P1]` |
| Recon P1 types (egress/ISO/case/report) | full-loop control | cross-module pointers | P1 type tests | mismatch dashboards | M | `[P1]` |
| FAPI-2 + FGAP-2 + passkeys + audit hash-chain | regulatory realism, cheap | Keycloak depth | security negatives | admin plane | S-M | `[P1]` |
| Risk/VoP advisory, search FTS, statements, PG19 promotions, routing gRPC extraction, 3D | breadth/labs | labelled tracks | comparison labs | panels | var | `[P2]` |

`[REJECT]` (re-confirmed, do not select even though they sound enterprise): regulator workspace; backup/restore *module*; BM25 as dependency; ML anything; BPMN/workflow engine; per-CSM engines of any kind; full ISO catalogue; topic-per-profile/type; six-simulator lab; gRPC for admin commands.

---

## 12. Concrete Backlog: Roadmap, Epics, Stories, Tasks

### 12.0 Roadmap — Iteration 0 and Iteration 1 strictly separated `[ROADMAP-GAP fix]`

The blueprint's ~50 existing epics (EPIC-IN/CORE/MONEY/OUT/XCUT/OWN/ISO/CASE/ROUTE/SETTLE/EGRESS/RECON) remain the domain backlog and are **not duplicated here** — this section adds the missing structure (Iteration 0, frontend, signature, Keycloak, observability) and the roadmap that sequences all of it.

# Iteration 0 — Platform Skeleton / Foundation

**Goal:** the technical skeleton exists, boots with one command, and every enforcement gate is live in CI **before** the first domain table is written (per the ownership integration's own §10 rule 3). Ported from HLD Tier 0/Phase 0 + TS-01..TS-17, updated to the current stack; no business flow is implemented here.

Scope (all `[MVP-BLOCKER]` for Iteration 1):
- Monorepo (`/backend`, `/frontend`, `/contracts`, `/infra`, `/docs/adr`, `/qa`) — monorepo decision reaffirmed from HLD Phase 0.
- Backend skeleton: Spring Boot 4.1 / Framework 7 / JDK 25 / **Maven**; Spring Modulith 2.1 module stubs for the 17-module map (§10) with `allowedDependencies` declared empty-but-real; `ApplicationModules.verify()` as a blocking CI test.
- Frontend skeleton: Next.js / React / TypeScript 6 / **pnpm**; app shell + Keycloak BFF login (ADR-N3) + health page (S-00).
- Local stack (Podman compose): PostgreSQL 18, Kafka (KRaft, single broker), Keycloak 26.6.x (realm seed import: `sepa-nexus`, one Organization, core roles, `sepa-web`/`sepa-api`/`sepa-integration` clients, `tenant_id`/`branch_id` claim mappers), OTel collector + Grafana; optional `postgres-pg19` lab profile (off).
- Flyway baseline: folder-per-module convention; schemas `security`, `evidence`, `audit`, per-module `outbox_events`/`inbox_events` per R-11 decision; claim→GUC filter + selective-RLS scaffolding; `ClockPort` bean (G8) from day zero.
- Contract folders + codegen + break gates: OpenAPI (oasdiff), AsyncAPI (§3.7 v2 as source, R-10 applied), GraphQL SDL snapshot, `.proto` folder (empty but gated, for the P2 extraction exercise).
- Testcontainers harness (PG+Kafka+Keycloak, worker-isolated); Playwright framework (per-role storageState, `waitForTimeout` lint ban); deterministic test-data generator seed utilities.
- CI pipeline: backend build → unit → Modulith verify → ArchUnit pack (foreign-repo ban, `Instant.now()` ban, GraphQL-read-only) → Testcontainers smoke (RLS two-token negative; empty-GUC-zero-rows; grant-matrix skeleton) → frontend build/lint → Playwright smoke (login + health).
- Observability wiring: structured JSON logs with `traceId`/`correlationId`; trace continuity smoke; health endpoints; one Grafana board.

**Acceptance criteria:** `mvn verify` green locally · `pnpm install && pnpm build` green · one command boots the stack; PG/Kafka/Keycloak/backend healthy · frontend authenticates via BFF and renders backend health + a GraphQL health/read query · Testcontainers PG+Kafka test green · Modulith boundary test green · Playwright smoke opens the app logged-in · CI runs the full pipeline and goes red on an intentional contract break.

### EPIC-FOUND-0: Platform Skeleton
#### Goal
Everything above, as the only epic of Iteration 0.
#### Stories

| Story ID | Story | Acceptance Criteria | Backend Tasks | Database Tasks | Eventing Tasks | API/GraphQL Tasks | React/Next.js Frontend Tasks | Security Tasks | Test Tasks | Priority |
|---|---|---|---|---|---|---|---|---|---|---|
| F0-S1 | Repo + Modulith + Maven skeleton | build green; module graph generated; verify() blocking | module stubs, allowedDependencies, parent pom | — | — | contract folders + gates | pnpm workspace, shell | — | verify()+ArchUnit pack in CI | Must |
| F0-S2 | Local stack one-command boot | all healthy; realm imported; lab profile opt-in | compose profiles | PG18 image + init | Kafka topic provisioning script (from §3.7 v2) | health endpoints | — | Keycloak realm seed export | compose smoke script | Must |
| F0-S3 | Flyway + ownership scaffolding | folder-per-module; grants matrix skeleton; RLS scaffold | claim→GUC filter, SystemSessionInitializer, ClockPort | baseline migrations (security/evidence/audit/outbox-inbox per R-11) | — | — | — | two-token RLS negative | grant-matrix + empty-GUC tests | Must |
| F0-S4 | Test frameworks | parallel-safe TC; Playwright login smoke; data generator seed | TC base classes | worker-isolated schemas | embedded-Kafka/TC Kafka | — | storageState per role | — | meta-tests (no waitForTimeout lint) | Must |
| F0-S5 | CI + observability baseline | full pipeline; red-on-break proof; one trace visible | OTel SDK wiring | — | — | — | FE build/lint stage | — | trace-continuity smoke | Must |

# Iteration 1 — Payment Spine Vertical Slice

**Goal:** first business flow end-to-end, starting **only after Iteration 0's acceptance criteria are green**. Scope = `ingress → idempotency → raw archive → (JSON_DIRECT identifiers per R-08) → payment lifecycle FSM → status history → outbox → Kafka → read model → GraphQL timeline → React list + timeline → Keycloak operator role → 1 Playwright happy path → audit + correlation id visible`.

Maps to existing epics: EPIC-IN-1 (S1–S3), EPIC-CORE-1, EPIC-CORE-2/EPIC-ISO-1 (subset: `iso_messages` SKIPPED + identifiers + lineage for JSON), EPIC-OWN-1/3 (gates already live from Iter 0), EPIC-XCUT-1, plus new EPIC-FE-1 (below) and EPIC-OBS-1 (segmented ingress metrics).

**Acceptance criteria:** one synthetic payment via REST returns `paymentId` · row in PG (`payment` schema) with coded history · lifecycle advances through RECEIVED→VALIDATED (minimal states) · event in outbox → Kafka, inbox-deduped · GraphQL timeline is read-only · React shows payment list + timeline (S-03/S-04) · idempotency replay returns same id, conflict returns 409 · one audit row per critical action, same TX · `correlationId` visible in logs and UI footer · Playwright happy path green in CI.

### Remaining roadmap

| Phase / Iteration | Scope | Why Now | Exit Criteria |
|---|---|---|---|
| **Iteration 0 — Platform Skeleton** | EPIC-FOUND-0 | nothing safe to build before the gates exist | acceptance list above |
| **Iteration 1 — Payment Spine** | spine slice above | proves the loop every module plugs into | acceptance list above |
| **Iteration 2 — Ledger + Instant + Egress status-out** | EPIC-MONEY-1/2, EPIC-SETTLE-1/2/5(core), EPIC-OWN-5/6, EPIC-EGRESS-1/2/3, EPIC-SIG (verify) | money correctness before variety; status-out needs signing-verify port | Σ=0 at COMMIT; instant settle+finality; RJCT-on-insufficient; pacs.002 delivered idempotently; five-status tests green |
| **Iteration 3 — Simulation** | simulation module `[MVP]` (R-05), failure profiles, EPIC-OWN-9 | determinism keystone; protects the flagship demo date | same seed → identical run incl. Playwright; delayed/dup/out-of-order/insufficient reproducible one-click |
| **Iteration 4 — Deferred + Reconciliation** | EPIC-MONEY-3, EPIC-SETTLE-3/4/6(core), EPIC-RECON-1..5, cycle dashboard | second family + control loop | cycle race test; netting=one SQL; injected mismatch → severity, no auto-fix |
| **Iteration 5 — ISO + Routing + Security depth** | EPIC-ISO-2/3 full, EPIC-ROUTE-1..6, EPIC-OWN-2/4, EPIC-SIG (sign), EPIC-SEC-KC (Organizations claims done Iter 1; FGAP-2 admin plane here) | ISO realism + explainable routing + signed outbound | signed pain.001 in → validated → routed (in-process) → settled → signed pacs.002 out; route explanation immutable |
| **MVP done** | = Iterations 0–5 | — | all `[MVP]` epics green; §16 test inventory green |
| **P1 wave** | EPIC-CASE-1..8, EPIC-ISO-4/5, EPIC-EGRESS-4(rest)/5(files)/6, EPIC-RECON-6(P1)/7, calendars, operator worklist (R-18), FAPI-2/passkeys, hash-chain | after finality/ledger are proven | flagship return-after-finality test; unified worklist live |
| **P2 wave** | risk/vop, search FTS, statements, PG19 promotions (on GA), routing gRPC extraction + G7 policy, SQL/PGQ lab, 3D | labs on evidence, never on calendar | each behind flag/profile |
| **Deferred** | direct-debit + mandates, resilience-incident (DORA), conformance | named homes, no schemas | — |
| **Rejected** | §19 list | — | — |

### 12.1 New epics added by this review (gap-closers; the ~50 blueprint epics stand unchanged)

## EPIC-SIG: Signature Module (R-13)
### Goal
Turn the named-everywhere `signature` module into a design: verify (Iter 2) and sign (Iter 5) on raw bytes, verdict recorded as evidence, synthetic key registry, HSM-like signer stub. `[SYNTHETIC]` — Szafir-like, no production crypto claims.
### Stories

| Story ID | Story | Acceptance Criteria | Backend Tasks | Database Tasks | Eventing Tasks | API/GraphQL Tasks | React/Next.js Frontend Tasks | Security Tasks | Test Tasks | Priority |
|---|---|---|---|---|---|---|---|---|---|---|
| SIG-S1 | Boundary + schema | §3.6.2 row exists; owns `signature.*` only | responsibilities doc; `SignaturePort` (verify/sign) | `signature.message_signatures(raw_message_id, verdict, key_id, algo, at)`, `signature.keys` | — | verdict joined into `messageEvidence` | verdict badge (S-18/S-05) | key-admin role | grant tests | Must `[MVP]` |
| SIG-S2 | Verify-before-parse (bank/file channels) | filter order pinned; FAILED verdict → reject pre-parse; raw archived either way | filter impl on cached raw bytes | verdict column linkage | — | 4xx taxonomy | — | channel policy: bank/file = required | tamper + wrong-key + missing-sig fixtures | Must `[MVP]` Iter 2 |
| SIG-S3 | Signing for egress | `SIGNED` state real; detached signature stored | signer stub via port | signature bytes on artifacts | — | — | — | signing key rotation note | sign→verify round-trip | Must `[MVP]` Iter 5 |

## EPIC-SEC-KC: Keycloak Adoption (R-14)
Stories: KC-S1 realm design doc + seed (Organizations per participant; `organization`/`branch_id` claims → GUCs; two-level RLS test with branch token) `[MVP Iter 1]` · KC-S2 FGAP v2 permission model bound to the admin-command inventory (§13.3) `[MVP Iter 5]` · KC-S3 FAPI-2 profile for `sepa-integration` + passkeys + SAML-off + patch-pin hardening `[P1]` · KC-S4 separate Keycloak DB + realm export in backup script `[MVP Iter 0]`.

## EPIC-FE-0/1: Frontend Foundation & Screens (R-15) — stories in §14.
## EPIC-OBS-1: Observability consolidation (R-17) — inventory in §17; stories: segmented latency metrics (Iter 1–2), Kafka lag + DLQ board (Iter 3), alert rules (Iter 4).
## EPIC-DOC-1: Documentation hygiene — apply R-04/R-06/R-10/R-16/R-22/R-23 supersession annotations and the §3.7 v2 topic table to the blueprint (one `str_replace` pass, ≤1 day).

**Task types per story (uniform, unchanged from blueprint §8):** DDL+migration · component code · unit tests · Testcontainers integration · contract (OpenAPI/AsyncAPI) · Playwright (where user-visible) · observability (metrics+trace) · docs (module canvas row).

---

## 13. Database / Eventing / API / GraphQL Impact

**13.1 Database (delta only — the blueprint DDL stands).** New/changed objects this plan requires: `signature` schema (2 tables, R-13) · 3 profile-catalog tables in `reference_data` (R-09) · `iso_message_versions` seed row for `JSON_DIRECT` + documented SKIPPED-row rule (R-08) · per-schema `outbox_events`/`inbox_events` + dispatcher-role grants (R-11) · `reporting.operator_worklist` projection (R-18) · deferred note on `settlement_attempts` partial unique (R-12). Everything else: `[ADOPT]` as written, created per module per iteration.

**13.2 Eventing.** Publish **§3.7 v2** as the single AsyncAPI source: current table + `egress.dead_lettered` + `egress.manual_intervention_required`; recon topics renamed `reconciliation.run.completed` / `reconciliation.run.failed` / `reconciliation.exception.detected` (R-10). Rules unchanged (producer-owns-topic, per-key ordering, inbox everywhere, DLQ per consumer group, simulator only via `csm.response.received`).

**13.3 REST admin-command inventory (R-20) — each becomes an OpenAPI contract in the iteration owning it; all role-gated (FGAP v2), audited, never GraphQL:**

| Command | Module | Role | Iteration |
|---|---|---|---|
| Submit payment / file; launch simulation | ingress / simulation | initiator / sim operator | 1 / 3 |
| Close cycle (manual override) | settlement | cycle-close | 4 |
| Assign / comment / resolve / false-positive / suppress exception | reconciliation | recon operator | P1 |
| Resend / cancel / force-close artifact | egress | egress operator | MVP(resend cmd)/P1 |
| Resolve recall (accept/reject), escalate, close case | case | recall_approver / case_supervisor | P1 |
| Manual correlation, reprocess, replay message | iso-adapter | ops senior | P1 |
| Reference-data CRUD (participants, codes, calendars, profiles) | reference-data | reference-data admin | MVP thin / P1 |

**13.4 GraphQL.** The 30+ read models stand `[ADOPT]`; add `inboundFile(fileId)`/`inboundFiles` (R-19) and `operatorWorklist` (R-18). Read-only rule enforced by the existing ArchUnit gate; SDL snapshot gate from Iteration 0.

---

## 14. React + Next.js Frontend / Dashboard Impact

**Foundation decisions to freeze (EPIC-FE-0):** ADR-N3 **BFF** (Next.js server session, HttpOnly cookie, tokens never in browser; DPoP-SPA documented alternative) · ADR-N4 **SSE** for live feeds (GraphQL subscriptions deferred) · type generation from OpenAPI + GraphQL codegen in CI · test-id convention `data-testid="{screen}.{component}.{element}"` · deep-link scheme `/payments/{id}`, `/files/{id}`, `/cases/{id}`, `/cycles/{id}`, `/reconciliation/runs/{id}`, `/exceptions/{id}`, `/artifacts/{id}` — every entity id rendered anywhere is a link · role-based nav from a single `role→screen` map (below) · loading/error/empty states mandatory per screen (HLD DoD carried forward).

| Screen / Dashboard | Business Purpose | Data Source | REST / GraphQL | Key Components | Roles / Permissions | Playwright Tests | Priority |
|---|---|---|---|---|---|---|---|
| S-00 Shell + login + health | authenticated entry | Keycloak BFF; health query | GraphQL health | nav, role menu, correlation-id footer | all | login smoke, role-gated nav | `[MVP]` Iter 0 |
| S-01 Operator worklist | one queue over orphans/exceptions/dead-letters/cases | `operatorWorklist` (R-18) | GraphQL + admin cmds | unified table, severity chips, claim/assign | operator, supervisor | claim→resolve flow | `[P1]` |
| S-02 Ops overview | volumes, statuses, SLA p95, lag | reporting projections + SSE | GraphQL + SSE | tiles, live counters | ops read | live counter updates (event-driven) | `[MVP]` Iter 2 thin |
| S-03 Payment list | find payments | `payments(filter)` | GraphQL | filterable table, deep links | viewer+ | filter + pagination | `[MVP]` Iter 1 |
| S-04 Payment timeline / detail | one payment's whole story | `payment(id)`, `paymentTimeline`, identifiers panel, route/settlement/egress panels | GraphQL | timeline, ISO-ids panel, status chips | viewer+ (branch-scoped) | ordered timeline; ids from `iso.payment_iso_identifiers` (existing assertion) | `[MVP]` Iter 1, panels grow per iteration |
| S-05 ISO lineage viewer | raw→parsed→validated→correlated chain | `messageLineage`, `messageEvidence`, `pacs002Correlation` | GraphQL | lineage graph/list, verdict badges | ops read | lineage chain renders (existing assertion) | `[MVP]` Iter 5 |
| S-06 File processing dashboard | files + item statuses + result file link | `inboundFiles`, `result_file_dashboard` | GraphQL | file table, item drill-down, counts | file ops | partial-accept counts | `[MVP]` Iter 2 |
| S-07 Routing decision viewer | why this route | `routeExplanation`, `routeCandidates`, `reachabilityMatrix` | GraphQL | explanation panel, candidate table | ops read | explanation matches decision (existing) | `[MVP]` Iter 5 |
| S-08 Settlement attempt timeline | attempt lifecycle + finality evidence | attempt/finality RMs | GraphQL | attempt FSM view, finality badge | ops read | accepted≠final rendering | `[MVP]` Iter 2 |
| S-09 Liquidity dashboard | balances, reservations, checks | `ledgerBalances`, `liquidity_check_view` | GraphQL | account table, shortage flags | treasury read | shortage scenario | `[MVP]` Iter 2 |
| S-10 Ledger journal (read-only) | provable money | journal/snapshot RMs | GraphQL | entry/line table, Σ badge | auditor/ops read | immutability messaging | `[MVP]` Iter 2 |
| S-11 Egress / outbound artifacts | artifact lifecycle | `egressDeliveries`, artifact views | GraphQL + resend cmd | lifecycle chips REQUESTED→CLOSED | egress operator | lifecycle transitions (existing assertion) | `[MVP]` Iter 2 |
| S-12 Delivery attempts / retry | failures, backoff, dead-letter, manual resend | `failed_delivery_queue`, `manual_resend_dashboard` | GraphQL + cmds | retry table, DLQ panel, resend button (role-gated) | egress operator | resend via admin cmd not GraphQL | `[MVP]`/P1 |
| S-13 Exception / orphan queues | recon exceptions + ISO orphans | `exception_queue`, `orphanedIsoMessages` | GraphQL + cmds (P1) | severity queue, evidence link | recon operator | severity + run shown (existing) | `[MVP]` Iter 4/5 |
| S-14 Reconciliation runs | run timeline, drift | run/drift RMs | GraphQL | run FSM, as_of display, results | recon operator | deterministic rerun display | `[MVP]` Iter 4 |
| S-15 Case / R-message dashboard | recall/return chains | case RMs | GraphQL + ResolveRecall cmd | R-message chain, return link, timing-matrix hints | recall_approver, supervisor | resolve via admin cmd (existing) | `[P1]` |
| S-16 Admin / reference-data | catalogs, profiles, calendars | catalog RMs + CRUD | REST CRUD | versioned catalog editors | reference-data admin (FGAP v2) | version-window edit | `[MVP]` thin / P1 |
| S-17 Simulation dashboard | launch, seed, replay | `simulationRun`, trace views | REST launch + GraphQL | scenario launcher, seed display, replay | sim operator | same-seed identical E2E (existing) | `[MVP]` Iter 3 |
| S-18 Evidence / audit viewer | raw vs parsed, verdicts, audit trail | `messageEvidence`, audit RMs | GraphQL | hash/verdict panels | auditor | evidence chain | `[P1]` |
| Observability board | lag, DLQ, latency budget | Grafana (outside app) | — | — | ops | — | `[MVP]` (Grafana, not React) |

**EPIC-FE-1 stories (per screen, uniform):** SDL/codegen types → components with test-ids → role gating from the map → loading/error/empty → deep links → Playwright (one happy + one negative per screen). Priority follows the screen table.

---

## 15. Security / Keycloak Impact

**Consolidated role → capability matrix (supersedes the HLD 13-role matrix for the current module set):**

| Role | Submit/simulate | View (branch-scoped) | Close cycle | Resolve recall / case | Recon ops (assign/resolve/FP) | Egress resend | Manual correlation/replay | RD admin | Audit read | Key admin |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| payment_initiator / integration client | ✅ | own | | | | | | | | |
| operator (ops read) | | ✅ | | | | | | | | |
| cycle_operator | | ✅ | ✅ | | | | | | | |
| recall_approver | | ✅ | | ✅ resolve | | | | | | |
| case_supervisor | | ✅ | | ✅ escalate/close | | | | | | |
| recon_operator | | ✅ | | | ✅ | | | | | |
| egress_operator | | ✅ | | | | ✅ | | | | |
| ops_senior | | ✅ | | | | | ✅ | | | |
| reference_data_admin | | | | | | | | ✅ | | |
| auditor | | cross-tenant read | | | | | | | ✅ | |
| key_admin (`signature`) | | | | | | | | | | ✅ |
| sim_operator | ✅ sim | ✅ | | | | | | | | |
| system_* service roles | (per §3.5/§4.7: narrow policies on RLS tables; grants on queue/ledger) | | | | | | | | | |

Mechanics `[ADOPT + ADD]`: one realm + **Organizations** per participant; `organization`+`branch_id` claims → `app.tenant_id`/`app.branch_id` GUCs → selective two-level RLS (tenant/evidence tables only) · **FGAP v2** binds the matrix above to the §13.3 admin commands · **FAPI-2** profile on `sepa-integration` with `signature` (Iter 5/P1) · **BFF** per ADR-N3 · hardening: SAML off, exact redirect URIs, 26.6.x pinned, short TTL + rotating refresh, separate Keycloak DB (Iter 0) · every admin command audited same-TX with actor/role.

---

## 16. Testing Strategy

The per-module suites in the blueprint and patches stand `[ADOPT]`. This table consolidates them and adds the two missing layers (Iteration 0 gates; frontend).

| Area | Business Tests | Technical Tests | Integration Tests (Testcontainers) | E2E / Playwright | Contract / DB / Arch Tests |
|---|---|---|---|---|---|
| Iteration 0 gates `[ADD]` | — | CI red-on-break proof | RLS two-token; empty-GUC-zero-rows; grant-matrix skeleton | login+health smoke | Modulith verify; ArchUnit pack; oasdiff/AsyncAPI/SDL gates |
| Ingress / files | partial-accept counts; dup-file semantics | idempotency replay/conflict/concurrency | Batch restart; XXE/bomb negatives; verify-before-parse | submit→timeline happy path | OpenAPI; unique-scope constraints |
| ISO lineage / correlation | ISO-vs-business reject split | 9-step correlation incl. JSON_DIRECT (R-08) | orphan→DLQ; ambiguous-no-mutation; dup no-op | lineage viewer chain | 4 ISO topic contracts; enum CHECKs |
| Lifecycle | late/weaker status policy | transition matrix; optimistic lock | inbox dedupe + ordering | timeline order | status-history integrity |
| Routing | outcome taxonomy per fixture | pipeline determinism (seed) | no-implicit-fallback; cutoff-read-not-recompute | routing viewer | snapshot immutability; `payment.routed` carries snapshot id |
| Settlement / ledger | finality rules; insufficiency by basis+mode | resolver-on-(basis,mode); profile-name-switch banned | Σ=0 at COMMIT; no-double-reserve; G6 race; mutation-denied | settle under SLA; RJCT path | settlement-role-no-ledger-grant; one-live-attempt |
| Egress | five-status separation; result file | idempotent artifact; retry/backoff/dead-letter | double-dispatcher SKIP LOCKED; failed-delivery-untouched-upstream | egress lifecycle; resend cmd | idempotency unique; delivered≠final |
| Reconciliation | severity classification; no-auto-fix | deterministic as_of (rerun-identical) | no-source-write grants; injected drift/missing-posting | exception queue | pointer PK; immutable results |
| Case (P1) | timing matrix legality | duplicate-suppress / conflict-escalate | **flagship**: return-after-finality → new payment, original journal byte-identical; no reverse path | case resolve via admin cmd | case-role grants |
| Simulation | scenario catalog | same-seed determinism | no-domain-write grant | one-click anomaly reproduction | topic entry only via `csm.response.received` |
| Security | role matrix positive/negative | FGAP-2 permission checks | branch-token two-level RLS | role-gated UI per screen | claims→GUC mapping |
| Signature `[ADD]` | channel policy (bank/file required) | tamper/wrong-key/missing fixtures | verdict recorded pre-parse | verdict badge | filter-order test |
| Frontend `[ADD]` | per-screen happy+negative | codegen type drift gate | — | deep links; loading/error/empty; no waitForTimeout | SDL snapshot |
| Observability | — | segmented latency budget emitted | trace continuity ingress→settled | correlation id in UI | audit exactly-once, same-TX |

**Order rule (carried from synthesis §15):** ledger invariants + idempotency + FSM transitions are written before the features they cover.

---

## 17. Observability and Operations Review

Correlation model `[ADOPT]`: `traceId` (OTel) · `paymentTraceId` · `correlationId`; entity ids (`payment/file/attempt/journal/case/run/artifact`) on every log line and event where present.

| Area | Logs | Metrics | Traces | Dashboard | Alert | Gap? |
|---|---|---|---|---|---|---|
| Ingress | structured, ids, verdicts | accept p95 (<300ms), rejects by reason, file throughput | span per stage | S-02 tile | reject-rate spike | — after EPIC-OBS-1 |
| Lifecycle | transition log | illegal-transition meter, status latency | consumer spans | timeline | illegal>0 | — |
| Segmented budget | — | ingress→validate→route→settle→pacs002→e2e histograms | one continuous trace | SLA board | e2e p95 breach | `[ADD]` Iter 2 (was designed, unowned R-17) |
| Kafka | consumer logs | **lag per group (first-class)**, retry count, DLQ depth | header propagation | lag board | DLQ>0 on `csm.response`, recon topics | `[ADD]` Iter 3 |
| Ledger | entry log | drift gauge (snapshot vs derived), reserve latency | — | S-10 | drift≠0 = CRITICAL | — |
| Settlement | cycle log | cycle-close ±30s, insufficiency rate | cycle span | S-08/cycle | close overdue | — |
| Egress | attempt log | pending/claimed/delivered/failed, retry depth, receipt lag | dispatch span | S-11/S-12 | dead-letter>0; status-out >5s | topic drift R-10 |
| Reconciliation | run log | run duration, exceptions by severity, ageing vs SLA | run span | S-14/S-13 | CRITICAL exception immediate; run.failed | — |
| Case (P1) | decision log | open-case ageing, escalations | — | S-15 | expired cases | — |
| Simulation | scenario log | injected-anomaly counts, run duration | scenario trace | S-17 | — | — |
| Security/audit | authz-denied log | denied count | — | — | audit-write failure = page | hash-chain `[P1]` |
| Dead-letter handling | DLQ consumer logs | per-topic DLQ depth | — | lag board | any DLQ growth | operator worklist R-18 (P1) |
| Operator queues | — | queue depths (orphans/exceptions/dead-letters/cases) | — | S-01 (P1) | depth thresholds | `[P1]` |

---

## 18. ADR List

Already frozen across the patches (reaffirm, file under `/docs/adr/`): one Modulith deployable · one-writer-per-schema · CPC-SP topology · profiles-not-per-CSM-engines (settlement/egress/routing) · strategy-by-(basis,mode) never CSM name · LedgerPort-only money path · ledger owns money correctness; SQL-enforced Σ=0; reversal=pre-finality booking-error only · finality-is-an-explicit-profile-rule; accepted/posted/delivered≠final · egress-owns-transport-only; failed-delivery-never-reverses; idempotent artifact creation · reconciliation read-only detect-and-escalate; deterministic as_of; pointers-not-copies; taxonomy in reference-data · case decision-and-coordination only; timing matrix; return-after-finality=new payment · GraphQL read-only · simulation-through-normal-paths · PG18 baseline / PG19-lab (explicitly resolves any "PG19 as main DB" reading of the original spec) · Maven · selective RLS · raw archive never deduplicates · FSM-not-workflow.

**New ADRs this review requires:**

| ADR | Decision | Reason | Alternatives Rejected | Impact |
|---|---|---|---|---|
| ADR-N1 | **Iteration 0 (Platform Skeleton) precedes Iteration 1**; foundation never mixed into business iterations | R-01; gates must exist before the first domain table | "Iteration 1 includes setup" (Master §11 as written) | roadmap §12; CI gates live from day 0 |
| ADR-N2 | **Routing is an in-process Modulith module consuming `payment.validated`** in MVP; gRPC extraction is a P2 educational exercise with its own G7 degraded-mode policy | R-07 contradiction; simpler, matches pipeline design; "≤1 gRPC" is a ceiling | out-of-process gRPC in MVP (network seam, G7 urgency, ceremony) | zero MVP gRPC; contracts folder stays gated for P2 |
| ADR-N3 | **BFF token model** (Next.js server session, HttpOnly cookie); DPoP-SPA documented alternative | S15 open since comprehensive review; unblocks all FE stories | SPA-with-token default | §14 foundation; Keycloak client config |
| ADR-N4 | **SSE for live feeds** in MVP; GraphQL subscriptions only if bidirectionality appears | S14 open; simpler, cache-friendly | WebSocket/subscriptions now | S-02 live counters; `reporting` SSE endpoint |
| ADR-N5 | **Per-schema outbox/inbox tables; shared-kernel dispatcher role with explicit grants** | R-11 ownership hole | one global unowned table; per-module relays | Flyway layout; grant tests; relay config |
| ADR-N6 | **One priority taxonomy**: `[MVP]`=Iterations 0–5, `[P1]`=wave 1, `[P2]`=wave 2/labs; simulation is `[MVP]` (Iteration 3) | R-02/R-05 | dual taxonomies | re-tag pass in blueprint (EPIC-DOC-1) |
| ADR-N7 | **`JSON_DIRECT` pseudo message-version**; JSON submissions create `iso_messages(parse_status='SKIPPED')` and record identifiers against it | R-08; keeps lineage uniform without weakening NOT NULLs | nullable `iso_message_id` (breaks PK); no identifiers for JSON (breaks correlation promise) | §4.3c seed + ingress path |
| ADR-N8 | **§3.7 v2 topic table is the sole AsyncAPI source**; patch documents reference it | R-10 drift | catalog fragments per patch | EPIC-DOC-1; topic provisioning script |

---

## 19. Rejected / Deferred Ideas

**Rejected `[REJECT]` (re-confirmed; do not build even though they sound professional):** per-CSM engines of any kind (settlement/egress/routing) · finality on delivery / on CSM-accepted · business-return-as-reversal · reconciliation that repairs; autonomous remediation; BPMN/workflow engine · full event sourcing; vector clocks; exactly-once-everywhere · ML fraud/risk · BM25 as a dependency; semantic search · full ISO catalogue / CBPR+ / graph DB for lineage; full party/attribute normalisation now · six in-JVM gRPC services; gRPC for admin commands; topic-per-profile/type/status · regulator workspace; backup/restore as a domain module (ops drill script only) · queueing formulas as runtime · six-simulator source lab · Three.js before an asserting SSE feed · implicit instant→batch fallback · RLS on queues/ledger · creating all schemas upfront.

**Deferred `[DEFER]` (named homes, enum-ready, no schemas until built):** direct-debit + mandates + full R-transaction reason matrix · DORA `resilience-incident` · `conformance` onboarding gate · VoP as regulatory module (advisory `risk.vop_checks` P2 first) · camt.052/053/054 statements (P2) · PG19 promotions on GA only (`ON CONFLICT DO SELECT`, `FOR PORTION OF`, MERGE/SPLIT, REPACK, SQL/PGQ lab) · routing gRPC extraction + G7 policy (P2 exercise) · saga at cross-module cycle-close (only when compensation is real) · audit hash-chaining (P1) · settlement retries / `settlement_queue_items` / CGS / prefunded (P1) · multi-CSM fallback, addressable/serviced modes (P1) · event upcasting machinery (schemaVersion field only) · OSS ledger extraction (evidence, not calendar).

---

## 20. Final Recommendation

**Proceed — in this exact order:**

1. **Paper pass (≤1 week, no code):** apply the §9 corrections to the blueprint (EPIC-DOC-1), write EPIC-SIG SIG-S1 and the Keycloak realm design (KC-S1), write §14's frontend foundation page, and commit the eight new ADRs (§18). This clears every `[MVP-BLOCKER]` flag except the build itself.
2. **Build Iteration 0** (EPIC-FOUND-0) to its acceptance list. Nothing else starts first — the ownership integration's own rule ("wire the four architecture gates into CI before the first domain table") is only satisfiable this way.
3. **Build Iteration 1** as the pure payment-spine vertical slice defined in §12, with ADR-N7's JSON_DIRECT path, and the first tests written before their features (ledger invariants land in Iteration 2, but idempotency + FSM matrix land here).
4. **Hold the iteration ladder** 2→3→4→5, protecting the Iteration 3 simulation demo date (the business-lens advice from the comprehensive review stands), then the P1 wave led by the case module and the operator worklist.
5. **Never re-open** CPC-SP, the ownership contract, the finality model, or the priority taxonomy without a superseding ADR — the document set's greatest asset is that eight adversarial passes failed to break them.

**The one sentence to remember:** *the architecture survived every kill attempt; what remains is not design work but delivery structure — split Iteration 0 out, paper the signature and the screens, freeze eight ADRs, and start pouring concrete.*

---

*End of review & task plan. Sources: 15 project Markdown artifacts read in full (master architecture & state; main blueprint v8/v10; comprehensive architecture review; blueprint update plan; ownership, ISO-lineage, routing, settlement/liquidity, egress, reconciliation, R-messages/case integrations; case module blueprint; settlement-engine review/CPC-SP; algorithm-research synthesis; HLD & implementation plan). No source document was modified. This is a design/planning artifact: no production code, no production DDL, no claim of production SEPA/EPC/KIR/CSM/RTGS compliance, and no 1:1 copy of TIPS/RT1/STEP2/STET/KIR.*
