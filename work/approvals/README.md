# Approvals

Place human-authored files here as:

```text
work/approvals/<TASK-ID>.approved
```

For DECISION lane / policy_profile:

```text
work/approvals/<TASK-ID>.decision.approved
work/approvals/<TASK-ID>.implementation.approved
```

## Semantics

```text
.approved  → implementation approval
.approved  ≠ permission for analysis artifacts in work/**
```

Canonical ACTIVE field:

```text
approval_state: NOT_REQUIRED | PENDING | APPROVED | REJECTED
```

Rules:

- `STANDARD` + `ANALYZING` + `WORK_ONLY` does **not** require `.approved`.
- `STANDARD`/`DECISION` + `IMPLEMENTING` requires `approval_state=APPROVED` (compat marker `.approved`).
- Agents must not forge approval files. Humans author them; then run `./tools/agent/approve-task`.
- Copy from `work/templates/approval-template.txt`.
