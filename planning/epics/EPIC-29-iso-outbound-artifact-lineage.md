---
status: not-started
depends_on: [EPIC-44-egress-profile-artifact-taxonomy]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-4, line 1271), [P1]"
---

# EPIC-29 — ISO: lineage artefaktów wychodzących (EPIC-ISO-4, `[P1]`)

## Story 29.1 — `iso.iso_outbound_artifacts`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `iso.iso_outbound_artifacts`.**
      `verify: psql -c "\d iso.iso_outbound_artifacts"`

## Story 29.2 — Snapshot render-profile/version

status: not-started
depends_on: [Story 29.1]

Taski:
- [ ] **Zapis snapshotu render-profile/version przy każdym wygenerowanym artefakcie.**
      `verify: ./mvnw -f backend test -Dtest=*RenderProfileSnapshotTest*`

## Story 29.3 — Rozdział lineage pacs.002/result-file od transportu

status: not-started
depends_on: [Story 29.1]

Taski:
- [ ] **Test: lineage wychodzące oddzielone od stanu transportu (egress boundary z EPIC-14).**
      `verify: ./mvnw -f backend test -Dtest=*OutboundLineageVsTransportSeparationTest*`

## Story 29.4 — Panel evidence wychodzącego (Playwright)

status: not-started
depends_on: [Story 29.1, EPIC-24-frontend-screens]

Taski:
- [ ] **Panel Playwright na evidence wychodzącym.**
      `verify: npm run test:e2e -- --grep "@smoke.*outbound-evidence"`
