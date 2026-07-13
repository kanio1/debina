---
name: session-handoff
description: Użyj na końcu każdej dłuższej sesji, gdy kontekst się zapełnia, albo gdy użytkownik sygnalizuje koniec — żeby zapisać HANDOFF.md; użyj też na początku nowej sesji, żeby go przeczytać jako pierwsze.
---

# session-handoff

## Na początku sesji

Jeśli `HANDOFF.md` istnieje w korzeniu repo — przeczytaj go **pierwszy, przed jakimkolwiek innym działaniem**. To jedyny nośnik pamięci między sesjami; bez niego zaczynasz na ślepo. Traktuj sekcję "Plan na następny krok" jako punkt startowy, nie jako sugestię do renegocjacji.

## Na końcu sesji

Zapisz `HANDOFF.md` w korzeniu repo, **CAŁKOWICIE nadpisując** poprzednią wersję. To jest jeden zawsze-aktualny dokument opisujący stan bieżący — nie rosnący log, nie historia sesji. Nie dopisuj, nie zachowuj starych sekcji "na wszelki wypadek" — jeśli coś ze starej wersji jest wciąż aktualne, przepisz to na nowo we właściwej sekcji.

Pisz tak, jakby czytał to zupełnie nowy agent bez żadnego kontekstu tej rozmowy — bez skrótów myślowych, bez "jak ustaliliśmy wcześniej", bez odwołań do czegoś, co nie jest w tym pliku ani w repo.

Zapisuj `HANDOFF.md` **zawsze**, niezależnie jak krótka lub jak mało istotna wydawała się sesja. Brak zmian merytorycznych to też stan wart zapisania (np. sesja spędzona wyłącznie na dyskusji bez decyzji).

## Wymagany format — dokładnie pięć sekcji, w tej kolejności

```markdown
# HANDOFF

## Zadanie
Jedno-dwa zdania: co to za projekt i co się w nim właśnie robi. Zrozumiałe dla kogoś, kto nigdy nie widział tego repo.

## Zrobione
Konkretnie, co zostało wykonane w tej sesji (i wcześniej, jeśli wciąż istotne) — z odniesieniem do realnych plików/epików/stories w /planning/ (np. "EPIC-01-platform-skeleton, story 0.2 — zaznaczona jako done"), a nie ogólnikami typu "trochę popracowaliśmy nad backendem".

## Utknęliśmy na
Dokładny bieżący stan pracy w toku. Jeśli coś nie działa — dokładny ostatni błąd (komunikat, komenda, plik). Jeśli nic nie blokuje i sesja zakończyła się czysto — napisz to wprost ("nic nie blokuje, następny task jeszcze nierozpoczęty").

## Plan na następny krok
Jednoznacznie: pierwsza czynność następnej sesji. Nie lista opcji do rozważenia — jedna konkretna czynność (np. "otwórz /planning/epics/EPIC-03-payment-lifecycle.md, story 1.2, wykonaj pierwszy nieodhaczony task i uruchom jego `verify:`").

## Pułapki, których nie wolno powtórzyć
Konkretne błędy już napotkane w tej lub poprzednich sesjach i jak ich uniknąć. Puste/"brak" jeśli faktycznie nic takiego się nie wydarzyło — nie wymyślaj pułapek na siłę.
```

## Zasady

- `HANDOFF.md` ≠ `CLAUDE.md`. `CLAUDE.md` to stałe zasady projektu, zmieniane rzadko. `HANDOFF.md` to zmienny stan bieżącej pracy, nadpisywany co sesję. Nigdy nie przenoś treści z jednego do drugiego i nigdy ich nie łącz w jeden plik.
- Jeśli w trakcie sesji natrafiono na pytanie, na które dokumentacja projektu nie odpowiada (patrz skill `artifact-derived-planning`) i nie zostało ono rozstrzygnięte — musi się pojawić w `HANDOFF.md`, najczęściej w sekcji "Utknęliśmy na" lub "Plan na następny krok", nigdy rozstrzygnięte samodzielnie tylko po to, by handoff wyglądał czyściej.
