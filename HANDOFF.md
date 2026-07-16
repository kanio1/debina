# HANDOFF

## Zadanie

Sesja `GOVERNANCE-CLOSURE + DATABASE-SKILLS + PLANNING-DEEPENING + EPIC-27-STORY-27.1` (2026-07-16, druga sesja tego dnia — kontynuacja po `AGENT-GOVERNANCE-AND-PLANNING-REDESIGN`, HEAD startowy `49430859c4159aae759f289aac775e58fe005236`, worktree czysty, zgodny z oczekiwanym baselinem). Sześć rezultatów: (1) domknięcie niespójności instrukcji agentów, (2) odświeżenie `HANDOFF.md`, (3) CI dla governance/capability graph, (4) pogłębiona inwentaryzacja + naprawa potwierdzonych zależności, (5) pięć projektowych skills bazodanowych (Testcontainers-first), (6) test-first implementacja `EPIC-27` Story 27.1. `PRODUCTION_CODE_CHANGES: ALLOWED ONLY FOR EPIC-27 STORY 27.1`, `DATABASE_MIGRATIONS: FORBIDDEN`, `PUSH: FORBIDDEN` — obie zasady honorowane przez całą sesję. Agent runtime: Claude Code CLI.

## Zrobione

**Część C — Governance closure:**
- Naprawiono sprzeczność w `AGENTS.md` session protocol (§3): było "kontynuuj od pierwszego not-started/in-progress task", teraz jawnie capability-first — najpierw `planning/README.md`, potem `BACKLOG-REDESIGN.md`/`capabilities.yaml`, wybór najwyżej sklasyfikowanej story `READY` (nie formalny status), re-check dependencies/decision gates/`verify:` przed startem. Analogicznie zaktualizowano `planning/AGENTS.md` session workflow.
- Usunięto jedyne aktywne odwołanie do `act` jako mechanizmu testowego: `backend/AGENTS.md` "Testing" → "Test execution model" (bez `act`, bez Docker-in-Docker jako standardowej diagnostyki). **Nie tknięto** historycznych zapisów `act` w `planning/epics/EPIC-07-ci-cd-foundation.md` (epik `done`, zapis dowodowy z przeszłości, nie żywa instrukcja).
- `infra/AGENTS.md`: nowa sekcja "Compose vs. Testcontainers" — testy automatyczne mają preferować izolowany Testcontainers, nie `infra_postgres_1`.
- `.github/workflows/planning-governance.yml` (nowy): uruchamia 4 walidatory + `git diff --check` na właściwym zakresie (PR: `base...head`, push: `before..after` z obsługą pierwszego commita/zero-SHA). Backend CI (`backend.yml`) już istniał i już był poprawny (bez `act`, bez `services:` Postgres, czysty `./mvnw -f backend test` na hostowanym runnerze z natywnym Dockerem) — nie utworzono duplikatu.
- `tools/agent-config/validate-governance.sh` (nowy) — lokalny agregator 4 walidatorów, bez `act`.

**Część E — Pogłębiona inwentaryzacja:**
- Background research agent przeczytał w pełni `EPIC-14`/`24`/`51`/`57`/`65` + wszystkie bezpośrednio cytowane źródła i powiązane epiki, z cytatami dowodowymi.
- Zastosowane decyzje: `EPIC-51`→`EPIC-12/Story 12.1` (NARROW), `EPIC-57`→`EPIC-12/Story 12.1` (NARROW), `EPIC-65`→`EPIC-27/Story 27.2` (NARROW, epik + Story 65.4), `EPIC-14/Story 14.1`→dodano `EPIC-46/Story 46.1`+`EPIC-47/Story 47.1` (ADD MISSING EDGE — drugi brakujący edge, `delivery_receipts`, nieodkryty w poprzedniej sesji), `EPIC-24/Story 24.7`→zawężono `EPIC-12` do `12.1`, ale **nie** oznaczono `READY` (`[OPEN-QUESTION]`: brak właściciela backendowego CRUD dla reference-data, `sepa-nexus-message-flow-and-data-blueprint.md` §7.2 nazywa endpoint, nikt go nie buduje).
- Wszystko zweryfikowane pod kątem cykli — `planning/capability-graph.json` (142 węzły, 252 krawędzie, regenerated), `validate-capability-graph.py` → `PASS`, 0 cykli.
- `planning/decisions/GRAPHQL-OWNER-DECISION.md` (nowy) — formalny decision gate `gate.graphql-owner`, 3 opcje właściciela (rozszerz `EPIC-16`, rozszerz `EPIC-23`, nowy epik), bez rekomendacji (nie architektura tej sesji). Wpięty w `capabilities.yaml`/`capability-graph.json`/`.mmd`/`BACKLOG-REDESIGN.md`, blokuje `EPIC-26/26.4`, `EPIC-16`, `EPIC-23/23.1B`, `EPIC-53`.
- `planning/story-inventory.json` (nowy, 277 stories, programowo wyprowadzony z `planning/epics/*.md` przez skrypt jednorazowy w scratchpadzie — nie skomitowany, tylko wynik) + `tools/agent-config/validate-story-inventory.py` (nowy walidator: duplikaty, status vocabulary, source_file istnieje, `done` ma dowód, `READY`-kandydat ma wykonalny `verify:`) → `PASS`.

