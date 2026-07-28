---
title: "Debina SEPA 2027 Master Plan"
document_id: DMP-MASTER-PLAN-001
status: DRAFT
document_role: WORKING_PLAN
baseline_repository: kanio1/debina
baseline_branch: main
baseline_commit: f601089d2f123ff01f41378f47be5dd9ce361fd2
created_at: 2026-07-27
last_reviewed_at: 2026-07-27
owner: project-owner
canonical_sources:
  - planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md
  - docs/product/roadmap.md
  - docs/product/documentation-backlog.md
  - work/active/SEPA-2027-MASTER-PLANNING/findings.md
supersedes: []
does_not_supersede:
  - AGENTS.md
  - README.md
  - accepted ADRs
  - planning/epics
  - planning/capability-graph.json
  - approved Use-Case 2.0 records
confidence: medium
---

# 1. Purpose

Przełożyć istniejący enterprise rebase na wykonalne, małe fazy przygotowujące pierwszy bezpieczny implementation slice.

# 2. Scope

Governance, evidence, product, architecture, UC2, smoke, semantic validation, backlog pilot i delivery handoff.

# 3. Non-goals

- wielki jednorazowy redesign;
- implementacja wszystkich 2027 capabilities;
- wieloagentowe równoległe zapisywanie kodu;
- automatic merge/push.

# 4. Current verified facts

Feature expansion pozostaje paused do spełnienia rebase completion criteria.

# 5. Assumptions and open questions

Plan wymaga human decisions z `decisions-required.md` i lokalnej weryfikacji baseline.

# 6. Main content

## Phase 0 - Baseline reconciliation

**Objective:** Zgodny lokalny i zdalny stan

**Inputs:** GitHub baseline, local git status, findings

**Tasks:** Porównaj SHA; rozstrzygnij Wave11; zweryfikuj helper docs

**Outputs:** Accepted baseline note, updated handoff plan

**Validation:** git/docs checks

**Decision gates:** DR-001

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** Nie zmieniać kodu.

## Phase 1 - Evidence integrity and traceability

**Objective:** Wiarygodny source baseline

**Inputs:** Corpus, source register, checksums

**Tasks:** Wyjaśnij hash; pobierz public artefacts; classify access; pilot claim map

**Outputs:** Validated source metadata, gap register

**Validation:** hash/schema/link validation

**Decision gates:** Licence/access decisions

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** Bez participant reconstruction.

## Phase 2 - Product/capability acceptance

**Objective:** Jasny product scope i priority boundaries

**Inputs:** Vision, capability map, decisions

**Tasks:** Review P0/P1/P2, VOP/EDS/SDD/rails

**Outputs:** Accepted synthesis or change requests

**Validation:** Cross-doc review

**Decision gates:** DR-002..006

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** Bez stories/kodu.

## Phase 3 - Architecture evidence pack

**Objective:** Spójny current/target/C4/lifecycle

**Inputs:** Architecture docs and existing methods

**Tasks:** Review boundaries; add deployment + selected dynamics; ATAM-lite

**Outputs:** Reviewed evidence pack

**Validation:** Mermaid/link/ADR consistency

**Decision gates:** Module admissions

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** Bez split/merge.

## Phase 4 - Top UC2 catalogue

**Objective:** 3 najważniejsze flows z pełnym trace

**Inputs:** Existing flows/epics/tests

**Tasks:** Model business/system UCs, slices, rules, examples, quality

**Outputs:** Accepted UC cohort

**Validation:** UC validators/manual review

**Decision gates:** Business decisions

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** Bez mass backfill.

## Phase 5 - Smoke preparation

**Objective:** Mały repeatable runtime gate

**Inputs:** ADR-N16, current runtime proofs

**Tasks:** Specify API/runtime smoke, synthetic fixtures, logs

**Outputs:** Smoke blueprint/wrapper plan

**Validation:** Local dry run

**Decision gates:** Scope approval

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** Bez Playwright w tym pakiecie.

## Phase 6 - Semantic governance and planning pilot

**Objective:** Mechaniczna ochrona spójności

**Inputs:** UC cohort, capability graph

**Tasks:** Add validators gradually; regenerate inventories; queue overlay

**Outputs:** Green semantic checks

**Validation:** existing/new validator commands

**Decision gates:** Tooling approval

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** Bez framework platform.

## Phase 7 - Backlog migration pilot

**Objective:** Wybrane stories traced do slices

**Inputs:** Cohort and epics

**Tasks:** Update only selected records; preserve history/status semantics

**Outputs:** Validated migration cohort

**Validation:** story/capability validators

**Decision gates:** No open blocker

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** Bez cosmetic rewrite.

## Phase 8 - First READY implementation slice

**Objective:** Jedna mała autonomiczna dostawa

**Inputs:** Approved UC slice, plan, verify

**Tasks:** Single writer implementation, tests, review, next-work

**Outputs:** Diff and runtime evidence

**Validation:** targeted/full gates

**Decision gates:** HUMAN_APPROVED

**Stop criteria:** output exists, review complete, no hidden blocker.

**Non-goals:** No auto push/merge.

## Operating model

```text
Cursor/ChatGPT discovery and documentation
        -> HUMAN_APPROVED
Codex/Cursor single implementation writer
        -> targeted/full verification
Independent read-only review
        -> human diff review
Next Work Review
```

## WIP limits

- jedna aktywna faza delivery;
- jeden implementation writer;
- do dwóch read-only research/review agents;
- maksymalnie jeden READY slice.

# 7. Relationships to existing artifacts

Plan implementuje istniejący rebase program; nie zastępuje jego faz ani planning backlogu.

# 8. Risks

- pominięcie local baseline check;
- documentation work bez acceptance;
- zbyt duży pierwszy UC cohort;
- automation przed stabilnymi conventions;
- source gaps ukryte w assumption.

# 9. Evidence and canonical sources

Rebase program, findings, roadmap, documentation backlog, source coverage.

# 10. Review triggers

Zakończenie fazy, decyzja użytkownika, zmiana baseline, blocker, final source publication.
