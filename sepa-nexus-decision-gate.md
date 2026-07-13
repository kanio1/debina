# SEPA Nexus — Analysis-Phase Decision Gate

**Nature.** This is not a review. It is a **decision gate** that closes the analysis phase. It selects the blockers, freezes the ADRs, locks MVP/P1/P2 scope, publishes the rejection list, fixes the blueprint patch order, and names the single next document. Every row is a decision, not a discussion.
**Inputs.** The 15 project artifacts + the full blueprint review & task plan. No source document is modified here.
**Authority model.** Newest integration patches > Master Architecture & State > Comprehensive Review > Main Blueprint v8/v10 > HLD/synthesis/update-plan. Conflicts that survive the hierarchy are resolved here by `[ADR]`, never left open.

---

## 1. Gate Verdict (one screen)

**The architecture is frozen and correct. The analysis phase ends now. The blocker is delivery structure, not design.** Eight adversarial passes failed to break CPC-SP, one-writer-per-schema, the finality model, or the five-status separation — so those are `[FREEZE]`, not revisitable. What is *not* ready is: (a) Iteration 0 does not exist as a phase, (b) the frontend has read models but no screens/token model, (c) `signature` is named everywhere and designed nowhere. These three plus two data/eventing holes (JSON identifier path; per-schema outbox ownership) are the only things standing between "analysis" and "pour concrete."

**Gate decision preview:** `[ITERATION-0-FIRST]` after a ≤1-week `[PATCH-FIRST]` paper pass. Neither `[GO]` (there are real MVP-blockers) nor `[NO-GO]` (nothing architectural is broken).

---

## 2. Blocker Register (the only things that gate the build)

Five `[MVP-BLOCKER]` items. Everything else is scheduled, not blocking.

| # | Blocker | Class | Why it gates | Closes via |
|---|---|---|---|---|
| B1 | Iteration 0 (Platform Skeleton) absent from the consolidated roadmap | `[HLD-GAP]` | The ownership integration's own rule — "wire the four CI gates before the first domain table" — is unsatisfiable without a foundation phase | ADR-N1 + `sepa-nexus-iteration-0-foundation-plan.md` |
| B2 | `signature` module named in filters/ports, designed nowhere | `[HLD-GAP]` | Iteration 2 status-out assumes a signing port; verify-before-parse assumes a verdict store | EPIC-SIG SIG-S1 (boundary + `signature.*` DDL) |
| B3 | Direct-JSON payments cannot record identifiers (`iso_message_id NOT NULL` vs "identifiers+lineage for JSON") | `[HLD-GAP]` | Iteration 1 hot path; breaks correlation promise as written | ADR-N7 (`JSON_DIRECT` pseudo-version + `SKIPPED` iso_messages row) |
| B4 | `outbox_events`/`inbox_events` have no schema owner | `[HLD-GAP]` | Blocks Flyway folder-per-module and grant tests — i.e. blocks Iteration 0 | ADR-N5 (per-schema tables + shared dispatcher role) |
| B5 | Frontend has no screens, navigation, role→screen map, or token model | `[HLD-GAP]` | No FE story is decomposable; BFF-vs-SPA unfrozen since the comprehensive review | ADR-N3 (BFF) + frontend blueprint §14 |

Not blockers (scheduled): routing invocation style (ADR-N2 defers the seam), Keycloak Organizations/FGAP/FAPI depth (Iter 1/5), topic-catalog drift (doc pass), G7 (dissolved by in-process routing).

---

## 3. HLD Gap Gate

Decision on every gap. `[FREEZE]` = settled, do not reopen. Only rows needing action carry a patch target.

