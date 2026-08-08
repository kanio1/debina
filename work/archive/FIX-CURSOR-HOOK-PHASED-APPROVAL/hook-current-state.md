# Hook current-state model (before fix)

## Decision map

| Question | Answer |
| --- | --- |
| 1. Which hook blocks STANDARD writes? | `.cursor/hooks/scope-approval-gate.py` on `preToolUse` matcher `Write\|Edit\|StrReplace\|Delete\|EditNotebook` |
| 2. Where is `lane` read? | `scope-approval-gate.py` from `work/ACTIVE.json`; `approval_ok(task_id, lane)` |
| 3. Where is `policy_profile` read? | Nowhere in hooks (only present in ACTIVE notes from readiness audit) |
| 4. Where is `.approved` checked? | `approval_ok()` for `lane==STANDARD` → `work/approvals/<TASK>.approved`; DECISION needs two files |
| 5. How are `allowed_paths` set? | From ACTIVE.json; overlay always adds `work/active/<task>/**`, `QUEUE`, `ACTIVE`, `HANDOFF` |
| 6. Controlled tools | Write/Edit/StrReplace/Delete/EditNotebook via scope gate; Shell via `command-guard.py` only |
| 7. Shell checked before execute? | Yes — `beforeShellExecution` → command-guard (git danger + sed -i + ambiguous outside-repo redirects) |
| 8. Post-Shell mutation check? | No |
| 9. Safe wrappers | Prefix allow: `./tools/agent/`, `tools/agent/`, `bash tools/`, `python3 tools/`, health-check |
| 10. Tests/builds recognized? | No special-case; mvnw/pnpm generally allowed unless matching deny patterns |
| 11. Fail mode | scope + command-guard: `failClosed: true`; checkpoint: fail-open |
| 12. Cursor IDE vs Agent CLI | Same project `.cursor/hooks.json`; CLI also uses `.cursor/cli.json` deny-first overlay |

## Failure modes driving this fix

1. STANDARD lane requires `.approved` for **any** Write, including analysis under `work/active/<task>/`.
2. Agents set `lane=FAST` while intending STANDARD rigor (F-01).
3. Shell python/redirect/heredoc can mutate the tree without allowed_paths/approval (F-02).
4. No `phase` / `write_scope` / `approval_state` vocabulary in enforcement.

## Target (this task)

```text
policy_profile → rigor
phase → current activity
approval_state → implementation consent
write_scope → write class
allowed_paths → implementation path constraint
```
