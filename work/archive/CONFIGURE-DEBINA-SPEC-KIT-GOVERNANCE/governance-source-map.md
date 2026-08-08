# Governance Source Map — CONFIGURE-DEBINA-SPEC-KIT-GOVERNANCE

| Constitution principle | Canonical Debina source | Authority | Copied or referenced |
| ---------------------- | ----------------------- | --------- | -------------------- |
| I — Canonical Governance and Precedence | `AGENTS.md` (constitutional rules, lean workflow) | Root agent policy | REFERENCED |
| I — Authority order (law, ADR, FREEZE) | `docs/standards/SOURCE-AUTHORITY-MATRIX.md` | Standards | REFERENCED |
| I — ADR binding | `AGENTS.md`, `docs/architecture/ADR-LIFECYCLE.md` | Architecture | REFERENCED |
| II — Task state (`work/ACTIVE.json`) | `AGENTS.md`, `docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md` §6 | Governance | REFERENCED |
| II — Approval policy | `docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md` §7 | Governance | REFERENCED |
| II — Harness wrappers | `tools/agent/bootstrap_task.py`, `closeout_task.py` | Harness | REFERENCED |
| III — FAST/STANDARD/DECISION profiles | `AGENTS.md` lean delivery; harness manual §6 | Root + governance | REFERENCED |
| III — FAST not security bypass | Harness manual §6 | Governance | REFERENCED |
| III — DECISION triggers | `AGENTS.md` DECISION lane definition | Root agent policy | REFERENCED |
| IV — LedgerPort-only | `docs/analysis/DEBINA-FINALITY-LEDGERPORT-DECISION-PACKET.md`, ADR-N9/N10 | Accepted decision | REFERENCED |
| IV — One writer per schema | `docs/data/ISO-XML-DOMAIN-DATABASE-MAPPING-METHOD.md` | Data method | REFERENCED |
| IV — RLS-only tenant isolation | Same | Data method | REFERENCED |
| IV — Five status axes | `docs/analysis/annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md` | Analysis annex | REFERENCED |
| IV — Source authority tags | `docs/standards/SOURCE-AUTHORITY-MATRIX.md` | Standards | REFERENCED |
| V — Cross-stack surfaces | User task spec; harness impact expectations | Task + governance | REFERENCED |
| VI — Verification profiles | `AGENTS.md` (verify-fast, verify-task, final-check) | Root agent policy | REFERENCED |
| VI — Dagger when PIPELINE_IMPACT | `AGENTS.md`, `docs/ci/DAGGER-PIPELINE-ARCHITECTURE.md` | CI | REFERENCED |
| VII — Agent safety (no commit/push) | `AGENTS.md`, harness manual §1, §9 | Root + governance | REFERENCED |
| VII — Hooks protect boundaries | Harness manual §6, `.cursor/hooks/**` | Governance | REFERENCED |
| VIII — Skills ownership | `tools/agent-config/CURSOR-SKILLS-OWNERSHIP.md`, runtime discovery evidence | Agent config | REFERENCED |
| VIII — Spec Kit v0.14.3 scope | `.specify/integration.json`, cursor-agent manifest | Spec Kit install | REFERENCED |
| Governance — Amendment | Harness manual §6–7; this task user specification | Task approval | REFERENCED |
| Governance — Versioning 1.0.0 / 2026-07-29 | User task specification | Explicit user decision | REFERENCED |

No new governance decisions invented. All principles trace to accepted sources or explicit user instruction.
