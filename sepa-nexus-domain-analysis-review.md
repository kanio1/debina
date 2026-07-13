# Review: SEPA/ISO 20022 Deep-Research Analysis vs. Our Frozen Documentation

**Nature.** A critical review of the just-produced SEPA domain-modeling research artifact, confronted against the project's existing frozen documentation. Not a new design. `[NO-CODE]`. The controlling question throughout: does this analysis *strengthen* our Playwright-Learning-Development project, or does it quietly push us toward a simulated production bank and a naming schism with what we already froze?

---

## 1. Executive Verdict

`[ADOPT-WITH-CORRECTION]` The analysis is **domain-solid, standards-verified, and directionally aligned** — its research findings (SCT batch nests File→Bulk→transaction; SCT Inst is strictly 1 msg = 1 transaction; three status tracks; "Interchange" is a card term; VoP 5s SLA; €100k cap removed) are accurate and genuinely useful as a *domain reference*. **But it must not be pasted into our documentation as-is**, for one structural reason: **it proposes a five-layer aggregate model (PaymentOrder → PaymentInstruction → PaymentBatch → ClearingSubmission → OutboundMessage) under names we do not use, for a system that already froze a single canonical `payment.payments` aggregate plus module-owned artifacts.** Adopting its object names verbatim would fork our naming, inflate our aggregate count, and re-litigate frozen ADRs — the exact overengineering our project treats as an anti-value.

Three-sentence decision: **the research is a keeper as a domain-grounding appendix and a source of a few precise corrections; its object model is not**, because ours already exists, is leaner, and is frozen. Safe to fold in the *findings and terminology discipline*; not safe to fold in the *five-aggregate model*. The single most valuable thing it surfaces that we should actually absorb is the **rail-dependent grouping rule** (batch groups, instant does not) — which we already imply but never stated as one explicit, testable rule.

---

## 2. Consistency With Project Documentation

| Analysis element | Our docs | Verdict |
|---|---|---|
| `OutboundMessage` / `InboundMessage` as ISO-message envelopes distinct from business objects | **Already ours** — `egress` owns `outbound_messages`/`OutboundMessage`; `ingress` owns `InboundMessage`/`InboundFile` (6 files reference these) | **Consistent** — analysis re-derives what we froze |
| Settlement / reconciliation / egress as separate concerns | **Already ours** — `settlement` (`SettlementCycle`/`SettlementPosition`), `reconciliation` (`ReconciliationRun`/`ReconciliationException`), `egress` are frozen Spring Modulith modules with schema ownership | **Consistent** |
| Three status tracks (business / technical / settlement) | **Already ours** — we froze a four-axis status model (business/settlement/egress/reconciliation) plus a fifth (approval) from the 4EV work; settlement axis already maps to pacs.002 codes | **Consistent, and ours is richer** |
| Outbox/inbox, idempotency keys, correlation_id, UETR, RLS, per-module ownership | **Already ours** — ADR-N5 (per-schema outbox/inbox), `idempotency_keys` store, selective RLS, one-writer-per-schema | **Consistent** |
| "Interchange" should not be used | **We never used it** — zero occurrences in any document | **Moot** — a non-issue for us; the recommendation is correct in general but solves a problem we don't have |
| VoP thin-check, four-eyes, fraud/risk hold | **Already ours** — §2.2b/§2.2c of the main blueprint, added in the persona-driven and 4EV passes | **Consistent** |
| `PaymentOrder` / `PaymentInstruction` / `PaymentBatch` / `ClearingSubmission` / `TransportFile` / `RailProfile` as **aggregates** | **Not ours** — zero occurrences; we use one canonical `payment.payments` + `PaymentLifecycle`, and handle grouping via `batch_id` + the `ingress` file rail | **Divergent** — this is the one real conflict |
| Rail-dependent grouping stated as an explicit rule | **Implied, never stated** — our file rail (pain.001 batch) and instant path exist, but "batch groups / instant is 1:1" is not written as one testable invariant | **Gap in ours the analysis usefully exposes** |

**Net:** ~80% of the analysis describes, in different words, a system we already designed. ~15% is a naming/aggregate-count divergence we should *not* adopt. ~5% is a genuinely useful sharpening (the explicit grouping rule) we *should* absorb.

