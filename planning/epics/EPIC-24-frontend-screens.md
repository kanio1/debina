---
status: not-started
depends_on: [EPIC-23-frontend-foundation]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md §14 line 410 (EPIC-FE-1); sepa-nexus-react-nextjs-frontend-blueprint.md §7 (7 workspaces); sepa-nexus-first/next/final-3-screens-ui-spec.md; sepa-nexus-react-component-foundation-blueprint.md §9 kroki 8-10"
---

# EPIC-24 — Frontend Screens (EPIC-FE-1)

Szablon jednolity per ekran: SDL/codegen types → komponenty z test-id → bramkowanie rolą z mapy → loading/error/empty → deep-linki → **Playwright (jeden happy + jeden negatywny per ekran)**. Priorytet wg tabeli ekranów poniżej.

## `[SEKWENCJONOWANIE — NIE ŁAMAĆ]` Kiedy wolno zacząć Playwright

Reguła wiążąca (component-foundation §9, krok 8→9, `sepa-nexus-react-component-foundation-blueprint.md` linie 166-167):
> Krok 8: "Implement first 3 screens — Operations Control Room, Payments & Files, Payment Detail/Timeline (Iteration 1 spine)."
> Krok 9 (dosłownie): "Add Playwright smoke/role/data tests — per the first-3-screens spec's §_.10 tables, plus the axe-core gate wired into CI."

Playwright zaczyna się **dopiero po zbudowaniu pierwszych 3 ekranów**, nie wcześniej — zgodne z `[NO-PLAYWRIGHT]` z Iteracji 0 (EPIC-00…EPIC-08). `[OPEN-QUESTION]` Dokument wizji Playwright (`sepa-nexus-playwright-learning-vision-and-success-criteria.md` §6) opisuje "walking skeleton" jako gotowy do pierwszych iteracji, gdy istnieje już m.in. "≥1 test Playwright UI" — to brzmi jakby dopuszczało Playwright wcześniej niż component-foundation §9 krok 9. Traktuję IT0 (`[NO-PLAYWRIGHT]`, jawnie sprawdzane w EPIC-08 Story 8.3) i component-foundation §9 jako bardziej konkretne i późniejsze w kolejności patchy — więc obowiązujące. Nie rozstrzygam tej rozbieżności samodzielnie poza tym zapisem; jeśli okaże się blokująca, wymaga potwierdzenia przez użytkownika.

## Story 24.1 — Workspace 1: Ops Control Room (S-00…S-02)

status: not-started
depends_on: [EPIC-25-observability-consolidation/Story 25.2]

Opis: shell+login+health (S-00, `[MVP]` Iter 0 — już częściowo w EPIC-05/06), ops overview z live SSE counters (S-02, ADR-N4, `[MVP]` Iter 2). Operator worklist (S-01) `[P1]` — nie w tym story, patrz Story 24.9.

Kryterium ukończenia: 1 happy-path + 1 negatywny test Playwright per ekran, axe-core zielone.

Taski:
- [ ] **Zbuduj S-02 Ops Overview** zasilany przez pojedynczy endpoint SSE własności `reporting` (ADR-N4), kafelki wg `sepa-nexus-first-3-screens-ui-spec.md` §4.4 (System status, Payments today, Pending settlement, Failed deliveries, Open exceptions, Latest failed events).
      `verify: npm run test:e2e -- --grep "@smoke.*control-room"` → happy path + asercja na danych ze streamu, nigdy na timing renderowania.

## Story 24.2 — Workspace 2: Payments & Files (S-03, S-04)

status: not-started
depends_on: [EPIC-20-payment-lifecycle-fsm, EPIC-21-iso-identifier-refactor]

Opis: lista płatności + szczegóły/timeline z panelem identyfikatorów ISO (`iso.payment_iso_identifiers`, w tym wiersze `JSON_DIRECT` per ADR-N7). `[MVP]` Iteracja 1 — to jest jeden z pierwszych 3 ekranów z komponent-foundation §9 kroku 8.

Kryterium ukończenia: 1 happy-path + 1 negatywny test Playwright, dane z `iso.payment_iso_identifiers` widoczne w panelu.

