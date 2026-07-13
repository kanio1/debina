---
status: not-started
depends_on: [EPIC-44-egress-profile-artifact-taxonomy]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-2, line 1304), [MVP]"
---

# EPIC-45 — Egress: cykl życia wiadomości wychodzącej (EPIC-EGRESS-2)

## Story 45.1 — Ujednolicony cykl życia REQUESTED→…→CLOSED

status: not-started
depends_on: []

Taski:
- [ ] **Zaimplementuj FSM `REQUESTED→…→CLOSED`.**
      `verify: ./mvnw -f backend test -Dtest=*OutboundMessageLifecycleFsmTest*`

## Story 45.2 — Render→sign→deliver

status: not-started
depends_on: [Story 45.1, EPIC-31-signature-module/Story 31.3]

Taski:
- [ ] **Łańcuch render→sign→deliver.**
      `verify: ./mvnw -f backend test -Dtest=*RenderSignDeliverChainTest*`

## Story 45.3 — Dispatcher SKIP LOCKED

status: not-started
depends_on: [Story 45.1]

Opis: współdzielony z EPIC-43 Story 43.1.

Taski:
- [ ] **Dispatcher `SKIP LOCKED` na poziomie cyklu życia wiadomości.**
      `verify: ./mvnw -f backend test -Dtest=*DoubleDispatcherTest*` (współdzielony z EPIC-43).

## Story 45.4 — Asercja tylko-transport

status: not-started
depends_on: [Story 45.1, EPIC-14-egress-boundary-ownership/Story 14.2]

Taski:
- [ ] **Test: cykl życia wiadomości nie zmienia stanu finalności/płatności.**
      `verify: ./mvnw -f backend test -Dtest=*EgressTransportOnlyTest*`
