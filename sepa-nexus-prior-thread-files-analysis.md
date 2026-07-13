# SEPA Nexus — Prior-Thread Files: Superseded or Complementary?

**Nature.** A file-by-file content comparison, not a structural inventory (that was done in `sepa-nexus-thread-files-readiness-review.md`). This document actually **reads** each of the 15 uploaded files and checks its claims/content against the current canonical documents — not just "does a successor file exist" but "is everything of value in this file actually present somewhere current." `[NO-CODE]` — analysis only.
**Method.** For each file: identify its core content, grep/verify specific claims, decisions, or scenarios against the canonical set, and classify as **pure stale version** (nothing left to extract) or **complementary** (contains something genuinely not yet captured).

---

## Headline Verdict

**13 of 15 files are pure stale versions — fully absorbed, nothing left to extract.** The main blueprint's own closing patch-log (v2 through v11) is a self-documenting audit trail proving section-by-section merge of the seven "integration patch" documents, down to `EPIC-XXX-N` granularity and named fault-injection test scenarios. This is not an assumption — it was verified by grepping specific scenario identifiers (`MISSING_LEDGER_POSTING`, `SILENT_MONEY_DRIFT`, `trigger_event_id`, the finality-correct-return model, the case timing matrix) and confirming they exist in the canonical `sepa-nexus-message-flow-and-data-blueprint.md` at the same or greater precision.

**2 of 15 files are complementary — they contain real, small, not-yet-captured content:**
- `sepa-nexus-hld-and-implementation-plan.md` — a complete, concrete **backup/restore drill design** (§2.9) that nobody transcribed anywhere current.
- `sepa-nexus-master-architecture-and-state.md` — two **named-but-undecided** gap-register items (data retention policy, GDPR/PII boundary marking) that were never explicitly resolved, deferred, or rejected anywhere in the current set.

Neither complementary finding is a blocker. Both are small, `[P2]`-scale, easily absorbed in minutes when next relevant — not a reason to revisit any frozen decision.

---

## Per-File Analysis

### 1. `sepa-nexus-message-flow-and-data-blueprint.md` (v10, pre-patch)
**Verdict: `[SUPERSEDED]` — pure stale version.** This is the direct, literal ancestor of the canonical `outputs/` copy (same filename, v11). Every gap this v10 had (JSON_DIRECT identifier hole, unowned global outbox, topic-naming drift, missing signature/frontend design) was closed by the ADR-N1…N8 patch pass already applied in this thread. Nothing to extract — the v11 copy is a strict superset.

### 2. `sepa-nexus-blueprint-ownership-integration.md` (pre-patch)
**Verdict: `[SUPERSEDED]` — pure stale version.** Direct ancestor of the canonical patched copy. The patch pass added the `signature` ownership row, per-schema outbox ownership, module-naming folds (`live-analytics`→`reporting`, `vop`→`risk`), and replaced its duplicate Kafka topic table with a pointer to the main blueprint's §3.7 v2. Verified: the canonical copy contains everything this one has, plus all of that.

### 3. `sepa-nexus-hld-and-implementation-plan.md`
**Verdict: `[SUPERSEDED]`, with one `[COMPLEMENTARY]` finding.** The bulk of this 1585-line document — the 18-module map, six-gRPC-service design, Tier/Phase roadmap, the 10 Epics (§6) — is explicitly superseded by CPC-SP and the current ADRs (its own successor documents say so). Verified specifically: **Epic 9 (Live Payment Flow View, Three.js/SSE) and Epic 10 (Rulebook/VoP/Risk)** are both already correctly reflected as `[P2]`/`[DEFER]` in the current scope gates — no lost decision. **However**, §2.9 **Backup & Resilience Architecture** contains a complete, concrete ops-drill design — `pg_basebackup`, WAL archiving, PITR into an isolated `postgres-restore` container, a restore drill that ingests N payments and compares counts pre/post, synthetic RPO≤5min/RTO≤30min targets, an evidence pack, and a `RESTORE_APPROVER` role with safety guardrails (never touches the primary volume). **This does not appear anywhere in the canonical set.** The decision gate correctly rejected backup/restore *as a domain module* ("Ops drill script, not a module" — Rejection Gate), but nobody carried the actual drill design forward as that script. `[COMPLEMENTARY]` — worth lifting into a small P2 ops-runbook note when Iteration 0/1 infrastructure work reaches backup tooling.

### 4. `sepa-nexus-iso-lineage-integration.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Confirmed via the main blueprint's own v4 patch-log entry: the pain.001/pacs.002 flow rewrites, §3.8 ISO adapter boundary, §4.3b/§4.3c lineage+DDL, reference-data profiles, four `iso.message.*` topics, eight lineage read models, and `EPIC-ISO-1..5` are all named as merged. Spot-checked the 9-step pacs.002 correlation policy (MATCHED/AMBIGUOUS/ORPHANED/IGNORED_DUPLICATE) — present in the canonical blueprint.

