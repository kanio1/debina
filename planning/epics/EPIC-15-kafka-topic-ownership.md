---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-04-outbox-inbox-kafka-thin]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-7, line 1263); sepa-nexus-blueprint-ownership-integration.md §9 (line 351, wersja z ADR-N8: generacja z §3.7 v2, trzy topiki terminalne)"
---

# EPIC-15 — Ownership: własność topiców Kafka (EPIC-OWN-7)

Generowane z §3.7 v2 głównego blueprintu (jedyne źródło prawdy, ADR-N8) — żaden dokument patch nie definiuje lokalnie własnej tabeli topiców.

## Story 15.1 — Generacja AsyncAPI z §3.7 v2

status: not-started
depends_on: []

Taski:
- [ ] **Skrypt/proces generujący specyfikację AsyncAPI wyłącznie z §3.7 v2 tabeli topiców (main blueprint), nie z lokalnej kopii.**
      `verify: test -f infra/asyncapi/asyncapi.yaml && grep -q "generated from §3.7 v2" infra/asyncapi/asyncapi.yaml` (lub równoważny znacznik pochodzenia).

## Story 15.2 — Jeden producent na topic + testy kontraktowe

status: not-started
depends_on: [Story 15.1]

Taski:
- [ ] **Test kontraktowy: schemat producenta zgodny z §3.7 v2 dla każdego topicu.**
      `verify: ./mvnw -f backend test -Dtest=*KafkaTopicContractTest*`

## Story 15.3 — Trzy topiki terminalne (ADR-N8 delta)

status: not-started
depends_on: [Story 15.1]

Opis: `egress.dead_lettered`, `egress.manual_intervention_required`, `reconciliation.run.failed` — dodane przez ADR-N8 do §3.7 v2.

Taski:
- [ ] **Potwierdź istnienie trzech topiców terminalnych w katalogu i ich konsumentów operacyjnych.**
      `verify: docker exec <kafka-container> kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -E "egress.dead_lettered|egress.manual_intervention_required|reconciliation.run.failed"`
