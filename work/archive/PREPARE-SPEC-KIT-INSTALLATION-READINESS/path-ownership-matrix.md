# Path Ownership Matrix

| Path | Current owner | Physical target | Future owner | Spec Kit collision | Action |
| --- | --- | --- | --- | --- | --- |
| `.claude/skills/**` | Debina | physical dirs (33 skills) | `DEBINA_CANONICAL` (or migrate per Variant B) | Claude/Spec Kit may also write here if Claude integration used | Never let Spec Kit Cursor/Codex install write through symlinks into this tree |
| `.agents/skills` | Debina bridge | symlink → `.claude/skills` | Per-skill Debina bridges + Spec Kit `speckit-*` | **HIGH** — Codex integration installs here | Before in-tree install: replace whole symlink with real dir |
| `.cursor/skills` | Debina bridge | symlink → `.claude/skills` | Per-skill Debina bridges + Spec Kit `speckit-*` | **HIGH** — Cursor integration installs here | Before in-tree install: replace whole symlink with real dir |
| `.codex/skills` | Debina bridge | symlink → `.claude/skills` | Optional Codex mirror / remove if `.agents` used | Medium | Align with Codex discovery model; avoid third whole symlink |
| `tools/codex/.agents/skills` | Debina Codex entry bridge | symlink → `.claude/skills` | Keep as bridge to canonical Debina skills | Medium if Spec Kit also uses `.agents` from other cwd | Pilot with `codex --cd tools/codex` |
| `.specify/` | absent | n/a | Spec Kit | None today | Create only after adoption decision, via pilot first |
| `specs/` | absent | n/a | Spec Kit feature artifacts | None today | Same |
| `.specify/memory/constitution.md` | absent | n/a | Thin Spec Kit adapter → AGENTS/ADRs | Overwrite risk if thick | Draft as pointer-only; never copy full constitution |
| `.cursor/rules/*.mdc` | Debina / Cursor | physical | Cursor routing adapter | Spec Kit may add `specify-rules.mdc` | Allow Spec Kit rule file; keep Debina rules thin |
| `.cursor/hooks.json` + `.cursor/hooks/**` | Debina / Cursor | physical | Cursor safety adapter | Unlikely Spec Kit ownership | Keep; do not put workflow in hooks |
| `.cursor/permissions.json` | Debina / Cursor | physical | Cursor | None | Human-reviewed; contains permissive git prefixes (known tension with deny-first) |
| `.cursor/cli.json` | Debina / Cursor | physical | Cursor | None | Keep deny-first overlay |
| `.cursor/mcp.json` | Debina / Cursor | physical | Cursor MCP adapter | Spec Kit must not own | Keep |
| `.cursor/commands/` | absent | n/a | unused | Spec Kit historically moved to skills | Prefer skills; do not revive parallel commands |
| `.cursor/agents/` | absent | n/a | unused | None | Leave absent unless needed |
| `.codex/config.toml` (repo) | absent | n/a | Codex adapter (optional) | None | Add only if needed; no secrets |
| `~/.codex/config.toml` | User | home | User / Codex | Out of repo | Do not modify in Debina tasks |
| `AGENTS.md` (+ nested) | Debina | physical | Portable policy | Spec Kit must not override | Keep authoritative |
| `CLAUDE.md` (+ nested) | Debina compatibility | physical | Compatibility / evaluate stale | Possible Spec Kit/Claude touch | Inventory; do not delete in this phase |
| `HANDOFF.md` | Debina | physical | Debina session state | None | Keep; complements Spec Kit run state |
| `work/ACTIVE.json` | Debina lean harness | physical | Debina overlay → Spec Kit feature id | Conceptual overlap | Shrink after adoption |
| `work/QUEUE.md` | Debina | physical | Debina | None | Keep |
| `work/active/**` | Debina | physical | Migrate mapping to `specs/` | Overlap | Deprecate parallel after pilot |
| `work/approvals/**` | Debina | physical | Debina policy gates | Overlap with Spec Kit gates | Map STANDARD/DECISION onto Spec Kit human gates |
| `work/templates/**` | Debina | physical | Debina overlays / Spec Kit templates | Partial | Keep Debina-specific templates |
| `planning/**` | Debina backlog | physical | Debina | Spec Kit is not backlog owner | Keep; no Spec Kit ownership |
| `planning/skills/skills-registry.yaml` | Debina | physical | Debina registry | Must list Spec Kit skills separately | Extend after install |
| `tools/agent/**` | Debina | physical | Debina verify/status wrappers | None | Keep; call from Spec Kit steps |
| `tools/agent-config/**` | Debina | physical | Debina validators | None | Keep |
| `tools/agent-policy/**` | absent | n/a | Future shared safety semantics | None | Optional extract from hooks |
| `tools/verification/**` | absent | n/a | Future profile dispatcher docs/scripts | None | Optional; Dagger remains authority |
| `tools/mcp/**` | Debina | physical | Shared MCP wrappers | Spec Kit must not own | Keep |
| `tools/codex/**` | Debina | physical | Codex entry adapter | Skills bridge collision | Keep README + bridge |
| `dagger/**` + `docs/ci/**` | Debina | physical | Debina verification | None | Keep |
| `docs/governance/**` | Debina | physical | Portable governance | Constitution should point here | Keep |
| `docs/standards/**` | Debina | physical | Source authority | None | Keep |

## Collision priority for pilot

1. `.cursor/skills` whole symlink  
2. `.agents/skills` whole symlink  
3. Whether Spec Kit `init`/`integration install` rewrites `AGENTS.md`  
4. Constitution thickness vs Debina AGENTS  
5. Dual install Cursor+Codex (`multi_install_safe` claimed for Cursor; verify in pilot)
