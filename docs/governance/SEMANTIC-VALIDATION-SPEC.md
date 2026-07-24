# Semantic Validation Specification

The next governance goal implements deterministic checks with a stable diagnostic ID, source location, severity, and remediation. Inputs are planning files/inventory/capability graph, module catalog, ADRs, source registry, use-case records, and repository symbols.

| ID | Check | Evidence / action |
|---|---|---|
| SV-01 | done story has unchecked task | parse story block; error |
| SV-02 | done epic has unfinished story | compare frontmatter and story statuses; error |
| SV-03 | done story has stale blocker language | allow explicit historical marker only; warning/error after review |
| SV-04 | verify target does not exist | resolve executable/file/test target; error |
| SV-05 | declared epic count differs from inventory | compare index, epics, inventory; error |
| SV-06 | future story lacks slice or quality-use-case trace | require ID from migration cutover; error |
| SV-07 | module lacks catalog classification | compare module declarations/catalog; error |
| SV-08 | new aggregate lacks admission record | detect declared aggregate; error |
| SV-09 | new ADR lacks lifecycle metadata | parse required fields; error |
| SV-10 | source-backed rule lacks source reference | tag/registry-key check; error |
| SV-11 | stale “capability does not exist” claim | compare tagged claims against implementation/capability evidence; warning requiring review |
| SV-12 | duplicate capability ownership | graph/catalog owner uniqueness; error |
| ESR-001–006 | ENFORCED story lacks a real use case, slice, process, actor goal or stable flow | resolve exact identifiers against the current catalogues; error |
| ESR-007–009 | ENFORCED story lacks source classification, per-claim evidence or evidence-backed rules | registry presence alone is insufficient; error |
| ESR-010–013 | ENFORCED story lacks module ownership, architecture, security or quality realization | resolve module and quality IDs; error |
| ESR-014–016 | verify is a placeholder, READY has an unresolved source gap, or done retains `NOT RUN` | fail closed with the exact diagnostic; error |
| ESR-017 | technical-only work invents a business use case/slice | route to a quality, architecture, infrastructure or governance scenario; error |

Start with SV-01–SV-05 as deterministic extensions of existing validators, retain exemptions only with reason/expiry, and never infer domain truth from a grep hit alone.

The `ESR-*` rules are active only for the explicit
`semantic_enforcement: ENFORCED` marker. Focused fixtures exercise both a valid
record and every fail-closed class. This is gradual enforcement: it does not
silently rewrite or upgrade legacy stories.

## Baseline audit — 2026-07-21

This constitution rebase inspected but deliberately did not mass-migrate legacy planning records.
The current generator/validator reports 79 epic files and 304 stories, while historical prose still
contains a 76-epic description. A read-only parser found eight `done` stories containing unchecked
tasks, one `done` epic (`EPIC-26`) whose Story 26.4 is still formally `in-progress`, and 23
candidate done-story blocks containing blocker/absence vocabulary that need semantic—not
grep-only—review. The candidates include completed records around GraphQL and evidence-audit.

Concrete stale-capability candidates are present in `planning/README.md` (the Phase 1 GraphQL
absence statement and EPIC-76 evidence-audit absence statement) and in historical planning/analysis
files. They conflict with current `com.sepanexus.graphql` and `com.sepanexus.evidenceaudit`
application modules and Waves 9–11 evidence. Phase F must classify each claim as historical,
still true, or stale before editing it; no legacy status is silently rewritten by this rebase.
