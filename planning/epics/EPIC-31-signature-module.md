---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md §12.1 line 339-348 (EPIC-SIG, SIG-S1..3); sepa-nexus-signature-module-blueprint.md (całość); sepa-nexus-blueprint-ownership-integration.md §9 line 354 (EPIC-OWN-10, ta sama granica modułu, wersja ownership-lens — połączona tu z EPIC-SIG, patrz otwarte pytanie)"
---

# EPIC-31 — Moduł Signature (EPIC-SIG, absorbuje EPIC-OWN-10)

Blocker B2 z decision gate. Weryfikacja na surowych bajtach + werdykt jako evidence, syntetyczny rejestr kluczy, brak realnych roszczeń kryptograficznych produkcyjnych. Weryfikacja i podpisywanie lądują w różnych iteracjach.

`[OPEN-QUESTION]` Moduł signature ma dwie odrębne tożsamości epika w źródłach: `EPIC-SIG` (BPR, granularne SIG-S1..3 z numerami iteracji) i `EPIC-OWN-10` (OWN §9, ta sama granica z perspektywy ownership/ArchUnit). Żaden dokument nie łączy tych ID jawnie. Konsoliduję je w jeden plik (EPIC-31), traktując SIG-S1..3 jako story główne, a szczegóły ArchUnit z OWN-10 (S2-S4) jako dodatkowe taski w Story 31.1/31.4 — to decyzja porządkowa tej konsolidacji, nie zmiana zakresu.

## Story 31.1 — SIG-S1: granica + schemat

status: not-started
depends_on: []

Opis: `signature.message_signatures(raw_message_id, verdict, key_id, algo, at)`, `signature.keys`/`signature.signature_keys`, `SignaturePort`(verify/sign), werdykt dołączony do `messageEvidence`, badge werdyktu na S-18/S-05. `[MVP]` Iteracja 0 (stub schematu) → pełne w Iteracji 2/5.

Kryterium ukończenia: schemat + porty istnieją, ArchUnit zakazuje dostępu spoza modułu.

Taski:
- [ ] **Migracja `signature.message_signatures`, `signature.signature_keys`, `signature.signature_verification_events`** (Iteracja 0: sam stub schematu + rola `signature_role`, bez logiki weryfikacji).
      `verify: psql -c "\dt signature.*"` → trzy tabele istnieją.
- [ ] **`SignaturePort`(verify/sign) + reguła ArchUnit: brak dostępu repository do `signature.*` spoza modułu `signature`** (OWN-10 S2).
      `verify: ./mvnw -f backend test -Dtest=*SignatureNoForeignRepoAccessTest*`
- [ ] **Grant-test: role spoza `signature` nie mogą pisać `signature.*`** (OWN-10 S4).
      `verify: ./mvnw -f backend test -Dtest=*NonSignatureRoleCannotWriteSignatureTest*`

## Story 31.2 — SIG-S2: weryfikacja przed parsowaniem (kanały bankowe/plikowe)

status: not-started
depends_on: [Story 31.1, EPIC-19-ingress-staging-pipeline/Story 19.2]

Opis: kolejność filtra przypięta, `FAILED` werdykt → odrzucenie przed parsowaniem, surowa wiadomość zarchiwizowana niezależnie od wyniku. Fixture'y: tamper, zły klucz, brak podpisu. `[MVP]` Iteracja 2 — "to jest iteracja, w której B2 musi być w pełni zamknięty, bo egress status-out zakłada istnienie realnego portu podpisu".

Kryterium ukończenia: test kolejności na łańcuchu filtrów `ingress`, nie tylko dokumentacja.

Taski:
- [ ] **Zaimplementuj pełną logikę weryfikacji + fixture'y negatywne (tamper/zły klucz/wygasły klucz/brak podpisu).**
      `verify: ./mvnw -f backend test -Dtest=*SignatureVerificationTest*`
- [ ] **Test kolejności: parsowanie XML nie wykonuje się przed weryfikacją na kanałach podpisywanych** (G1, jako test kolejności filtrów, nie tylko opis).
      `verify: ./mvnw -f backend test -Dtest=*SignatureBeforeParseOrderingTest*` (współdzielony z EPIC-19 Story 19.2 — ten sam test).

## Story 31.3 — SIG-S3: podpisywanie dla egress

status: not-started
depends_on: [Story 31.1, EPIC-43-egress-rail-outbound-dispatch]

Opis: stan `SIGNED` realny, podpis detached, signer-stub przez port, round-trip sign→verify. `[MVP]` Iteracja 5.

Taski:
- [ ] **`SignatureSigningPort` wywoływany z `egress`, guardowany flagą `signing_required`, podpis detached przechowany.**
      `verify: ./mvnw -f backend test -Dtest=*SigningRoundTripTest*`

## Story 31.4 — Rejestr kluczy (KeyRegistryPort)

status: not-started
depends_on: [Story 31.1]

Opis: rejestracja+lookup po `as_of`. `[MVP]` rejestracja/lookup, `[P1]` UI rotacji.

Taski:
- [ ] **`KeyRegistryPort`: rejestracja i lookup klucza po `as_of`.**
      `verify: ./mvnw -f backend test -Dtest=*KeyRegistryLookupTest*`

## Otwarte pytania

- `[OPEN-QUESTION]` Story Iteracji 1 dla signature jest jawnie warunkowa w źródle (§13 blueprintu signature): cienki wiersz werdyktu może wylądować w Iteracji 1 "jeśli chcemy wczesnego demo podpisanego kanału, inaczej czeka" — nierozstrzygnięte w dokumentacji, nie rozstrzygam.
