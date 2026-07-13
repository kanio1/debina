---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OUT-1, line 1253), [MVP]"
---

# EPIC-43 — Egress: szyna wychodząca (EPIC-OUT-1)

## Story 43.1 — `outbound_messages` + dispatcher SKIP LOCKED

status: not-started
depends_on: []

Opis: test na podwójny dyspozytor.

Taski:
- [ ] **Migracja `egress.outbound_messages` + dispatcher `SKIP LOCKED`.**
      `verify: ./mvnw -f backend test -Dtest=*DoubleDispatcherTest*`

## Story 43.2 — Renderer (Prowide) + signer

status: not-started
depends_on: [Story 43.1, EPIC-31-signature-module/Story 31.3]

Taski:
- [ ] **Renderer oparty o Prowide + wywołanie `SignatureSigningPort`.**
      `verify: ./mvnw -f backend test -Dtest=*EgressRendererSignerTest*`

## Story 43.3 — Retry/backoff/ABANDONED + DLQ

status: not-started
depends_on: [Story 43.1]

Taski:
- [ ] **Polityka retry/backoff, stan `ABANDONED`, DLQ.**
      `verify: ./mvnw -f backend test -Dtest=*EgressRetryBackoffDlqTest*`

## Story 43.4 — Kolektor wsadowy + pliki wychodzące

status: not-started
depends_on: [Story 43.1]

Taski:
- [ ] **Kolektor wsadowy budujący `outbound_files`.**
      `verify: ./mvnw -f backend test -Dtest=*BatchCollectorTest*`

## Story 43.5 — Korelacja potwierdzenia dostawy

status: not-started
depends_on: [Story 43.1]

Taski:
- [ ] **Korelacja `delivery_receipts` z odpowiadającym `outbound_message`.**
      `verify: ./mvnw -f backend test -Dtest=*DeliveryConfirmationCorrelationTest*`
