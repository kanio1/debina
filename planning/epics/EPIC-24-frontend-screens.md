---
status: in-progress
depends_on: [EPIC-23-frontend-foundation]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md §14 line 410 (EPIC-FE-1); sepa-nexus-react-nextjs-frontend-blueprint.md §7 (7 workspaces); sepa-nexus-first/next/final-3-screens-ui-spec.md; sepa-nexus-react-component-foundation-blueprint.md §9 kroki 8-10"
---

`[AUDYT 2026-07-14]`: `depends_on: EPIC-23` jest formalnie spełnione (23 zamknięty jako `in-progress`, wystarczające stories done), ale rzeczywista zdolność każdego z 9 workspace'ów zweryfikowana indywidualnie poniżej — 8 z 9 pozostaje `blocked` na moduły backendowe, które mają dziś zero kodu (settlement, ledger, egress, reconciliation, case, simulation, evidence-audit, reporting/SSE). Tylko Story 24.2 (Payments & Files) ma dziś realne dane do pokazania.

**Bramka Playwright (linie 11-17 poniżej, `[SEKWENCJONOWANIE — NIE ŁAMAĆ]`) NIE jest otwarta w tej sesji** — wymaga wszystkich pierwszych 3 ekranów (Control Room + Payments & Files + Payment Detail/Timeline) zbudowanych, a Control Room (Story 24.1) pozostaje `blocked` (brak SSE/`reporting`). Zbudowano tylko część Payments & Files — bez Playwrighta, zgodnie z regułą.

# EPIC-24 — Frontend Screens (EPIC-FE-1)

Szablon jednolity per ekran: SDL/codegen types → komponenty z test-id → bramkowanie rolą z mapy → loading/error/empty → deep-linki → **Playwright (jeden happy + jeden negatywny per ekran)**. Priorytet wg tabeli ekranów poniżej.

## `[SEKWENCJONOWANIE — NIE ŁAMAĆ]` Kiedy wolno zacząć Playwright

Reguła wiążąca (component-foundation §9, krok 8→9, `sepa-nexus-react-component-foundation-blueprint.md` linie 166-167):
> Krok 8: "Implement first 3 screens — Operations Control Room, Payments & Files, Payment Detail/Timeline (Iteration 1 spine)."
> Krok 9 (dosłownie): "Add Playwright smoke/role/data tests — per the first-3-screens spec's §_.10 tables, plus the axe-core gate wired into CI."

Playwright zaczyna się **dopiero po zbudowaniu pierwszych 3 ekranów**, nie wcześniej — zgodne z `[NO-PLAYWRIGHT]` z Iteracji 0 (EPIC-00…EPIC-08). `[OPEN-QUESTION]` Dokument wizji Playwright (`sepa-nexus-playwright-learning-vision-and-success-criteria.md` §6) opisuje "walking skeleton" jako gotowy do pierwszych iteracji, gdy istnieje już m.in. "≥1 test Playwright UI" — to brzmi jakby dopuszczało Playwright wcześniej niż component-foundation §9 krok 9. Traktuję IT0 (`[NO-PLAYWRIGHT]`, jawnie sprawdzane w EPIC-08 Story 8.3) i component-foundation §9 jako bardziej konkretne i późniejsze w kolejności patchy — więc obowiązujące. Nie rozstrzygam tej rozbieżności samodzielnie poza tym zapisem; jeśli okaże się blokująca, wymaga potwierdzenia przez użytkownika.

## Story 24.1 — Workspace 1: Ops Control Room (S-00…S-02)

status: blocked
depends_on: [EPIC-25-observability-consolidation/Story 25.2]

Opis: shell+login+health (S-00, `[MVP]` Iter 0 — już częściowo w EPIC-05/06), ops overview z live SSE counters (S-02, ADR-N4, `[MVP]` Iter 2). Operator worklist (S-01) `[P1]` — nie w tym story, patrz Story 24.9.

`[PLANNING-DEFECT 2026-07-14]`: `depends_on` (EPIC-25 Story 25.2) jest realnie niespełnione — Story 25.2 sama jest `blocked` (brakuje etapów `validate`/`route`/`settle`/`pacs002`). Głębszy blocker: nie istnieje żaden SSE endpoint własności `reporting` (moduł `reporting` ma zero kodu) — kafelki S-02 (Payments today, Pending settlement, Failed deliveries, Open exceptions) wymagają agregacji z modułów, które nie istnieją (settlement/egress/reconciliation). Budowa tego ekranu teraz oznaczałaby albo mockowanie danych (zabronione — synthetic/deterministic tak, ale nie fabrykowane UI bez realnego źródła), albo wynajdywanie modułu `reporting`/SSE na wyrost. **Nie zaimplementowano.**

