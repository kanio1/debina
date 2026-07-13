---
status: not-started
depends_on: [EPIC-19-ingress-staging-pipeline]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-IN-2, line 1247)"
---

# EPIC-73 — Ingress: szyna plikowa (EPIC-IN-2)

## Story 73.1 — Przyjęcie pliku + idempotencja per nadawca

status: not-started
depends_on: []

Opis: unikalność `MsgId` scoped do nadawcy, nie sha-unique-archive.

Taski:
- [ ] **Endpoint przyjęcia pliku + idempotencja scoped do nadawcy na `MsgId`.**
      `verify: ./mvnw -f backend test -Dtest=*FileIngestionIdempotencyTest*`

## Story 73.2 — Job Spring Batch (SkipPolicy, test restartu)

status: not-started
depends_on: [Story 73.1]

Taski:
- [ ] **Job Spring Batch z `SkipPolicy`, test restartu po awarii w połowie.**
      `verify: ./mvnw -f backend test -Dtest=*BatchJobSkipPolicyRestartTest*`

## Story 73.3 — Dane wyniku częściowej akceptacji

status: not-started
depends_on: [Story 73.2]

Taski:
- [ ] **`file_items` + liczniki częściowej akceptacji per item.**
      `verify: ./mvnw -f backend test -Dtest=*FilePartialAcceptTest*`

## Story 73.4 — Renderowanie i dostawa pliku wynikowego

status: not-started
depends_on: [Story 73.3, EPIC-48-egress-batch-result-file-delivery]

Taski:
- [ ] **Renderowanie i dostawa pliku wynikowego przez `egress`.**
      `verify: ./mvnw -f backend test -Dtest=*ResultFileDeliveryTest*` (współdzielony z EPIC-48).
