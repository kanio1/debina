# FIX-WAVE11-STATUS-HYGIENE — Plan

## Classification

- Lane: FAST
- Status: SPEC_READY
- Approval required: no
- Nature of work: canonical documentation and planning status hygiene
- Business behavior change: none
- Planning schema change: none
- Definition-of-done change: none

## Objective

Align canonical Wave 11 status claims to the evidence currently available.

Target semantic outcome:

- implementation delivered;
- focused tests delivered;
- Checkpoint 2 proven;
- Checkpoint 3 pending;
- remaining runtime proofs open;
- formal completion not reached.

These are descriptive semantics, not new status enum values.

## Decision escalation gates

Stop and reclassify the task as DECISION if implementation would require:

- waiving Checkpoint 3;
- changing the definition of `done`;
- closing Story 26.4 without required proofs;
- changing the Wave 11 evidence model;
- adding a new planning status;
- changing Checkpoint 3 acceptance criteria.

## Source-of-truth ordering

1. Current code, contracts and executable tests.
2. Runtime and checkpoint evidence.
3. Accepted ADRs and frozen decisions.
4. Enterprise Rebase and Wave 11 programs.
5. Epics, stories and capability inventory.
6. Synthetic planning views.
7. Historical baseline and staged master-planning documents.

An epic `done` status is not runtime evidence.

## Planned changes

### 1. Wave 11 program

Clarify that:

- Checkpoint 2 is proven;
- Checkpoint 3 is pending or undocumented;
- remaining evidence includes the required full backend runs and governance/database cleanup;
- formal completion has not been reached;
- the `540/540` claim is not a substitute for Wave 11 Checkpoint 3.

### 2. EPIC-26

- Change epic frontmatter from `done` to `in-progress`.
- Preserve Stories 26.1–26.3 as completed where currently supported.
- Preserve Story 26.4 as `in-progress`.
- Replace language suggesting complete Wave 11 closure.
- Scope the `540/540` evidence correctly.
- Mark historical “GraphQL does not exist” claims as superseded.

### 3. Planning README

Replace claims that EPIC-26 and Stories 26.1–26.4 are fully completed with language reflecting:

- implementation delivery;
- Checkpoint 2 proof;
- open Checkpoint 3 and remaining proofs;
- lack of formal completion.

### 4. Capabilities source

In `planning/capabilities.yaml`:

- change Story 26.4 from `blocked` to `in-progress`;
- remove the current claim that GraphQL does not exist;
- describe remaining runtime proofs and formal non-completion;
- do not mark the capability `done`.

### 5. Capability graph

- Synchronize graph files with `capabilities.yaml`.
- Do not invent a missing generator.
- Follow the repository’s documented co-maintenance process.
- Validate with `validate-capability-graph.py`.
- Update `.mmd` only if required by the existing graph maintenance rules.

### 6. Story inventory

Run the existing story inventory generator with its documented write mode.

Then run the story inventory validator.

### 7. Planning semantic validators

First determine whether correcting the source documents is sufficient.

Only change a validator if it currently permits the concrete contradiction:

- epic `done`;
- required child story unfinished.

Any validator change must:

- implement the existing SV-02 semantics;
- introduce no new statuses;
- avoid imposing unsupported rules on unrelated epics;
- include an appropriate validator self-test or fixture if the repository convention requires it.

## Explicitly out of scope

- product code;
- domain tests;
- migrations;
- OpenAPI and AsyncAPI;
- ADRs;
- Checkpoint 3 criteria;
- GraphQL owner decision resolution;
- master-planning promotion;
- Enterprise Rebase scope changes;
- new planning schema or enum values.

## Validation plan

1. Validate `work/ACTIVE.json`.
2. Validate story inventory.
3. Validate capability graph.
4. Run planning semantic validation.
5. Run affected agent-configuration validators if modified.
6. Run `verify-fast`.
7. Run `git diff --check`.
8. Review the diff against `allowed_paths`.
9. Confirm no staged master-planning document changed.
10. Confirm Story 26.4 remains non-terminal.

## Completion conditions

The implementation phase may proceed only when:

- no DECISION gate has been triggered;
- all planned source files have been identified;
- the graph maintenance process has been confirmed;
- the `540/540` claim has a verifiable source and limited scope;
- the implementation does not require planning schema expansion.
