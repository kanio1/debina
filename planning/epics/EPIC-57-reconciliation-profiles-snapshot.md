---
status: not-started
depends_on: [EPIC-12-reference-data-ownership]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-RECON-1, line 1312), [MVP]"
---

# EPIC-57 — Reconciliation: profile i model snapshotu (EPIC-RECON-1)

## Story 57.1 — Katalog `reconciliation_profile`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `reconciliation.reconciliation_profile`.**
      `verify: psql -c "\d reconciliation.reconciliation_profile"`

## Story 57.2 — `reconciliation_profiles_snapshot`

status: not-started
depends_on: [Story 57.1]

Taski:
- [ ] **Snapshot profilu per run.**
      `verify: psql -c "\d reconciliation.reconciliation_profiles_snapshot"`

## Story 57.3 — Deterministyczny `as_of` + `reconciliation_run_sources`

status: not-started
depends_on: [Story 57.1]

Taski:
- [ ] **Deterministyczny znacznik `as_of` + rejestr źródeł runa.**
      `verify: ./mvnw -f backend test -Dtest=*DeterministicAsOfTest*`

## Story 57.4 — Cykl życia runa

status: not-started
depends_on: [Story 57.3]

Taski:
- [ ] **FSM cyklu życia `reconciliation_run`.**
      `verify: ./mvnw -f backend test -Dtest=*ReconciliationRunLifecycleTest*`
