# Phase B Gap Assessment

| Class | Finding / evidence | Recommendation | ADR / UC / implementation | Priority |
|---|---|---|---|---|
| MODEL-DOCUMENTATION-GAP | `payment.payments` suffices for current one-payment lifecycle; PC-01 | retain current representation | UC2 may revisit PaymentInstruction | High |
| USE-CASE-GAP | instruction/group/file cardinality has no current invariant; PC-02–PC-06 | do not split/admit before file UC2 and source profile | EUC-03; future implementation | High |
| ARCHITECTURE-GAP | ClearingSubmission and stronger TransportFile lifecycle lack source/use-case evidence | rail-specific admission only; delivery receipt/render profile first | EUC-11; ADR only if boundary changes | High |
| SOURCE-GAP | interchange/batch/bulk/submission meanings vary; public landing pages insufficient | record participant documentation required | EUC-03/EUC-11 | High |
| ARCHITECTURE-GAP | ingress/ISO are physically packaged in payment lifecycle | assess after UC2; no refactor now | ADR-REVIEW-CANDIDATE if justified | Medium |
| ARCHITECTURE-GAP | GraphQL is demand-driven but could become broad | source-port catalogue/fitness validation | C1 validator | Medium |
| IMPLEMENTATION-GAP | reporting/reconciliation/case remain absent or partial | realize only by prioritized use case | EUC-09–13 | Medium |
| PLANNING-DRIFT | stale statuses/blockers/count claims found in baseline audit | validators before controlled batches | C1 then E | High |
| VALIDATION-GAP | quality scenarios had few measures | use new scenarios as Phase C1/ D targets | C1/D | High |
| DECISION-REQUIRED | new same-transaction coordination beyond gross instant | require source/use case and ADR lifecycle review | future ADR candidate | High |
