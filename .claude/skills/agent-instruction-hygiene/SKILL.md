---
name: agent-instruction-hygiene
description: Use when creating or editing Debina AGENTS.md, CLAUDE.md, Cursor rules, skills, skills-registry entries or skill routing evals; enforces instruction ownership, frontmatter, Unicode safety, prompt-safety and registry integrity via the hygiene validator.
---

# Agent instruction hygiene

## When used

Editing `.cursor/rules/**`, `.claude/skills/**`, `**/AGENTS.md`, `**/CLAUDE.md`,
`planning/skills/skills-registry.yaml`, or `tools/skills/evals/**`.

Do not use for ordinary Java/Kotlin/TypeScript payment implementation.

## Workflow

1. Check overlapping AGENTS / rules / skills before editing; prefer references.
2. Keep universal rules short; keep specialist rules scoped.
3. Preserve positive and negative routing boundaries.
4. Run:

```bash
python3 tools/agent-config/validate-agent-instruction-hygiene.py
python3 tools/agent-config/validate-agent-instruction-hygiene.py --self-test
bash tools/skills/validate-all-skills.sh
```

When only a subset changed, pass an explicit newline-delimited file list via
`--changed-files` (caller-provided path; not a repository source of truth).

Follow `.cursor/rules/60-agent-instruction-hygiene.mdc`.

## CORRECT / WRONG

CORRECT: reference existing AGENTS/rules/skills, then run the hygiene validator

WRONG: duplicate long procedures into root AGENTS or invent an alwaysApply broad security rule