---

## 3. Domain-Model Review (object by object)

Verdict format: **keep / rename-to-ours / merge / defer / drop**.

| Analysis object | Verdict for us | DDD nature | Scope | Rationale |
|---|---|---|---|---|
| `PaymentOrder` | **merge → our `payment.payments`** | our existing aggregate root | `[MVP]` | We deliberately collapsed "intent" and "processed unit" into one canonical payment. Splitting intent from instruction is textbook-correct for a real bank and **overengineering for us** — it doubles the aggregate count to teach a distinction Playwright tests don't need. Keep one aggregate. |
| `PaymentInstruction` | **merge → our `payment.payments`** | our existing aggregate root | `[MVP]` | Same reason. The ISO `CdtTrfTxInf` maps to a *field group on our payment*, not a second aggregate. |
| `PaymentBatch` | **rename → our existing `batch_id` grouping** | grouping attribute + read model, **not a new aggregate** | `[MVP]` thin | We already group via `batch_id` on `payment_approvals` and the file rail. A full `PaymentBatch` aggregate with its own FSM is more than we need; the `batch_id` grouping + batch-approval command already teaches batch multi-user testing. |
| `ClearingSubmission` | **defer / partially map → `egress` + `routing`** | our `egress` already owns dispatch | `[P1]` at most | This is the analysis's best domain idea (the STEP2 "bulk" = dispatch unit). But our `egress` module + `route_decisions` already cover "how a payment leaves." A distinct `ClearingSubmission` aggregate is a **P1 teaching refinement**, not MVP — and only if a second rail profile is ever added. |
| `TransportFile` | **defer** | technical envelope | `[P1]` | We already have `inbound_files`/`inbound_file_items` on ingress and `outbound_files` on egress. A first-class `TransportFile` aggregate wrapping bulks is real STEP2 fidelity we explicitly don't need for MVP. |
| `OutboundMessage` | **keep — already ours** | technical envelope | `[MVP]` | Identical to our frozen `egress.outbound_messages`. No change. |
| `InboundMessage` | **keep — already ours** | technical envelope + inbox dedup | `[MVP]` | Identical to our frozen `ingress.InboundMessage`. No change. |
| `SettlementResult` | **merge → our `settlement_*` + reconciliation read models** | read-model/value record | `[MVP]` | We surface settlement outcome via `settlement_positions`/`settlement_items` and the settlement status axis. A separate `SettlementResult` object is redundant with what we froze. |
| `ReconciliationItem` | **rename → our `reconciliation_results`/`reconciliation_exceptions`** | read model | `[MVP]` | Ours already exists under a different name and is richer (severity, source run, mismatch taxonomy). Keep ours. |
| `PaymentStatusHistory` | **keep — already ours** | audit projection | `[MVP]` | We have `payment_status_history`. Consistent. |
| `AuditEvent` | **keep — already ours** | append-only audit | `[MVP]` | We have `audit_log`, same-transaction-with-command. Consistent. |
| `RailProfile` | **map → our existing `routing_profiles`/`scheme_profiles`** | reference-data catalog | `[MVP]` config | We already have rail/scheme/routing profiles owned by `reference-data` (§3.6.4). The analysis's `RailProfile` is our `scheme_profiles` under a new name — **use ours, don't add theirs**. |

**Summary:** of twelve proposed objects, **five are already ours under our names** (keep), **four merge/rename into existing structures** (don't duplicate), **two defer to P1** (`ClearingSubmission`, `TransportFile`), and **one (`RailProfile`) maps to an existing catalog**. Zero should be adopted as a genuinely new aggregate. This is the crux: the analysis's model is not *wrong*, it is *heavier than ours*, and ours is frozen.

---

## 4. Terminology Review

- **"Interchange" — remove recommendation is correct but moot.** The analysis is right that it's a card-scheme term. We never used it. No action beyond noting the reasoning in the glossary so nobody introduces it later.
- **"Batch" vs "Bulk":** the analysis's distinction is accurate (SEPA-Clearer says "bulk," ACH says "batch," they're near-synonyms). **For us: standardize on "batch"** (we already use `batch_id`), and mention "bulk" only as the STEP2 mapping note in the glossary. Do **not** rename our `batch_id` to `bulk_id`.
- **"ClearingSubmission":** good name, but for us it's a `[P1]` concept mostly covered by `egress` + `routing`. If we ever add it, the name is fine — better than "dispatch package."
- **"TransportFile" in MVP:** **no.** We have `inbound_files`/`outbound_files` already; a separate transport-file aggregate is P1 STEP2 fidelity.
- **"PaymentInstruction" scope:** the analysis defines it as one debtor→creditor movement. For us that *is* our canonical payment — so the term is fine as a **synonym in the glossary**, not as a second aggregate.
- **"OutboundMessage"/"InboundMessage":** correctly separate ISO message from business object — and we already do exactly this. Endorsed, because it's already ours.

