# HANDOFF

## Zadanie

To jest repozytorium **SEPA Nexus** — w pełni udokumentowany, wcześniej niezaimplementowany, syntetyczny projekt platformy płatniczej SEPA/ISO 20022, którego celem jest bycie realistycznym poligonem do nauki Playwright/testowania systemów enterprise dla Senior QA/SDET. Praca postępuje sekwencyjnie przez katalog `/planning/`, task po tasku, zgodnie z `sepa-nexus-iteration-0-foundation-plan.md`. Ta sesja kontynuowała EPIC-00 od miejsca, w którym poprzednia sesja się zatrzymała.

## Zrobione

1. **Ponownie uzgodniono i zweryfikowano Story 0.1** (`/planning/epics/EPIC-00-repository-agent-foundation.md`). Wykryto rozbieżność: `frontend/.node-version` faktycznie zawierał `24.18.0`, mimo że plik epika i `HANDOFF.md` z poprzedniej sesji twierdziły, że jest ustawiony na `20` i zweryfikowany. Naprawiono `frontend/.node-version` na dokładnie `20` i ponownie uruchomiono wszystkie trzy weryfikacje Story 0.1 (istnienie katalogów+README, `.node-version`, `npm view typescript-eslint peerDependencies` + pin TS 5.9.3 w `frontend/README.md`) — wszystkie **PASS**. Opis tasku `.node-version` w EPIC-00 zaktualizowano, żeby odzwierciedlał tę naprawę. Story 0.1 pozostaje `status: done`.
2. **Naprawiono nieaktualny opis stanu repo w `CLAUDE.md`.** Usunięto zdanie "zero code, zero commits" (nieaktualne — istnieje pierwszy commit i katalog `/planning/`), zastąpiono opisem faktycznego stanu: monorepo skeleton (`backend/`, `frontend/`, `infra/`) istnieje z EPIC-00 Story 0.1, `/planning/` istnieje, brak jeszcze prawdziwego kodu aplikacji/buildów backend/frontend, aktywne zadanie to EPIC-00 Story 0.2. Dodano `@AGENTS.md` jako pierwszą linię importu w `CLAUDE.md` (pojedynczy import, treść `AGENTS.md` nie została skopiowana do `CLAUDE.md`).
3. **Ukończono całą EPIC-00, Story 0.2** — `AGENTS.md` i pięć projektowych skilli implementacyjnych:
   - Utworzono root `AGENTS.md` z regułami working agreements (no-Playwright w Iteracji 0 — zawiera dosłowny, małymi literami ciąg "no Playwright" — verify-before-checkbox, granice modułów) i mapą repo. Treść skopiowana z `sepa-nexus-iteration-0-foundation-plan.md` (linie 64-81), nieparafrazowana.
   - Utworzono pięć plików `SKILL.md` w `.claude/skills/`: `spring-modulith-module`, `postgres-rls-migration`, `keycloak-realm-config`, `nextjs-bff-route`, `shadcn-component-scaffold` — treść skopiowana verbatim z tego samego dokumentu źródłowego (linie 86-167), każdy z YAML frontmatter (`name`, `description`).
   - Trzy istniejące skille planistyczne (`artifact-derived-planning`, `epic-story-task-catalog`, `session-handoff`) pozostały nietknięte.
   - Naprawiono błędną, nieaktualną komendę `verify:` przy tasku `spring-modulith-module` w EPIC-00 (oryginał: `find .claude/skills -name SKILL.md | wc -l` → `5`, nieaktualne bo trzy skille planistyczne już istniały przed Story 0.2 — docelowa liczba to `8`, nie `5`). Nowa komenda weryfikuje istnienie i treść tego jednego pliku; łączna liczba `8` sprawdzana zbiorczo na końcu Story 0.2. Uzasadnienie zapisane bezpośrednio przy tasku w EPIC-00.
   - Zmirrorowano skille dla Codex CLI: `.codex/` nie istniał wcześniej, więc utworzono `mkdir -p .codex && ln -s ../.claude/skills .codex/skills` (względny symlink, nie kopia). `diff -r .claude/skills .codex/skills` → brak różnic.
   - Uruchomiono kompletną listę 20 weryfikacji z Kroku 5 work packetu (istnienie/treść AGENTS.md, wszystkich pięciu SKILL.md, licznik 8 plików, mirror diff, `git diff --check`) — **wszystkie PASS**.
   - Zaktualizowano `/planning/epics/EPIC-00-repository-agent-foundation.md`: wszystkie taski Story 0.2 odhaczone `[x]` z `PASS` i datą 2026-07-13, `Story 0.2 status: done`.
   - `Story 0.3 status: not-started` i `EPIC-00 status: in-progress` pozostały bez zmian (świadomie, zgodnie z instrukcją sesji) — `/planning/README.md` również wciąż pokazuje EPIC-00 jako `in-progress`, bez potrzeby edycji.

