# Independent review — PILOT-SPEC-KIT-CURSOR-ONLY

## Verdict

```text
PASS_WITH_FINDINGS
```

## Scope compliance

- Spec Kit only under `/tmp/debina-spec-kit-cursor-pilot/**`
- Debina writes limited to `work/` (+ approval marker)
- No commit/push/branch change
- No `--force`
- No persistent CLI install
- No Skills migration; no Codex/production changes

## Findings

### F-01 — verify-fast FAIL (pre-existing / out of write scope)

`HANDOFF-010`, inaccessible `CLAUDE.md` during verify-fast's `git diff --check`, Maven local-repo sandbox failure. Standalone `git diff --check` and `health-check` PASS. Not caused by Spec Kit pilot writes.

### F-02 — Cursor runtime discovery incomplete

`cursor-agent --print --mode ask` requires login/API key. Marked `CURSOR_RUNTIME_DISCOVERY_REQUIRES_MANUAL_REOPEN`. Does not overturn layout safety.

### F-03 — Workflow runtime partial

Agent-driven specify→implement not executed. CLI/scripts + static skills provide strong structural evidence; marked `WORKFLOW_RUNTIME_TEST_PARTIAL`.

### F-04 — Non-empty init requires confirmation/`--force`

Future Debina install must design a controlled merge; pilot correctly refused `--force`.

### F-05 — Upgrade through whole-dir symlink can remove managed skills from shared tree

Reinforces layout migration requirement before any Debina install.

## Adoption alignment

Reviewer agrees with:

```text
ADOPT_CURSOR_WITH_LAYOUT_CHANGE
NEXT: PREPARE-CURSOR-SKILLS-FOR-SPEC-KIT
```

## Closeout actions

1. Archive task
2. Clear ACTIVE
3. Set QUEUE next/now to `PREPARE-CURSOR-SKILLS-FOR-SPEC-KIT`
4. Do not start next task
5. Keep Design Council `ON_HOLD_PENDING_SPEC_KIT_EVALUATION`
