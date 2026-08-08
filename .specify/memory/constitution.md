<!--
Sync Impact Report
Version change: template → 1.0.0
Modified principles: all placeholders replaced with Debina governance adapter principles
Added sections: Core Principles I–VIII; Spec Kit Execution Scope; Governance
Removed sections: generic Spec Kit placeholder principles; "supersedes all" semantics
Templates: ✅ plan-template.md | ✅ spec-template.md | ✅ tasks-template.md | ✅ checklist-template.md
Follow-up: MICRO_FEATURE_RUNTIME_VALIDATION_REQUIRED; LOCAL_PRESET_OR_WORKFLOW_OVERLAY_MAY_BE_REQUIRED
-->

# Debina Spec Kit Constitution

This constitution governs **Spec Kit execution only** within the Debina repository.
It is a concise adapter between GitHub Spec Kit (`specify → plan → tasks → implement`)
and canonical Debina governance. It does **not** override accepted Debina governance,
ADRs, frozen invariants, or source authority.

## Core Principles

### Principle I — Canonical Governance and Precedence

The canonical authority order is:

1. explicit current user decision;
2. applicable law and normative external requirements;
3. accepted `[FREEZE]` invariants and accepted ADRs;
4. root and nested `AGENTS.md`;
5. canonical Debina governance and source-authority documents
   (including `docs/standards/SOURCE-AUTHORITY-MATRIX.md`);
6. approved task specification and plan;
7. this Spec Kit constitution;
8. generated Spec Kit artifacts.

On conflict, the higher authority wins.

This constitution governs Spec Kit execution only. It may summarize and reference
canonical policy; it MUST NOT duplicate or override it. The phrase
"Constitution supersedes all other practices" does **not** apply in Debina.

### Principle II — Controlled Task State and Approval

- `work/ACTIVE.json` is the **single source of authority** for the active Debina task.
- `.specify/feature.json` (when present) is Spec Kit feature metadata only — not project-task authority.

Every repository-changing operation MUST:

- belong to the active task recorded in `work/ACTIVE.json`;
- respect `allowed_paths`;
- respect `phase` and `write_scope`;
- respect `approval_state`;
- pass Cursor Hooks (`.cursor/hooks/**`).

For `STANDARD` and `DECISION` profiles, **implementation** requires explicit approval
(`approval_state=APPROVED` and, where required, `work/approvals/<TASK-ID>.approved`).
Analytical artifacts in `WORK_ONLY` scope do not require implementation approval.

Spec Kit MUST NOT autonomously:

- create a parallel active Debina task;
- replace or bypass `work/ACTIVE.json`;
- modify `work/QUEUE.md` without harness wrappers;
- start the next feature;
- downgrade a task policy profile.

Harness wrappers (`bootstrap-task`, `approve-task`, `set-task-state`, `closeout-task`)
are the only approved path for task-state mutations.

### Principle III — Execution Policy Profiles

`FAST`, `STANDARD`, and `DECISION` are **rigor profiles**, not a separate workflow engine.
A profile is not a write permission; hooks and `allowed_paths` remain mandatory.

**FAST** — only for changes that are: small, reversible, non-domain, without public
contract change, without data migration, without security-boundary change, and without
payment-invariant change. FAST is not a security bypass.

**STANDARD** — default functional implementation: specification, plan, tasks, impact
analysis, one explicit approval before implementation, appropriate verification profile,
and independent review where required.

**DECISION** — required when any of: open ADR, `[FREEZE]` change, finality or
settlement change, money-movement invariant change, new trust boundary, data-ownership
change, deviation from normative source, or formal compliance claim. DECISION requires:
evidence → alternatives → recommendation → explicit human decision → ADR/RFC where
required → then implementation. A profile MAY be raised automatically; it MUST NOT
be lowered autonomously.

### Principle IV — Payment and Data Integrity

Payment and data rules MUST be source-backed per
`docs/standards/SOURCE-AUTHORITY-MATRIX.md`. Unconfirmed domain detail MUST NOT be
invented. External scheme claims (EPC, STET, TIPS, RT1, STEP2, ISO 20022) MUST be
classified as `NORMATIVE`, `SCHEME_SPECIFIC`, `LOCAL_DESIGN`, or `INFERENCE`.

Accepted frozen invariants (referenced, not duplicated) include:

- **LedgerPort-only** money movement (`docs/analysis/DEBINA-FINALITY-LEDGERPORT-DECISION-PACKET.md`, ADR-N9/N10);
- **one writer per schema** (`docs/data/ISO-XML-DOMAIN-DATABASE-MAPPING-METHOD.md`);
- **RLS-only tenant isolation** with fail-closed tenant context (same);
- **five independent payment status axes** — business, ISO, finality, transport, receipt
  (`docs/analysis/annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md`).

