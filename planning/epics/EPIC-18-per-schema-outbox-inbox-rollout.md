---
status: blocked
depends_on: [EPIC-09-ownership-schema-grants, EPIC-04-outbox-inbox-kafka-thin]
source: "sepa-nexus-blueprint-ownership-integration.md §9 (EPIC-OWN-11, line 355, [ADD, ADR-N5, closed] — nieobecne w MFB §8)"
---

# EPIC-18 — Ownership: rollout outbox/inbox per schemat na pozostałe moduły (EPIC-OWN-11)

EPIC-01/EPIC-04 (Iteracja 0) zrealizowały wzorzec ADR-N5 tylko dla schematu `payment`. Ten epik rozszerza go na każdy pozostały publikujący/konsumujący schemat, w miarę jak dany moduł powstaje (nie wszystko naraz — `[DEFER]` z §3.6.3: schematy tworzone per moduł, per iteracja).

`[PLANNING-DEFECT, potwierdzone 2026-07-14]`: dosłownie "w miarę jak dany moduł powstaje" — dziś **żaden kolejny moduł z outbox/inbox nie istnieje**. `reference_data` (EPIC-12, powstały w tej sesji) jest jedynym nowym schematem, ale per §3.6.3 jest czysto katalogowy (nie publikuje/konsumuje eventów), więc świadomie **nie dostaje** pary outbox/inbox — nie jest to przeoczenie. **Status `blocked`** (jedyna wartość dozwolona przez `.claude/skills/epic-story-task-catalog/SKILL.md`) — odblokuj przy pierwszej migracji pierwszego modułu spośród `iso-adapter`/`routing`/`settlement`/`egress`/`reconciliation`/`case`, każdy z których per §3.6.3 rzeczywiście potrzebuje własnej pary `<schema>.outbox_events`/`<schema>.inbox_events`.

## Story 18.1 — DDL `<schema>.outbox_events`/`<schema>.inbox_events` per moduł

status: not-started
depends_on: []

Kryterium ukończenia: każdy nowo powstający moduł dostaje własną parę outbox/inbox w tej samej migracji co jego pierwszy schemat.

Taski:
- [ ] **Szablon migracji Flyway dla pary outbox/inbox**, powielany przy pierwszej migracji każdego kolejnego modułu (analogicznie do `payment.outbox_events` z EPIC-01 Story 1.3).
      `verify: dla każdego nowego modułu M: psql -c "\d M.outbox_events" i "\d M.inbox_events"` → obie tabele istnieją.

## Story 18.2 — `outbox_dispatcher_role` grant + negatywny sweep

status: not-started
depends_on: [Story 18.1]

Taski:
- [ ] **Rozszerz `outbox_dispatcher_role`** o wąski grant `SELECT`/`UPDATE(published_at)` na outbox nowego modułu, bez grantu na jego tabele domenowe.
      `verify: ./mvnw -f backend test -Dtest=*OutboxDispatcherNoDomainWriteSweepTest*` → negatywny sweep przez wszystkie schematy, nie allowlist per tabela.

## Story 18.3 — Test: writer modułu nie pisze cudzego outbox

status: not-started
depends_on: [Story 18.1]

Taski:
- [ ] **SQL grant-test: rola-writer modułu A nie ma zapisu na `<schemat B>.outbox_events`.**
      `verify: ./mvnw -f backend test -Dtest=*CrossModuleOutboxWriteDeniedTest*`

## Story 18.4 — Dedup inbox + replay-safe

status: not-started
depends_on: [Story 18.1]

Taski:
- [ ] **Test: redelivery Kafka na dowolny inbox nie duplikuje efektu domenowego (unique na id źródłowego eventu).**
      `verify: ./mvnw -f backend test -Dtest=*InboxReplayDoesNotDuplicateTest*`
