# Skills layout experiments

Evidence: `/tmp/debina-spec-kit-cursor-pilot/evidence/experiment-b-whole-symlink.json`, `experiment-c-per-skill.json`, `experiments-bc-summary.json`

Sentinel hash (constant across B/C when preserved):

`1978d593b8c62cb3f97c37048e7603849517c1a9d312d0a5b04dbc75b97db175`

## Experiment B — whole-directory symlink

Layout after restructure:

```text
.claude/skills/
  debina-sentinel/SKILL.md
  speckit-*/SKILL.md   ← Spec Kit content moved into Claude tree
.cursor/skills → ../.claude/skills
```

### Observations

1. Init onto pre-built non-empty symlink layout without `--force`: **refused** (exit 1; asks `--force`).
2. After clean init + restructure to whole-dir symlink, Spec Kit managed skills lived **physically under `.claude/skills`**.
3. Upgrade (same tag, no `--force`) followed the symlink target and reported removal of 10 stale files; afterward `.claude/skills` retained **only** `debina-sentinel` (speckit skills removed from the shared tree while still following the directory symlink model).
4. Sentinel hash unchanged through upgrade; sentinel survived uninstall.
5. Manifest paths remain `.cursor/skills/speckit-*` logically, but filesystem operations resolve through the directory symlink → **Debina canonical tree is in the blast radius**.

### Verdict B

```text
WHOLE_DIRECTORY_SYMLINK_UNSAFE
```

Do **not** use this model in Debina main repo for Spec Kit.

## Experiment C — per-Skill bridges

Layout:

```text
agent-skills-src/debina-sentinel/SKILL.md
.cursor/skills/
  debina-sentinel → ../../agent-skills-src/debina-sentinel
  speckit-*       (real Spec Kit directories)
```

`readlink`: `../../agent-skills-src/debina-sentinel`  
resolved: `/tmp/.../per-skill-bridges-project/agent-skills-src/debina-sentinel`

### Observations

1. Init on non-empty bridge layout without `--force`: refused (exit 1).
2. After clean init + bridge add: `debina-sentinel` remained a symlink; hash identical after upgrade.
3. `speckit-*` coexist as separate entries beside the bridge.
4. Manifest tracks only Spec Kit managed `speckit-*` files (sentinel not owned).
5. Second upgrade after modifying `.cursor/skills/speckit-specify/SKILL.md`: **blocked** without `--force` (`1 file(s) have been modified… Use --force…`). Modified hash preserved.
6. Uninstall without `--force`: removed 9 unmanaged-clean Spec Kit skills; **preserved** modified `speckit-specify`; **preserved** `debina-sentinel` bridge + hash.

### Expected safe model (verified in disposable)

```text
.cursor/skills/
├── debina-*   → per-Skill bridge
└── speckit-*  → managed by Spec Kit
```

## Model comparison (summary)

| Criterion | Whole-dir symlink (current Debina) | Per-skill bridges + Spec Kit | Minimal migration (bridges → `.claude/skills`) |
|---|---|---|---|
| Simplicity | Highest today | Medium | Medium-low |
| Upgrade risk | **High** (writes/removes via symlink into canonical tree) | Low (isolated entries) | Low if bridges only |
| Ownership | Blurred | Clear | Clear |
| Registry compatibility | Works today; unsafe with Spec Kit | Compatible with Cursor discovery of `SKILL.md` dirs | Compatible |
| Cursor | Works | Works (files present; runtime auth not proven in pilot) | Works |
| Future Codex | Same symlink risk if Codex path is also whole-dir | Can mirror bridges under Codex path | Same |
| Migrate 33 skills | N/A (already linked) | One bridge per skill | One bridge per skill into `.claude` |
| Broken symlink detection | Single link | Per-skill (easier to validate) | Per-skill |
| Spec Kit takeover risk | **Yes** | **No** (verified) | **No** (if no whole-dir link) |

**Recommended for Debina:** per-Skill bridges (either to `agent-skills-src/` or to `.claude/skills/<skill>`). No Skills migration in this pilot.