### 5. `sepa-nexus-case-module-blueprint.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Confirmed via v5 patch-log: `case` added to ownership map, three `case.*` topics, and — critically — the **finality-correct return model** (a post-finality return/accepted-recall is a NEW opposite-direction payment, never a ledger reversal) is verified present in the canonical blueprint §5, word-for-word consistent with this source document's central decision (§4). The case timing matrix and R-message four-concept clarity (reject/return/recall/internal-reversal) are both present at full fidelity in canonical §3.13/§4.14.

### 6. `sepa-nexus-routing-reachability-integration.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Confirmed via v6 patch-log: §3.9 routing responsibilities, §4.10 route-resolution pipeline, the synthetic reachability taxonomy, `route.failed` topic, and `EPIC-ROUTE-1..6` are all merged. The routing-in-process-vs-gRPC question this document left open is now closed by ADR-N2 (in-process for MVP, gRPC a named P2 exercise) — a cleaner resolution than anything in this source file.

### 7. `sepa-nexus-settlement-liquidity-integration.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Confirmed via v7 patch-log: §3.10 settlement responsibilities, §4.11 (the binding chain `route_decision→settlement_basis→liquidity_mode→strategy→LedgerPort→finality_rule`), the 6-strategy taxonomy, and `EPIC-SETTLE-1..8` are merged. Strategy-by-`(basis,mode)`-never-by-profile-name — this document's central architectural correction — is frozen as an ADR-equivalent rule in the canonical blueprint.

### 8. `sepa-nexus-egress-delivery-integration.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Confirmed via v8 patch-log and direct grep: the five-status separation, the unified `REQUESTED→…→CLOSED` lifecycle, idempotent artifact creation on `UNIQUE(trigger_event_id, artifact_type, recipient)`, and `manual_delivery_actions` are all present verbatim in the canonical blueprint. The Testing Strategy paragraph's specific scenarios (no-finality-mutation grant test, no-ledger-write grant test, dead-letter/manual-intervention queue, simulation egress-failure injection) all verified present in the canonical blueprint and the Egress & Delivery UI spec.

### 9. `sepa-nexus-reconciliation-integration.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Confirmed via v9 patch-log and direct grep of named fault-injection scenarios (`MISSING_LEDGER_POSTING`→`LEDGER_RISK`, `SILENT_MONEY_DRIFT`→`CRITICAL`, `STATUS_MISMATCH`/`FINALITY_MISMATCH`) — all present in the canonical blueprint's mismatch taxonomy **and** promoted into named backlog stories (`EPIC-RECON-3`/`EPIC-RECON-4`). The "no source mutation, action-request-is-a-request-not-a-mutation" rule is verified present, matching this source's flagship architectural risk callout.

### 10. `sepa-nexus-r-messages-case-rules-integration.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Confirmed via v10 patch-log: the case timing matrix (gating recall/reject/return legality by `as_of` finality state) is present in canonical §3.13 at the same precision as this source, including the exact five timing bands (before-route / after-route-pre-settlement / post-acceptance-pre-finality / post-finality / post-egress). Duplicate/conflicting R-message handling (`CASE_DUPLICATE_SUPPRESSED`, dedupe-on-same-event) verified present.

### 11. `sepa-nexus-settlement-engine-review-and-fresh-architecture.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** This is the document that **originated CPC-SP** — every architectural decision here (kill the 12-engine topology, adopt profiles-not-engines, the 5-strategy taxonomy, the 5-iteration MVP ladder) is exactly what the canonical blueprint implements today, so by construction there is nothing left unabsorbed. Spot-checked §11's "QA/SDET educational value" surface-to-lesson table (REST/GraphQL/gRPC/Kafka/SQL/Keycloak/Playwright/state-transition/decision-table/failure-testing) against the Playwright test-learning document's coverage matrix — same surfaces, same lessons, consistent framing (including the gRPC-is-one-P2-exercise detail, which ADR-N2 later formalized).

### 12. `sepa-nexus-algorithm-research-critical-synthesis.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Its §16 "Missing Topics Not Covered Enough" lists thirteen explicit fills the project committed to adopting (bounded contexts, reason-code lifecycle, ISO versioning, tenant isolation via claim→GUC→RLS, deterministic simulation seeds, test-data generation, audit/evidence, observability plumbing, failure taxonomy, migration ladder, educational lesson structure, GraphQL read models, synthetic-realism disclaimers). **All thirteen were verified present** in the canonical set — this document's entire reason for existing (filtering research into adoptable fills) is complete and superseded by the resulting architecture.

