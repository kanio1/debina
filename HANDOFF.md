# HANDOFF

## Zadanie

Niezależny, source-backed review i hardening lokalnych pipeline’ów Dagger Go SDK: odtworzenie Phase D, audyt automatycznych checks, powtarzalności, cache, source inputs, usług, generatorów, log health i Engine, następnie implementacja oraz runtime proof bez remote CI i bez `git push`.

## Zrobione

- Odtworzono czysty snapshot `c68b17c` na `rebase/enterprise-evolution`, potwierdzono pięć wskazanych commitów, Dagger CLI/Engine 0.21.4, rootful Podman 5.8.4 i początkowy hash chronionego javadoc `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`.
- Udowodniono P0: siedem dawnych `+check` dawało zagnieżdżony graf (Fast i Integration po 3, D3A 4, każdy D3B 3, assurance 2). Został jeden kanoniczny `PhaseD // +check`; publiczne callable functions pozostały, a AST regression blokuje powrót złej topologii.
- Wprowadzono Model A: `phase-d` i `all-socket-free` są kompletne socket-free, `all` jest jawnym legacy aliasem, a `full-local` dodaje Testcontainers wyłącznie przez typed socket.
- Dodano natywny `.dagger/lock`, centralny inventory 10 obrazów i live/frozen lifecycle. Pełny frozen proof przeszedł, a usunięty wpis spowodował oczekiwany fail-closed.
- Naprawiono krytyczne cache’owanie starego worktree: auto-injected constructor `Workspace` jest częścią stanu modułu. Zawężono backend/frontend/Dagger/governance inputs i manifest-only pnpm dependency layer; exact AsyncAPI i Keycloak realm fixtures zostały przywrócone po focused failures.
- Rozdzielono output determinism od cache reuse i selective invalidation. Wersjonowany runner zapisuje cold/warm/changed traces, vertexy, digesty i jawne `CACHED`; cache volumes są jawnie SHARED, wersjonowane toolchainem i mają izolowany reset/stress proof.
- Dodano focused service-binding alias/DNS/HTTP regression; przechodzi na 0.21.4 i kandydacie 0.21.7. Zachowano lazy services, brak host ports i brak ręcznego Start/Stop.
- Odtworzono NPE mappera dla paymentu oczekującego na approval z legalnym `null` business status. Mappers i frontend są null-safe bez scalania osi approval/business. Pełny Testcontainers regression: 542 testy, 0 failures/errors/skips.
- Dodano fail-closed log-health scanner. Odrzuca NPE, uncaught/JVM exceptions, panic, fatal startup i runtime ERROR/FATAL; dopuszcza tylko dokładne expected-failure markers oraz dwie dokładne obserwacje pustego Keycloak PostgreSQL bootstrap.
- Binding regeneration jest idempotentny, `dagger check --generate` nie wykazuje driftu. Redaction, unexpected-child propagation, service DNS, frozen lock, cache proof i cache-volume reset/stress przeszły.
- Finalny `dagger check --progress=plain` po ostatniej poprawce przeszedł w 250.92 s. Trace ma po jednym logicznym vertexie D3A i każdego D3B, a journeys są sekwencyjne. `fast`, `integration`, `smoke`, `smoke-payments`, `phase-d` i `all` zwróciły exit 0.
- Engine pozostaje 0.21.4. Kandydat 0.21.7 przeszedł focused fast/frozen/DNS, lecz repo-only bump łamie zwykłe calls przy zainstalowanym CLI 0.21.4; decyzja to DEFER do atomowej zmiany CLI+Engine.
- Dokumentacja architektury, manifest, toolchain baseline, implementation record, blueprint, program D11 i datowany review są zaktualizowane. Runtime traces pozostają wyłącznie w `/tmp`.

## Utknęliśmy na

Brak blokera dla zamkniętego D11. Upgrade do Dagger 0.21.7 jest świadomie odroczony, ponieważ wymaga atomowej aktualizacji host CLI i Engine oraz ponowienia pełnej macierzy; Phase E/remote CI nie jest autoryzowane.

## Plan na następny krok

Jeżeli właściciel projektu autoryzuje zmianę host toolchainu, rozpocznij osobny, atomowy upgrade CLI+Engine do 0.21.7 od odtworzenia pełnej macierzy z `docs/ci/DAGGER-ARCHITECTURE-REVIEW-2026-07-23.md`.

## Pułapki, których nie wolno powtórzyć

- Nie dodawaj `+check` do callable children/aliases kanonicznego `PhaseD`; niefiltrowany check uruchamia odkryte roots równolegle.
- Nie wywołuj `dag.CurrentWorkspace()` dopiero wewnątrz cacheable function; Workspace musi być injected inputem konstruktora lub funkcji.
- Nie usuwaj exact `infra/asyncapi/asyncapi.yaml` ani `infra/keycloak/realm-export.json` z backend boundary.
- Nie nazywaj równego digestu dowodem cache reuse; wymagaj jawnego `CACHED` trace oraz osobnego changed-input proofu.
- Nie stosuj szerokiego log allowlist. Negatywne Testcontainers security cases nie są zwykłym runtime log-health inputem.
- Nie łącz approval z business/ISO/finality/transport/receipt axes i nie wymuszaj sztucznego business status przed startem lifecycle.
- Nie dodawaj ambient host socketu do socket-free gates, nie implementuj remote CI/Phase E i nie aktualizuj samego `engineVersion` bez zgodnego CLI.
- Nie commituj traces, cache, sekretów, incidental generated files ani chronionego javadoc; nie wykonuj `git push`.