| Gap ID | Gap | Decision | Patch Target | Priority |
|---|---|---|---|---|
| R-01 | Iteration 0 missing | `[CHANGE]` add as first-class phase | roadmap / Iter 0 plan | `[MVP-BLOCKER]` |
| R-02 / R-05 | Two priority taxonomies; simulation mis-tagged | `[CHANGE]` one taxonomy; simulation `[MVP]` Iter 3 | ADR index + blueprint re-tag | `[HLD-GAP]` |
| R-07 | Routing: gRPC vs in-process contradiction | `[CHANGE]` in-process for MVP; gRPC = P2 exercise | ADR + main blueprint §3.7/§4.10 | `[HLD-GAP]` |
| R-08 | JSON payments can't record identifiers | `[CHANGE]` `JSON_DIRECT` pseudo-version | main blueprint §4.3c + ingress path | `[MVP-BLOCKER]` |
| R-09 | validation/mapping/render profile tables missing | `[ADD]` 3 versioned catalogs | main blueprint §4.13 | `[MVP]` |
| R-10 | Kafka topic-catalog drift | `[CHANGE]` §3.7 v2 = sole AsyncAPI source | event catalog / AsyncAPI | `[MVP]` |
| R-11 | outbox/inbox unowned | `[CHANGE]` per-schema + dispatcher role | main + ownership blueprint | `[MVP-BLOCKER]` |
| R-13 | `signature` undesigned | `[ADD]` module design + DDL | signature blueprint | `[MVP-BLOCKER]` |
| R-14 | Keycloak adoption is a recommendation table | `[ADD]` realm design + claim→GUC mapping | Keycloak/security blueprint | `[MVP]` (claims) |
| R-15 | No frontend design | `[ADD]` screens + nav + roles + token model | frontend blueprint | `[MVP-BLOCKER]` |
| R-17 | Observability un-consolidated | `[ADD]` one inventory | main blueprint (new §) | `[MVP]` |
| R-18 / R-19 | Operator worklist + inbound-file read models missing | `[ADD]` two read models | main blueprint §6.6 | `[P1]` / `[MVP]` |
| R-20 | Admin-command REST contracts uninventoried | `[ADD]` contract list | main blueprint §13 | `[MVP]` per iteration |
| R-21 | G7 routing-down policy | `[DEFER]` to gRPC extraction exercise | — | `[P2]` |
| R-24 | `live-analytics`/SSE ADR open | `[CHANGE]` fold into `reporting`; SSE for MVP | ADR + main blueprint | `[P1]` |
| R-03/04/06/12/16/22/23 | Module-count/name/priority/DDL-note/stack-row/batch-module/HLD-residue hygiene | `[CHANGE]`/`[DEFER]` cosmetic | doc-hygiene pass | low |
| — | CPC-SP, one-writer, finality-as-new-payment, five-status, LedgerPort, selective RLS, PG18-baseline, deterministic as_of, timing matrix | `[FREEZE]` | — | — |

**Gate outcome:** 5 blocker-class, ~9 MVP-non-blocking, ~7 cosmetic, ~9 frozen. Zero architectural.

---

## 4. ADR Freeze Pack

Frozen now, filed under `/docs/adr/`. The pre-existing frozen set (Modulith deployable, CPC-SP, profiles-not-engines, strategy-by-(basis,mode), LedgerPort-only, finality-is-a-profile-rule, egress-transport-only, reconciliation-read-only, case-decision-only, GraphQL-read-only, simulation-through-normal-paths, PG18-baseline, Maven, selective-RLS, FSM-not-workflow, raw-archive-never-dedupes) is reaffirmed `[FREEZE]` and not re-listed. Eight **new** ADRs close the open contradictions:

| ADR | Decision | Status | Rejected Alternative | Blocks Until Frozen |
|---|---|---|---|---|
| ADR-N1 | Iteration 0 precedes Iteration 1; foundation never mixed into a business iteration | `[FREEZE]` | "Iter 1 includes setup" (Master §11) | roadmap, all CI gates |
| ADR-N2 | Routing = in-process Modulith module consuming `payment.validated`; gRPC extraction is a P2 educational exercise carrying its own G7 degraded-mode policy | `[FREEZE]` | out-of-process gRPC in MVP `[OVERENGINEERING]` | contracts, G7, routing tests |
| ADR-N3 | BFF token model (Next.js server session, HttpOnly cookie); DPoP-SPA is the documented alternative | `[FREEZE]` | SPA-with-token default | every frontend story |
| ADR-N4 | SSE for MVP live feeds; GraphQL subscriptions only if bidirectionality appears | `[FREEZE]` | WebSocket/subscriptions now | S-02 live counters, `reporting` SSE |
| ADR-N5 | Per-schema `outbox_events`/`inbox_events`; one shared-kernel dispatcher role with explicit grants | `[FREEZE]` | one global unowned table | Flyway layout, grant tests |
| ADR-N6 | One taxonomy: `[MVP]`=Iter 0–5, `[P1]`=wave 1, `[P2]`=wave 2/labs; simulation is `[MVP]` (Iter 3) | `[FREEZE]` | dual taxonomies | backlog planning |
| ADR-N7 | `JSON_DIRECT` pseudo message-version; JSON path writes `iso_messages(parse_status='SKIPPED')` and records identifiers against it | `[FREEZE]` | nullable `iso_message_id` (breaks PK) / no JSON identifiers (breaks correlation) | Iteration 1 ingress path |
| ADR-N8 | §3.7 v2 topic table is the sole AsyncAPI source; patch docs reference it | `[FREEZE]` | per-patch catalog fragments | event contracts |

`[DECISION-NEEDS-EVIDENCE]` (not frozen, revisit on data): OSS ledger extraction; PG19 feature promotions (only on GA); routing gRPC extraction go/no-go; BM25 vs FTS beyond MVP.

---

