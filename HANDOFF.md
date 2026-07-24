# HANDOFF

## Zadanie

Debina realizuje enterprise rebase obejmujący governance Use-Case 2.0,
architekturę i lokalną platformę weryfikacji. Zakończono hardening istniejącego
skillu `enterprise-use-case-engineering`; nie rozpoczęto kontrolowanej migracji
backlogu Phase E i nie wykonano `git push`.

## Zrobione

- Finalna architektura Dagger Wave 0–7 pozostaje ukończona i runtime-proven na
  bazowym commicie `b53a254`. Kanoniczne funkcje, acceptance orchestration,
  pipeline assurance oraz wyniki 146 fast + 397 testcontainers są zapisane w
  dokumentacji Phase D. Tymczasowe artefakty `/tmp/debina-wave6-architecture`
  nie są już dostępne; trwałe implementation records pozostają w repozytorium.
- Commit `08f17be` utwardził istniejący
  `.claude/skills/enterprise-use-case-engineering/SKILL.md`, metodę
  `docs/requirements/USE-CASE-METHOD.md` i krótki root gate w `AGENTS.md`.
  Skill ma obowiązkowy source discovery gate, jawne klasyfikacje,
  behavioral admission oraz pełny handoff source → use case → architecture →
  planning.
- Commit `e3f0538` dodał 10 pozytywnych i 10 negatywnych routing cases,
  10 regression cases, 12 adversarial cases i reprezentatywny czterostopniowy
  E2E chain. `tools/skills/validate-enterprise-use-case-evals.py` przeszedł;
  jest to deterministyczny proof kontraktu. Niejawny model routing pozostaje
  uczciwie `NOT_EXECUTED`, ponieważ repo nie ma bezpiecznego runnera tego typu.
- Commit `c7714b3` rozszerzył planning semantics o gradual
  `semantic_enforcement: ENFORCED`. Validator sprawdza istniejący use case,
  slice, proces, reguły, per-claim evidence, module owner, architecture,
  security, quality scenarios i executable verify. Dziewięć negatywnych
  fixtures failuje z kodami `ESR-002`, `007`, `008`, `010`, `014`–`017` i
  `004`; poprawna fixture przechodzi.
- Commit `2d5e160` zaktualizował skill registry, roadmap, defect register,
  enterprise rebase program oraz trwały record
  `docs/governance/methodology-assurance/ENTERPRISE-USE-CASE-SKILL-HARDENING.md`.
  Tylko ten skill otrzymał nowy claim eval-proven.
- Końcowy enterprise governance runner przeszedł: 79 epików, 304 stories,
  24 aktywne skills, 15 use cases, 38 slices, 15 modułów i ADR lifecycle.
  Zachowano 296 ostrzeżeń legacy traceability oraz 69 planning-semantic
  warnings; nie wykonano masowej kosmetycznej migracji backlogu. Siedem
  validator unit tests, YAML/JSON parse, skill quick validation i
  `git diff --check` przeszły.

## Utknęliśmy na

Nic nie blokuje zakończonego hardeningu. Phase E pozostaje celowo
nierozpoczęta: 304 legacy stories nie mają być automatycznie oznaczone
`ENFORCED`, a istniejące AI-drafted i source-blocked artefakty nadal wymagają
kontrolowanej migracji oraz human review.

## Plan na następny krok

Po osobnej autoryzacji Phase E utworzyć jej programowy record i wybrać pierwszy
mały, reviewowalny kohort stories zgodnie z kolejnością w
`planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md`, następnie zastosować
nowy kontrakt `ENFORCED` i uruchomić pełny enterprise governance runner.

## Pułapki, których nie wolno powtórzyć

- Nie traktować wpisu w `SOURCE-REGISTRY.yaml` ani linku jako per-claim
  evidence; `VERIFY-PER-USE`, konflikt i participant-only gap blokują `READY`.
- Nie tworzyć fikcyjnego aktora, use case’u, slice’a ani agregatu dla tabeli,
  klasy, endpointu, migracji, CI, Daggera lub prywatnego refaktoru.
- Nie kopiować rail-specific zachowania do common core, nie uznawać kodu za
  scheme authority i nie scalać pięciu osi statusu.
- Nie zmieniać frozen ADR bez superseding ADR ani nie oznaczać AI draft jako
  human-reviewed.
- Nie przepisywać hurtowo 304 legacy stories, nie obniżać walidatorów i nie
  wykonywać `git push`.