**Część F — 5 skills bazodanowych** (`.claude/skills/`, współdzielone z Codex CLI przez istniejący symlink `tools/codex/.agents/skills`, zweryfikowany):
- `postgres-rls-migration` rozbudowany (SKILL.md skrócony + 5 nowych `references/*.md`: USING vs WITH CHECK, grant matrix, SECURITY DEFINER, role-switching tests, RLS Testcontainers-first).
- `sepa-nexus-flyway-safe-change` (nowy, 7 references): append-only, expand/contract, lock analysis, NOT VALID constraints, concurrent indexes, forward-fix, upgrade verification.
- `sepa-nexus-payments-data-integrity` (nowy, 7 references): money/currency, ISO 20022 identifiers, idempotency, append-only evidence, ledger invariants, outbox/inbox, correlation integrity (MATCHED/AMBIGUOUS/ORPHANED).
- `sepa-nexus-database-testing` (nowy, 8 references): Testcontainers PG/Kafka (wzorce z realnego kodu: `PaymentsRlsTest`, `KafkaIntegrationSupport`), migration fresh/upgrade, grants/writer-isolation, RLS negative tests, concurrency/idempotency, non-vacuous tests (mutation-proof).
- `sepa-nexus-database-review` (nowy, 4 references): druga faza po implementacji, verdict PASS/CHANGES REQUIRED, 4 checklisty (migration/schema/query/security).
- Routing dodany do `backend/AGENTS.md`/`infra/AGENTS.md` (krótkie tabele, bez duplikacji treści).
- `tools/agent-config/validate-database-skills.sh` (nowy) — struktura/frontmatter/references istnieją, brak duplikatu pod `tools/codex`, brak zakazanych instrukcji (BYPASSRLS, FLOAT dla pieniędzy, edycja applied migration, `act` jako bramka, cross-schema access, auto DROP/TRUNCATE) → `PASS`.

**Część G — EPIC-27 Story 27.1** (jedyna dozwolona zmiana kodu produkcyjnego):
- Zbadano istniejące konwencje (`Pain001CanonicalMapper`, `CanonicalMappingResult`/`MappingError`/`MappingErrorCode`, `HardenedXmlFactory`) przed pisaniem kodu.
- Test-first: `Pacs002IdentifierExtractionTest` napisany jako pierwszy → RED (błąd kompilacji, klasy produkcyjne nie istniały) → zaimplementowano `Pacs002IdentifierExtractor`/`Pacs002OriginalIdentifiers`/`Pacs002IdentifierExtractionResult` (pakiet `com.sepanexus.modules.paymentlifecycle.isoadapter`, ta sama tymczasowa lokalizacja co pozostałe klasy iso-adapter) → GREEN (11/11).
- Pinned `pacs.002.001.10` (realna, aktualna wersja ISO 20022, sparowana z `pacs.008.001.08`/`pain.001.001.09`) — ta sama metoda rozstrzygnięcia jak dla pain.001.
- Zakres ściśle wg Story 27.1: tylko `OrgnlMsgId`/`OrgnlEndToEndId`, żadnej korelacji, żadnego zapisu DB, żadnej decyzji biznesowej — zgodnie z wiążącą zasadą "adapter koreluje, payment-lifecycle przechodzi FSM".
- Mutation-proof: zamieniono tag `OrgnlMsgId` na nieistniejący → 7/11 testów poprawnie zawiodło → mutacja cofnięta → 11/11 ponownie PASS, `git diff --check` czysty, brak pozostałości.
- Pełna regresja backendu: `./mvnw -f backend test` → **178/178 PASS**, `BUILD SUCCESS`.
- Planning zaktualizowany: Story 27.1 → `done`, `EPIC-27` → `in-progress`, `planning/README.md`, `capability-graph.json` (status węzła), `story-inventory.json` (regenerated) — wszystkie 4 walidatory ponownie `PASS`.
- `sepa-nexus-database-review` uruchomiony na zmianie: `NO DATABASE DDL/DML CHANGE`, weryfikacja semantyki identyfikatorów i granic modułu — **PASS**, brak blocking findings.

