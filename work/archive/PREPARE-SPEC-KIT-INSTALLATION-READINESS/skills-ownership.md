# Skills Ownership

## Physical source (today)

| Role | Path | Type |
| --- | --- | --- |
| Canonical | `.claude/skills/<name>/` | Real directories (33 ACTIVE) |
| Bridge | `.agents/skills` | **Whole-directory symlink** → `../.claude/skills` |
| Bridge | `.cursor/skills` | **Whole-directory symlink** → `../.claude/skills` |
| Bridge | `.codex/skills` | **Whole-directory symlink** → `../.claude/skills` |
| Bridge | `tools/codex/.agents/skills` | **Whole-directory symlink** → `../../../.claude/skills` |

Answers to audit questions:

1. Physical source of Debina Skills: **`.claude/skills`**
2. Bridge directories: `.agents`, `.cursor`, `.codex`, `tools/codex/.agents`
3. `.agents/skills` whole symlink: **yes**
4. `.cursor/skills` whole symlink: **yes**
5. Per-skill symlinks: **no** (only whole-dir bridges)
6. Duplicate copies: **no** Debina skill trees outside bridges
7. Different versions of same Skills: **no** (single physical content)
8. Could Spec Kit write its Skills through Debina symlink: **yes — high risk**
9. Could Cursor and Codex accidentally manage the same physical dir: **yes — both bridges point to `.claude/skills`**

Broken symlinks: **none** under `.claude` / `.agents` / `.cursor` / `.codex`.

## Registry

- File: `planning/skills/skills-registry.yaml`
- `authoring_source`: `.claude/skills`
- `codex_discovery_path`: `.agents/skills`
- ACTIVE: 33 (matches disk)
- PLANNED (paths absent): `debina-dependency-version-gate`, `debina-next16-keycloak-bff`, `debina-playwright-payment-lab`
- Drift: **no ACTIVE drift**; PLANNED-only gaps are intentional

## Per-skill inventory (summary)

All 33 ACTIVE skills share:

| Field | Value |
| --- | --- |
| physical location | `.claude/skills/<name>/` |
| visible from Cursor | yes (via `.cursor/skills` symlink) |
| visible from Codex | yes (via `.agents/skills` and/or `tools/codex/.agents/skills`) |
| registry entry | yes (ACTIVE) |
| current owner | `DEBINA_CANONICAL` |
| future owner | `DEBINA_CANONICAL` (content) + `DEBINA_BRIDGE` (platform views) |

SHA256 prefixes of `SKILL.md` (for integrity baseline; full list in readiness notes):

See shell inventory from audit session (33 hashes recorded). Representative:

| name | hash16 |
| --- | --- |
| debina-discover-and-specify | `db85e0982bf9b063` |
| debina-implement-and-verify | `193004d11e2bec8f` |
| debina-review-and-next-work | `ffed8dce004f4ce8` |
| enterprise-use-case-engineering | `6c8173a02bf5751e` |
| dagger-go-pipeline | `f959a303234c1f90` |

## Spec Kit Skills (future, not created)

| Pattern | Owner class | Location |
| --- | --- | --- |
| `speckit-*` | `SPEC_KIT_CURSOR` | `.cursor/skills/speckit-*/` |
| `speckit-*` | `SPEC_KIT_CODEX` | `.agents/skills/speckit-*/` |

**Mandatory rule:** do not maintain two independent Debina skill bodies under `.cursor/skills/debina-*` and `.agents/skills/debina-*`. One canonical content only.

**Do not** symlink entire `.cursor/skills` ↔ `.agents/skills` if Spec Kit manages both directories.

## Variant comparison

### Variant A — keep current model

```text
.claude/skills = canonical
.agents/skills = whole bridge
.cursor/skills = whole bridge
```

| Criterion | Assessment |
| --- | --- |
| Cursor compat | Works today |
| Codex compat | Works today |
| Spec Kit compat | **Fails safely** — install would write into canonical or break symlink |
| Update risk | High |
| Manifest collision | High |
| Maintenance | Simple today |
| Migration cost | Low now / high later |
| Registry | Aligned |
| Hash validation | Easy (one tree) |
| Future tools | Fragile |

### Variant B — neutral source + per-skill bridges (recommended)

```text
agent-skills-src/debina-*          # or keep .claude/skills as src temporarily renamed
.agents/skills/debina-*  = per-skill symlink
.cursor/skills/debina-*  = per-skill symlink
.claude/skills/debina-*  = compatibility symlink/bridge
.agents/skills/speckit-* = Spec Kit owned (real)
.cursor/skills/speckit-* = Spec Kit owned (real)
```

| Criterion | Assessment |
| --- | --- |
| Cursor / Codex / Spec Kit | Best separation |
| Update risk | Low if Spec Kit only adds `speckit-*` |
| Collision | Contained |
| Maintenance | Moderate |
| Migration cost | Medium (scripted) |
| Registry | Needs path update |
| Hash validation | Canonical tree hashes |
| Future tools | Best |

**Recommendation:** Variant B. Mark `PILOT_REQUIRED` to confirm Spec Kit install does not delete sibling entries and supports mixed real+symlink children.

### Variant C — `.agents/skills` as canonical

Better for Codex-first shops; worse for current registry/`authoring_source` and Claude compatibility. Higher migration churn. Not preferred unless pilot shows Codex requires non-symlink canonical under `.agents`.

## Ownership classification vocabulary (applied)

| Class | Meaning |
| --- | --- |
| `DEBINA_CANONICAL` | Single source of Debina skill text |
| `DEBINA_BRIDGE` | Symlink/view only |
| `SPEC_KIT_CURSOR` | Platform Spec Kit skills for Cursor |
| `SPEC_KIT_CODEX` | Platform Spec Kit skills for Codex |
| `PLATFORM_SPECIFIC` | Non-shared adapters |
| `DUPLICATE` | Forbidden for Debina content |
| `UNKNOWN` | Not used — roots are known |

## Required pilot tests (Skills)

1. Install Cursor integration into empty temp repo; inventory `.cursor/skills`.
2. Install Codex integration; inventory `.agents/skills`.
3. Simulate Debina per-skill symlinks beside `speckit-*`; re-run update/uninstall.
4. Confirm uninstall does not delete Debina bridges.
5. Confirm whole-dir symlink today would pollute canonical — reproduce in temp only.
6. Hash Debina skills before/after update.
