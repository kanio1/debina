---
status: not-started
depends_on: [EPIC-02-keycloak-realm-iteration-0, EPIC-03-spring-modulith-backend-skeleton]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-XCUT-1, line 1254)"
---

# EPIC-22 — Tożsamość i czas: cross-cutting (EPIC-XCUT-1)

## Story 22.1 — Filtr claim→GUC + selektywne dwupoziomowe RLS

status: not-started
depends_on: []

Opis: rozszerzenie mechanizmu z EPIC-03 Story 3.4 o poziom `branch_id`, tylko dla tabel tenant-facing.

Taski:
- [ ] **Rozszerz interceptor GUC o `app.branch_id`, RLS tylko na tabelach z §4.7 (tenant/evidence), nie na queue/ledger.**
      `verify: ./mvnw -f backend test -Dtest=*BranchLevelRlsTest*`

## Story 22.2 — Granty ownership na tabelach queue/ledger (bez RLS)

status: not-started
depends_on: [Story 22.1]

Opis: G3 — worker w tle działa jako `system_<n>`, nie domyślna sesja RLS; test zero-rows na pustym GUC.

Taski:
- [ ] **Test: worker w tle z rolą `system_<n>` i wąską polityką `p_system_*`, nigdy domyślna sesja RLS.**
      `verify: ./mvnw -f backend test -Dtest=*BackgroundWorkerScopeTest*`

## Story 22.3 — `ClockPort` wszędzie

status: not-started
depends_on: []

Opis: reguła ArchUnit zakazująca `Instant.now()` poza portem (kluczowe dla deterministycznej symulacji, Iteracja 3).

Taski:
- [ ] **Reguła ArchUnit: zero bezpośrednich wywołań `Instant.now()` poza `ClockPort`.**
      `verify: ./mvnw -f backend test -Dtest=*ClockPortEnforcementTest*`
