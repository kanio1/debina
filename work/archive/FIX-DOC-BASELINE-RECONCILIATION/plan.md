# Plan

**task_id:** FIX-DOC-BASELINE-RECONCILIATION  
**title:** Read-only reconciliation of SEPA-2027-MASTER-PLANNING package vs current repo  
**lane:** FAST  
**source:** discovery — `work/imports/SEPA-2027-MASTER-PLANNING/`; Wave 11 conflict across HANDOFF / EPIC-26 / planning index / capability graph / program record / code  

## Goal

Reconcile the imported master-planning package against the current local branch without copying documents into canonical trees. Classify package claims as still true, stale, decision-blocked, or source-blocked. Resolve or precisely classify the Wave 11 contradiction first. Produce `promotion-matrix.md` and stop before any promotion copy.

## Allowed paths

- `work/ACTIVE.json`
- `work/QUEUE.md`
- `work/active/FIX-DOC-BASELINE-RECONCILIATION/`
- `work/imports/SEPA-2027-MASTER-PLANNING/` (read-only evidence)

## Steps

1. Capture local baseline: branch, HEAD SHA, working tree, distance from package baseline `f601089d2f123ff01f41378f47be5dd9ce361fd2`.
2. Classify Wave 11 across HANDOFF (baseline vs current), Wave 11 program, EPIC-26, planning index, capabilities/capability-graph, code/tests, and commits — without treating epic status as runtime proof.
3. Inventory package documents vs existing canonical paths; detect semantic collisions (`PRODUCT-VISION`, C4, Context Map, etc.).
4. Build promotion matrix with allowed statuses only; exclude copying package `QUEUE.md`, `NEXT-WORK.md`, and old active-task state.
5. End `SPEC_READY` with exactly one recommended next step; no canonical doc copy, no production/migration/contract edits, no formal story status flips.

## Verify commands

- `test -f work/active/FIX-DOC-BASELINE-RECONCILIATION/plan.md`
- `test -f work/active/FIX-DOC-BASELINE-RECONCILIATION/progress.md`
- `test -f work/active/FIX-DOC-BASELINE-RECONCILIATION/promotion-matrix.md`
- `python3 -c "import json; d=json.load(open('work/ACTIVE.json')); assert d['task_id']=='FIX-DOC-BASELINE-RECONCILIATION' and d['status']=='SPEC_READY'"`

## Approval

- required: no (FAST, documentation discovery only)
- decision approval path: n/a
- implementation approval path: n/a (no promotion in this task)
