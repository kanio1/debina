# Generated files inventory

## New paths (this task)

### `.specify/**` (Spec Kit owned)

| path | type | owner | generated_by | pilot_equivalent | expected_or_unexpected |
|---|---|---|---|---|---|
| `.specify/init-options.json` | file | Spec Kit | init | yes | expected |
| `.specify/integration.json` | file | Spec Kit | init | yes | expected |
| `.specify/integrations/cursor-agent.manifest.json` | file | Spec Kit | cursor-agent | yes | expected |
| `.specify/integrations/speckit.manifest.json` | file | Spec Kit | init | yes | expected |
| `.specify/memory/constitution.md` | file | Spec Kit template | constitution setup | yes | expected |
| `.specify/memory/.constitution-template.json` | file | Spec Kit | init | yes | expected |
| `.specify/scripts/bash/*.sh` (5) | files | Spec Kit | shared infra | yes | expected |
| `.specify/templates/*.md` (5) | files | Spec Kit | shared infra | yes | expected |
| `.specify/workflows/speckit/workflow.yml` | file | Spec Kit | bundled workflow | yes | expected |
| `.specify/workflows/workflow-registry.json` | file | Spec Kit | bundled workflow | yes | expected |

### `.cursor/skills/speckit-*` (real directories; not symlinks)

| path | sha256 | type | owner | pilot_equivalent |
|---|---|---|---|---|
| `speckit-analyze/SKILL.md` | `2e89ffed…c44e585d` | real dir + file | Spec Kit | yes (match) |
| `speckit-checklist/SKILL.md` | `4eb7e030…c7cf9dcb` | real dir + file | Spec Kit | yes |
| `speckit-clarify/SKILL.md` | `5e239a81…847d0e98` | real dir + file | Spec Kit | yes |
| `speckit-constitution/SKILL.md` | `0fc8ce68…db3322b1` | real dir + file | Spec Kit | yes |
| `speckit-converge/SKILL.md` | `1355b748…977a4ce2` | real dir + file | Spec Kit | yes |
| `speckit-implement/SKILL.md` | `248c2fa4…3f88f0cd` | real dir + file | Spec Kit | yes |
| `speckit-plan/SKILL.md` | `c5522489…d6ea41b3` | real dir + file | Spec Kit | yes |
| `speckit-specify/SKILL.md` | `fc1a68dc…1c1dee79` | real dir + file | Spec Kit | yes |
| `speckit-tasks/SKILL.md` | `2dceed43…3421efba` | real dir + file | Spec Kit | yes |
| `speckit-taskstoissues/SKILL.md` | `2c00fdc7…a52d6c67` | real dir + file | Spec Kit | yes |

Count: **10** `speckit-*` real directories.

## Not generated (matches pilot)

| path | note |
|---|---|
| `.cursor/rules/specify-rules.mdc` | **NONE_GENERATED** (same as pilot) |
| `specs/**` | absent |
| `.specify/feature.json` | absent until feature workflow |
| Codex paths | absent |

## Unchanged existing paths (verified)

- Debina Rules under `.cursor/rules/00-*` … `40-*`
- `.cursor/hooks/**`, `.cursor/hooks.json`, `.cursor/cli.json`, `.cursor/mcp.json` (hashes match baseline)
- `.claude/skills/**` content hashes (33 identical)
- 33 Debina per-Skill bridges retained
- `AGENTS.md` not modified by this install
- Production `backend/**` `frontend/**` `infra/**` untouched by install diff

## Task work artifacts

`work/ACTIVE.json`, `work/QUEUE.md`, `work/active/INSTALL-SPEC-KIT-CURSOR-IN-DEBINA/**`
