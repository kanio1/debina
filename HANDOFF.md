# HANDOFF

## Zadanie

Realizacja programu `docs/ci/DAGGER-FINAL-ARCHITECTURE-CONTRACT.md` w ośmiu
lokalnych falach. Celem jest jeden automatyczny `acceptance`, trwała
klasyfikacja testów, finalne grafy integration/full-local, pełny runtime proof
i czysty worktree bez `git push`.

## Zrobione

- Wave 0 odtworzyła stan, zapisała finalny kontrakt i zakończyła się lokalnym
  commitem `18818e1 docs(ci): record final dagger architecture contract`.
- Wave 1 ustanowiła finalne nazwy/topologię i zakończyła się commitem
  `159a736 refactor(ci): establish final acceptance topology`.
- Wave 2 wprowadziła rozłączne tagi 39 klas `fast` / 99 klas
  `testcontainers`, equivalence 146+397=543 i zakończyła się commitem
  `b654a10 test(ci): classify backend suites by durable tags`.
- Wave 3 skonsolidowała pięć finalnych integration leaves i zakończyła się
  commitem `233a862 refactor(ci): consolidate integration contracts`.
- Wave 4 wyodrębniła jeden runtime-neutral frontend production build,
  udowodniła integration/smoke/cache i zakończyła się commitem
  `c935bf7 refactor(ci): reuse frontend production build`.
- Wave 5 ustawiła finalne stage: `fast` + `integration` równolegle, po ich
  sukcesie sekwencyjny `smoke-suite`. AST regression blokuje zmianę kolejności
  lub membership. `pipeline-assurance` pozostaje oddzielne, a `full-local`
  pozostaje sekwencyjnym `acceptance + backend-testcontainers`. Zmiany
  zakończyły się commitem
  `c69203f feat(ci): finalize acceptance orchestration`.
- Dodano wykonywalny hostowy `tools/ci/verify-dagger-architecture.sh`; w kodzie
  funkcji Dagger nadal nie ma nested Dagger CLI.
- Runner wykonuje static topology, generator hash idempotence,
  `dagger check --generate`, canonical functions/aliases, positive frozen image
  runtime, missing-lock negative regression, canonical `dagger check`,
  oddzielne `pipeline-assurance`, log-health obu trace’ów, cache trace,
  cache-volume concurrency i expected non-zero unexpected-child propagation.
- Pierwszy run ujawnił, że CLI 0.21.4 samo nie failuje po usunięciu lock entry.
  Dodano więc fail-closed mapowanie wszystkich image constants do root lock;
  disposable missing-entry copy jest teraz negatywnym proofem. Drugi run
  ujawnił brak frontend/root-lock inputs wewnątrz `moduleSelfTest`; inputs są
  teraz jawnie montowane w `/workspace`.
- Wave 6 ponownie wykonała kompletny runner na aktualnym HEAD. Wszystkie
  markery static/generator/lock/check/assurance/log/cache/cache-volume/
  unexpected-failure są zielone; artefakty:
  `/tmp/debina-wave6-architecture/`.
- Osobne runtime proofy Wave 6 zakończyły się kodem 0:
  `backend-testcontainers` 397/0/0/0,
  `backend-regression-all` 543/0/0/0 oraz `full-local`. Trace `full-local`
  zawiera dokładnie jedno wykonanie selektora `fast`, jedno
  `testcontainers` i jeden zakończony compositor.
- Bezpośredni Maven `fast` potwierdził 146/0/0/0. Zbiory raportowanych klas:
  39 `fast`, 99 `testcontainers`, przecięcie 0, suma 138 i dokładna zgodność
  z 138 klasami pełnego oracle. Logi i zbiory są pod
  `/tmp/debina-wave6-*.log` oraz `/tmp/debina-wave6-*-classes.txt`.
- Chroniony `build/generated-spring-modulith/javadoc.json` nie zmienił się
  podczas proofów i ma oczekiwany SHA-256
  `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`.

## Utknęliśmy na

Nic nie blokuje. Wave 6 jest runtime-proven i gotowa do lokalnego commita.
Zachowane wcześniejsze zmiany dokumentacyjne/planning nadal pozostają poza tym
selektywnym commitem.

## Plan na następny krok

Zacommitować stan Wave 6, następnie wykonać Wave 7: ujednolicić manifest,
blueprint, implementation record, runbook, smoke matrix, planning i HANDOFF z
finalnym kontraktem; zapisać alias migration table, dokładne komendy/wyniki,
counts, commity, upstream i protected audit. Potem wykonać końcowy audyt bez
nowego refaktoru, doprowadzić worktree do czystości i nie wykonywać `git push`.

## Pułapki, których nie wolno powtórzyć

- Nie dodawać backend `fast` z powrotem do top-level `Fast`; jego właścicielem
  w acceptance jest `backend-integration`.
- Nie uruchamiać unfiltered `BackendRegressionAll` jako dziecka `FullLocal`;
  duplikowałoby 146 testów `fast`.
- Nie rozbijać `database-contract` na niezależne fresh services; upgrade ma
  pozostać jedyną drugą instancją.
- Nie dodawać OIDC/BFF endpointów ani secretu przed
  `frontendProductionBuild`; zmieniłoby to cache key i mogłoby bake'ować runtime
  configuration.
- Nie usuwać jawnych frontend/root-lock inputs z `moduleSelfTest`; pure
  regressions muszą działać identycznie lokalnie i wewnątrz Dagger.
- Nie przypisywać CLI 0.21.4 nieudowodnionego missing-lock fail-closed;
  odpowiedzialność za kompletność ma repozytoryjny regression.
- Nie dodawać ambient host socketu, nested Dagger CLI, remote CI ani `git push`.
- Nie zmieniać chronionego `build/generated-spring-modulith/javadoc.json`.