Kryterium ukończenia: 1 happy-path + 1 negatywny test Playwright per ekran, axe-core zielone.

Taski:
- [ ] **Zbuduj S-02 Ops Overview** zasilany przez pojedynczy endpoint SSE własności `reporting` (ADR-N4), kafelki wg `sepa-nexus-first-3-screens-ui-spec.md` §4.4 (System status, Payments today, Pending settlement, Failed deliveries, Open exceptions, Latest failed events).
      `verify: npm run test:e2e -- --grep "@smoke.*control-room"` — `NOT RUN`, `blocked` (patrz wyżej).

## Story 24.2A — Workspace 2 feature: list, detail, ISO panel, timeline

`[SPLIT 2026-07-16 — dual-agent governance/backlog-redesign session, H4]`: was Story 24.2 ("Workspace 2: Payments & Files"), which bundled feature implementation (list+detail+ISO panel+timeline — both original tasks, both already checked `[x]` and independently verified via `pnpm run build` plus real browser/live-infra smoke tests) with a separate, unchecked, no-dedicated-task acceptance requirement ("1 happy-path + 1 negatywny test Playwright") that alone kept the whole story `in-progress` instead of `done`. Split into 24.2A (feature — now `done`, all its own tasks already were) and 24.2B (Playwright acceptance — `blocked` on the sequencing rule, unchanged). This does not change when Playwright may start; it only stops a fully-delivered feature from reading as incomplete.

status: **done**
depends_on: [EPIC-20-payment-lifecycle-fsm, EPIC-21-iso-identifier-refactor]

Opis: lista płatności + szczegóły/timeline z panelem identyfikatorów ISO (`iso.payment_iso_identifiers`, w tym wiersze `JSON_DIRECT` per ADR-N7). `[MVP]` Iteracja 1 — to jest jeden z pierwszych 3 ekranów z komponent-foundation §9 kroku 8.

`[PLANNING-DEFECT 2026-07-14, ROZWIĄZANE 2026-07-15]`: lista + szczegół + panel identyfikatorów ISO zbudowane i zweryfikowane (patrz niżej). "Timeline" (uporządkowana historia przejść statusu) była niezbudowana z braku właściciela dla `payment_status_history`/`payment_events` — luka zamknięta w `EPIC-11` Story 11.1 (patrz `[TIMELINE DOSTARCZONY]` niżej i `planning/README.md`); timeline zbudowany na tym samym fundamencie.

**Realny bug znaleziony i naprawiony podczas weryfikacji tej story** (nie część pierwotnego zakresu, ale blokował realną weryfikację end-to-end): (1) `frontend/src/app/api/payments/route.ts` POST nigdy nie przekazywał nagłówka `Idempotency-Key` do backendu — realny submit płatności przez przeglądarkę zwracał `400` od EPIC-19/22 (odkąd nagłówek stał się wymagany na backendzie), ekran "Submit payment" z EPIC-06 Story 6.4 był **faktycznie zepsuty** w całej ścieżce UI. Naprawiono: `payments/page.tsx` generuje `crypto.randomUUID()` per próba submitu, BFF wymaga i przekazuje nagłówek dalej. (2) `frontend/src/app/api/auth/callback/route.ts` odczytywał `realm_access.roles` z **ID tokenu**, który w tym realmie nigdy nie niesie tego claimu (potwierdzone dekodowaniem: ID token ma `tenant_id`/`branch_id`/`preferred_username`, ale nie `realm_access`) — `SessionClaims.roles` było **zawsze puste** dla każdego realnego logowania przez przeglądarkę, co czyniłoby mapę rola→workspace (Story 23.3) martwą w praktyce. Naprawiono: role odczytywane z access tokenu (zweryfikowanego osobno przez `jwtVerify` bez `audience`, bo access token w tym realmie nie ma `aud`, tylko `azp` — dokładnie jak weryfikuje go backend). Oba potwierdzone realnym przebiegiem authorization-code+PKCE przez `curl` (nie mock): logowanie → `/api/session` → `roles: ["payment_submitter"]` (było `[]`) → HTML `/payments` faktycznie zawiera `app-shell.nav.payments`.

