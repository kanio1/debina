# HANDOFF

## Zadanie

Debina realizuje enterprise rebase obejmujący governance Use-Case 2.0,
architekturę i lokalną platformę weryfikacji. Domknięto pozostałe luki assurance
skillu `enterprise-use-case-engineering`; Phase E pozostaje nierozpoczęta.

## Zrobione

- Dodano jedno źródło prawdy
  `docs/governance/methodology-assurance/CLASSIFICATION-VOCABULARY.yaml`:
  32 kanoniczne wartości z podkreśleniami, 21 jawnych deprecated aliases,
  producentów, konsumentów i reguły blokowania readiness. Cztery skills
  (`enterprise-use-case-engineering`, `source-backed-payments-modeling`,
  `architecture-evolution-review`, `planning-semantic-integrity`) mają
  zgodne, deklaratywne kontrakty producer/consumer.
- `tools/governance/validate-classification-vocabulary.py` wykrywa drift,
  kolizje, nieznane tokeny/skills, niedostarczone inputs, aliases w aktywnych
  artefaktach i fałszywy claim model proof. Runtime proof zakończył się:
  `CLASSIFICATION-VOCABULARY: PASS canonical=32 aliases=21 skills=4` oraz
  `CROSS-SKILL-CLASSIFICATION-CONTRACT: PASS stages=4`.
- Fixture assurance jest nazwane uczciwie: routing 10/10, regression 10,
  adversarial 12 i czterostopniowy cross-skill chain 16 wymaganych outputs
  przeszły jako `*-FIXTURE-CONTRACT`. `MODEL-BEHAVIOR: NOT_EXECUTED`, ponieważ
  repo nie ma zatwierdzonego, izolowanego runnera zwracającego wiarygodny
  dowód wyboru skillu. Dostępność ogólnego `codex exec` lub `claude -p` nie
  stanowi takiego dowodu.
- `docs/standards/SOURCE-EVIDENCE-CATALOG.yaml` ma trwały model świeżości.
  Istniejące cztery evidence zachowują uczciwe statusy `VERIFY_PER_USE` albo
  `INCOMPLETE`; nie dopisano fikcyjnych dat ani reviewerów.
  `validate-source-traceability.py` sprawdza statusy, daty, obowiązywanie,
  supersession i sprzeczne claims `VERIFIED`.
- `validate-planning-semantics.py` blokuje `READY` dla evidence
  `VERIFY_PER_USE`, `INCOMPLETE`, `STALE`, `CONFLICTING` i `RESTRICTED`;
  `PROJECT_INTERPRETATION` wymaga project authority, a
  `PROJECT_SIMULATION` synthetic boundary. Focused negative fixtures failują
  z przyczynowymi kodami `CV-*`, `SRC-013` i `ESR-018`–`ESR-022`.
- Pełny enterprise governance runner przeszedł: 79 epików, 304 stories,
  24 aktywne skills, 15 use cases, 38 slices, 15 modułów i ADR lifecycle.
  Zachowano jawne 296 use-case traceability warnings i 69 planning-semantic
  warnings dla legacy backlogu. Repozytoryjne 193 JSON i 58 YAML parsują się;
  poprawiono wcześniejszy błąd serializacji
  `docs/governance/semantic-enforcement.yaml`.
- Phase D w
  `planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md` jest oznaczona jako
  complete i runtime-proven; remote CI, `act` oraz deployment/release
  pozostają deferred/out of scope i nie blokują Phase E. Root `AGENTS.md`
  wskazuje lokalne Dagger acceptance checks bez nieaktualnego warunku.
- W tej sesji nie wykonano `git push`. Po commicie tego handoffu lokalny
  `rebase/enterprise-evolution` jest 8 commitów ahead i 0 behind względem
  `origin/rebase/phase-b-product-domain-architecture`; jest to stan odczytany
  lokalnie, bez przypisywania autorstwa publikacji.

## Utknęliśmy na

Brak blockera dla Phase E. Pełny implicit model routing i human methodology
review pozostają jawnie `NOT_EXECUTED`/`NOT_REVIEWED`, ale są odrębnymi
poziomami assurance i nie unieważniają ukończonych static-contract ani
validator-runtime proofów.

## Plan na następny krok

Po osobnej autoryzacji Phase E utworzyć mały kohort signed `pain.001`
submission stories, zastosować `semantic_enforcement: ENFORCED`, przeprowadzić
human review source evidence i uruchomić pełny enterprise governance runner;
nie migrować pozostałych legacy stories w tej samej zmianie.

## Pułapki, których nie wolno powtórzyć

- Nie używać deprecated aliases z myślnikami w nowych aktywnych artefaktach;
  historyczne wystąpienia są dozwolone wyłącznie przez jawny zakres vocabulary.
- Nie traktować wpisu w source registry, `VERIFY_PER_USE`, nieaktualnego ani
  participant-only evidence jako podstawy `SOURCE_CONFIRMED + READY`.
- Nie opisywać fixture-contract proof jako model behavior lub pełnego agent
  E2E; brak bezpiecznego runnera musi pozostać `NOT_EXECUTED`.
- Nie tworzyć use case’u lub agregatu z technicznego rzeczownika, nie kopiować
  rail-specific reguł do common core i nie uznawać implementacji za scheme
  authority.
- Nie zmieniać frozen ADR, nie rozpoczynać hurtowej migracji 304 legacy
  stories, nie modyfikować chronionego Modulith javadoc i nie wykonywać push.
