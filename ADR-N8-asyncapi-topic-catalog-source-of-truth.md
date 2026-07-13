# ADR-N8 — §3.7 v2 Topic Table Is the Sole AsyncAPI Source

## Status

Frozen

## Context

The Kafka topic catalog has drifted across patches. The main blueprint's §3.7 table is the frozen baseline, but two later integration patches introduced topics and renamed events without updating it: the egress integration patch adds `egress.dead_lettered` and `egress.manual_intervention_required` as ops-visible Kafka topics (§7 of that patch) that never made it into §3.7; the reconciliation integration patch renames the run/exception events to `reconciliation.run.completed`, `reconciliation.run.failed`, and `reconciliation.exception.detected` (§8 of that patch), while §3.7 still shows the older `reconciliation.completed` and `reconciliation.mismatch.detected`. Anyone generating an AsyncAPI spec or wiring a producer/consumer today would not know which name is current without reading every patch document.

## Decision

`[FREEZE]` **The main blueprint's §3.7 topic table, updated to v2 by this patch, is the single source of truth for every Kafka topic in the system.** AsyncAPI specifications are generated from this table, not authored by hand elsewhere. Every integration-patch document that mentions a topic name must reference §3.7 v2 rather than restate or rename it. The v2 table adds the two egress ops topics and standardizes on the reconciliation patch's names (`reconciliation.run.completed`, `reconciliation.run.failed`, `reconciliation.exception.detected`); the older names are retired with an alias note, not left as a second valid spelling. Enduring rules, reaffirmed: Kafka is internal-only (external parties never consume it directly); one producer owns each topic; every topic has an explicit key and ordering guarantee; every consumer group is inbox-gated; no topic-per-profile, no topic-per-status, no topic-per-transport-type; the simulator's only entry point into real traffic is `csm.response.received`.

## Consequences

- One AsyncAPI generation source; topic provisioning scripts (Iteration 0, TS-07 equivalent) read §3.7 v2 directly.
- Any future integration patch that needs a new topic adds a row to §3.7 v2 in the same edit — patch documents no longer carry their own competing topic tables.
- A topic name appearing in a patch document but not in §3.7 v2 is a documentation defect (`[EVENT-RISK]`), flagged and fixed before the next contract-generation run.
- Closes R-10 from the blueprint review.

## Alternatives Rejected

- **Let each integration patch own its topics; reconcile at generation time** — rejected: this is the exact process that produced the current drift; reconciliation-at-generation-time is manual, error-prone, and already failed twice (egress, reconciliation).
- **Version the AsyncAPI spec independently of the blueprint** — rejected: introduces a second document that can itself drift from §3.7; keeping the table and the generation source identical removes an entire class of drift.
