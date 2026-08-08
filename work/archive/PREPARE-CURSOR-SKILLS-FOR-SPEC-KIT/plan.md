# PREPARE-CURSOR-SKILLS-FOR-SPEC-KIT — Plan

## Classification

- policy_profile: STANDARD
- phase: IMPLEMENTING
- approval_state: APPROVED (user chat 2026-07-29)
- write_scope: IMPLEMENTATION
- lane(compat): STANDARD

## Objective

Replace `.cursor/skills → ../.claude/skills` whole-directory symlink with a real directory of per-Skill bridges. Keep `.claude/skills` canonical. Do not install Spec Kit. Do not create `speckit-*`. Do not edit Skill contents. Do not touch Codex.

## Steps

1. Baseline inventory + collision check
2. Add `tools/agent-config/sync-cursor-skills.py` (+ fixture tests + ownership doc)
3. `--dry-run` then `--apply`
4. `--check`, idempotence, Spec Kit namespace fixture, rollback fixture
5. Baseline-aware verify + closeout → `INSTALL-SPEC-KIT-CURSOR-IN-DEBINA`
