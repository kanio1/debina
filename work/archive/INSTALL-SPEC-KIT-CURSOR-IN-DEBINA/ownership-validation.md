# Ownership validation

## Integration state (`.specify/integration.json`)

| Field | Value |
|---|---|
| version | `0.14.3` |
| integration_state_schema | `1` |
| default_integration | `cursor-agent` |
| installed_integrations | `["cursor-agent"]` |
| script | `sh` |
| invoke_separator | `-` |

## Manifests

### `cursor-agent.manifest.json`

Tracks **only** the ten `.cursor/skills/speckit-*/SKILL.md` paths with content hashes. Debina bridges are **not** listed.

### `speckit.manifest.json`

Tracks shared scripts (5) + templates (5) under `.specify/`.

## Namespace isolation

| Check | Result |
|---|---|
| Debina bridges | 33 symlinks |
| Spec Kit skills | 10 real directories |
| Name overlap | 0 |
| Broken links | 0 |
| `.claude/skills/speckit-*` | none |
| Synchronizer | PASS; `SPEC_KIT_LEFT_ALONE` for all 10 |

## Rules / Hooks / MCP

| Area | Impact |
|---|---|
| Debina Rules | preserved; no `specify-rules.mdc` |
| Hooks | unchanged (hashes match baseline) |
| MCP | unchanged |
| Codex | not installed |

## Upgrade/uninstall readiness

Pinned CLI `uvx --from …@v0.14.3` can manage update/uninstall later via Spec Kit integration commands; manifests + hashes retained.