Taski:
- [ ] **Zbuduj listę płatności + szczegół/timeline**, panel identyfikatorów zasilany z `iso.payment_iso_identifiers`.
      `verify: npm run test:e2e -- --grep "@smoke.*payments-and-files"`

## Story 24.3 — Workspace 3: Settlement & Liquidity (S-08, S-09, S-10)

status: not-started
depends_on: [EPIC-33-instant-settlement, EPIC-32-ledger-core]

Opis: timeline attempt rozliczenia, dashboard płynności, dziennik ledger (read-only). `[MVP]` Iter 2, zamknięcie cyklu Iter 4.

Taski:
- [ ] **Zbuduj widok szczegółu cyklu/attempt rozliczenia.**
      `verify: npm run test:e2e -- --grep "@smoke.*settlement"`

## Story 24.4 — Workspace 4: Egress & Delivery (S-11, S-12)

status: not-started
depends_on: [EPIC-44-egress-profile-artifact-taxonomy]

Opis: lista/szczegół artefaktów outbound. `[MVP]` Iter 2.

Taski:
- [ ] **Zbuduj listę/szczegół artefaktów outbound.**
      `verify: npm run test:e2e -- --grep "@smoke.*egress"`

## Story 24.5 — Workspace 5: Reconciliation & Cases (S-13, S-14, S-15)

status: not-started
depends_on: [EPIC-57-reconciliation-profiles-snapshot, EPIC-65-case-r-message-catalog]

Opis: kolejka roboczych, szczegół runa/wyjątku/przypadku. Przypisanie wyjątku ("Assign to me") to zapis compare-and-set. `[P1]` poza przypisaniem wyjątku, które jest `[MVP]`.

Taski:
- [ ] **Zbuduj kolejkę roboczą + widok szczegółu run/exception**, akcja "Assign to me" jako optimistic-locking write.
      `verify: npm run test:e2e -- --grep "@smoke.*reconciliation"`

## Story 24.6 — Workspace 6: Simulation Lab (S-17)

status: not-started
depends_on: []

Opis: launcher scenariuszy + szczegół runa. `[MVP]` Iteracja 3, keystone demo determinizmu. `[OPEN-QUESTION]` — zależy od modułu `simulation`, który nie ma jeszcze własnego epika backendowego (patrz EPIC-17 Otwarte pytania).

Taski:
- [ ] **Zbuduj launcher scenariuszy + widok szczegółu runa symulacji.**
      `verify: npm run test:e2e -- --grep "@smoke.*simulation"`

## Story 24.7 — Workspace 7: Reference Data / Admin (S-16)

status: not-started
depends_on: [EPIC-12-reference-data-ownership]

Opis: edytory katalogów. `[MVP]` cienki / `[P1]` pełny (rejestr kluczy).

Taski:
- [ ] **Zbuduj cienkie edytory katalogów reference-data** z optimistic locking na konfliktach wersji.
      `verify: npm run test:e2e -- --grep "@smoke.*reference-data"`

## Story 24.8 — Evidence/Audit drawer + workspace auditor (S-18)

status: not-started
depends_on: []

Opis: globalna szuflada Evidence (`[MVP]`) + cienki workspace auditora (`[P1]`). Format eksportu bundle'a evidence i reguły PII-gating jawnie oznaczone jako "wymaga decyzji governance przed buildem" (`sepa-nexus-final-3-screens-ui-spec.md` §11) — nie rozstrzygam.

Taski:
- [ ] **Zbuduj globalną szufladę Evidence** (bez eksportu — eksport czeka na decyzję governance, patrz otwarte pytanie w README).
      `verify: npm run test:e2e -- --grep "@smoke.*evidence-drawer"`

## Story 24.9 — Operator worklist (S-01, `[P1]`)

status: not-started
depends_on: [Story 24.1, EPIC-68-case-decisions-action-requests]

Opis: umiejscowienie ciągle "leaning Control Room tab, nie zdecydowane ostatecznie" w trzech niezależnych dokumentach UI-spec — patrz otwarte pytanie w README.

Taski:
- [ ] **Zbuduj `operatorWorklist`** po ostatecznym potwierdzeniu umiejscowienia (Control Room tab vs osobna powierzchnia P1).
      `verify: npm run test:e2e -- --grep "@smoke.*worklist"`
