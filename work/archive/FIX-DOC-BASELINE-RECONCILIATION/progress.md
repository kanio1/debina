# Progress

## Status

COMPLETED

Read-only reconciliation only. No formal Wave 11 status change. No documents promoted to canonical locations. Wave 11 / EPIC-26 / Story 26.4 were not marked completed by this task.

## Independent review

Result: PASS

- Promotion matrix is evidence-backed.
- No formal Wave 11 status was changed.
- No documents were promoted to canonical locations.
- The only next task is FIX-WAVE11-STATUS-HYGIENE.

## Findings

### Local vs package baseline

- Branch: `rebase/enterprise-evolution`.
- HEAD: `b7cae78bfc1dd2bb6176c26dd46c5dcdee9ba4be`.
- Package baseline: `f601089d2f123ff01f41378f47be5dd9ce361fd2` (`docs(governance): rebase enterprise payment constitution`).
- Local HEAD is ~98 commits ahead of that baseline on this branch. Working tree at discovery start: untracked `work/imports/` only (plus this task’s new files under `work/`).
- Current `HANDOFF.md` is about MCP runtime repair / UCS-SCT-002-C commit readiness — it no longer describes Wave 11. Package findings that cite HANDOFF as Wave 11-incomplete refer to the baseline-era handoff, not today’s file.

### Wave 11 contradiction — classified verdict

**One evidence-backed story, three inconsistent planning layers.**

| Layer | Claim | Verdict |
| --- | --- | --- |
| Code at HEAD | `PaymentIsoEvidenceQuery`, `IsoPaymentEvidenceReadModel`, Query-only GraphQL `paymentIsoEvidence`, BFF allowlist, Evidence Drawer ISO section | **TRUE** — surface present |
| Focused tests at HEAD | `PaymentLineageGraphQLTest`, `IsoPaymentEvidenceQueryIntegrationTest`, `ApprovalGraphQlRuntimeTest` | **TRUE** — tests present (this reconciliation did not re-run them) |
| Commits | `9d59080` feat checkpoint; `51b3c77` isolation tests; `bc21e97` pinned pain version; `d650334` planning “complete ISO evidence tranche” | **TRUE** — implementation + planning close commits exist |
| Wave 11 program record | Checkpoint 1 = implemented / not complete; Checkpoint 2 = live Keycloak+BFF+Spring+isolated PG JSON_DIRECT + signed pain.001 + focused 32/32; remaining mandatory proof still lists two full backend regressions + governance/db cleanup; **no Checkpoint 3** | **TRUE as durable evidence ceiling** |
| EPIC-26 Story 26.4 formal status | `in-progress`; tasks checked; note `[DONE WAVE-11]` claims live proof **and** “two full backend runs passed 540/540” | **MIXED** — live/focused evidence aligns with Checkpoint 2; **540/540 is not recorded in the Wave 11 program** (540 appears elsewhere as a later Dagger/Testcontainers regression figure, not as Wave 11 Checkpoint 3) |
| EPIC-26 epic frontmatter | `status: done` while 26.4 remains `in-progress` | **STATUS DEFECT** (also noted by `docs/governance/SEMANTIC-VALIDATION-SPEC.md`) |
| `planning/README.md` | EPIC-26 done; Stories 26.1–26.4 done; Wave 11 live proof | **OVERCLAIM** relative to formal story status and unfinished program remaining proofs |
| `planning/story-inventory.json` | 26.4 `in-progress` | **CONSISTENT** with epic story block, not with README |
| `capabilities.yaml` / `capability-graph.json` | 26.4 `blocked`; note “GraphQL doesn’t exist anywhere” | **STALE / FALSE** — GraphQL + Wave 11 surface exist; `gate.graphql-owner` is `RESOLVED` in the graph while `GRAPHQL-OWNER-DECISION.md` remains `OPEN` (second consistency defect) |
| Baseline `HANDOFF.md` @ `f601089` | Says live runtime + two full regressions unrun; do not call 26.4 done | **PARTLY STALE** — Checkpoint 2 documents live proofs already run; **still correct** that calling formal `done` was premature while remaining program proofs lack a durable checkpoint |
| Current `HANDOFF.md` | No Wave 11 content | **SUPERSEDED topic** — not evidence for or against Wave 11 completion |
| Enterprise Rebase program | No Wave 11 section; feature expansion paused through Phase F / Wave 12 evaluation | **TRUE** for pause; **silent** on Wave 11 status |
| Runtime evidence rule | Epic/index “done” ≠ runtime proof | Applied — README/EPIC done prose is **not** accepted as proof of 540/540 |

