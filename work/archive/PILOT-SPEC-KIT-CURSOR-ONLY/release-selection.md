# Release selection — Spec Kit pin

## Selected release

| Field | Value |
|---|---|
| selected_tag | `v0.14.3` |
| release_date | Not taken from GitHub Releases API; tag confirmed via `git ls-remote` on 2026-07-29 |
| commit_sha (tag object) | `ec45316a2c240ae1a118bef596f2824c96eae9f8` |
| CLI version printed | `0.14.3` |
| source | `https://github.com/github/spec-kit` |
| selection_method | `git ls-remote --tags --refs https://github.com/github/spec-kit.git` → latest stable `vX.Y.Z` without alpha/beta/rc/dev |

## Rejection filter

No alpha/beta/rc/dev tags were selected. Latest stable family is `v0.14.x`; tip is `v0.14.3`.

## Tag existence confirmation

`refs/tags/v0.14.3` present on official remote before any uvx run.

## Usage (session-local only)

```bash
SPEC_KIT_TAG="v0.14.3"
uvx --from "git+https://github.com/github/spec-kit.git@${SPEC_KIT_TAG}" specify ...
```

Not written to user global environment. No `uv tool install` / `pip` / `pipx`.

## CLI smoke

- `specify version` → CLI Version 0.14.3
- `specify --help` → available
- `specify integration list` (from Spec Kit project) → `cursor-agent` present; Multi-install Safe = yes
- `specify integration upgrade --help` → `--force` exists; pilot never used it
- `specify integration uninstall --help` → `--force` exists; pilot never used it
