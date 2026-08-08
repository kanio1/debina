# Adoption recommendation

## Verdict

```text
ADOPT_CURSOR_WITH_LAYOUT_CHANGE
```

## Why not WITHOUT_LAYOUT_CHANGE

Current Debina layout `.cursor/skills -> ../.claude/skills` is **not** safe with Spec Kit:

- Spec Kit writes/removes managed `speckit-*` under `.cursor/skills`
- Directory symlink causes those operations to hit `.claude/skills`
- Experiment B confirmed `WHOLE_DIRECTORY_SYMLINK_UNSAFE`

## Why not REJECT

- Clean `cursor-agent` install works (v0.14.3, no `--force`)
- Multi-install safe = yes
- Per-skill bridges coexist with `speckit-*`
- Upgrade blocks modified managed files without `--force`
- Uninstall preserves Debina sentinel bridge and modified managed files
- No Rules/hooks takeover in Cursor integration
- Generic SDD engine is real (skills + bundled workflow)

## Why not PILOT_BLOCKED

Blockers that remain are **non-fatal for adoption decision**:

- Cursor Agent runtime skill listing needs manual login/reopen (`CURSOR_RUNTIME_DISCOVERY_REQUIRES_MANUAL_REOPEN`)
- Full agent-driven specify→implement marked `WORKFLOW_RUNTIME_TEST_PARTIAL`
- Main-repo `verify-fast` fails on pre-existing HANDOFF/CLAUDE/maven issues outside pilot scope

These do not overturn filesystem/layout safety evidence.

## Required before Debina install

1. Migrate `.cursor/skills` from whole-directory symlink to **per-Skill bridges**
2. Keep Spec Kit ownership limited to `speckit-*`
3. Keep Debina Rules (`00-core`…`40-testing`) and Hooks authoritative
4. Plan non-empty init carefully (CLI wants `--force` for merge; Debina must not use blind `--force`)

## Next task (exactly one)

```text
PREPARE-CURSOR-SKILLS-FOR-SPEC-KIT
```

Do **not** start that task in this closeout. Do **not** install Spec Kit in Debina yet.
