# Cursor Skills ownership

## Canonical content

```text
.claude/skills/**
→ canonical Debina Skill content
```

Do not edit Skill body via Cursor bridges. Bridges are pointers only.

## Cursor bridges

```text
.cursor/skills/<Debina Skill>
→ per-Skill symlink to ../../.claude/skills/<same-name>
→ DEBINA_MANAGED
```

## Spec Kit namespace

```text
.cursor/skills/speckit-*
→ reserved for Spec Kit ownership (real directories)
→ SPEC_KIT_MANAGED
```

Debina sync (`tools/agent-config/sync-cursor-skills.py`) never creates, deletes, or edits `speckit-*`.

## Forbidden layout

```text
whole-directory symlinks for platform-managed Skills roots
→ forbidden
```

Example of forbidden layout: `.cursor/skills -> ../.claude/skills`.

## Rules

- Do not place a Debina Skill under a `speckit-*` name.
- Do not edit a Spec Kit-managed Skill as if it were Debina-owned.
- Keep `.claude/skills` as the only canonical Debina Skill source for this layout.

## Commands

```bash
python3 tools/agent-config/sync-cursor-skills.py --check
python3 tools/agent-config/sync-cursor-skills.py --dry-run
python3 tools/agent-config/sync-cursor-skills.py --apply
python3 tools/agent-config/test_sync_cursor_skills.py
```
