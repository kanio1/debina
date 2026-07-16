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

## Story 43.2 — Renderer (Prowide) + integracja z SignatureSigningPort

status: not-started
depends_on: [Story 43.1, EPIC-31-signature-module/Story 31.3A]

`[SPLIT 2026-07-16 — dual-agent governance/backlog-redesign session, H1]`: `depends_on` narrowed from `EPIC-31-signature-module/Story 31.3` to `Story 31.3A` — the old, unsplit `Story 31.3` itself depended on the whole of this epic (`EPIC-43`), which transitively includes this very story, i.e. a real cycle (31.3 → EPIC-43 → 43.2 → 31.3). `Story 31.3A` is the narrowed, standalone half of the old 31.3 (signing capability only, no `EPIC-43` dependency — see `EPIC-31-signature-module.md`). The invocation-from-egress detail formerly also described (redundantly) in the old Story 31.3's task text now lives only here, since it's this story's own scope. See `planning/BACKLOG-REDESIGN.md` for the full writeup.

Taski:
- [ ] **Renderer oparty o Prowide + wywołanie `SignatureSigningPort` z `egress`, guardowane flagą `signing_required`, podpis detached przechowany na outbound message.**
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