## Utknęliśmy na

Nic nie blokuje merytorycznie tę sesję. Story 27.2 (9-krokowa korelacja) celowo nierozpoczęta — poza zakresem tej sesji (`PRODUCTION_CODE_CHANGES: ALLOWED ONLY FOR EPIC-27 STORY 27.1`).

## Plan na następny krok

Osobna sesja implementacyjna dla `EPIC-27` Story 27.2: 9-krokowa polityka korelacji → `iso.iso_message_correlation` (MATCHED/AMBIGUOUS/ORPHANED), wymaga migracji Flyway (obecnie zabronione w tej sesji) i realnych testów Testcontainers PostgreSQL. Aktywować przed startem: `sepa-nexus-flyway-safe-change`, `sepa-nexus-payments-data-integrity`, `sepa-nexus-database-testing`, `sepa-nexus-database-review`, `postgres-rls-migration` (tylko jeśli zmiana dotyka RLS/roli). Nie łączyć Story 27.2 z 27.1 w jednym vertical slice (już rozdzielone). Nie uruchamiać przez `act`.

Alternatywnie: rozstrzygnięcie `planning/decisions/GRAPHQL-OWNER-DECISION.md` (decyzja użytkownika/zespołu wymagana) odblokowałoby `EPIC-26/26.4`, `EPIC-16`, `EPIC-23/23.1B`, `EPIC-53` jednym posunięciem. Albo `EPIC-10`'s `SECURITY DEFINER` decision gate (wciąż `PROPOSED — REQUIRES USER ACCEPTANCE`, niezmieniony przez tę sesję) — akceptacja odblokowałaby `10.1A`–`10.1D`, `10.2`.

`EPIC-24/Story 24.7`'s brakujący właściciel backendowego CRUD dla reference-data (`sepa-nexus-message-flow-and-data-blueprint.md` §7.2) to nowy, nierozstrzygnięty `[OPEN-QUESTION]` — potrzebuje nowej story (prawdopodobnie pod `EPIC-12`) zanim `24.7` będzie `READY`.

## Pułapki, których nie wolno powtórzyć

- **Niespójność session-protocol między dwoma plikami (`AGENTS.md` vs `planning/AGENTS.md`) potrafi przetrwać całą sesję niewykryta, jeśli nikt jej jawnie nie szuka** — poprzednia sesja dodała capability-first do jednego pliku, zapominając zsynchronizować drugi. Zawsze sprawdzaj oba przy zmianie reguł wyboru pracy.
- **Znaleziska z poprzedniej sesji oznaczone "not deep-dived" bywają odkładane bez terminu** — `EPIC-51`/`57`/`65`/`14.1`/`24.7` czekały od poprzedniej sesji jako "candidates, not verdicts". Warto planować osobny "dependency deep-dive" krok zamiast zakładać, że ktoś do tego wróci przy okazji.
- **Jeden potwierdzony brakujący edge (`EPIC-14/14.1`→`EPIC-46`) może kryć drugi, nieodkryty** (→`EPIC-47`) — grant-test story wymieniał trzy tabele, poprzednia sesja znalazła tylko jedną z dwóch brakujących zależności. Zawsze czytaj cały task/verify tekst, nie tylko pierwszą wzmiankę.
- **Węzły grafu na różnej granulacji (epic-level vs story-level) wymagają jawnego sprawdzenia przed dodaniem krawędzi** — `EPIC-16`/`EPIC-46`/`EPIC-47`/`EPIC-51`/`EPIC-57`/`EPIC-65` mają tylko węzły epic-level w `capability-graph.json` (nie były częścią 12 story-poziomowych epików poprzedniej sesji); nowe krawędzie musiały celować w istniejące węzły (epic-level), nie w wymyślone story-level ID.
- Pułapki z poprzednich sesji (niezmienione, wciąż aktualne): broad epic-level `depends_on` może manufakturować cykle; epik "not done" może kryć gotową sub-capability; `blocked` story może kryć już-`done` pracę; JPA write-behind pod przełączoną rolą; `act`+Testcontainers = zawsze `Could not find a valid Docker environment`; ArchUnit domyślnie skanuje test-classes; `SECURITY DEFINER` wymaga jawnego `SET search_path`; `set_config(..., true)` wraca do `''` po commit i po rollback.