## 5. MVP / P1 / P2 Feature Gate

Per ADR-N6. `[MVP]` = inside Iterations 0–5. This is the *selected* set, not a wish-list.

**`[MVP]` (Iterations 0–5) — locked:**
Platform skeleton + CI gates (Iter 0) · payment spine + idempotency + timeline + JSON_DIRECT (Iter 1) · ISO lineage core + correlation + 15-test suite · ledger + GrossInstant + Σ=0 trigger + finality rules (Iter 2) · egress rail + result files + `signature` verify (Iter 2) · simulation + seed + failure profiles (Iter 3) · NetDeferred + cycles + netting (Iter 4) · reconciliation core, 4 types, as_of (Iter 4) · routing pipeline + immutable explanation (Iter 5) · ISO in/out + `signature` sign (Iter 5) · Keycloak Organizations claims + two-level RLS · frontend baseline (BFF, S-00…S-05, S-08, S-11, S-17) · observability core (segmented latency, correlation).

**`[P1]` (wave 1) — scheduled:**
Case module (recall/return/reject, MVP-of-P1, flagship return-after-finality) · egress P1 (outbound files, receipts-in, camt.029/pacs.004) · reconciliation P1 types (egress/ISO/case/report) · business calendars + rollover · operator worklist (S-01) · FGAP-2 admin plane + FAPI-2 + passkeys · audit hash-chaining · settlement retries / queue / CGS / prefunded.

**`[P2]` (wave 2 / labs) — parked with homes:**
risk/VoP advisory · search (FTS-first) · statements (camt.052/053/054) · PG19 promotions on GA · routing gRPC extraction + G7 policy · SQL/PGQ lab · Three.js 3D (after asserting SSE feed).

**Gate rule:** no `[P1]`/`[P2]` item may enter an MVP iteration without a superseding ADR. Iteration 3 (simulation demo) date is protected — nothing may slip into it.

---

## 6. Rejection Gate

`[REJECT]` — do not build, even though each sounds enterprise-grade. Rejection is final unless a superseding ADR overturns it.

| Rejected | Why `[REJECT]` |
|---|---|
| Per-CSM engines (settlement / egress / routing) | Behaviour is a config row; engines re-import the killed 12-engine topology `[OVERENGINEERING]` |
| Finality on delivery / on CSM-accepted; business-return-as-reversal | Violates the frozen finality model; silently un-settles money |
| Reconciliation that repairs; autonomous remediation | Breaks read-only detect-and-escalate boundary |
| Six in-JVM gRPC services; gRPC for admin commands | gRPC-for-gRPC in one JVM; REST is sufficient `[OVERENGINEERING]` |
| BPMN / workflow / DMN engine as core | FSM + decision tables are the whole point |
| Full event sourcing; vector clocks; exactly-once-everywhere | Hybrid ledger + per-key order + idempotency already solve it |
| ML fraud/risk; BM25 as a dependency; semantic search | Non-deterministic / AGPL / no corpus; rules + FTS win |
| Full ISO catalogue; CBPR+; graph DB for lineage; full party normalisation now | Surface without a new lesson |
| Regulator workspace; backup/restore as a domain module | Ops drill script, not a module |
| Topic-per-profile/type/status; six-simulator source lab | Catalog sprawl; one seeded simulator + profiles suffices |
| RLS on queues/ledger; all schemas upfront; implicit instant→batch fallback | Cost/race/false-realism the patches already removed |
| Three.js before an asserting SSE feed | Decoration without test value |
| Queueing formulas as runtime | Dashboard annotations only |

---

## 7. Blueprint Patch Order

Strict order. Each patch is a paper edit (no code). "Blocks" = what cannot start until this lands.