Apply domain Skills (e.g. `debina-money-movement-invariants`, `debina-payment-state-finality`,
`sepa-nexus-payments-data-integrity`) before material payment changes.

### Principle V — Cross-Stack Completeness

Every `STANDARD` or `DECISION` feature MUST assess these surfaces:

`business`, `banking-sources`, `domain-model`, `spring-modulith`, `backend`,
`postgresql`, `kafka`, `rest`, `graphql`, `keycloak`, `bff`, `nextjs`, `react`,
`typescript-zod`, `observability`, `infrastructure`, `testing`, `documentation`.

Each surface MUST be marked `AFFECTED`, `NOT_AFFECTED` (with reason), or `UNKNOWN`.
For `STANDARD` and `DECISION`, **UNKNOWN blocks implementation readiness**.

Plan, tasks, diff, and verification evidence MUST remain consistent.
`speckit-analyze` MAY compare spec/plan/tasks artifacts; Debina validators remain
the source of completeness criteria.

### Principle VI — Verification Before Completion

Passing generic tests does **not** by itself prove task completion.

Required before closeout:

- verification profile matched to risk (`FAST` / `STANDARD` / `DECISION`);
- targeted tests and current verification evidence;
- Dagger composed checks only when `PIPELINE_IMPACT` or profile requires it;
- review of the actual diff;
- acceptance-criteria alignment;
- no unapproved scope expansion.

Verification evidence MUST be tied to current `HEAD`, working-tree diff or fingerprint,
profile, commands executed, and outcome. Full Dagger is NOT required for every `FAST` task.

### Principle VII — Safe Agent Execution

```
Hooks protect boundaries.
Skills provide expertise.
Spec Kit drives generic SDD workflow.
Validators prove completeness.
```

Without explicit user instruction, agents MUST NOT: commit, push, merge, rebase, reset,
clean, publish PRs, delete user changes, disable hooks, bypass Write/Edit via Shell,
expose secrets, bypass `allowed_paths`, or modify governance outside the active task scope.

Spec Kit-generated instructions MUST NOT weaken these rules. Suggested commit messages
are permitted; executing commits is not.

### Principle VIII — Spec Kit Ownership and Boundaries

**Spec Kit owns:** `.specify`-managed workflow artifacts; `.cursor/skills/speckit-*`;
generic spec/plan/tasks/analyze/implement/converge mechanics.

**Debina owns:** governance; payment expertise; canonical Skills (`.claude/skills`);
task approval; impact criteria; verification profiles; Dagger; next-task prioritization;
queue/portfolio state; safety hooks.

Spec Kit MUST NOT autonomously select the next backlog item. Spec Kit does not replace
Product Manager, Business Analyst, Payments Analyst, Technical Architect, or
Verification Architect roles — select Skills by profile and impact.

## Spec Kit Execution Scope

This constitution applies to the Spec Kit workflow phases:

| Phase | Skill | Governance gate |
|-------|-------|-----------------|
| Constitution | `speckit-constitution` | STANDARD/DECISION task + source map |
| Specify | `speckit-specify` | spec aligned with governance; no parallel ACTIVE |
| Clarify | `speckit-clarify` | unresolved items marked; no scope expansion |
| Plan | `speckit-plan` | policy profile, impact surfaces, verification profile |
| Tasks | `speckit-tasks` | ordered tasks within `allowed_paths`; tests where required |
| Analyze | `speckit-analyze` | artifact consistency; not a Debina completeness substitute |
| Implement | `speckit-implement` | ACTIVE + APPROVED + allowed_paths before writes |
| Converge | `speckit-converge` | no scope expansion without re-impact analysis |

## Governance

### Amendment procedure

Changing this constitution requires:

- an active `STANDARD` or `DECISION` task with explicit scope;
- a governance source map showing canonical references;
- impact check on dependent `.specify/templates/**`;
- human review;
- semantic versioning and dated amendment record.

### Versioning

- **MAJOR** — precedence, authority order, or mandatory principle change;
- **MINOR** — new principle or material extension;
- **PATCH** — clarification without semantic change.

### Compliance review

Every spec, plan, tasks document, and implementation review MUST verify compliance
with this constitution and all higher authorities listed in Principle I.

**Version**: 1.0.0 | **Ratified**: 2026-07-29 | **Last Amended**: 2026-07-29
