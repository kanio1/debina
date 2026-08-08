# Cursor integration analysis

## Integration key

`cursor-agent` (Name: Cursor)

## Observed properties (v0.14.3)

| Property | Value |
|---|---|
| CLI Required | no (IDE) |
| Multi-install Safe | yes |
| Default after clean init | yes (`installed (default)`) |
| Skills path | `.cursor/skills/speckit-*` |
| Rules path | not created |
| Hooks path | not created |
| AGENTS.md | not created |
| Constitution | `.specify/memory/constitution.md` |
| Manifest | `.specify/integrations/cursor-agent.manifest.json` |
| Bundled workflow | Full SDD Cycle (`speckit`) — specify → plan → tasks → implement (+ gates) |

## Skills / command names generated

- `/speckit-constitution`
- `/speckit-specify`
- `/speckit-clarify`
- `/speckit-plan`
- `/speckit-tasks`
- `/speckit-implement`
- `/speckit-analyze`
- `/speckit-checklist`
- `/speckit-converge`
- `/speckit-taskstoissues`

## Spec Kit extension hooks (static)

Skills optionally read `.specify/extensions.yml` for `hooks.before_*`. These are **Spec Kit extension hooks**, not Cursor `.cursor/hooks`. Debina Cursor hooks remain separate and must stay authoritative.

## Debina policy mapping (target)

```text
Cursor Rules  → routing / context (untouched by this integration)
Spec Kit Skills → generic SDD workflow (speckit-*)
Debina Skills → domain knowledge (debina-*)
Hooks → security / write gates (Debina-owned)
```

## Conflict with current Debina layout

Debina today: `.cursor/skills -> ../.claude/skills` (whole-directory symlink).

Spec Kit writes physical `speckit-*` under `.cursor/skills`. Through a directory symlink those writes land in `.claude/skills`, mixing Spec Kit managed files into the Debina canonical skill tree. Experiment B confirmed `WHOLE_DIRECTORY_SYMLINK_UNSAFE`.

## Init into non-empty tree

Without `--force`, `specify init --here` on a non-empty directory fails and asks for confirmation / `--force`. Debina install later must plan a non-destructive merge strategy; pilot did **not** use `--force`.
