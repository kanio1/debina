---
status: not-started
depends_on: [EPIC-02-keycloak-realm-iteration-0]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md §12.1 line 350-351 (EPIC-SEC-KC); sepa-nexus-keycloak-26-security-architecture-blueprint.md (całość); sepa-nexus-keycloak-security-blueprint.md (całość)"
---

# EPIC-74 — Adopcja Keycloak poza zakres Iteracji 0 (EPIC-SEC-KC)

Rozszerza EPIC-02 (4 role) do pełnego modelu: 12 ról (`sepa-nexus-keycloak-26-security-architecture-blueprint.md` §10 — rewizja z 11 do 12, dodaje `payment_approver`, maker≠checker), FGAP v2, FAPI-2, step-up auth, admin command authorization matrix. Blocker B5 (część bezpieczeństwa) i R-14. Brak w źródle drobniejszych story-ID (`SEC-KC-S1` itd.) — jedna linia opisu na "story" w BPR §12.1; poniższy podział na 4 story wynika z naturalnych granic tematycznych już obecnych w treści blueprintów, nie z nowych ID.

## Story 74.1 — Realm design + pełny seed (Organizacje, 12 ról)

status: not-started
depends_on: []

Opis: `[MVP]` Iteracja 1. Organization per uczestnik, claim `organization`/`branch_id`→GUC, dwupoziomowy test RLS z tokenem branch.

Taski:
- [ ] **Rozszerz realm z EPIC-02 do pełnego modelu 12 ról + Keycloak Organizations, claim mappery `organization_id`/`branch_id`.**
      `verify: ./mvnw -f backend test -Dtest=*FullRoleModelRealmTest*`
- [ ] **Test: `payment_submitter` i `payment_approver` nigdy ten sam użytkownik dla tego samego tenant/branch w seedowanym realmie MVP** (higiena danych seed, nie runtime rule).
      `verify: ./mvnw -f backend test -Dtest=*MakerCheckerSeedHygieneTest*`

## Story 74.2 — FGAP v2 dla planu admin (`[P1]`)

status: not-started
depends_on: [Story 74.1]

Opis: `[MVP]` Iteracja 5, powiązane z inwentarzem komend admin (§13.3 starszego dokumentu / §11 nowego).

Taski:
- [ ] **Skonfiguruj FGAP v2 permission model bound do inwentarza komend admin.**
      `verify: ./mvnw -f backend test -Dtest=*FgapV2AdminPermissionTest*`

## Story 74.3 — FAPI-2 dla `sepa-integration` + passkeys (`[P1]`)

status: not-started
depends_on: [Story 74.1]

Taski:
- [ ] **Profil FAPI-2/DPoP na kliencie `sepa-integration` + passkeys/WebAuthn dla `security_admin`.**
      `verify: ./mvnw -f backend test -Dtest=*Fapi2DpopClientTest*`

## Story 74.4 — Step-up authentication dla czterech komend wysokiego ryzyka

status: not-started
depends_on: [Story 74.1]

Opis: `[MVP, minimal]` — approve/reject powyżej progu, VoP-override, zwolnienie fraud-hold, config limitów. `@RequireStepUp(withinMinutes = 5)`.

Taski:
- [ ] **Zaimplementuj `@RequireStepUp` z oknem świeżości `auth_time` 5 min, przekierowaniem `acr_values=step-up`.**
      `verify: ./mvnw -f backend test -Dtest=*StepUpAuthenticationTest*`

## Story 74.5 — Osobna baza Keycloak w skrypcie backupu

status: not-started
depends_on: [Story 74.1]

Opis: `[MVP]` Iteracja 0 wg starszego blueprintu §16 — mogło zostać pominięte w konkretnym `iteration-0-foundation-plan.md`, patrz otwarte pytanie.

Taski:
- [ ] **Osobna baza danych Keycloak + eksport realmu w skrypcie backupu.**
      `verify: test -f infra/scripts/backup-keycloak.sh`

## Otwarte pytania

- `[OPEN-QUESTION]` Starszy blueprint Keycloak samodzielnie deklaruje się jako "source of truth dla modelu jedenastu ról", ale nowszy blueprint (26.x) rewiduje to do dwunastu ról (`payment_approver`) — starszy dokument nie został zaktualizowany o tę rewizję. Traktuję nowszy dokument jako obowiązujący (zgodnie z jego własnym statusem `[SUPERSEDED, partial]` — tylko specyfika wersji/protokołu jest superseded, ale akurat liczba ról jest tu realną rozbieżnością między dwoma dokumentami, nie tylko "specyfiką protokołu"). Nie modyfikuję żadnego dokumentu źródłowego — tylko odnotowuję rozbieżność.
- `[OPEN-QUESTION]` `sepa-ops-cli` (trzeci potencjalny klient Keycloak) oznaczony `[DEFER P1]` — sama jego przyszła obecność jest warunkowa, nie tylko zakres.