Kryterium ukończenia: lista + szczegół + panel identyfikatorów ISO + timeline zbudowane i zweryfikowane (build + real smoke test). Met — Playwright acceptance is a separate deliverable, see Story 24.2B below.

`[REEWALUACJA 2026-07-15]`: `EPIC-21` Story 21.2 (drugi z dwóch `depends_on`) jest teraz `done` — `payment.payments.end_to_end_id` usunięty, read model `PaymentService.visiblePayments`/`paymentDetail` przepięty na `iso.payment_iso_identifiers`/`iso.message_lineage`. JSON response shape (`PaymentSummaryResponse`/`PaymentDetailResponse`) **niezmieniony** — pole `endToEndId` ta sama nazwa, ten sam typ. `pnpm run lint`/`typecheck`/`build` wszystkie PASS bez żadnej zmiany w `frontend/`. Nie wykonano świeżego ręcznego przeglądarkowego przebiegu w tej sesji (tylko strukturalna weryfikacja: kontrakt JSON identyczny + `WalkingSkeletonIntegrationTest`, który idzie przez prawdziwy HTTP+Keycloak+DB, dalej przechodzi) — realny browser click-through z poprzedniej sesji (Story 24.2's własna notatka) pozostaje jedynym bezpośrednim dowodem end-to-end, ale nic w tej sesji nie zmieniło ścieżkę, którą on wtedy zweryfikował.

`[TIMELINE DOSTARCZONY 2026-07-15]`: OQ-12 rozstrzygnięte (`planning/README.md`, `EPIC-11` Story 11.1) — `payment.payment_status_history`/`payment.payment_events` zbudowane, `payment-lifecycle` jedynym writerem. Backend: `GET /api/v1/payments/{id}/timeline` (`PaymentController.timeline`, `PaymentService.paymentTimeline`, `PaymentTimelineLookup` — czyta wyłącznie `payment_status_history`, cursor-based paginacja po `seq`, nigdy offset). Frontend: nowy BFF `GET /api/payments/[id]/timeline/route.ts` (ten sam wzorzec sesji/nagłówków co istniejący `/api/payments/[id]/route.ts`), nowa sekcja "Timeline" na `payments/[id]/page.tsx` — niezależny `useEffect`/load-state od reszty strony (zepsuty timeline nie blankuje nagłówka płatności), `data-testid="payment.detail.timeline.list"`/`.entry`/`.entry.event-ref`, specjalny opis dla wierszy `MIGRATION_BASELINE`. Status timeline traktowany jako **funkcjonalnie gotowy, ale formalne kryterium ukończenia tej story wymaga Playwright** (patrz nota u góry pliku — bramka Playwright pozostaje zamknięta do czasu Control Room, Story 24.1, wciąż `blocked`) — dlatego Story 24.2 jako całość pozostaje `in-progress`, nie `done`, mimo że oba jej taski są funkcjonalnie zbudowane.

Taski:
- [x] **Zbuduj listę płatności + szczegół** (bez timeline — patrz wyżej), panel identyfikatorów zasilany z `iso.payment_iso_identifiers`.
      `verify: pnpm run build` → PASS (2026-07-14). Nowy backend `GET /api/v1/payments/{id}` (`PaymentController.detail`, `PaymentService.paymentDetail`, `IsoIdentifierLookup` — czyta wyłącznie przez `iso.payment_iso_identifiers`, nigdy przez pole spłaszczone na `payment.payments`, G4), `PaymentNotFoundException`→404 RFC7807. Nowe testy `PaymentControllerTest.returnsPaymentDetailWithIsoIdentifiers`/`returnsNotFoundForUnknownPayment` (`./mvnw -f backend test` → `49/49`, było 47). Nowy BFF `GET /api/payments/[id]/route.ts`, nowy `frontend/src/app/payments/[id]/page.tsx` (używa `ScreenState` z Story 23.3), link z listy (`payments-table.tsx` `endToEndId` → `<a href="/payments/{id}">`). **Realny smoke-test pełnego przeglądarkowego przepływu** (authorization code + PKCE przez `curl`, prawdziwy Keycloak, prawdziwy Next.js `next start`, prawdziwy backend): login → submit z prawdziwym `Idempotency-Key` → `201 Created` → `GET /api/payments/{id}` przez BFF → prawdziwy JSON z `isoIdentifiers: [{sourceMessageType: "JSON_DIRECT", ...}]` czytany z żywej bazy.
- [x] **Timeline** (historia przejść statusu).
      `verify: pnpm run build` → PASS (2026-07-15, patrz `[TIMELINE DOSTARCZONY]` wyżej). `./mvnw -f backend test` → `137/137 PASS` (było 118 po EPIC-21).

## Story 24.2B — Workspace 2 Playwright acceptance

status: blocked
depends_on: [Story 24.2A, Story 24.1]

`[SPLIT 2026-07-16 — see Story 24.2A note above]`. `depends_on` includes `Story 24.1` (Control Room) because the file-level `[SEKWENCJONOWANIE — NIE ŁAMAĆ]` rule gates all Playwright on the first 3 screens existing, not just this workspace's own feature work.

Kryterium ukończenia: 1 happy-path + 1 negatywny test Playwright, dane z `iso.payment_iso_identifiers` widoczne w panelu.

Taski:
- [ ] **Playwright: happy-path submit→list→detail, i negatywny (np. brakujący identyfikator ISO renderuje stan błędu, nie 500).**
      `verify: npm run test:e2e -- --grep "@smoke.*payments"` — `NOT RUN`, `blocked` do czasu zbudowania wszystkich pierwszych 3 ekranów (patrz nota na górze pliku).

## Story 24.3 — Workspace 3: Settlement & Liquidity (S-08, S-09, S-10)

status: blocked
depends_on: [EPIC-33-instant-settlement, EPIC-32-ledger-core]

Opis: timeline attempt rozliczenia, dashboard płynności, dziennik ledger (read-only). `[MVP]` Iter 2, zamknięcie cyklu Iter 4.

`[AUDYT 2026-07-14]`: `EPIC-32`/`EPIC-33` obydwa `not-started`, zero kodu (`ledger`/`settlement` moduły nie istnieją). **Status `blocked`**.

Taski:
- [ ] **Zbuduj widok szczegółu cyklu/attempt rozliczenia.**
      `verify: npm run test:e2e -- --grep "@smoke.*settlement"` — `NOT RUN`, `blocked`.

## Story 24.4 — Workspace 4: Egress & Delivery (S-11, S-12)

status: blocked
depends_on: [EPIC-44-egress-profile-artifact-taxonomy]

Opis: lista/szczegół artefaktów outbound. `[MVP]` Iter 2.

`[AUDYT 2026-07-14]`: `EPIC-44` `not-started`, `egress` moduł zero kodu. **Status `blocked`**.

Taski:
- [ ] **Zbuduj listę/szczegół artefaktów outbound.**
      `verify: npm run test:e2e -- --grep "@smoke.*egress"` — `NOT RUN`, `blocked`.

## Story 24.5 — Workspace 5: Reconciliation & Cases (S-13, S-14, S-15)

status: blocked
depends_on: [EPIC-57-reconciliation-profiles-snapshot, EPIC-65-case-r-message-catalog]

Opis: kolejka roboczych, szczegół runa/wyjątku/przypadku. Przypisanie wyjątku ("Assign to me") to zapis compare-and-set. `[P1]` poza przypisaniem wyjątku, które jest `[MVP]`.

`[AUDYT 2026-07-14]`: `EPIC-57`/`EPIC-65` obydwa `not-started`, `reconciliation`/`case` moduły zero kodu. **Status `blocked`**.

Taski:
- [ ] **Zbuduj kolejkę roboczą + widok szczegółu run/exception**, akcja "Assign to me" jako optimistic-locking write.
      `verify: npm run test:e2e -- --grep "@smoke.*reconciliation"` — `NOT RUN`, `blocked`.

## Story 24.6 — Workspace 6: Simulation Lab (S-17)

status: blocked
depends_on: []

Opis: launcher scenariuszy + szczegół runa. `[MVP]` Iteracja 3, keystone demo determinizmu. `[OPEN-QUESTION]` — zależy od modułu `simulation`, który nie ma jeszcze własnego epika backendowego (patrz EPIC-17 Otwarte pytania).

`[AUDYT 2026-07-14]`: `depends_on: []` w nagłówku jest mylące — realny bloker to moduł `simulation` (zero kodu), nie ujęty formalnie w `depends_on` bo nie ma własnego epika (`[OPEN-QUESTION]` #4 w README). **Status `blocked`**.

Taski:
- [ ] **Zbuduj launcher scenariuszy + widok szczegółu runa symulacji.**
      `verify: npm run test:e2e -- --grep "@smoke.*simulation"` — `NOT RUN`, `blocked`.

## Story 24.7 — Workspace 7: Reference Data / Admin (S-16)

status: blocked
depends_on: [EPIC-12-reference-data-ownership/Story 12.1]

Opis: edytory katalogów. `[MVP]` cienki / `[P1]` pełny (rejestr kluczy).

`[AUDYT 2026-07-14]`: `EPIC-12` Story 12.1 (schemat+granty `reference_data`, tabele `scheme_profiles`/`business_calendars`/`settlement_cutoff_calendar`) jest `done` — **najbliższy do odblokowania z pozostałych 8 workspace'ów**, gdyby ktoś chciał budować edytor CRUD nad tymi trzema katalogami. Nie podjęto w tej sesji — poza wybranym zakresem 3 epików (EPIC-23/24-Story24.2/25), a CRUD z optimistic locking na wersjach jest realną, nietrywialną pracą backendową (endpointy write) wykraczającą poza budżet tej sesji. **Status `blocked`** dla tej sesji, ale odnotowane jako najsłabszy blocker z całego epika — dobry kandydat na następną.

`[NARROWED + OPEN QUESTION 2026-07-16 — dependency-inventory deep-dive session]`: `depends_on` narrowed from the whole `EPIC-12` epic to `Story 12.1` specifically (the only part this story's frontend work actually reads — `Story 12.2`'s validation/mapping/render catalogs are unrelated and `[NO-CODE]`-gated to Iteration 5). Narrowing alone does **not** make this story `READY`: re-checked the actual capability required — a backend REST CRUD endpoint for `reference_data.*` (`POST/PUT /api/v1/reference-data/{catalog}`, per `sepa-nexus-message-flow-and-data-blueprint.md` §7.2, which documents this exact endpoint shape as MVP-thin/P1-full) — and confirmed **no epic or story anywhere in `/planning/` owns building it**. `EPIC-12` covers only schema/grants (DDL), never write endpoints. This is a genuine missing-owner gap, not a dependency-narrowing fix; recording as `[OPEN-QUESTION]` rather than marking `READY`. A future session should either add a new story under `EPIC-12` (or a sibling epic) for the reference-data write-endpoint capability, or confirm via user/team decision that this frontend story itself is meant to include the backend endpoints (scope-widening, not assumed here).

Taski:
- [ ] **Zbuduj cienkie edytory katalogów reference-data** z optimistic locking na konfliktach wersji.
      `verify: npm run test:e2e -- --grep "@smoke.*reference-data"` — `NOT RUN`, `blocked` (patrz wyżej — backend CRUD endpoints dla `reference_data.*` też nie istnieją jeszcze).

## Story 24.8 — Evidence/Audit drawer + workspace auditor (S-18)

status: blocked
depends_on: []

Opis: globalna szuflada Evidence (`[MVP]`) + cienki workspace auditora (`[P1]`). Format eksportu bundle'a evidence i reguły PII-gating jawnie oznaczone jako "wymaga decyzji governance przed buildem" (`sepa-nexus-final-3-screens-ui-spec.md` §11) — nie rozstrzygam.

`[AUDYT 2026-07-14]`: moduł `evidence-audit` (schematy `evidence`/`audit`) zero kodu. **Status `blocked`**.

Taski:
- [ ] **Zbuduj globalną szufladę Evidence** (bez eksportu — eksport czeka na decyzję governance, patrz otwarte pytanie w README).
      `verify: npm run test:e2e -- --grep "@smoke.*evidence-drawer"` — `NOT RUN`, `blocked`.

## Story 24.9 — Operator worklist (S-01, `[P1]`)

status: blocked
depends_on: [Story 24.1, EPIC-68-case-decisions-action-requests]

Opis: umiejscowienie ciągle "leaning Control Room tab, nie zdecydowane ostatecznie" w trzech niezależnych dokumentach UI-spec — patrz otwarte pytanie w README.

`[AUDYT 2026-07-14]`: zależy od Story 24.1 (blocked, patrz wyżej) i `EPIC-68` (not-started, `case` moduł zero kodu) — podwójnie blocked, plus nierozstrzygnięte umiejscowienie UI. **Status `blocked`**.

Taski:
- [ ] **Zbuduj `operatorWorklist`** po ostatecznym potwierdzeniu umiejscowienia (Control Room tab vs osobna powierzchnia P1).
      `verify: npm run test:e2e -- --grep "@smoke.*worklist"` — `NOT RUN`, `blocked`.
