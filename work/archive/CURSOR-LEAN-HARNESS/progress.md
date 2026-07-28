# Progress — CURSOR-LEAN-HARNESS

## Status

DONE

## Findings

- Branch: `rebase/enterprise-evolution` @ `021aa8d28510d546a8848f0bc12d427bff707f69`
- Pre-existing dirty tree: only untracked `.cursor/permissions.json` (IDE allowlist; permissive vs lean deny-first).
- `.agents/skills -> ../.claude/skills` and `.cursor/skills -> ../.claude/skills` are symlinks (no physical skill duplicates).
- `tools/agent/` and `work/` did not exist.
- Cursor IDE `3.13.10`; hooks format from create-hook skill: `hooks.json` version 1; shell gating prefers `beforeShellExecution`; `preToolUse` still valid for Write/Delete.
- Hygiene validator hardcodes `UNIVERSAL_ALWAYS_APPLY` for `00-project-operating-model.mdc` + `00-cursor-workflow.mdc` — must update when replacing with `00-core.mdc`.
- Multiple skills/registry entries reference old rule paths (`25-handoff`, `60-agent-instruction-hygiene`, etc.).
- session-handoff routing pressure fixture currently requires HANDOFF even when no code changed — conflicts with lean handoff policy.
- MCP: no project-local Serena/NotebookLM config found; user CLI config has minimal allowlist; home `~/.cursor/cli-config.json` exists.
- Protected javadoc hash (pre): `29cdd24c04f7d3ee567d6aac013b12858647fd21aaa56daa1a1859390e1956fb`
- Backup: `/tmp/debina-cursor-harness-backup-20260727172713/`

## Log

- [x] Read-only audit + backup
- [x] work/ overlay + templates
- [x] AGENTS lean section
- [x] five rules + hygiene allowlist
- [x] skills (3 edits + 4 new) + registry/fixtures
- [x] hooks + wrappers + cli.json
- [x] manual setup + validation + archive