## Utknęliśmy na

Nic nie blokuje. Story 0.2 zakończona czysto i w całości zweryfikowana (20/20 komend `verify` PASS). Zgodnie z instrukcją tej sesji — świadomie zatrzymano się tutaj, **nie rozpoczęto Story 0.3**, mimo że jest technicznie kolejna w kolejności `depends_on`.

## Plan na następny krok

Otwórz `/planning/epics/EPIC-00-repository-agent-foundation.md`, Story 0.3 — "Base Docker Compose (Postgres, Keycloak, Kafka)". Pierwszy nieodhaczony task: "Napisz `infra/docker-compose.yml`" z trzema serwisami (`postgres:18` port 5432 + named volume, `quay.io/keycloak/keycloak:26.6.4` z `start-dev --import-realm` port 8080 importujący `infra/keycloak/realm-export.json` — ten plik jeszcze nie istnieje i trzeba go będzie stworzyć przed testem importu, `apache/kafka:latest` w trybie KRaft bez osobnego Zookeepera port 9092). Dokładny szkielet YAML podany w `sepa-nexus-iteration-0-foundation-plan.md` liniach 178-208. `verify: docker compose -f infra/docker-compose.yml config` → poprawna konfiguracja, brak błędów. Story 0.3 `depends_on: [Story 0.1]`, która jest `done`, więc nic jej nie blokuje.

## Pułapki, których nie wolno powtórzyć

- **Nie ufaj bezkrytycznie zapisowi `PASS` w pliku epika z poprzedniej sesji bez ponownego uruchomienia komendy `verify`.** W tej sesji `frontend/.node-version` był opisany jako zweryfikowany `20`, a faktycznie zawierał `24.18.0` — rozbieżność między dokumentacją a stanem repo. Zawsze uruchom `verify:` ponownie przy wznowieniu pracy nad już "ukończonym" taskiem, jeśli coś wygląda niespójnie.
- Komendy `verify:` skopiowane z dokumentu źródłowego (`sepa-nexus-iteration-0-foundation-plan.md`) mogą być nieaktualne względem faktycznego stanu repo (np. licznik plików `SKILL.md` zakładający, że katalog `.claude/skills/` jest pusty przed Story 0.2, podczas gdy trzy skille planistyczne już tam były z poprzedniej sesji). Napraw komendę minimalnie, zachowując intencję wymagania, i zapisz uzasadnienie przy tasku — nie usuwaj ani nie osłabiaj testu, żeby "przeszedł".
- `.codex/` katalog nie istniał wcześniej w tym repo — nie było ryzyka nadpisania istniejącej zawartości, ale zawsze sprawdź `test -e .codex/skills` przed utworzeniem symlinku/kopii, żeby nie ubić wcześniejszej pracy użytkownika.
- `[OPEN-QUESTION]` z poprzedniej sesji wciąż nierozstrzygnięte i nadal aktualne: ADR-N1/decision gate opisują stos Iteracji 0 jako zawierający OTel, ale konkretny `docker-compose.yml` w `sepa-nexus-iteration-0-foundation-plan.md` (linie 178-208) go nie zawiera. Nie rozstrzygnięto samodzielnie — patrz sekcja "Otwarte pytania" w `/planning/epics/EPIC-00-repository-agent-foundation.md`. Przy Story 0.3 nie dodawaj samodzielnie serwisu OTel do compose — trzymaj się dokładnie trzech serwisów ze źródła, chyba że użytkownik rozstrzygnie to pytanie.
