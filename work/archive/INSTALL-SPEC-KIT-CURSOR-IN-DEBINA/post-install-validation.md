# Post-install validation

## Skills

| Check | Result |
|---|---|
| Canonical hash comparison (33) | **IDENTICAL** to pre-install baseline |
| Debina bridges | 33 symlinks → `.claude/skills` |
| Spec Kit dirs | 10 real directories under `.cursor/skills` |
| Broken symlinks | 0 |
| Namespace collisions | 0 |
| `sync-cursor-skills.py --check` | PASS (`SPEC_KIT_LEFT_ALONE` ×10) |
| `.claude/skills/speckit-*` | absent |

## Spec Kit skill policy review (static)

Reviewed all `.cursor/skills/speckit-*/SKILL.md`.

| Concern | Finding |
|---|---|
| Auto commit/push | not instructed as default behavior |
| `--force` / hook bypass | not instructed |
| Secrets | not instructed |
| Disable tests | not instructed |
| Replace Debina governance | not instructed |
| Extension hook examples | mention `speckit.git.commit` as **example extension command id** when `.specify/extensions.yml` exists — informational; no extensions.yml installed |

Verdict: **no SPEC_KIT_SKILL_POLICY_CONFLICT** requiring human block. Residual risk is extension-hook surface if someone later adds git extensions — deferred.

## Scripts (read-only)

`bash -n` on all five `.specify/scripts/bash/*.sh` → **PASS**.

Static notes (not executed):

- `create-new-feature.sh` / `common.sh` interact with git feature directories and may write `.specify/feature.json`
- scripts can resolve/create under `specs/` once feature workflow starts
- no commit/push executed in this task

## Constitution

`.specify/memory/constitution.md` is **placeholder template** (`[PROJECT_NAME]`, `[PRINCIPLE_*]`, HTML comments). Not auto-filled with Debina governance. Does not override AGENTS/ADRs.

### Future thin adapter model (not written yet)

```text
Spec Kit artifacts must comply with:
- AGENTS.md;
- accepted ADRs;
- source authority;
- Use-Case 2.0;
- architecture method;
- cross-stack completeness;
- Debina Definition of Done.

Spec Kit artifacts may not override frozen invariants
or accepted Debina governance.
```

## verify-fast

| | Baseline | Final |
|---|---|---|
| Overall | FAIL | FAIL |
| CLAUDE.md access / hygiene | FAIL | FAIL (same) |
| HANDOFF-010 | FAIL | FAIL (same; HANDOFF out of this task scope) |
| Maven sandbox `/root/.m2` | FAIL | FAIL (same) |
| skills validation | PASS | PASS |
| health-check | PASS | PASS |

```text
NO_NEW_VERIFY_FAST_FAILURES
```

Standalone `git diff --check` → PASS.  
`python3 .cursor/hooks/health-check.py` → PASS.

## Runtime discovery

```text
SPEC_KIT_INSTALLED
RUNTIME_DISCOVERY_PENDING
POST_INSTALL_CURSOR_REOPEN_REQUIRED
```

Do **not** invoke `/speckit.*` in the install session.

## Review verdict

```text
PASS_WITH_FINDINGS
```

### F-01 — `POST_INSTALL_CURSOR_REOPEN_REQUIRED`

New `speckit-*` must be discovered in a fresh Cursor session.

### F-02 — `PRE_EXISTING_SKILL_DISCOVERY_COUNT_27_OF_33`

Preserved: `CURSOR_SKILLS_RUNTIME_DISCOVERY_SOURCE_AMBIGUOUS` (prior session presented 27 via `.agents/skills`).

### F-03 (informational) — `SPECIFY_RULES_MDC_NONE_GENERATED`

Matches pilot; no Rules merge required.
