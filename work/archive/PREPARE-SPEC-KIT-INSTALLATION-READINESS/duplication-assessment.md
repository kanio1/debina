# Duplication Assessment — Debina harness vs GitHub Spec Kit

Recommendation vocabulary: `KEEP_DEBINA` | `ADAPT_TO_SPEC_KIT` | `REPLACE_BY_SPEC_KIT` | `DEPRECATE_AFTER_PILOT` | `REMOVE_REQUIRES_HUMAN_REVIEW` | `PLATFORM_ADAPTER` | `UNRESOLVED`

| Current capability | Current implementation | Spec Kit equivalent | Debina-specific value | Recommendation |
| --- | --- | --- | --- | --- |
| Feature specification generation | `debina-discover-and-specify` + `plan.md` / use-case slice | `/speckit.specify`, `specs/**/spec.md` | Payment BA, source authority, Use-Case 2.0 | `ADAPT_TO_SPEC_KIT` — Spec Kit owns generic spec; Debina Skills fill domain content |
| Clarification / open questions | Informal in plan/progress; `[OPEN-QUESTION]` culture | `/speckit.clarify` | Source-authority unknowns | `ADAPT_TO_SPEC_KIT` |
| Technical planning | `technical-architect` + discover plan | `/speckit.plan`, `plan.md` in feature | Modulith/ADR constraints | `ADAPT_TO_SPEC_KIT` |
| Task decomposition | `scrum-master` + plan steps; no universal tasks.md engine | `/speckit.tasks`, `tasks.md` | Domain Done/Verify | `REPLACE_BY_SPEC_KIT` for generic tasks; keep Debina Verify/DoD fields |
| Workflow state machine | `ACTIVE.json` status + progress vocabulary (`DISCOVERY`…`DONE`) | Spec Kit feature/run state under `.specify/` / specs | Lane approvals + allowed_paths | `REPLACE_BY_SPEC_KIT` for generic state; retain Debina policy overlays |
| Pause / resume | Implicit via artifacts + HANDOFF; no formal pause engine | Spec Kit workflow pause/resume | Cross-tool handoff needs repo artifacts | `REPLACE_BY_SPEC_KIT` |
| Human gate mechanics | `work/approvals/*.approved` + hook | Spec Kit human gates | STANDARD/DECISION rigor, dual DECISION files | `ADAPT_TO_SPEC_KIT` — reuse Spec Kit gate UX; keep Debina approval semantics |
| Convergence / consistency analysis | Manual review skill; planning-semantic-integrity | Spec Kit artifact consistency / convergence | Capability graph + semantic enforcement | `ADAPT_TO_SPEC_KIT` — generic consistency to Spec Kit; Debina semantic integrity stays |
| Implementation loop | `debina-implement-and-verify` | Spec Kit implement workflow | allowed_paths, one-writer, verify wrappers | `ADAPT_TO_SPEC_KIT` |
| Independent review + next task | `debina-review-and-next-work` | Partial review/merge helpers | Debina DoD, QUEUE, planning backlog selection | `KEEP_DEBINA` for review criteria + next-task selection; drop duplicated generic steps after pilot |
| Slash discover/implement/review chain | Four `disable-model-invocation` skills | `speckit-*` skills | Debina orchestration UX target `/debina-start` | `DEPRECATE_AFTER_PILOT` as primary workflow engine; keep thin Debina adapters |
| `work/ACTIVE.json` | Sole machine-readable active task | Spec Kit active feature state | allowed_paths, verify_commands, policy_profile | `ADAPT_TO_SPEC_KIT` — shrink to Debina overlay pointing at Spec Kit feature id |
| `work/QUEUE.md` | NOW/NEXT/LATER overlay | Not a Spec Kit backlog | Debina operational focus vs `planning/` | `KEEP_DEBINA` |
| `work/active/**` plan/progress/evidence | Task workspace | Spec Kit `specs/<feature>/` | Temporary until mapped | `ADAPT_TO_SPEC_KIT` |
| Lane FAST/STANDARD/DECISION | AGENTS + hooks write-gate | None (generic workflow only) | Execution policy profiles | `KEEP_DEBINA` (as profiles, not workflow engine) |
| Banking / ISO / EPC modeling | Domain Skills + source matrix | None | Core Debina IP | `KEEP_DEBINA` |
| Cross-stack impact completeness | Mostly missing enforcement | Generic artifact storage only | Payment→DB→Kafka→API→BFF→UI | `KEEP_DEBINA` (must build Debina overlay; currently `MISSING_DEBINA_CROSS_STACK_ENFORCEMENT`) |
| Autonomy / Shell / Git safety | Hooks + cli.json + permissions | Not a substitute | Safety policy | `KEEP_DEBINA` + `PLATFORM_ADAPTER` |
| Dagger verification profiles | `docs/ci` + `dagger/**` + wrappers | May invoke as step | Local verification authority | `KEEP_DEBINA` |
| MCP read-only stack | `.cursor/mcp.json` + `tools/mcp/*` | Should not own | Runtime inspection | `KEEP_DEBINA` + `PLATFORM_ADAPTER` |
| AGENTS.md / nested AGENTS | Portable policy | `.specify/memory/constitution.md` thin pointer | ADR/source/DoD | `KEEP_DEBINA`; Spec Kit constitution = adapter only |
| Cursor Rules `*.mdc` | Routing + reminders | Spec Kit may add `specify-rules.mdc` | Technology routing | `PLATFORM_ADAPTER` |
| Codex command policy | Sparse in-repo; home config | Separate | Sandbox/approvals | `PLATFORM_ADAPTER` |
| session-handoff / HANDOFF.md | Operational continuity | Not equivalent | Debina session continuity | `KEEP_DEBINA` (complement Spec Kit state) |
| Design Council portable workflow | Queued, not implemented | Spec Kit may cover much generic council flow | Debina experts/profiles | `UNRESOLVED` until pilot; keep ON_HOLD |
| Validator / hygiene tooling | `tools/agent-config`, `tools/skills` | Spec Kit validators for its artifacts | Instruction hygiene, capability graph | `KEEP_DEBINA` |
| Whole-dir skills symlinks | `.agents`/`.cursor`/`.codex` → `.claude` | Spec Kit manages platform skill dirs | Collision risk | `REMOVE_REQUIRES_HUMAN_REVIEW` (replace with per-skill bridges after pilot) |

## Summary counts

| Recommendation | Count (approx.) |
| --- | --- |
| KEEP_DEBINA | High (domain, safety, verification, QUEUE, review criteria) |
| ADAPT_TO_SPEC_KIT | Medium (discover/plan/implement overlays, ACTIVE shrink) |
| REPLACE_BY_SPEC_KIT | Medium (generic state, tasks, pause/resume, generic specify) |
| DEPRECATE_AFTER_PILOT | Four lean workflow skills as primary engine |
| PLATFORM_ADAPTER | Rules, Hooks, MCP manifests |
| REMOVE_REQUIRES_HUMAN_REVIEW | Whole-directory skill symlinks |
| UNRESOLVED | Design Council vs Spec Kit overlap (held) |

## Anti-pattern to avoid

```text
Spec Kit workflow engine
+
custom Debina workflow engine (four slash skills as parallel driver)
```

After adoption, Debina must not keep a second `spec → plan → tasks → implement → converge` engine. Retain policy profiles + domain Skills + verification + review DoD only.
