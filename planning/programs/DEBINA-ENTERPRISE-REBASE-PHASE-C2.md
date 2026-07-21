# Debina Enterprise Rebase — Phase C2

Baseline `f2369fc`; branch `rebase/enterprise-evolution`. C2 applies Cockburn fully dressed narratives, Use-Case 2.0, Example Mapping, ISO/EPC/rail source classification, DDD concept admission, C4 dynamic views, arc42 quality links and ATAM-lite.

Catalogue outcome: the C1 approval pilot plus 14 C2 enforced actor-goal records; approved records are single SCT, inbound status and audit investigation, while source/participant-dependent file, clearing, deferred cycle and R-transaction records remain blocked. The concept-admission record admits no new aggregate. Backlog mapping remains bounded and legacy warnings are intentionally retained.

Validation and mutation evidence: the complete local governance runner passes with 15 use cases and 38 slices; legacy warnings remain 296 traceability and 69 planning. C2 proved missing methodology (`UCT-008`), unsupported methodology (`UCT-008`), missing mapped slice (`UCT-010`), duplicate slice (`UCT-001`) and invalid rail status (`SRC-004`), restoring every mutation before commit.

Architecture findings: no new aggregate was admitted; file/group, clearing and deferred-cycle boundaries are source/aggregate review candidates; no ADR is changed. Six high-value dynamic diagrams cover single SCT, file, approval, instant settlement, inbound status and audit investigation. Bounded backlog map covers five representative groups with no story metadata bulk migration.

Phase D entry commands: enterprise governance runner; backend fast/integration regression; frontend codegen/lint/typecheck/build; local smoke; minimal Playwright smoke. C2 adds no Dagger implementation or remote CI choice.
