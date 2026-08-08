# Runtime Binding Evidence — Phase A

## Harness write_gate behavior (discovered)

During `ANALYZING` + `WORK_ONLY`, `tools/agent_policy/write_gate.py` allows writes **only** to:

- `work/active/<ACTIVE_TASK>/**`
- `work/archive/**` (wrapper closeout only)

`allowed_paths` in `ACTIVE.json` (including `specs/**`, `.specify/feature.json`) are **ignored** until `IMPLEMENTING` + approval.

**Impact**: Feature artifacts placed under `work/active/PILOT.../specs/001-markdown-h1-checker/` instead of canonical `specs/001-markdown-h1-checker/`.

## Runtime binding matrix

| Constraint | Hooks | Constitution | Templates | Skill | AGENTS | Prompt | Mechanically enforced |
|------------|------:|-------------:|----------:|------:|-------:|-------:|----------------------:|
| Active Debina task | partial | yes | yes | no | yes | yes | HOOK_ENFORCED (work/active only in ANALYZING) |
| Approval before impl | yes | yes | yes | partial | yes | yes | HOOK_ENFORCED (IMPLEMENTING gate) |
| allowed_paths | partial | yes | yes | no | yes | yes | HOOK_ENFORCED (impl phase only) |
| Policy profile STANDARD | no | yes | yes | no | yes | yes | DOCUMENTED_ONLY (analysis phase) |
| Cross-stack analysis | no | yes | yes | partial | no | yes | TEMPLATE_ENCODED |
| Verification profile | no | yes | yes | partial | yes | yes | DOCUMENTED_ONLY |
| No commit/push | yes | yes | yes | partial | yes | yes | HOOK_ENFORCED + PROMPT_DEPENDENT |
| No auto-start next feature | no | yes | no | partial | yes | yes | DOCUMENTED_ONLY |
| No impl before approval | yes | yes | yes | partial | yes | yes | HOOK_ENFORCED |
| Task-state precedence | partial | yes | yes | no | yes | yes | CONSTITUTION + HOOKS |
| Spec Kit feature ≠ task authority | no | yes | yes | no | yes | yes | DOCUMENTED_ONLY |
| specs/** in analysis phase | **blocked** | implied | n/a | expects root | n/a | yes | **GAP — not enforced as allowed** |

### Enforcement classification summary

| Class | Constraints |
|-------|-------------|
| HOOK_ENFORCED | impl approval, impl paths, no shell bypass, work/active in ANALYZING |
| TEMPLATE_ENCODED | governance fields in spec/plan/tasks templates |
| DOCUMENTED_ONLY | constitution precedence, no auto-next-feature, feature metadata |
| PROMPT_DEPENDENT | no commit, clarify assumptions, blocked tasks T002–T008 |
| **GAP** | `allowed_paths` for `specs/**` during ANALYZING |

## Branch protection

- speckit-specify: branch creation optional via hooks only; none configured
- Feature tracked via `.specify-feature.json` mirror (canonical path blocked)
- Branch `rebase/enterprise-evolution` unchanged

## Spec Kit Skills runtime

All five skills executed via agent following SKILL.md workflows (not slash-invoked in IDE).

## Preset verdict

**PRESET_RECOMMENDED** — Debina template customizations not in speckit.manifest hashes; upgrade risk MEDIUM.

## Workflow overlay verdict

**WORKFLOW_OVERLAY_REQUIRED** — Need harness alignment for:

1. `allowed_paths` during ANALYZING/SPEC_READY for `specs/**` and `.specify/feature.json`
2. Optional: `work/ACTIVE.json` injection into Spec Kit skill context
3. Phase transition ANALYZING → SPEC_READY → IMPLEMENTING with approval gate
