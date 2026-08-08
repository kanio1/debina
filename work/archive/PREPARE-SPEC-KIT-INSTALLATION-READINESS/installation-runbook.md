# Installation Runbook (future — do not execute in this task)

## Sequence

```text
readiness audit                          ✅ this task
→ isolated pilot                         NEXT: PILOT-SPEC-KIT-CURSOR-CODEX-INTEGRATION
→ human adoption decision
→ prepare Skills ownership (Variant B)
→ install Cursor integration (main tree only after decision)
→ validate
→ install Codex integration
→ validate
→ merge thin constitution
→ add Debina extension/overlay (/debina-start, profiles, impact)
→ handoff pilot
→ deprecate duplicated harness workflow skills as engine
```

---

## Step 0 — Readiness audit

| Field | Value |
| --- | --- |
| Preconditions | Clean understanding of skill symlinks; no Spec Kit present |
| Expected files | `work/active/PREPARE-SPEC-KIT-INSTALLATION-READINESS/**` |
| Expected diff | `work/ACTIVE.json`, `work/QUEUE.md`, task artifacts only |
| Verification | `task-status`, `verify-fast`, `git diff --check` |
| Rollback | Delete task dir; restore QUEUE/ACTIVE |
| Ownership | Debina audit |
| Human approval | Not required for audit artifacts |

---

## Step 1 — Isolated pilot (`PILOT-SPEC-KIT-CURSOR-CODEX-INTEGRATION`)

| Field | Value |
| --- | --- |
| Preconditions | Readiness `READY_FOR_ISOLATED_PILOT`; pin official Spec Kit version; **outside main working tree** (temp repo or separate worktree) |
| Commands (pilot only) | Pin + install specify in isolation; `specify init` in temp repo; `specify integration install cursor-agent`; then Codex; inventory; sample feature; Cursor↔Codex handoff; update/uninstall tests |
| Forbidden | `--force` against unsafe multi-install; modifying main Debina tree; writing secrets |
| Expected files | Only inside isolated repo/worktree |
| Expected diff | None on main Debina checkout |
| Verification | Inventory manifests; symlink behavior; AGENTS impact; constitution; multi-install; uninstall cleanliness |
| Rollback | Delete temp repo / remove worktree |
| Ownership | Pilot operator |
| Human approval | **Yes** before starting (adopts tool download) |

Pilot checklist (from task brief):

1. Fetch pinned official Spec Kit  
2. Create clean repo  
3. Init Cursor integration  
4. Inventory all files  
5. Install Codex integration  
6. Inventory multi-install  
7. Check integration manifest  
8. Check default integration  
9. Check update  
10. Check uninstall  
11. Check symlink behavior  
12. Check `.agents/skills` collision patterns  
13. Check `.cursor/skills` collision patterns  
14. Check impact on `AGENTS.md`  
15. Check constitution  
16. Create sample feature  
17. Start in Cursor  
18. Continue in Codex  
19. Return to Cursor  
20. Verify shared state  
21. Decide need for extensions/presets/overlays/custom workflows  

---

## Step 2 — Human adoption decision

| Field | Value |
| --- | --- |
| Preconditions | Pilot report complete |
| Expected files | Decision note under `work/approvals/` or governance decision record |
| Outcomes | Adopt / Adopt-with-Variant-B-prep / Reject |
| Rollback | Leave harness unchanged |
| Human approval | **Required** |

---

## Step 3 — Prepare Skills ownership (Variant B)

| Field | Value |
| --- | --- |
| Preconditions | Adoption approved; pilot confirms Spec Kit tolerates sibling `debina-*` entries |
| Expected files | Real `.agents/skills/` and `.cursor/skills/` dirs; per-skill symlinks to canonical; no whole-dir symlink |
| Expected diff | Symlink layout + registry path updates |
| Verification | Hash equality of Debina skills; Cursor/Codex discovery; no broken links |
| Rollback | Restore whole-dir symlinks |
| Ownership | Debina harness |
| Human approval | **Required** (harness change) |

---

## Step 4 — Install Cursor integration (main tree)

| Field | Value |
| --- | --- |
| Preconditions | Variant B applied; clean git status for harness paths; pinned specify version |
| Command shape | `specify integration install cursor-agent` (exact flags from pilot) — **no `--force` unless human-reviewed** |
| Expected files | `.cursor/skills/speckit-*/**`, possibly `.specify/**`, maybe `specify-rules.mdc` |
| Expected diff | Only Spec Kit + agreed adapter paths |
| Verification | Inventory; Debina skills intact; hooks still pass health-check |
| Rollback | Spec Kit uninstall path verified in pilot; git restore if needed (human) |
| Ownership | Spec Kit (skills) + Debina review |
| Human approval | **Required** |

---

## Step 5 — Validate Cursor install

| Field | Value |
| --- | --- |
| Preconditions | Step 4 complete |
| Verification | List skills; open `/speckit.*` or skills; run `tools/agent/health-check`; ensure Debina skill hashes unchanged |
| Rollback | Uninstall Cursor integration |
| Human approval | Review of diff |

---

## Step 6 — Install Codex integration

| Field | Value |
| --- | --- |
| Preconditions | Cursor install validated; `.agents/skills` is real directory |
| Command shape | `specify integration install codex` with skills options from pilot |
| Expected files | `.agents/skills/speckit-*/**` |
| Verification | `codex --cd tools/codex` still sees Debina skills; MCP unchanged |
| Rollback | Uninstall Codex integration |
| Human approval | **Required** |

---

## Step 7 — Merge thin constitution

| Field | Value |
| --- | --- |
| Preconditions | Dual install stable |
| Expected files | `.specify/memory/constitution.md` pointer-only |
| Expected diff | Thin file + maybe registry note |
| Verification | Spec Kit reads it; does not duplicate AGENTS |
| Rollback | Delete constitution file |
| Human approval | **Required** |

---

## Step 8 — Debina extension / overlay

| Field | Value |
| --- | --- |
| Preconditions | Spec Kit usable in both tools |
| Expected files | `/debina-start`, `/debina-review-next` adapters; policy_profile mapping; impact-analysis template; verification profile mapper |
| Expected diff | Debina overlays only; deprecate four lean skills as primary engine after soak |
| Verification | End-to-end FAST_MICRO and STANDARD dry runs without production writes |
| Rollback | Keep Spec Kit; disable overlays |
| Human approval | **Required** |

---

## Step 9 — Handoff pilot + deprecation

| Field | Value |
| --- | --- |
| Preconditions | Overlay accepted |
| Actions | Document Cursor↔Codex resume; mark lean workflow skills `DEPRECATE_AFTER_PILOT` complete; keep domain skills; resolve Design Council hold |
| Verification | Single workflow engine in daily use |
| Human approval | **Required** |

---

## Hard prohibitions (all steps)

- No `specify` install into main tree until Step 4 human approval  
- No `--force` without explicit human review  
- No secret commits  
- No production/migration/API/planning epic edits as part of Spec Kit install  
- No deleting Debina Skills/Rules/Hooks/Commands in readiness or pilot  
- No parallel long-term dual workflow engines  

## Pinning

Record exact Spec Kit version, uv tool version, cursor-agent version, and codex version in the pilot report before any main-tree install.