**Wave 11 classified outcome (not a formal status change):**

1. **Implementation delivered** for Story 26.4’s declared surface (code + focused tests + Checkpoint 2 live proof).
2. **Formal `done` is not justified** by the Wave 11 program’s own remaining mandatory proofs (no Checkpoint 3 for two full regressions / governance cleanup).
3. **Capability graph / capabilities.yaml `blocked` + “no GraphQL” is rejected as stale** against current repo.
4. **README / epic-level `done` overclaim** and **story `in-progress` + DONE note** need planning hygiene — do not silently flip statuses in this task.
5. Package F-010 / DR-001 correctly spotted the conflict; local evidence now **classifies** it. Closing as “Wave 11 completed” still needs an explicit human choice: either accept Checkpoint 2 + focused verify as enough, or require Checkpoint 3 before formal `done`.

### Package claim classes (summary)

| Still true (with local precedence) | Stale / needs update | Decision-blocked | Source-blocked |
| --- | --- | --- | --- |
| Non-bank / ADR-N15 boundary; ADR-N1…N17 / `[FREEZE]` | Wave 11 HANDOFF conflict text as stated at package time | DR-002…DR-008, DR-011–012 product/rail/jurisdiction | VOP YAML/EDS, STEP2 participant docs, final 2027 EPC, GAP-* in SOURCE-COVERAGE |
| Use-Case 2.0 / capability-first planning; `planning/` canonical | “76 epics” (local: **79** epic files) | DR-001 remaining human choice on formal Wave 11 close criteria | Bank MIG ≠ EPC; participant-only envelopes |
| Feature expansion paused through rebase Phase F | Package “Dagger semantic CI = PROPOSED” — local Phase D is complete/proven on this branch | Whether package synthesis paths become canonical vs merge into existing PRODUCT-VISION / BUSINESS-CAPABILITY-MAP / C4 | Corpus `validation_report.json` checksum mismatch |
| Query-only GraphQL; LedgerPort-only; five status axes | Package QUEUE/NEXT overlay proposals must not replace local `work/QUEUE.md` | — | — |
| SCT spine first before VOP/EDS/SDD implementation | Baseline-remote-only analysis; local branch has E1/UC2/Dagger progress beyond `f601089` | — | — |

### Non-copy rules applied

- Do not copy package `work/QUEUE.md`, `work/NEXT-WORK.md`, or package active-task state into live `work/`.
- Package MANIFEST lists QUEUE/NEXT, but this extract under `work/imports/.../work/` contains only `active/SEPA-2027-MASTER-PLANNING/` — treat MANIFEST QUEUE/NEXT as package-evidence-only / do-not-promote.
- Package is DRAFT synthesis, not approved architecture and not a compliance assessment.

## Completed steps

1. Created `work/ACTIVE.json` (FAST, SPEC_READY) and task directory.
2. Compared local HEAD to package baseline; inventoried package files.
3. Classified Wave 11 across all required sources.
4. Wrote `promotion-matrix.md` for every package document.
5. Updated `work/QUEUE.md` NOW to this task.
6. Independent review PASS recorded; task closed and archived (closeout only).

## Verify results

- Artifact files present under task directory (active then archive).
- No canonical `docs/` or `planning/` copies performed.
- No production code, migrations, contracts, or formal story status edits.
- Independent review: PASS.

## Next step

`FIX-WAVE11-STATUS-HYGIENE`

## Blockers

- Human decision still required to accept Checkpoint 2 as formal completion vs require Checkpoint 3 (package DR-001).
- External SEPA 2027 corpus gaps (VOP/EDS/STEP2/final 2027) remain source-blocked for any 2027 implementation claims.
- Package QUEUE/NEXT files listed in MANIFEST are absent from this extract; do not reconstruct them into live `work/`.
