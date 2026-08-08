# Policy Profile Mapping

## Principle

`FAST` | `STANDARD` | `DECISION` are **Execution Policy Profiles**, not a second workflow engine.

```text
policy_profile: FAST | STANDARD | DECISION
```

Spec Kit drives workflow stages. Profiles select rigor, experts, gates, artifacts, verification, banking analysis, and review depth.

## Current vs target

| Today | Target |
| --- | --- |
| `lane` in ACTIVE.json drives both classification and write-gate | Split: `policy_profile` (rigor) vs write/approval gate mapping |
| Hooks treat STANDARD as “need `.approved` before any Write” | STANDARD human gate before **implementation**, not before work-only audit docs |
| Four slash skills implement workflow | Spec Kit implements workflow; Debina profiles parameterize it |

Observed friction in this audit: STANDARD write-gate blocked `work/QUEUE.md` until lane set to FAST. That coupling must be fixed in a later harness change (not now).

## Profile table

| Profile | Spec Kit workflow | Debina analysis | Human gate | Verification |
| --- | --- | --- | --- | --- |
| `FAST_MICRO` | Skip full feature pack when change is trivial (typo, tiny script). Optional micro-checklist only | Minimal; no Design Council; no banking analysis | None (unless safety hook trips) | `verify-fast` / targeted unit only |
| `FAST` | Short Spec Kit path or micro-feature; may omit heavy clarify | Minimal impact notes; affected surfaces only | No mandatory plan gate | Targeted; no full Dagger unless risk |
| `STANDARD` | Full specify → clarify(as needed) → plan → tasks → implement | Debina impact analysis + required domain Skills; banking sources if payment-touching | One human approval before implementation | Profile-mapped Dagger/wrapper gate + independent review |
| `DECISION` | Spec Kit may hold evidence pack; then STANDARD implementation workflow after decision | Evidence, alternatives, recommendation; ADR/RFC when required; frozen-invariant check | `HUMAN_DECISION` (+ later implementation approval) | After decision: STANDARD verification; may require `RUNTIME_PROOF` / security profile |

## Profile semantics

### FAST / FAST_MICRO

Use for: small reversible docs, validator tweaks, tiny tests, non-domain config, limited bugfixes without contract change.

Determines: minimal analysis; no full Design Council; no mandatory banking analysis; no mandatory human plan gate; targeted verification; no full Dagger unless risk.

### STANDARD

Use for: normal features; multi-layer changes; backend+API; Kafka; migrations; user-visible behavior; domain-linked UI.

Determines: Spec Kit spec+plan; Debina impact analysis; one human approval before implementation; verification profile; independent review.

### DECISION

Use for: settlement finality; money movement invariants; new trust boundary; data ownership; tenant isolation; normative source deviation; status-model change; formal compliance claim; ADR accept/change; irreversible architecture.

Determines: evidence + alternatives + recommendation + `HUMAN_DECISION` + ADR/RFC where required; then STANDARD implementation workflow.

## Escalation

```text
FAST → discover contract change or multi-layer impact → STANDARD
STANDARD → discover frozen invariant / strategic decision → DECISION
```

- Escalation may be automatic.
- De-escalation requires explicit justification.

## Mapping to Debina Skills (examples)

| Profile | Always | Conditional |
| --- | --- | --- |
| FAST_MICRO | none / session-handoff | agent-instruction-hygiene if instruction files |
| FAST | technical-architect (light) | testing skills as needed |
| STANDARD | enterprise-use-case-engineering (if behaviour), technical-architect, domain skills by surface | source-backed-payments-modeling, postgres/kafka/iso skills |
| DECISION | debina-decision-record + architecture-evolution-review | money/finality/source skills |

## Verification profile names (Debina-owned; Spec Kit invokes)

Retain Debina names (do not replace with Spec Kit):

- `PLANNING_ONLY`
- `BACKEND_TARGETED`
- `POSTGRES_MIGRATION`
- `KAFKA_CONTRACT`
- `API_BFF_FRONTEND`
- `PAYMENT_STATE_STANDARD`
- `PAYMENT_FULL_STACK`
- `SECURITY_AUTHORIZATION`
- `RUNTIME_PROOF`

Current Dagger callable gates (`fast`, `integration`, `smoke-*`, `acceptance`, …) remain the executable substrate; the names above are policy-facing profiles to map in a later task (many not yet first-class enums in code — treat as target vocabulary).

## Simplified daily UX (target)

```text
Cursor:  /debina-start          /debina-review-next
Codex:   $debina-start          $debina-review-next
```

`debina-start` classifies `policy_profile`, selects Spec Kit workflow + Debina Skills + impact + verification, stops at human gate, resumes from repo artifacts across tools.

`debina-review-next` runs independent review (PASS / PASS_WITH_FINDINGS / CHANGES_REQUIRED / BLOCKED), then on PASS: closeout, QUEUE update, exactly one next task + Next Task Brief, stop before implementing next.
