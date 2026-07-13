---
name: artifact-derived-planning
description: Użyj przy tworzeniu lub aktualizacji katalogu epic/story/task w /planning/ — dyscyplina wyprowadzania planu z istniejącej dokumentacji bez wymyślania architektury.
---

# artifact-derived-planning

## Zasada nadrzędna

Jedynym źródłem prawdy jest dokumentacja/artefakty faktycznie obecne w tym repo. To narzędzie do **wyprowadzania** planu pracy z tego, co już zdecydowane — nie do projektowania architektury ani priorytetów od nowa. Jeśli czujesz pokusę, żeby "dopisać coś, co i tak by tam było" — nie rób tego; zapisz brak jako otwarte pytanie.

## Zanim napiszesz jakikolwiek epik

Przeskanuj całą dostępną dokumentację projektu — nie tylko jeden plik. W szczególności potraktuj istniejące pliki z zalążkami backlogu (np. sekcje "Backlog seed", "Concrete Backlog", "Updated Backlog") jako **input do przetworzenia i skonsolidowania**, nie jako gotowy wynik do skopiowania 1:1 ani jako coś do zignorowania.

Wyciągnij systematycznie:

1. **Wszystkie oznaczenia `[MVP]` / `[P1]` / `[P2]`** (lub odpowiadające im, np. numer iteracji). To jest priorytetyzacja projektu — nigdy nie twórz własnej. Jeśli dwa dokumenty się nie zgadzają, obowiązuje ten, który jest jawnie oznaczony jako nadrzędny/nowszy/`[FREEZE]` (np. ADR ponad wcześniejszym draftem).
2. **Wszystkie `[FREEZE]` i decyzje ADR.** To są nienegocjowalne inputy — ograniczają możliwe sekwencje pracy (np. "moduł X nie może powstać przed CI gates"), ale plan nigdy ich nie zmienia ani nie renegocjuje.
3. **Wszystkie jawnie nazwane zależności między modułami/epikami** (np. "blokuje X", "wymaga Y", "closes R-NN", "depends on ADR-N5"). To one, nie intuicja tematyczna, ustalają kolejność techniczną.
4. **Jawnie ustalone sekwencjonowanie** (np. "Playwright dopiero gdy istnieją konkretne ekrany", "Iteracja 0 przed Iteracją 1", "signature blueprint przed EPIC-SIG"). Szanuj je dosłownie — nie przesuwaj "bo logicznie by pasowało wcześniej".

## Gdy dokumentacja nie odpowiada na pytanie planistyczne

Zapisz to jako otwarte pytanie (w pliku epika jako `[OPEN-QUESTION]` w sekcji odpowiedniej story, albo w `HANDOFF.md` jeśli blokuje całą sesję) — **nigdy nie wymyślaj odpowiedzi**, nawet jeśli wydaje się oczywista. Przykład dobrego zachowania: "dokumentacja nie precyzuje kolejności wewnątrz EPIC-RECON — zostawiam jako open question" zamiast cichego ustalenia własnej kolejności.

## Kolejność: techniczna ważniejsza niż tematyczna

Grupowanie epików wg tematu (np. "wszystko o egress w jednym miejscu") jest wtórne wobec faktycznych zależności odczytu/zapisu. Jeśli zadanie B czyta/konsumuje coś, co tworzy zadanie A (tabelę, event, kontrakt, port, plik konfiguracyjny) — **A musi poprzedzać B** w katalogu i w `depends_on`, niezależnie od tego, do jakiego modułu tematycznie należą.

## Kryteria ukończenia

Każde zadanie (task) potrzebuje **konkretnego, wykonywalnego kryterium ukończenia** — dokładnej komendy (`verify: <komenda>`) i oczekiwanego rezultatu. Nigdy opisu słownego typu "działa poprawnie", "zaimplementowane", "przetestowane ręcznie" bez podania jak to zweryfikować. Jeśli w danym momencie repo nie ma jeszcze frameworka testowego/build systemu do uruchomienia realnej komendy (np. wciąż faza dokumentacyjna) — komenda weryfikująca może być np. sprawdzeniem istnienia/zawartości pliku (`test -f ...`, `grep -q ...`), ale musi być czymś uruchamialnym, nie zdaniem opisowym.

## Sequencing, którego nie wolno łamać w tym konkretnym projekcie

- Iteracja 0 (platform skeleton) poprzedza Iterację 1 (`ADR-N1`) — żadna tabela domenowa przed zielonymi CI gates.
- Testy Playwright nie są planowane ani scaffoldowane, dopóki dokument wizji/pokrycia Playwright nie wskaże, że dany ekran/moment już na to pozwala (Iteracja 0 ma jawną regułę `[NO-PLAYWRIGHT]`).
- `[P1]`/`[P2]` nie wchodzi do iteracji MVP bez nadrzędnego ADR (reguła z decision gate).
- Moduł `signature` musi mieć zamkniętą granicę (ports/DDL) zanim epiki zależne od jego portu (np. egress signing, ingress verify-before-parse) mogą być zaplanowane jako wykonywalne.
