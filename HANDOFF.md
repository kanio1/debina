# HANDOFF

## Zadanie

SEPA Nexus jest syntetyczną platformą płatności ISO 20022 do nauki testów enterprise. Wave 5 dostarcza źródłowo potwierdzony deferred-settlement cycle slice i settlement-owned cutoff read boundary.

## Zrobione

- Wave 3 pozostaje dowodem dla EPIC-51 Stories 51.1--51.2, EPIC-52 Stories
  52.1--52.3 i EPIC-53 Stories 53.1--53.3 (`planning/programs/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3.md`);
  nie zostały odtworzone ani policzone ponownie.
- Wave 4 (`planning/programs/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-4.md`) zakończył
  sześć READY stories: EPIC-51/51.4, EPIC-54/54.1--54.3 oraz EPIC-56/56.1--56.2.
  `RouteCandidatesReadModel` daje wyłącznie wewnętrzny, read-only model
  kandydatów bez GraphQL/REST i bez mutacji płatności.
- ADR-N12 zdecydował techniczną tożsamość fallback rule: V49 tworzy
  `reference_data.profile_fallback_rules` z UUID i zachowaną unikalnością
  `(profile_id, priority)`; V50 wiąże `routing.route_decisions.fallback_rule_id`
  FK i flagą. `reference_data_role` jest jedynym writerem katalogu, a
  `routing_role` tylko czyta/referenceuje go i zapisuje własne `routing.*`.
- `FallbackPolicy` wybiera tylko jawną, bezwarunkową regułę w rosnącym
  priorytecie. Brak reguły nie daje fallbacku; wybrany nie-null `condition`
  kończy się fail-closed bez dynamicznego języka. Recorder atomowo zapisuje
  fallback decision/candidates/snapshot pod tenant GUC; rollback, FK, RLS,
  cross-tenant, role grants, PUBLIC denial i V48→V50 upgrade są pokryte przez
  Testcontainers PostgreSQL 18.
- Deterministyczne fixture'y i testy pairwise wykonują realny resolver/policy.
  Odwrócenie kolejności produkcyjnego komparatora celowo dało 2 błędy testów,
  po czym zostało cofnięte. Focused routing suite: 25/0/0. Dwie kolejne pełne
  regresje backendu: 475/0/0 i 475/0/0 (`/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-4/backend-regression-1.log`,
  `backend-regression-2.log`). Governance, inventory, capability graph oraz
  `bash tools/skills/validate-all-skills.sh` są PASS; wygenerowany
  `build/generated-spring-modulith/javadoc.json` przywrócono.
- Wave 5 commit `4c47158 feat(settlement): add deferred cycle finality slice`: ADR-N13, V51/V52,
  `NetDeferredStrategy`, G6 cycle FSM, one-statement netting, item-based
  `ON_CYCLE_SETTLED` authority and `CutoffStateReader`. Done: EPIC-37/37.1--37.4,
  shared EPIC-34/34.1--34.2, EPIC-55/55.1. Focused Testcontainers tests pass;
  V50→V52 keeps routing evidence; two full backend regressions are `485/0/0`.

## Utknęliśmy na

EPIC-55/55.2 is SOURCE-BLOCKED: source lacks cutoff-vs-cycle disagreement precedence.
EPIC-55/55.3 is SOURCE-BLOCKED: no account/amount/result/consistency contract; 55.4 is capability-blocked. EPIC-51/51.3 remains
`SOURCE-BLOCKED` (allowed-message-set), EPIC-54/54.4 `SOURCE-BLOCKED`
(multi-CSM), EPIC-56/56.3 `SOURCE-BLOCKED` (amount/currency boundary
vocabulary), a 52.4, 53.4, 55.1 i 56.4 są `CAPABILITY-BLOCKED`. Nie wolno
samodzielnie rozstrzygać nadal niezmienionych Class C luk: `payment.sla.breached`,
return/requested-payment intake, outbound artifact/render-profile snapshot,
reconciliation DDL, case DDL ani file-intake policy.

## Plan na następny krok

Open `planning/programs/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-5.md` and prepare the exact Class C decision packet for EPIC-55 Story 55.2; do not implement routing outcomes until a source defines cutoff/cycle disagreement precedence.

## Pułapki, których nie wolno powtórzyć

- Maven nadpisuje `build/generated-spring-modulith/javadoc.json`; przywróć go
  po każdym przebiegu, jeśli zmiana nie jest celowa.
- Nie traktuj `condition text` jako języka reguł, nie wyprowadzaj fallbacku z
  profile family/CSM/settlement basis i nie dodawaj routingowego Kafka topic.
- Nie licz aktywacji skills jako implicit-routing proof ani nie wymyślaj
  semantyki Class C. Final gate wymaga dokładnie dwóch zielonych regresji po
  ostatniej zmianie.
