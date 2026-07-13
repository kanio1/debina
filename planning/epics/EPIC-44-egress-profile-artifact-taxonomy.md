---
status: not-started
depends_on: [EPIC-43-egress-rail-outbound-dispatch]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-1, line 1303), [MVP]"
---

# EPIC-44 — Egress: profil i taksonomia artefaktów (EPIC-EGRESS-1)

## Story 44.1 — Katalog `egress_profile` + snapshoty

status: not-started
depends_on: []

Taski:
- [ ] **Katalog `egress_profile` + snapshoty per artefakt.**
      `verify: psql -c "\d egress.egress_profile"`

## Story 44.2 — `outbound_artifacts` + klucz idempotencji

status: not-started
depends_on: [Story 44.1]

Opis: `UNIQUE(trigger_event_id, artifact_type, recipient)`.

Taski:
- [ ] **Migracja `egress.outbound_artifacts` z unikalnym kluczem idempotencji `(trigger_event_id, artifact_type, recipient)`.**
      `verify: psql -c "\d egress.outbound_artifacts"` → unique constraint obecny.

## Story 44.3 — Mapa typ-artefaktu→trigger

status: not-started
depends_on: [Story 44.1]

Taski:
- [ ] **Mapa artifact-type/trigger jako katalog konfiguracyjny.**
      `verify: ./mvnw -f backend test -Dtest=*ArtifactTriggerMapTest*`
