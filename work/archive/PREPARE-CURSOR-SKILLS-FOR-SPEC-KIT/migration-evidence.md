# Migration evidence

## Dry-run

```text
eligible Skills: 33
DRY-RUN: would replace whole-directory symlink with real directory + per-Skill bridges
DRY-RUN: would not create speckit-*
DRY-RUN: would not modify .claude/skills contents
exit 0
```

## Apply

```text
python3 tools/agent-config/sync-cursor-skills.py --apply
→ apply: OK — 33 per-Skill bridges; .claude/skills untouched
exit 0
```

First sandbox attempt failed with `Read-only file system` on staging under `.cursor/`; original whole-directory symlink restored (`MIGRATION_ROLLED_BACK`). Retry outside sandbox succeeded. No partial layout left.

## Post-layout

| Check | Result |
|---|---|
| `.cursor/skills` is directory | yes |
| `.cursor/skills` is symlink | no |
| per-Skill bridges | 33 symlinks |
| real dirs under `.cursor/skills` | none |
| broken links (`find -xtype l`) | none |
| `speckit-*` created | none |

## Sample bridges

```text
.cursor/skills/session-handoff -> ../../.claude/skills/session-handoff
.cursor/skills/planning-semantic-integrity -> ../../.claude/skills/planning-semantic-integrity
.cursor/skills/technical-architect -> ../../.claude/skills/technical-architect
```

## Source hashes

All 33 baseline hashes unchanged (`hash-bridge-evidence.json`).

## Skills skipped

None.

## Namespace collisions

None.

## Tooling added

- `tools/agent-config/sync-cursor-skills.py`
- `tools/agent-config/test_sync_cursor_skills.py` (7 fixture tests PASS)
- `tools/agent-config/CURSOR-SKILLS-OWNERSHIP.md`