---

## 5. Pipeline & Status Review

- **Pipeline is too elaborate for Iteration 0 if taken literally** (14 stages × 6 rail variants = 84 cells). Our Iteration 0 is a walking skeleton: submit → validate → persist → one status transition → one Playwright test. The analysis's pipeline is a **useful P1+ reference map, not an Iteration-0 build order**.
- **Necessary stages for MVP:** acceptance, technical+business validation, 4EV gate (already ours), dispatch via egress, inbound status via inbox, reconciliation, audit. **Educationally valuable:** the batch-vs-instant split, the reservation/timeout path for instant. **Overengineering for us:** separate enrichment stage as its own step, full R-transaction taxonomy in MVP, TransportFile-level three-tier rejection.
- **Status tracks:** the analysis's three tracks (business/technical/settlement) are **a subset of our five axes** — no conflict, but we must not let its three *replace* our five. Our approval axis (from 4EV) and egress axis are not in its model; keep ours.
- **FSM risk:** adopting the analysis's per-object FSMs (order FSM + instruction FSM + batch FSM + submission FSM + message FSM + reconciliation FSM = six FSMs) would **multiply our test surface without teaching more Playwright**. Our existing FSM (one payment lifecycle + status axes) is the leaner, already-frozen choice. **Reject the six-FSM expansion.**

---

## 6. Technical-Architecture Review

Confronted against our stack, the analysis is **consistent where it matters and adds nothing we lack**:

