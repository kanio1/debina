# HANDOFF

## Zadanie

Realizacja programu `docs/ci/DAGGER-FINAL-ARCHITECTURE-CONTRACT.md` w ośmiu
lokalnych falach. Celem jest jeden automatyczny `acceptance`, trwała
klasyfikacja testów, finalne grafy integration/full-local, pełny runtime proof
i czysty worktree bez `git push`.

## Zrobione

- Wave 0 odtworzyła stan, zapisała finalny kontrakt i zakończyła się lokalnym
  commitem `18818e1 docs(ci): record final dagger architecture contract`.
- Wave 1 ustanowiła `Acceptance` jako jedyny `+check`; AST regression potwierdza
  dokładnie `fast`, `integration`, `smoke-suite`.
- Dodano publiczne `SmokeSuite` i niezależne `PipelineAssurance`. `smoke`,
  `phase-d`, `all-socket-free`, `all`, `cache-reuse` i `cache-invalidation`
  pozostają bezpośrednimi deprecated aliases.
- Canonical cache leaves nazywają uczciwie dowód rezultatu:
  `cache-output-determinism` i `cache-output-input-sensitivity`; trace-based
  cache proof pozostaje odpowiedzialnością zewnętrznego runnera.
- Pure compositors odrzucają pustą nazwę, nil runner i zduplikowaną
  klasyfikację przed uruchomieniem grafu.
- Focused proof Wave 1: `go test ./...`, `git diff --check`,
  `dagger develop -y`, `dagger check --generate`, `dagger check -l`,
  `dagger functions`, `dagger call pipeline-assurance --lock=frozen
  --progress=plain` oraz `dagger call smoke-suite --lock=frozen
  --progress=plain` zakończyły się kodem 0. `smoke-suite` wykonało komplet D3A
  i trzech D3B journey w 3m50s.

## Utknęliśmy na

Nic nie blokuje. Wave 1 jest gotowa do lokalnego commita. Niecommitowane
zmiany dokumentacyjne i planning spoza wyznaczonego zakresu commita Wave 1
pozostają zachowane do późniejszych fal.

## Plan na następny krok

Zacommitować wyłącznie implementację, manifest, architecture doc i HANDOFF dla
Wave 1. Następnie rozpocząć Wave 2: sklasyfikować każdą konkretną klasę JUnit
dokładnie jednym tagiem `fast` albo `testcontainers`, dodać completeness
regression i udowodnić liczniki oraz equivalence pełnej regresji.

## Pułapki, których nie wolno powtórzyć

- Nie włączać `pipeline-assurance` do `acceptance`; jest oddzielnym publicznym
  gate.
- Nie zmieniać finalnej orkiestracji acceptance przed Wave 5: Wave 1 ustala
  nazwy i membership, a Wave 5 zmieni pierwsze dwa etapy na równoległe.
- Nie zastępować tagów JUnit kolejną ręczną listą klas.
- Nie dodawać ambient host socketu, nested Dagger CLI, remote CI ani `git push`.
- Nie zmieniać chronionego `build/generated-spring-modulith/javadoc.json`.