### 13. `sepa-nexus-comprehensive-architecture-review.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** Its Keycloak question (§6: "9/10 capability, planned to 6/10 depth — FAPI-2, Organizations, FGAP v2, passkeys close the gap") is precisely what `sepa-nexus-keycloak-26-security-architecture-blueprint.md` now delivers in full, verified detail. Its SEPA/KIR flow verdict (§7.4: "8/10 — missing fifth is file-rail semantics + XML threat model") maps to gaps G1 (XML threat model → closed by the signature blueprint's verify-before-parse rule) and G5 (file-rail semantics → closed by item-level partial-accept + business calendars in the canonical blueprint), both confirmed resolved. Superseded in full by `sepa-nexus-full-blueprint-review-and-task-plan.md`, its direct successor.

### 14. `sepa-nexus-master-architecture-and-state.md`
**Verdict: `[SUPERSEDED]`, with two `[COMPLEMENTARY]` findings.** Its 20-module map, iteration numbering, and priority taxonomy are all explicitly superseded by ADR-N1…N8 and the decision gate. Its §9 Gap Register (G1–G9) was checked item-by-item: G1 (XML threat model) → signature blueprint; G2 (egress delivery guarantee) → outbound_artifacts/dead-letter; G3 (RLS for background workers) → `system_<n>` GUC tier in the Keycloak blueprints; G4 (correlation keys) → `payment_iso_identifiers`; G5 (batch partial-accept) → item-level SkipPolicy; G6 (cut-off race/calendars) → `business_calendars`/`settlement_cutoff_calendar`; G7 (routing-down policy) → dissolved by ADR-N2 (in-process for MVP); G8 (clock authority) → `ClockPort`; G9 (ledger snapshot isolation/audit-same-tx) → same-transaction evidence rule in the signature blueprint — **all nine resolved**. DORA/`resilience-incident`, VoP, conformance, and direct-debit+mandates are all explicitly `[DEFER]`red with named future homes in `sepa-nexus-full-blueprint-review-and-task-plan.md` §11/§19 — correctly scoped out, not forgotten. **But two Low/Low-Med items were never explicitly decided anywhere**: **data retention policy** ("define synthetic retention windows") and **GDPR/PII boundary marking** ("mark payment data as personal; synthetic-only disclaimer in UI") — zero mentions in any canonical document, not even as a named `[DEFER]`. `[COMPLEMENTARY]` — both are small, `[P2]`-scale, and trivially addressed with a one-line disclaimer + a deferred-retention-policy note whenever Evidence/Audit or Reference Data work is next touched.

### 15. `sepa-nexus-blueprint-update-plan.md`
**Verdict: `[SUPERSEDED]` — pure stale version.** This document's entire job was to plan *how* to apply the deep-research findings to the main blueprint — that planning is complete, executed (the v2…v10 patches it planned are the same ones verified above), and its own purpose is now historical. The decision gate and the patch-pass work in this thread are its direct, completed successors.

---

## What "Complementary" Actually Means Here

Neither finding changes any decision, reopens any ADR, or blocks anything:

| Finding | Where it lives now | Severity | Suggested handling |
|---|---|---|---|
| Backup/restore drill design (concrete runbook) | `sepa-nexus-hld-and-implementation-plan.md` §2.9 only | `[P2]`, non-blocking | Lift into a short ops-runbook note when Iteration 0/1 infra work reaches backup tooling — the design is already complete, just needs transcription |
| Data retention policy | `sepa-nexus-master-architecture-and-state.md` §9 only | Low, non-blocking | One line in the Evidence/Audit or Reference Data area next time either is touched: "synthetic retention window, not a real regulatory retention claim" |
| GDPR/PII boundary marking | `sepa-nexus-master-architecture-and-state.md` §9 only | Low, non-blocking | One line in the frontend or evidence blueprint: mark payment/participant data as synthetic-personal, disclaimer in the UI where a "regulator" or auditor view appears |

None of these three items is large enough to justify reopening any frozen document. They are the kind of thing that gets a one-sentence mention the next time a relevant document is naturally edited — not a reason to patch anything today.

---

## Final Answer

**These are, overwhelmingly, old versions — not a parallel or missed body of documentation.** 13 of 15 files are fully and verifiably absorbed into the current canonical set, several confirmed at the level of named test-scenario identifiers and epic numbers, not just topic-level summaries. The main blueprint's own ten-version patch log is a legitimate, checkable audit trail of that absorption — this analysis independently verified a sample of its claims rather than trusting the log at face value, and every sampled claim held up.

The two complementary findings are real but minor: one fully-designed ops artifact (backup/restore drill) that was correctly *scoped out* as a domain module but never transcribed as the runbook it should become later, and two small regulatory-adjacent gap-register items that were never explicitly decided either way. None of the three blocks `sepa-nexus-iteration-0-foundation-plan.md` or any current work — they are safe to note and defer, not reasons to pause.

---

*End of analysis. `[NO-CODE]` — comparison and verdict only. No documents modified, no decisions reopened.*
