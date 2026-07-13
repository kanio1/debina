# ADR-N6 — One Priority Taxonomy

## Status

Frozen

## Context

Two incompatible priority systems exist side by side. The Master Architecture & State document plans work in five numbered **Iterations** (1 — Spine, 2 — Ledger+Instant, 3 — Simulation, 4 — Deferred+Reconciliation, 5 — ISO+Routing), all implicitly "MVP." The main message-flow blueprint and its patches instead tag every table/epic/feature with **`[MVP]` / `[P1]` / `[P2]`**, independently of iteration number. The two systems disagree concretely: `simulation` is Master's Iteration 3 core engine but is tagged `[P1] [EDU-ONLY]` in blueprint §3.6.2; `reconciliation` is Master's Iteration 4 but `[MVP]` in the v9-patched blueprint. Planning cannot proceed with two priority vocabularies that contradict each other on load-bearing modules.

## Decision

`[FREEZE]` **One taxonomy.** `[MVP]` = everything scheduled inside Iterations 0–5 (Iteration 0 per ADR-N1, plus Master's Iterations 1–5). `[P1]` = the first post-MVP wave (case module full depth, egress P1 artifacts, business calendars, reconciliation P1 types, operator worklist, FAPI-2/passkeys, audit hash-chaining, settlement retries/queue/CGS/prefunded). `[P2]` = second-wave breadth and labs (risk/VoP, search, statements, PG19 feature promotions on GA, routing gRPC extraction per ADR-N2, SQL/PGQ, 3D visualization). Specifically: **`simulation` is re-tagged `[MVP]`, Iteration 3** — it is the determinism keystone the review calls "the demo that sells the project," and demoting it to `[P1]`/`[EDU-ONLY]` as a priority (as opposed to a *nature* label) was an error the taxonomy conflict produced. `reconciliation` core (4 MVP types) stays `[MVP]`, Iteration 4, as already correctly patched in blueprint v9 — any older document still reading `[P1]` for reconciliation is superseded by this ADR.

## Consequences

- Every `[MVP]`/`[P1]`/`[P2]` tag in the blueprint and its patches is now interpreted against the Iteration 0–5 boundary; no module is "MVP" by feature-tag alone if its iteration number places it after Iteration 5.
- `[EDU-ONLY]` remains a valid *nature* label (this module exists to teach, not to move real business volume) but is no longer conflated with a *priority* label; a module can be both `[MVP]` and `[EDU-ONLY]` (simulation is exactly this).
- Backlog planning tools reference iteration number as the primary sequencing key; `[MVP]/[P1]/[P2]` is the secondary filter for post-MVP triage.
- Closes R-02 and R-05 from the blueprint review.

## Alternatives Rejected

- **Keep both taxonomies and cross-reference them ad hoc** — rejected: this is exactly the state that produced the simulation/reconciliation contradictions; ad hoc cross-referencing does not scale past two modules, let alone seventeen.
- **Drop the Iteration numbering, keep only MVP/P1/P2** — rejected: iteration numbers carry sequencing and dependency information (e.g. "ledger before deferred settlement") that a flat priority tag cannot express.
