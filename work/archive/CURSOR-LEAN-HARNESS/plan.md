# Plan — CURSOR-LEAN-HARNESS

**Lane:** FAST (developer harness / config only)  
**Scope:** agent workflow, Cursor rules/hooks, skills registry, wrappers, docs/governance  
**Out of scope:** business features, migrations, domain tests, GitHub, commits

## Steps

1. Read-only audit of AGENTS, planning, skills, `.cursor/**`, validators; backup configs to `/tmp`.
2. Create `work/` overlay (QUEUE, templates, ACTIVE.example, approvals/archive).
3. Add Lean delivery section to root `AGENTS.md` (no rewrite of constitution).
4. Replace `.cursor/rules` with five lean rules; update hygiene allowlist + skill references.
5. Adjust `session-handoff`, `debina-runtime-proof-testing`; set `disable-model-invocation` on heavy skills; add four workflow skills + registry/routing fixtures.
6. Add three hooks + health-check; create four `tools/agent` wrappers; add `.cursor/cli.json`.
7. Write manual setup doc; run validators/wrappers/health-check; archive this task; leave no `ACTIVE.json`.

## Verify

```bash
bash tools/skills/validate-all-skills.sh
./tools/agent/task-status
./tools/agent/verify-fast
./tools/agent/final-check
python3 .cursor/hooks/health-check.py
git diff --check
```

## Allowed paths (this task)

- `work/**`
- `.cursor/**` (rules, hooks, cli.json; preserve skills symlink)
- `.claude/skills/**` (three edits + four new workflow skills)
- `planning/skills/skills-registry.yaml`
- `tools/skills/evals/routing/**` (new fixtures + session-handoff fixture fix)
- `tools/agent/**`
- `tools/agent-config/validate-agent-instruction-hygiene.py`
- `AGENTS.md`
- `docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md`
- `HANDOFF.md` (material session end)
