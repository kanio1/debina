# Installation evidence — INSTALL-SPEC-KIT-CURSOR-IN-DEBINA

## Tag / CLI

| Field | Value |
|---|---|
| Tag | `v0.14.3` |
| Tag commit | `ec45316a2c240ae1a118bef596f2824c96eae9f8` |
| CLI | `uvx --from git+https://github.com/github/spec-kit.git@v0.14.3 specify …` |
| Reported version | `0.14.3` |
| Persistent install | none (`uv tool install` / pip / pipx not used) |

## Attempt without `--force`

```bash
uvx --from git+https://github.com/github/spec-kit.git@v0.14.3 \
  specify init --here --integration cursor-agent --script sh --ignore-agent-tools
```

| Field | Value |
|---|---|
| exit code | 1 |
| outcome | refused: non-empty directory, no confirmation available |
| message | `Re-run with --force to merge into it.` |
| partial install | none (`.specify` absent; no `speckit-*`) |

## Conditional `--force` (all gates met)

```bash
uvx --from git+https://github.com/github/spec-kit.git@v0.14.3 \
  specify init --here --force --integration cursor-agent --script sh --ignore-agent-tools
```

| Field | Value |
|---|---|
| exit code | 0 |
| outcome | Project ready; Cursor integration installed |
| `--force` necessary | yes (non-empty repo only) |

## Post-command integration

| Field | Value |
|---|---|
| default | `cursor-agent` |
| installed | `cursor-agent` |
| script | `sh` |
| invoke_separator | `-` |
| schema | `integration_state_schema: 1` |
| managed skill files | 10 (cursor-agent.manifest.json) |
| managed shared files | 10 (speckit.manifest.json scripts+templates) |

## Status after install

```text
SPEC_KIT_INSTALLED
RUNTIME_DISCOVERY_PENDING
```
