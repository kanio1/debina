---
status: not-started
depends_on: [EPIC-43-egress-rail-outbound-dispatch/Story 43.4]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-5, line 1307), [MVP]"
---

# EPIC-48 — Egress: dostawa wsadowego pliku wynikowego (EPIC-EGRESS-5)

## Story 48.1 — Kolektor

status: not-started
depends_on: []

Opis: współdzielony z EPIC-43 Story 43.4.

Taski:
- [ ] **Kolektor budujący plik wynikowy.**
      `verify: ./mvnw -f backend test -Dtest=*BatchCollectorTest*` (współdzielony z EPIC-43).

## Story 48.2 — Plik wynikowy pain.002-style, jeden artefakt per plik

status: not-started
depends_on: [Story 48.1]

Taski:
- [ ] **Renderowanie pliku wynikowego w stylu pain.002, jeden artefakt = jeden plik.**
      `verify: ./mvnw -f backend test -Dtest=*ResultFileOneArtifactPerFileTest*`

## Story 48.3 — Dashboard plików wynikowych (Playwright)

status: not-started
depends_on: [Story 48.2, EPIC-24-frontend-screens]

Taski:
- [ ] **Dashboard Playwright na plikach wynikowych.**
      `verify: npm run test:e2e -- --grep "@smoke.*result-file"`
