# HANDOFF

## Current objective

`FIX-CURSOR-HOOK-PHASED-APPROVAL` closed PASS. Queue target is `PILOT-SPEC-KIT-CURSOR-ONLY` (disposable dir outside main repo). Do not install Spec Kit into the main tree. Do not start Design Council implementation.

## Current use case

none

## Current state

```yaml
workflow_phase: READY_TO_PLAN
active_story: "none"
active_use_case: "none"
active_slice: "none"
readiness_verdict: NOT_APPLICABLE
last_completed_step: "FIX-CURSOR-HOOK-PHASED-APPROVAL archived PASS; Spec Kit readiness PASS_WITH_FINDINGS"
last_verification: "hook synthetic 37 PASS; health-check PASS; verify-fast PASS; py_compile PASS"
working_tree: modified
next_action: "Run isolated Cursor-only Spec Kit pilot outside main working tree. Do not touch .cursor/skills yet."
```

- Archives: `work/archive/PREPARE-SPEC-KIT-INSTALLATION-READINESS/`, `work/archive/FIX-CURSOR-HOOK-PHASED-APPROVAL/`.
- Hooks enforce `policy_profile` / `phase` / `approval_state` / `write_scope` via `tools/agent_policy`.
- Design Council remains `ON_HOLD_PENDING_SPEC_KIT_EVALUATION`.

## Completed

- Spec Kit readiness audit: `READY_FOR_ISOLATED_PILOT` with findings F-01..F-05.
- Cursor phased-approval hook fix: STANDARD analysis WORK_ONLY without `.approved`; implementation requires approval; Shell write bypass blocked; wrappers for bootstrap/approve/set-state/closeout.
- Prior: FIX-WAVE11-STATUS-HYGIENE archived after review PASS.

## Decisions

- `policy_profile` is rigor; `phase`/`write_scope` control write class; `lane` is compat mirror only.
- `.approved` means implementation approval only.
- Spec Kit not installed; Skills layout unchanged pending pilot.

## Blocked for production

- Full EPC TVS / scheme certification; participant-only STEP2/VOP/EDS artefacts
- Formal Wave 11 `done` until Checkpoint 3 or explicit human acceptance of Checkpoint 2 (DR-001)
- SEPA 2027 corpus gaps (final rulebooks, VOP YAML, EDS, checksum mismatch)
- Remote CI / `act` / deployment automation; Dagger Cloud

## Next tasks

1. **Owner: harness** — Isolated `PILOT-SPEC-KIT-CURSOR-ONLY` in disposable directory.
   **Done when:** Cursor integration inventoried; skills collision behavior documented; no main-tree install.
   **Verify:** pilot report exists; main repo has no `.specify/` / `speckit-*`.

2. **Owner: human** — After pilot, decide Spec Kit adoption and Skills Variant B prep.
   **Done when:** decision recorded.
   **Verify:** written decision under work/approvals or governance note.

3. **Owner: discover** — Resume `IMPLEMENT-PORTABLE-DEBINA-DESIGN-COUNCIL` only after Spec Kit evaluation decision.
   **Done when:** hold lifted intentionally.
   **Verify:** QUEUE no longer lists ON_HOLD for that reason.

4. **Owner: human** — Decide DR-001 (Checkpoint 2 vs Checkpoint 3 as formal Wave 11 close) when ready.

## Resume from here

Start `PILOT-SPEC-KIT-CURSOR-ONLY` in a disposable directory/worktree outside the main Debina working tree. Do not modify `.cursor/skills` or install Spec Kit into the main repo yet.
