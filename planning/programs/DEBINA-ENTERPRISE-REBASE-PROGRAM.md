# Debina Enterprise Rebase Program

This program pauses feature expansion, including Wave 12, until governance work is complete. It preserves existing implementation and frozen invariants.

| Phase | Outcome | Completion criteria |
|---|---|---|
| A — stop expansion and adopt constitution | ADR-N15–N17, authority and governance policy accepted | constitution links valid; no feature work started |
| B — product/domain/architecture model | concept model, module taxonomy, C4/context/quality method | source-backed assumptions and catalog review complete |
| C — top enterprise use-case catalogue | ranked business/system UCs, slices/rules/traces | top catalogue accepted, no invented rail behaviours |
| D — minimal smoke only | executable narrow smoke design/implementation when separately authorized | six approved smoke journeys evidenced |
| E — semantic governance and Dagger | validators and Go Dagger implementation | SV-01–SV-12 cover the recorded baseline and CI mapping passes |
| F — backlog migration | selected backlog slices traced and stale claims reconciled | migration cohort validated; no mass cosmetic rewrite |
| G — resumed feature delivery | next READY slice selected through UC2 | dependencies, ownership and verify are executable |

## Follow-up goals in recommended order

1. **Governance implementation** — inputs: this constitution, inventory, capability graph, semantic-validation baseline; done when SV-01–SV-12 are implemented or explicitly staged with CI integration.
2. **Use-case catalogue and traceability migration pilot** — inputs: source registry, top existing payment/approval flows; done when a small prioritized cohort has approved UC2 records and trace links.
3. **Architecture evidence pack** — inputs: module catalog/context map; done when C4 landscape/context/container/deployment and selected dynamics are reviewed.
4. **Dagger Go pipeline** — inputs: supported local commands and quality scenarios; done when targets run locally/CI through thin triggers.
5. **Minimal smoke implementation** — inputs: accepted ADR-N16 and stable runtime routes; done when the six-scope smoke has runtime evidence.
6. **Backlog migration** — inputs: validators and catalogue; done when feature queue is slice/quality-use-case traced and semantic drift is resolved.
7. **Resume feature delivery** — inputs: all previous phases; done per the next UC2 slice, never this program alone.
