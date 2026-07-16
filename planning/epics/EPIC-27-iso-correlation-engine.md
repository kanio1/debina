---
status: not-started
depends_on: [EPIC-26-iso-message-lineage-core/Story 26.3]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-2, line 1269), [MVP]"
---

# EPIC-27 — ISO: silnik korelacji (EPIC-ISO-2)

Wiążąca zasada: adapter koreluje, payment-lifecycle przechodzi FSM. 9-krokowa polityka korelacji pacs.002.

`[NARROWED 2026-07-16 — dual-agent governance/backlog-redesign session]`: `depends_on` narrowed from the whole `EPIC-26-iso-message-lineage-core` epic to `Story 26.3` specifically (richer identifier extraction — the actual capability the correlation engine reads pacs.002 identifiers against). Found while building `planning/capability-graph.json`: `EPIC-26` as a whole is not `done` (Story 26.4, an unrelated GraphQL read-model panel, is still `blocked`), which made this epic read as transitively blocked even though everything it actually needs (`26.1`–`26.3`) has been `done` since 2026-07-15. **This epic is READY today** under the narrowed dependency — see `planning/BACKLOG-REDESIGN.md`.

## Story 27.1 — Ekstrakcja identyfikatorów pacs.002

status: not-started
depends_on: []

Taski:
- [ ] **Ekstrakcja `OrgnlMsgId`/`OrgnlEndToEndId` z pacs.002.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs002IdentifierExtractionTest*`

## Story 27.2 — 9-krokowa korelacja → `iso.iso_message_correlation`

status: not-started
depends_on: [Story 27.1]

Opis: wyniki MATCHED/AMBIGUOUS/ORPHANED.

Taski:
- [ ] **Zaimplementuj 9-krokową politykę korelacji dokładnie wg opisu w main blueprincie §2.4, zapis do `iso.iso_message_correlation`.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs002CorrelationPolicyTest*` → pokrywa MATCHED/AMBIGUOUS/ORPHANED.

## Story 27.3 — Duplikat i out-of-order

status: not-started
depends_on: [Story 27.2]

Taski:
- [ ] **Test: duplikat → `IGNORED_DUPLICATE`; wiadomość out-of-order → polityka FSM, nie błąd.**
      `verify: ./mvnw -f backend test -Dtest=*DuplicateAndOutOfOrderTest*`

## Story 27.4 — Orphan → DLQ `[MVP]`

status: not-started
depends_on: [Story 27.2]

`[SPLIT 2026-07-16 — dual-agent governance/backlog-redesign session, H6]`: was "Orphan → DLQ + read model operatora", one task/verify mixing an `[MVP]` deliverable (DLQ write) with a `[P1]` deliverable (manual-correlation operator read model) via a mid-sentence priority-tag split. Scope narrowed to the `[MVP]` half only; the `[P1]` half moved to the new Story 27.5 below, per ADR-N6's one-priority-taxonomy rule (iteration number primary, tag secondary — these two halves belong to different waves).

Taski:
- [ ] **Nierozpoznany status → DLQ.**
      `verify: ./mvnw -f backend test -Dtest=*OrphanDlqTest*`

## Story 27.5 — Read model operatora do ręcznej korelacji `[P1]`

status: not-started
depends_on: [Story 27.4]

`[SPLIT 2026-07-16 — see Story 27.4 above for the split rationale]`.

Taski:
- [ ] **Read model operatora nad DLQ do ręcznej korelacji.**
      `verify: ./mvnw -f backend test -Dtest=*OrphanOperatorReadModelTest*`