- **Modules:** its proposed modules (origination/paymentlifecycle/compliance/approval/grouping/egress/ingress/settlement/reconciliation/simulation/audit) are **a renamed superset of ours** — we already have ingress/routing/settlement/egress/reconciliation/signature/reference-data/simulation/payment-lifecycle/risk. Its `origination`+`grouping`+`approval`+`compliance` split is finer-grained than our frozen boundaries. **Keep ours** (Spring Modulith boundaries are frozen; re-slicing them is a bounded-context rewrite we don't need).
- **Tables/constraints/idempotency/outbox/inbox/RLS:** all already ours, often in more detail (our `uuidv7` PKs, selective RLS with empty-GUC-zero-rows, per-schema outbox with dispatcher-role negative sweep). The analysis adds **nothing here we don't have**, and lacks our security depth.
- **Locking:** the analysis's "optimistic for aggregates, pessimistic for submission sealing" is sound and **matches our two-checker-race and assignment-race optimistic pattern** — no change needed.
- **Read models:** its CQRS-lite projection list matches our GraphQL-read-model ownership table. Consistent.

---

## 7. Security Review

This is where the analysis is **weakest relative to our docs, and must not be allowed to dilute what we froze**:

- **What it covers adequately:** four-eyes, idempotency, tenant isolation, RLS, basic role list, audit trail — all present, all correct, all *already deeper in our docs*.
- **What it under-specifies vs. ours:** **BOLA/IDOR** (we have a custom `AuthorizationManager<MethodInvocation>` object-level check; the analysis only says "ownership checks on every request"), **step-up auth** (we have a full `auth_time`/`acr_values` design; the analysis omits it entirely), **mass assignment** (we froze narrow command DTOs; the analysis's generic `PUT`-style endpoints in §8 would *reintroduce* mass-assignment risk if copied), **data masking** (neither of us has a full design — genuine shared gap, see §9), **sensitive-business-flow rate limiting** (we added it per OWASP API6:2023; the analysis omits it).
- **Concrete hardening the review demands:** do **not** adopt the analysis's REST endpoint table verbatim — its generic `/payments/{id}` object endpoints are exactly the BOLA surface our security pass hardened. Any endpoint we take from it must inherit our object-level `AuthorizationManager` rule, our idempotency-key column, and our RFC 7807 error shape. **The analysis's API table is a functional checklist, not a security spec — ours is the security spec.**

---

## 8. Impact on Playwright-Learning-Development

Where the analysis genuinely strengthens the teaching goal, and where it's costly-but-low-yield:

**Strengthens (adopt the teaching value):**
- **Batch-vs-instant split** → teaches two different test shapes: batch (file upload, multi-item, partial rejection, delayed status) vs. instant (single item, timeout, reservation/restore). High Playwright value: file upload/download, polling, negative timeout paths. **This is the analysis's best educational contribution.**
- **Three-level batch rejection (file/bulk/transaction)** → teaches level-correct negative-path assertions. Good value, but `[P1]` (needs TransportFile).
- **camt-driven reconciliation** → teaches SQL validation + event-driven assertions. Already ours; the analysis reinforces it.
- **R-transaction pipeline (reject/return/recall)** → teaches state-machine negative paths and multi-message correlation. Already ours (case module); reinforced.

**Costly, low Playwright yield (do not build for testing's sake):**
- Full five-aggregate model → more mapping code, more fixtures, **zero additional Playwright technique** taught vs. our single aggregate.
- Six per-object FSMs → more state to seed, no new test *type*.
- TransportFile three-tier hierarchy in MVP → heavy fixture setup, teaches one negative-path variant already coverable more cheaply.
- Real liquidity/netting (the analysis correctly says don't build this) → agreed, reject.

---

## 9. Gaps in the Analysis (relative to our project)

The analysis is domain-complete but **project-incomplete** — it omits everything that makes our project *ours*:

- **Operator personas / real operator day** — absent. We have nine personas and cross-persona handoffs. The analysis models roles as a flat RBAC list, the exact weakness we already corrected.
- **Supervisor / fraud-analyst / reconciliation-analyst workflows** — absent. Ours are designed.
- **UI/UX consequences** — absent entirely (it's a backend analysis). Our nine-screen specs carry these.
- **Frontend security, BFF, step-up in the UI** — absent. Ours are frozen.
- **Test-data strategy, DB migrations, observability, walking skeleton / local dev** — absent. These are exactly our Iteration-0 gaps, and the analysis doesn't help close them.
- **Data masking / PII boundary** — absent in both (shared open gap — this and retention are the two genuinely unabsorbed items from the earlier prior-thread analysis).

None of these absences is a *flaw* in the analysis (they were out of its scope) — but they mean it **cannot be treated as a project plan**, only as a domain appendix.

---

## 10. Distortions & Risks

- **Scope-inflation risk (highest):** the five-aggregate model, if adopted, moves us measurably toward a simulated production bank — the exact drift our anti-goals forbid. **Primary risk to guard against.**
- **Naming-schism risk:** adopting its object names creates two vocabularies (its PaymentOrder/Instruction vs. our canonical payment) across frozen documents.
- **Unverified-detail risk (low, well-flagged):** the analysis is honest about what it can't confirm (STEP2 acronyms beyond the public page, proprietary CSM internals) — it does **not** fabricate. This is good discipline and matches ours. No PSF/PDF-type invention crept in.
- **"Start coding later" risk:** if we treat the 84-cell pipeline as a build order, Iteration 0 never starts. Must be explicitly demoted to reference.
- **Over-copying real systems:** the analysis leans hard on Bundesbank/STEP2 specifics (999 bulks, 100k messages). Accurate, but for us these are **glossary trivia, not requirements** — must be marked as such so nobody implements a 100k-message limit.

---

## 11. Concrete Documentation Corrections (paste-ready)

Only **three** small insertions are warranted. Everything else the analysis offers, we already have.

**(A) Into the main blueprint's glossary / domain-naming section — a synonym-and-boundary note:**

> `[ADD, domain-grounding]` **Standards vocabulary vs. our object names.** Our single canonical `payment.payments` aggregate corresponds to what SEPA/ISO 20022 literature calls, at different layers, a *payment order* (customer/operator intent) and a *payment instruction* (`CdtTrfTxInf`, the processed unit). We deliberately collapse these into one aggregate — the intent/instruction split is correct for a production bank and unnecessary for this educational platform. Grouping is expressed by `batch_id` (business/operational grouping; the SEPA-Clearer term is "bulk," which we do not adopt as an object name). The ISO *message* is never the business object: `egress.outbound_messages` (`OutboundMessage`) and `ingress` `InboundMessage` are the message envelopes, distinct from the payment. **"Interchange" is a card-scheme term and is never used here.** `ClearingSubmission` and `TransportFile` are recognized `[P1]` refinements (dispatch package and physical file), not MVP objects — their roles are covered in MVP by `egress` + `routing` and by `inbound_files`/`outbound_files` respectively.

**(B) Into the main blueprint, near the file-rail / instant-path sections — the one genuinely useful new rule:**

> `[ADD, domain-grounding][FREEZE]` **Rail-dependent grouping rule (explicit, testable).** Batch rails (our file channel, pain.001) group many instructions into one dispatch unit; instant rails are strictly **one payment = one message** (`NbOfTxs = 1`), never grouped. This is not a stylistic choice — it mirrors the real EPC inter-PSP rule (SCT Inst inter-PSP messages carry exactly one transaction) and it is a **named Playwright test target**: the batch path exercises multi-item upload + partial-rejection + delayed status; the instant path exercises single-item + timeout + reservation/restore. Neither path's grouping behavior may leak into the other.

**(C) Into the Playwright vision doc's coverage table — one row:**

> | Batch-vs-instant grouping `[ADD, domain-grounding]` | file-rail multi-item submission vs. instant single-item | pełne | — | średni→zaawansowany | must | brak — utrwala istniejący podział, nie dodaje domeny |

**Do NOT paste:** the five-aggregate model, the six-FSM expansion, the 14×6 pipeline as a build order, the generic REST endpoint table, or the module re-slicing. These conflict with frozen decisions or inflate scope.

---

## 12. Final Decision

**1. Adopt as-is:** the analysis's *research findings and terminology discipline* (three status tracks confirmation, Interchange-is-wrong, batch/bulk synonymy, VoP 5s, €100k-cap-removed, SCT-Inst-is-1:1) — as a **domain-reference appendix**, plus the three paste-ready inserts in §11.

**2. Adopt after correction:** the **rail-dependent grouping rule** (insert B) — the single most useful new thing, reframed as a testable invariant rather than a production requirement.

**3. Reject:** the five-aggregate model (`PaymentOrder`/`PaymentInstruction`/`PaymentBatch`/`ClearingSubmission`/`TransportFile` as new aggregates), the six-FSM expansion, the module re-slicing, and the generic REST endpoint table (BOLA regression risk). All either conflict with frozen ADRs or inflate scope with zero added Playwright value.

**4. Needs further research:** nothing blocking. The two honestly-flagged uncertainties (STEP2-side acronyms beyond the public page; proprietary CSM internals) are correctly out of scope — we simulate behavior, never internals.

**5. Must enter before Iteration 0:** only insert (B), the grouping rule — because it defines two distinct test shapes the walking skeleton's successor iterations target. Inserts (A) and (C) are documentation hygiene, not blockers.

**6. Can wait:** `ClearingSubmission` and `TransportFile` as `[P1]` refinements; the full R-transaction taxonomy; three-level batch rejection; any second rail profile.

**7. Minimal model with maximum educational value (the actual recommendation):** **keep exactly what we froze** — one canonical `payment.payments` aggregate, `batch_id` grouping, module-owned `OutboundMessage`/`InboundMessage`/`settlement_*`/`reconciliation_*`, five status axes, 4EV/VoP/fraud-hold, outbox/inbox, RLS, `uuidv7`, object-level `AuthorizationManager` — **plus the one explicit grouping rule.** That model already teaches SEPA concepts, multi-user (4EV), REST/SQL/Kafka testing, idempotency, audit, reconciliation, batch, and instant processing. The analysis's heavier model teaches **the same Playwright techniques with more mapping code** — which is the definition of overengineering for a test-learning project.

---

*End of review. `[NO-CODE]`. Bottom line: the analysis is an excellent domain reference and a poor build plan for us — keep its knowledge, keep our model. Three small inserts, one of them genuinely valuable; the rest already ours or deliberately out of scope.*
