# HANDOFF

## Current objective

Commit the agent instruction-hygiene tooling tranche after independent review
fixes.

## Current use case

none

## Current state

```yaml
workflow_phase: READY_TO_COMMIT
active_story: none
active_use_case: none
active_slice: none
readiness_verdict: NOT_APPLICABLE
last_completed_step: instruction-hygiene HIGH fix + governance wiring
last_verification: "validate-agent-instruction-hygiene(+self-test) PASS; validate-governance PASS; validate-all-skills PASS; changed-files mode PASS; dagger call fast PASS (hygiene+self-test composed); MODEL-BEHAVIOR NOT_EXECUTED"
working_tree: modified
next_action: create one coherent instruction-hygiene commit when the user asks
```

- Previous readiness/pipeline tranche is committed (`f2d25b3`); not reopened.
- New hygiene validator, scoped Cursor rule, thin skill, routing evals, and
  governance/Dagger composition via existing enterprise governance script.
- Nothing staged.
- Remaining MEDIUM: eval YAML not content-scanned (rule loads for edits; AIR
  checks target instruction files); optional extra fixtures for AIR-005/012/013;
  AIR-008 may need nuance for doc-only secret+URL lines.

## Completed

- `PRE_PLAN_READINESS` / `PIPELINE_IMPACT` / HANDOFF checkpoints (prior commit).
- Instruction hygiene: AIR-001…015 validator, fixtures via ephemeral self-test,
  rule `60-agent-instruction-hygiene.mdc`, skill + registry + routing evals.
- HIGH review fix: prohibition markers no longer suppress later imperatives;
  mixed-line `reject …; curl | sh` self-test expects AIR-009.
- Enterprise governance now runs hygiene + `--self-test` (Dagger `fast` path).
- Governance container installs distro `python3-yaml` so enterprise validators
  can run inside the Go toolchain image (finite validation-only install).
## Decisions

- Inspiration from awesome-cursorrules limited to frontmatter/changed-files/
  hygiene ideas; Debina policy remains authoritative; no framework rule copy.
- Hygiene rule is never `alwaysApply`.
- No new Dagger root callable; compose through existing governance.
- Thin `agent-instruction-hygiene` skill exists only for routing/procedure.

## Blocked for production

- Dated specialist approvals / review councils / canonical migration admission
- Remote CI provider selection, GitHub Actions, `act`, deployment/release automation
- Dagger Cloud / persistent smoke-state volumes (forbidden)
- Implicit model-routing assurance (`MODEL-BEHAVIOR: NOT_EXECUTED`)

These are `BLOCKED_FOR_PRODUCTION` and must not block safe local educational
implementation.

## Next tasks

1. Create one coherent instruction-hygiene commit when the user asks.
2. Optionally confirm `dagger call fast` completed green for this session.
3. Select the next educational product slice.
4. Later: add AIR-005/012/013 and `.env.example` fixtures; decide whether eval
   YAML should be content-scanned.
5. When a safe model-routing evaluator exists, execute routing behaviour proofs.

## Resume from here

create one coherent instruction-hygiene commit when the user asks