| Order | Document | Patch | Reason | Blocks |
|---|---|---|---|---|
| 1 | ADR index (`/docs/adr/`) | Freeze ADR-N1…N8 (§4) | Every downstream patch cites these; freezing first prevents re-litigation | all patches below |
| 2 | Event catalog / AsyncAPI | Publish §3.7 v2 (add `egress.dead_lettered`, `egress.manual_intervention_required`; rename recon topics) per ADR-N8 | Single source of truth for producers/consumers before any contract is generated | Iter 0 topic provisioning; all eventing stories |
| 3 | Main message-flow blueprint | Apply R-08 (JSON_DIRECT), R-09 (profile catalogs), R-11 (per-schema outbox), R-10 pointer, R-17 (observability §), R-18/19/20 (read models + admin commands), R-02/07 re-tag, supersession notes (R-23) | The spine every module FKs into; JSON_DIRECT + outbox are Iter-1/0 hot path | Iteration 0, Iteration 1 |
| 4 | Ownership integration blueprint | Add `signature` row to §3.6.2/§3.6.3; confirm per-schema outbox ownership (ADR-N5); fold `live-analytics`→`reporting`, `vop`→`risk`; `batch` schema owner = `ingress` | Ownership contract gates every migration and grant test | Flyway folder-per-module; grant tests |
| 5 | Signature module blueprint (new) | Boundary + `signature.message_signatures`/`signature.keys` DDL + verify/sign ports + verdict-recording + synthetic key registry (SIG-S1) | B2; egress status-out (Iter 2) and verify-before-parse depend on it | Iteration 2 |
| 6 | Keycloak / security blueprint (new) | Realm design + Organizations claims → `app.tenant_id`/`app.branch_id` GUCs + FGAP-2 permission model bound to admin-command inventory + BFF client config + hardening | Two-level RLS needs the claim source; admin commands need permissions | Iteration 1 (claims), Iteration 5 (FGAP-2) |
| 7 | Frontend / React Next.js blueprint (new) | 19-screen inventory (S-00…S-18) + navigation + role→screen map + BFF (ADR-N3) + SSE (ADR-N4) + test-id + deep-link conventions | B5; no FE story is decomposable without it | all frontend stories |
| 8 | Roadmap / Iteration 0 plan (new) | Iteration 0 phase + EPIC-FOUND-0 stories + acceptance criteria; re-cut Iteration 1 to a pure vertical slice | B1; sequences everything above into a build order | first commit |

Patches 1–2 are pure hygiene (hours). 3–4 are the data/ownership spine (1–2 days). 5–8 are the four missing design docs (the bulk of the paper week).

---

## 8. Next Document to Produce

**Choice: `sepa-nexus-iteration-0-foundation-plan.md`** — but only after patches 1–4 in §7 land (they are hours-to-two-days of edits, not a document each).

**Why this one, not the alternatives:**
- **Not `sepa-nexus-blueprint-patch-plan.md`.** The patch plan is already *in this gate* (§7) at sufficient resolution; writing a separate document to describe edits I've already ordered is process theater `[OVERENGINEERING]`. The patches get *applied*, not re-described.
- **Not `sepa-nexus-module-ddl-api-event-contract-blueprint.md` yet.** It is the correct *eventual* next big artifact (the algorithm-synthesis §19 "recommended next prompt" points at it), but producing full DDL/API/event contracts for 17 modules **before Iteration 0 exists** repeats the exact failure this gate is closing: designing depth on top of an undefined foundation. The contract blueprint is the natural output of *Iteration 5 planning*, not of the gate.
- **`sepa-nexus-iteration-0-foundation-plan.md` is the highest-leverage next artifact** because B1 blocks the largest surface (all CI gates, all grants, all module stubs), it is the one document that turns "analysis done" into "build started," and it is small and fully specified already (EPIC-FOUND-0, F0-S1…S5, acceptance list). It also forces patches 5–7 to converge (signature/Keycloak/frontend all wire into the skeleton), so it is the integration point, not just another doc.

**Sequence to hand to the builder:** apply §7 patches 1–4 → write signature + Keycloak + frontend blueprints (§7 patches 5–7, can be parallel) → write `sepa-nexus-iteration-0-foundation-plan.md` (patch 8) → build Iteration 0 to its acceptance list → then, and only then, `sepa-nexus-module-ddl-api-event-contract-blueprint.md` for Iterations 1–5.

---

## 9. Final Decision

```text
DECISION: ITERATION-0-FIRST (after a ≤1-week PATCH-FIRST paper pass)
WHY: The architecture survived eight adversarial passes with zero ownership reversals, so it is FROZEN, not a GO/NO-GO question — nothing structural is broken and nothing warrants re-design. But five MVP-blockers sit on the Iteration-0/1 path (Iteration 0 undefined, signature undesigned, JSON-identifier DDL hole, unowned outbox, no frontend), and the project's own ownership rule forbids writing the first domain table before the CI gates exist. Therefore the gate closes analysis by freezing eight ADRs, ordering eight paper patches, and pouring the foundation before any business flow — a GO would skip real blockers, a NO-GO would deny a design that has already earned its topology.
NEXT: sepa-nexus-iteration-0-foundation-plan.md (produced after §7 patches 1–4 land; it is the single artifact that converts "analysis complete" into "build started" and forces the signature/Keycloak/frontend blueprints to converge on the skeleton).
```

---

*End of decision gate. This document closes the SEPA Nexus analysis phase: blockers selected (§2), HLD gaps decided (§3), eight ADRs frozen (§4), MVP/P1/P2 locked (§5), rejections final (§6), patch order fixed (§7), next document named (§8), decision recorded (§9). No source artifact was modified. No production code, DDL, or compliance claim; no 1:1 replica of TIPS/RT1/STEP2/STET/KIR.*
