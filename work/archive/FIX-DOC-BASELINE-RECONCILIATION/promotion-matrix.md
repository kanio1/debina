# Promotion matrix

Baseline package: `work/imports/SEPA-2027-MASTER-PLANNING/debina-master-planning/`  
Local HEAD: `b7cae78bfc1dd2bb6176c26dd46c5dcdee9ba4be` on `rebase/enterprise-evolution`  
Package SHA: `f601089d2f123ff01f41378f47be5dd9ce361fd2`  
Rule: current repo + ADR/`[FREEZE]` win; no copy in this task.

| Dokument źródłowy | Status | Docelowa ścieżka | Kolizja | Wymagana aktualizacja | Decyzja |
| --- | --- | --- | --- | --- | --- |
| `README.md` (package root) | `KEEP_AS_EVIDENCE_ONLY` | none (do not overwrite root `README.md`) | would overwrite Debina constitution README | keep under imports only | never promote as root README |
| `MANIFEST.md` | `KEEP_AS_EVIDENCE_ONLY` | `work/imports/.../MANIFEST.md` (stay) | none | note local HEAD ≠ package baseline | evidence of package integrity only |
| `IMPORT-TO-LOCAL-REPO.md` | `KEEP_AS_EVIDENCE_ONLY` | none | import procedure superseded by this reconciliation | mark Wave 11 check as classified here | do not copy; follow matrix instead |
| `SOURCE-COVERAGE.md` | `ACCEPT_AFTER_UPDATE` | `docs/evidence/claim-maps/SOURCE-COVERAGE-2027.md` (candidate) | none at path; corpus external | refresh against local corpus availability; keep checksum mismatch explicit | promote only after corpus path decision (DR-011) |
| `CONSISTENCY-REVIEW.md` | `ACCEPT_AFTER_UPDATE` | `work/active/SEPA-2027-MASTER-PLANNING/CONSISTENCY-REVIEW.md` (if package task revived) or evidence folder | none | replace open Wave 11 conflict with classified verdict from this task | do not treat as architecture approval |
| `docs/product/debina-product-vision.md` | `MERGE_WITH_EXISTING` | `docs/product/PRODUCT-VISION.md` | semantic collision with existing PRODUCT-VISION | merge 2027 research framing without weakening ADR-N15; do not create parallel `debina-product-vision.md` | human merge review |
| `docs/product/debina-capability-map.md` | `MERGE_WITH_EXISTING` | `docs/product/BUSINESS-CAPABILITY-MAP.md` (+ link to `planning/capabilities.yaml`) | semantic collision with BUSINESS-CAPABILITY-MAP; must not replace capability graph | update epic count 76→79; Wave 11 lineage = implemented/partial formal close; keep 2027 overlay as research, not backlog | human merge; graph remains canonical for story IDs |
| `docs/product/roadmap.md` | `ACCEPT_AFTER_UPDATE` | `docs/product/roadmap.md` | path free; content assumes remote baseline | exit criterion “Wave 11 konflikt rozwiązany” → point to this reconciliation; rebase Phase D/E/F local progress; remove any implication package is approved architecture | defer until Wave 11 hygiene |
| `docs/product/documentation-backlog.md` | `ACCEPT_AFTER_UPDATE` | `docs/product/documentation-backlog.md` | path free | drop “update HANDOFF/Wave11” as NOW if hygiene task owns it; align with local HANDOFF topic (MCP/E1) | defer copy |
| `docs/architecture/current-state.md` | `ACCEPT_AFTER_UPDATE` | `docs/architecture/current-state.md` | path free; overlaps MODULE-CATALOG / program records | retarget baseline to local HEAD; fix Dagger from PROPOSED→Phase D complete on this branch; replace Wave 11 CONFLICT with classified verdict; epic count | defer copy |
| `docs/architecture/target-state.md` | `ACCEPT_AFTER_UPDATE` | `docs/architecture/target-state.md` | path free; must not supersede ADRs | keep DRAFT/proposal labels; require architecture-evolution-review before any admission language | defer; not implementation approval |
| `docs/architecture/context/debina-context.md` | `MERGE_WITH_EXISTING` | `docs/architecture/c4/SYSTEM-CONTEXT.md` / `SYSTEM-LANDSCAPE.md` | C4 context already exists | merge only net-new synthesis; preserve non-participation / simulated rail claims | human merge into c4/ |
| `docs/architecture/containers/debina-containers.md` | `MERGE_WITH_EXISTING` | `docs/architecture/c4/CONTAINERS.md` | containers C4 already exists | same as context | human merge into c4/ |
| `docs/architecture/domain-boundaries.md` | `MERGE_WITH_EXISTING` | `docs/architecture/CONTEXT-MAP.md` (+ MODULE-CATALOG) | Context Map is canonical | strengthen existing map; no split/merge module proposals as accepted | merge as commentary, not new BC map |
| `docs/architecture/lifecycle-model.md` | `MERGE_WITH_EXISTING` | domain/lifecycle docs + ADR status-axis rules (no single identical path) | must not invent sixth axis or collapse axes | keep five axes separate; cite payment-state-finality skill / ADRs | promote only if it cites canonical status docs |
| `docs/evidence/claim-maps/master-traceability.md` | `ACCEPT_AFTER_UPDATE` | `docs/evidence/claim-maps/master-traceability.md` | path free | retarget SHAs; mark VOP/EDS/SDD rows SOURCE_BLOCKED; do not claim compliance | defer; useful navigation after Wave 11 hygiene |
| `work/active/SEPA-2027-MASTER-PLANNING/findings.md` | `KEEP_AS_EVIDENCE_ONLY` | stay under imports (or archive) | would create parallel active task | F-010 superseded by this task’s Findings; F-004 epic count stale; F-005 pause still true | do not install as live active task |
| `work/active/SEPA-2027-MASTER-PLANNING/decisions-required.md` | `KEEP_AS_EVIDENCE_ONLY` | optional later `work/active/...` only after human opens that task | DR register ≠ approved decisions | DR-001 partially classified here; remaining DRs still HUMAN_DECISION_REQUIRED | do not auto-resolve; do not copy QUEUE from it |
| `work/active/SEPA-2027-MASTER-PLANNING/master-plan.md` | `KEEP_AS_EVIDENCE_ONLY` | none until after Wave 11 hygiene + human scope accept | would compete with Enterprise Rebase program | rebase Phase C/D largely done locally — master-plan phases need rewrite | reject as live plan until updated |
| `work/QUEUE.md` (package, MANIFEST only) | `REJECT_AS_STALE` | none — **forbidden copy** | would overwrite lean local `work/QUEUE.md` | n/a — absent from this extract anyway | never promote |
| `work/NEXT-WORK.md` (package, MANIFEST only) | `REJECT_AS_STALE` | none — **forbidden copy** | would invent parallel backlog | n/a — absent from this extract | never promote |

## Status legend (applied)

- `ACCEPT_AS_IS` — none in this pass (every candidate needs local SHA and/or Wave 11 update).
- `ACCEPT_AFTER_UPDATE` — useful synthesis after baseline/Wave 11/Dagger refresh.
- `MERGE_WITH_EXISTING` — overlaps stronger local canonical docs.
- `KEEP_AS_EVIDENCE_ONLY` — retain under imports/archive; not live authority.
- `SOURCE_BLOCKED` — used inside claim rows (VOP/EDS/STEP2), not as whole-doc status here.
- `DECISION_BLOCKED` — product DRs remain open; whole-doc promotion of vision/roadmap waits on those where noted.
- `REJECT_AS_STALE` — package QUEUE/NEXT and any overlay that fights current `work/` + HANDOFF.

## Promotion gate

No document above is copied in this task. Next promotion batch should wait for `FIX-WAVE11-STATUS-HYGIENE` so Wave 11 exit criteria in roadmap/current-state are not re-imported as an open conflict.
