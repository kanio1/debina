# Lifecycle tests — upgrade, uninstall, rollback

Evidence: `/tmp/debina-spec-kit-cursor-pilot/evidence/lifecycle-tests.json`, `experiment-c-per-skill.json`, `experiment-b-whole-symlink.json`

## Upgrade (same pinned tag, no `--force`)

Project: `/tmp/debina-spec-kit-cursor-pilot/lifecycle-project` (copy of clean)

| Run | Exit | Notes |
|---|---|---|
| upgrade #1 | 0 | Success; shared infra paths left untouched unless `--force` |
| upgrade #2 | 0 | Idempotent success |
| status | 0 | Modified managed files: 0; Missing: 0 |

Shared templates/scripts are **not** refreshed on upgrade without `--force` (explicit warning). Pilot never used `--force`.

## Modified-file protection (Experiment C)

1. Modified managed `.cursor/skills/speckit-specify/SKILL.md`
2. `specify integration upgrade cursor-agent` **without `--force`**
3. Exit **1** with explicit conflict: `1 file(s) have been modified… Use --force to overwrite…`
4. Modified content hash preserved

Safe behaviours observed: **upgrade blocked** + **explicit conflict reported** + **modified file preserved**. Silent overwrite: **not** observed → PASS.

## Uninstall (Experiment C, no `--force`)

- Removed 9 unmodified Spec Kit skill files
- Preserved modified `speckit-specify`
- Preserved `debina-sentinel` per-skill bridge + identical hash
- Manifest/integration state updated (integration uninstalled)

## Uninstall (Experiment B)

- Sentinel survived
- Spec Kit skills under symlink target were already disrupted by upgrade-through-symlink behaviour earlier (see B)

## Rollback / conflict install

Project: `/tmp/debina-spec-kit-cursor-pilot/rollback-project`

1. Replaced `.cursor/skills/speckit-analyze` directory with a **file** of the same name (`BLOCK\n`)
2. `specify integration upgrade cursor-agent` without `--force`
3. Exit **1**
4. Message: `Error: Failed to upgrade… [Errno 17] File exists: '…/speckit-analyze'` + `The previous integration files may still be in place.`
5. Filesystem check: block file **still present**; skill list otherwise unchanged; **no silent repair**
6. Not treated as full transactional rollback of all partial writes; treated as **fail-closed with residual conflict state** that must be fixed before retry

## Hooks / Rules impact

| Area | Result |
|---|---|
| `.cursor/rules` generated | **No** → `NONE_GENERATED` |
| `.cursor/hooks` generated | **No** → `NO_HOOK_INTERACTION` |
| Spec Kit extension hooks in Skills | Static references to `.specify/extensions.yml` only |
| Debina Cursor hooks | Untouched (not installed in disposable; policy remains superior in Debina) |

## Force usage

**None.** All lifecycle commands omitted `--force`.
