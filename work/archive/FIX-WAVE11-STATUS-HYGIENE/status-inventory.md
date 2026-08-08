# FIX-WAVE11-STATUS-HYGIENE — Status inventory

| Source | Current claim | Evidence | Problem | Target semantics | Planned change |
|---|---|---|---|---|---|
| Wave 11 program | CP1 implemented/not complete; CP2 live and focused 32/32; remaining still includes two full runs and governance work; no CP3 section | `planning/programs/DEBINA-ISO-LINEAGE-IDENTIFIER-EVIDENCE-WAVE-11.md` | Post-CP2 remaining work is not summarized as an explicit formal-completion ceiling | CP2 proven; CP3 pending; remaining open; formal completion not reached | Add an explicit evidence-status and remaining-proof summary without waiving CP3 |
| EPIC-26 frontmatter | `status: done` | Epic frontmatter | Status defect while Story 26.4 is `in-progress` | Epic remains incomplete while required Story 26.4 is open | Change epic to `in-progress` |
| Story 26.4 | `in-progress`; tasks checked; note cites live proof and `540/540` | EPIC-26 Story 26.4 | Live evidence corresponds to CP2, but `540/540` is not identified as Wave 11 CP3 | Keep non-terminal; implementation and CP2 proven; formal completion open | Rewrite status note; retain `in-progress`; remove `540/540` as formal close evidence |
| Story 26.4 historical notes | GraphQL does not exist / blocked on GraphQL | Historical Story 26.4 notes | Stale against Query-only GraphQL implementation | Historical constraint superseded by Waves 9–11 | Mark the historical claim as superseded |
| Planning README | EPIC-26 done; Stories 26.1–26.4 done; Wave 11 delivered | `planning/README.md` | Overclaim against formal story state and remaining proofs | Partial delivery, CP2 proven, formal completion open | Correct the epic/index summary |
| Story inventory | Story 26.4 is `in-progress` | `planning/story-inventory.json` | Correct relative to story, inconsistent with epic and README | Preserve `in-progress` | Regenerate after epic correction |
| Capabilities YAML | Story 26.4 is `blocked`; GraphQL does not exist anywhere | `planning/capabilities.yaml` | Status and note are stale | Story 26.4 `in-progress`; implementation exists; remaining proofs open | Correct status and note; do not set `done` |
| Capability graph | Story 26.4 `blocked`; GraphQL owner gate `RESOLVED` | `planning/capability-graph.json` | Story status drifts from canonical epic; owner gate conflicts with an external decision document outside task scope | Story 26.4 `in-progress`; owner gate unchanged in this task | Synchronize Story 26.4 only; record owner-gate conflict as a finding |
| Checkpoint 2 | Live Keycloak, BFF, Spring and PostgreSQL proof; JSON_DIRECT and signed pain.001; focused tests 32/32 | Wave 11 CP2 evidence | Correct evidence is obscured by broader done language | `CHECKPOINT_2_PROVEN` | Cite CP2 as the current live-proof ceiling |
| Checkpoint 3 | No documented CP3 section or completion evidence | Absence in the Wave 11 program | Formal evidence gap | `CHECKPOINT_3_PENDING` | Explicitly state pending; do not change criteria |
| `540/540` claim | Two full backend regression runs passed | `docs/ci/DAGGER-IMPLEMENTATION.md`, D2B, dated 2026-07-22 | Evidence belongs to Dagger Phase D regression scope, not automatically to Wave 11 CP3 | Scoped Dagger evidence only | Remove from formal Wave 11 close argument; optional scoped footnote |
| Query-only GraphQL | `paymentIsoEvidence`, Query-only schema, BFF allowlist and UI integration exist | GraphQL schema, backend query, BFF route and tests | Historical “GraphQL absent” claims are false as current-state statements | Implementation delivered, Query-only | Remove or supersede stale current claims |
| Enterprise Rebase | Pause governed by Phase F / Wave 12; no formal Wave 11 close statement | Enterprise Rebase program | Not contradictory, but not proof of Wave 11 completion | No change | Leave unchanged |
| Validators | SV-02 exists in semantic specification, but the current contradiction does not hard-fail | Semantic validation specification and validator | Validator may permit epic `done` with unfinished required story | Existing status model enforced consistently | Change only if source corrections alone do not address the validation gap |

## Findings

### Status model

The status model:

```text
not-started
in-progress
blocked
done
```

does not independently represent:

* implementation completion;
* runtime-proof completion;
* formal program completion.

This task will use existing statuses plus precise prose. It will not extend the schema.

### Meaning of `blocked`

`blocked` represents an inability to proceed because of an unresolved dependency, decision or missing capability.

Story 26.4 is no longer blocked by an absent GraphQL implementation. Its correct formal state is `in-progress`, analytically corresponding to “implemented but not fully verified”.

It must not be changed directly from `blocked` to `done`.

### Capability graph maintenance

The repository contains a story inventory generator.

No `generate-capability-graph.py` was found during discovery. The capability graph appears to be co-maintained with `capabilities.yaml` and validated through `validate-capability-graph.py`.

Implementation must confirm and follow the current documented maintenance procedure rather than inventing a generator.

### External decision conflict

The capability graph’s resolved GraphQL owner gate may conflict with a separate decision document that remains open.

That conflict is outside the current allowed scope and must not be silently resolved by this task.
