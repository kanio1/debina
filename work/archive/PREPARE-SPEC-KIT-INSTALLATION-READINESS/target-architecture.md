# Target Architecture — Spec Kit + Debina + Cursor/Codex

## One-liner

```text
Spec Kit drives workflow.
Debina policy profiles determine rigor.
Skills provide expertise.
AGENTS.md and governance define portable policy.
Validators prove completeness.
Hooks protect boundaries.
Dagger provides verification evidence.
```

## Layer diagram

```text
┌─────────────────────────────────────────────────────────────┐
│ Daily UX: /debina-start  ·  /debina-review-next             │
│ (thin Debina orchestrators — not a second workflow engine)  │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│ GitHub Spec Kit                                             │
│ specification · clarification · planning · tasks            │
│ workflow state · human gates · pause/resume                 │
│ implement loop · artifact analysis · convergence            │
│ artifacts: .specify/  ·  specs/<feature>/                   │
└───────────────────────────┬─────────────────────────────────┘
                            │ parameterized by
┌───────────────────────────▼─────────────────────────────────┐
│ Debina Execution Policy Profiles                            │
│ FAST_MICRO | FAST | STANDARD | DECISION                     │
│ + impact analysis · autonomy · DoD · next-task selection    │
│ + domain Skills · source authority · verification profiles  │
│ + planning/ backlog · QUEUE overlay                         │
└───────┬─────────────────────────────┬───────────────────────┘
        │                             │
┌───────▼──────────┐         ┌────────▼──────────┐
│ Cursor adapter   │         │ Codex adapter     │
│ Rules (routing)  │         │ command/sandbox   │
│ Hooks            │         │ Hooks if proven   │
│ permissions/cli  │         │ config.toml       │
│ mcp.json         │         │ MCP entries       │
│ speckit-* skills │         │ speckit-* skills  │
│ debina-* bridges │         │ debina-* bridges  │
└──────────────────┘         └───────────────────┘
        │                             │
        └──────────────┬──────────────┘
                       ▼
              Canonical Debina Skills
              (single content tree)
                       +
              tools/mcp wrappers
              tools/agent verify*
              dagger/** evidence
```

## Ownership boundaries

| Concern | Owner |
| --- | --- |
| Workflow stages / pause / resume / generic tasks | Spec Kit |
| Policy rigor / approvals semantics / escalation | Debina profiles |
| Payment/banking expertise | Debina Skills + docs/standards |
| Cross-stack completeness | Debina (currently under-enforced) |
| Safety boundaries | Hooks (+ future shared agent-policy) |
| Verification evidence | Dagger + tools/agent |
| Portable law | AGENTS.md, ADRs, governance |
| Platform routing / sandbox | Cursor / Codex adapters |
| MCP | Debina wrappers + platform manifests |

## Anti-duplication rule

Forbidden end-state:

```text
Spec Kit workflow engine
+
custom Debina workflow engine
```

Allowed:

```text
Spec Kit workflow engine
+
Debina policy/expertise/verification overlays
+
thin /debina-start and /debina-review-next UX
```

## Cursor ↔ Codex handoff

```text
conversation history is not task state
repository artifacts are task state
```

Shared: Spec Kit feature artifacts + run state, AGENTS, Debina Skills, impact analysis, verification profile/evidence, diff, next incomplete task.

Platform-local: chat history, terminal approvals, Cursor checkpoints, Codex sandbox approvals, platform Hooks/Rules.

Minimal resume:

1. Read active feature  
2. Read Spec Kit run state  
3. Check HEAD + diff  
4. Read tasks  
5. Read progress  
6. Check verification evidence  
7. Continue first incomplete step  

Constraint: **one working tree → one writing agent at a time**.

## Cross-stack completeness (Debina)

Each surface must be `AFFECTED` | `NOT_AFFECTED`+reason | `UNKNOWN`.  
`UNKNOWN` blocks STANDARD/DECISION implementation readiness.

Surfaces: business, banking-sources, domain-model, spring-modulith, backend, postgresql, kafka, rest, graphql, keycloak, bff, nextjs, react, typescript-zod, observability, infrastructure, testing, documentation.

Current gap: **`MISSING_DEBINA_CROSS_STACK_ENFORCEMENT`** (no dedicated template/validator). Spec Kit will not invent payment→UI dependency knowledge.

## Constitution (future, not created now)

`.specify/memory/constitution.md` = thin adapter pointing to:

- root `AGENTS.md`
- accepted ADRs / ADR lifecycle
- `docs/standards/SOURCE-AUTHORITY-MATRIX.md`
- Use-Case 2.0
- architecture method
- cross-stack standard (to be authored)
- Definition of Done
- autonomy policy

Must not copy the full Debina constitution.

## Design Council

`IMPLEMENT-PORTABLE-DEBINA-DESIGN-COUNCIL` remains `ON_HOLD_PENDING_SPEC_KIT_EVALUATION`. Do not mark completed/cancelled/replaced until pilot decision.
