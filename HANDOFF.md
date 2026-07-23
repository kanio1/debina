# HANDOFF

## Zadanie

Realizacja programu `docs/ci/DAGGER-FINAL-ARCHITECTURE-CONTRACT.md` w ośmiu
lokalnych falach. Celem jest jeden automatyczny `acceptance`, trwała
klasyfikacja testów, finalne grafy integration/full-local, pełny runtime proof
i czysty worktree bez `git push`.

## Zrobione

- Wave 0 odtworzyła stan z HEAD `5f089fa`, bieżącego statusu, lokalnych
  commitów, wcześniejszego HANDOFF oraz zachowanych trace pod `/tmp`.
- Zapisano stały kontrakt, kanoniczną powierzchnię, grafy, wymagania
  klasyfikacji i terminalne markery w
  `docs/ci/DAGGER-FINAL-ARCHITECTURE-CONTRACT.md`.
- Potwierdzono Dagger CLI/Engine `v0.21.4`, Go `1.26.5`, rootful Podman oraz
  niezależny probe `DAGGER-WAVE0-ENGINE-PROBE`.
- Audyt wykazał 137 backendowych klas testowych: 62 z `@Testcontainers` i 75
  bez tej adnotacji. Nie ma obecnie żadnego `@Tag`.
- Obecna ręczna lista socket-free integration obejmuje tylko 35 z 75 klas bez
  Testcontainers. To jest potwierdzona luka Wave 2 i nie może pozostać
  podstawą finalnej klasyfikacji.
- Zachowano istniejące niecommitowane zmiany przygotowujące część Wave 1; nie
  zostały odrzucone ani nadpisane.

## Utknęliśmy na

Nic nie blokuje. Wave 0 jest gotowa do focused static proof i lokalnego
commita; Wave 1 nie została jeszcze domknięta według nowego kontraktu.

## Plan na następny krok

Uruchomić focused static proof Wave 0, zacommitować wyłącznie kontrakt i ten
HANDOFF, następnie przejść do Wave 1: wprowadzić `SmokeSuite`, oddzielić
`PipelineAssurance` od `Acceptance` i oznaczyć compatibility aliases jako
deprecated.

## Pułapki, których nie wolno powtórzyć

- Nie traktować wcześniejszego zielonego `Acceptance` jako dowodu nowego
  kontraktu: wcześniej zawierał `pipeline-assurance`, był w pełni sekwencyjny i
  używał nazwy `smoke`.
- Nie zgubić istniejących niecommitowanych zmian podczas selektywnego commita
  Wave 0.
- Nie zastępować tagów JUnit kolejną ręczną listą klas.
- Nie dodawać ambient host socketu, nested Dagger CLI, remote CI ani `git push`.
- Nie zmieniać chronionego `build/generated-spring-modulith/javadoc.json`.
