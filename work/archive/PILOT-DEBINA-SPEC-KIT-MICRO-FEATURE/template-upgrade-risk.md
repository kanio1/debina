# Template Customization Upgrade Risk

**Date**: 2026-07-29

## Manifest tracking (speckit.manifest.json v0.14.3)

Tracked templates (baseline hashes at install):

| Template | In manifest | Current state |
|----------|-------------|---------------|
| plan-template.md | yes | **locally modified** (Debina governance sections) |
| spec-template.md | yes | **locally modified** |
| tasks-template.md | yes | **locally modified** |
| checklist-template.md | yes | **locally modified** |
| constitution-template.md | yes | unchanged (active constitution is `.specify/memory/constitution.md`) |

## Assessment

1. **Install manifest tracks templates** — yes, with SHA-256 at install time
2. **Modified vs v0.14.3** — yes; hashes no longer match manifest
3. **`specify upgrade` without `--force`** — likely **blocked** or would skip/ warn on modified files
4. **Deterministic reproduction** — yes, via `work/archive/CONFIGURE-DEBINA-SPEC-KIT-GOVERNANCE/synchronization-evidence.md` and git diff
5. **Preset migration** — recommended to preserve Debina governance blocks across upgrades

## Verdict

```text
CURRENT_TEMPLATE_CUSTOMIZATION_UPGRADE_RISK: MEDIUM
```

Rationale: Templates are Spec Kit-managed and hash-tracked, but locally customized without preset manifest. Upgrade requires `--force` or preset packaging of Debina overlay blocks.

## Preset verdict

**PRESET_RECOMMENDED** (not REQUIRED yet — documented local mods sufficient for pilot)

## Workflow overlay verdict

**WORKFLOW_OVERLAY_REQUIRED** (see runtime-binding-evidence.md)
