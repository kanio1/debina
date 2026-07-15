---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md §12.1 line 339-348 (EPIC-SIG, SIG-S1..3); sepa-nexus-signature-module-blueprint.md (całość); sepa-nexus-blueprint-ownership-integration.md §9 line 354 (EPIC-OWN-10, ta sama granica modułu, wersja ownership-lens — połączona tu z EPIC-SIG, patrz otwarte pytanie)"
---

# EPIC-31 — Moduł Signature (EPIC-SIG, absorbuje EPIC-OWN-10)

Blocker B2 z decision gate. Weryfikacja na surowych bajtach + werdykt jako evidence, syntetyczny rejestr kluczy, brak realnych roszczeń kryptograficznych produkcyjnych. Weryfikacja i podpisywanie lądują w różnych iteracjach.

`[OPEN-QUESTION]` Moduł signature ma dwie odrębne tożsamości epika w źródłach: `EPIC-SIG` (BPR, granularne SIG-S1..3 z numerami iteracji) i `EPIC-OWN-10` (OWN §9, ta sama granica z perspektywy ownership/ArchUnit). Żaden dokument nie łączy tych ID jawnie. Konsoliduję je w jeden plik (EPIC-31), traktując SIG-S1..3 jako story główne, a szczegóły ArchUnit z OWN-10 (S2-S4) jako dodatkowe taski w Story 31.1/31.4 — to decyzja porządkowa tej konsolidacji, nie zmiana zakresu.

## Story 31.1 — SIG-S1: granica + schemat

status: done
depends_on: []

Opis: `signature.message_signatures(raw_message_id, verdict, key_id, algo, at)`, `signature.keys`/`signature.signature_keys`, `SignaturePort`(verify/sign), werdykt dołączony do `messageEvidence`, badge werdyktu na S-18/S-05. `[MVP]` Iteracja 0 (stub schematu) → pełne w Iteracji 2/5.

Kryterium ukończenia: schemat + porty istnieją, ArchUnit zakazuje dostępu spoza modułu.

`[2026-07-15]` Zbudowano jako prawdziwy, osobny top-level moduł Spring Modulith `com.sepanexus.signature` (siblingiem `modules`/`security`/`shared`, nie zagnieżdżony jak `ingress`/`iso-adapter` w `payment-lifecycle`) — `package-info.java` z `@ApplicationModule(allowedDependencies = {})`. `SignaturePort`(verify/sign) zadeklarowany jako czysty interfejs bez implementacji (weryfikacja/podpisywanie to Story 31.2/31.3 — implementowanie teraz byłoby wynajdywaniem architektury na wyrost). Migracja `V13__signature_schema.sql`: schemat `signature`, dedykowana rola `signature_role` (LOGIN), trzy tabele z pełnym zestawem kolumn z blueprintu §5 (`signature_keys`, `message_signatures` z FK do `ingress.raw_inbound_messages`, `signature_verification_events`). **Zero grantów dla `sepa_app`** na schemacie `signature` (celowo, silniejsze niż `reference_data`/`ingress`/`iso`, które grantują `sepa_app` — blueprint §3 mówi wprost "other modules do not read signature.* directly to learn a verdict"). Implementacja wewnętrzna (`JdbcKeyRegistryStore`) żyje w `com.sepanexus.signature.internal` — Modulith traktuje podpakiety jako nie-publiczne.

Taski:
- [x] **Migracja `signature.message_signatures`, `signature.signature_keys`, `signature.signature_verification_events`.**
      `verify: podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus -c "\dt signature.*"` → trzy tabele — PASS (2026-07-15, przeciw realnej długo działającej bazie po `flyway:migrate`, upgrade z wersji 12→13 bez błędów).
- [x] **`SignaturePort`(verify/sign) + reguła ArchUnit: brak dostępu repository do `signature.*` spoza modułu `signature`** (OWN-10 S2).
      `verify: ./mvnw -f backend test -Dtest=SignatureNoForeignRepoAccessTest` → `Tests run: 1, Failures: 0` — PASS (2026-07-15). ArchUnit: żadna klasa poza `com.sepanexus.signature..` nie zależy od `com.sepanexus.signature.internal..`.
- [x] **Grant-test: role spoza `signature` nie mogą pisać `signature.*`** (OWN-10 S4).
      `verify: ./mvnw -f backend test -Dtest=NonSignatureRoleCannotWriteSignatureTest` → `Tests run: 4, Failures: 0` — PASS (2026-07-15). Testcontainers: `signature_role` zapisuje własne tabele; `sepa_app` nie może ani czytać, ani pisać `signature.*` (42501 na oba); `signature_role` nie może pisać `payment.payments` (42501) — symetryczny test non-domain-mutation z blueprintu §2.

## Story 31.2 — SIG-S2: weryfikacja przed parsowaniem (kanały bankowe/plikowe)

status: done
depends_on: [Story 31.1]

Opis: kolejność filtra przypięta, `FAILED` werdykt → odrzucenie przed parsowaniem, surowa wiadomość zarchiwizowana niezależnie od wyniku. Fixture'y: tamper, zły klucz, brak podpisu. `[MVP]` Iteracja 2 — "to jest iteracja, w której B2 musi być w pełni zamknięty, bo egress status-out zakłada istnienie realnego portu podpisu".

`[PLANNING-DEFECT 2026-07-15, naprawione]`: `depends_on` wcześniej wskazywał `EPIC-19-ingress-staging-pipeline/Story 19.2`, podczas gdy `EPIC-19` Story 19.2 z kolei zależał od całego `EPIC-31` — cykl na poziomie epika. Naprawione na granulacji story: `31.2` (zdolność weryfikacji) zależy tylko od `31.1` (schemat/porty, teraz `done`), nie od `19.2`. `19.2` (integracja z kolejnością filtrów w `ingress`) zależy od `31.1` **i** `31.2` — patrz EPIC-19.md. Kierunek zależności realny: schemat/porty → zdolność weryfikacji → integracja z ingress, nigdy odwrotnie. Zakres nie zmieniony, tylko granulacja zależności.

`[2026-07-15 — zaimplementowane]`: `SignaturePort.verify` rozszerzony minimalnie z Story 31.1 (dodano `SignatureVerificationRequest` — `rawMessageId`, `rawBytes`, `signatureBytes`, `declaredSignerId`, `algo`, `channel`, `signatureRequired`, `asOf`; bez tego portu nie dało się w ogóle zweryfikować podpisu). `[OPEN-QUESTION]` blueprint nie przypina dokładnej nazwy JCA algorytmu ("one synthetic algorithm suffices for MVP", §12) — wybrano **Ed25519** (natywny w JDK, bez zewnętrznego providera) jako jedyny dozwolony algorytm (allowlist, nie dowolna wartość od callera). `Ed25519SignatureVerifier` (`com.sepanexus.signature.internal`): allowlist algorytmu → obecność podpisu vs `signatureRequired` (polityka kanału **dostarczana przez callera**, nigdy wywnioskowana z `messageType.endsWith("xml")`) → `KeyRegistryPort.lookup` → weryfikacja kryptograficzna → werdykt. Werdykty pozostają dokładnie te trzy z blueprintu §5 (`VERIFIED`/`FAILED`/`NOT_APPLICABLE`); szczegół w `reasonCode` (`TAMPERED_OR_INVALID`, `KEY_NOT_FOUND_OR_INACTIVE` — blueprintowy wildcard "KEY_*" pokrywający unknown/expired/future/revoked jednym kodem, dokładnie jak w §6 flow, `MISSING_REQUIRED_SIGNATURE`, `UNSUPPORTED_ALGORITHM`). Idempotencja rozstrzygnięta wprost przez źródło (§5: "append-only verdict log: every verify attempt and its outcome") — każde wywołanie `verify()` zapisuje NOWY wiersz `signature_verification_events`, nigdy deduplikacja/return-existing; `message_signatures` dodatkowo tylko dla `VERIFIED`, w tej samej transakcji. Migracja `V14__signature_evidence_append_only.sql` (nie edytuje V13, już zaaplikowanej): `REVOKE UPDATE, DELETE` na obu tabelach evidence (nie na `signature_keys`, która legalnie potrzebuje `UPDATE` do rotacji kluczy per §8).

Kryterium ukończenia: test kolejności na łańcuchu filtrów `ingress`, nie tylko dokumentacja.

Taski:
- [x] **Zaimplementuj pełną logikę weryfikacji + fixture'y negatywne (tamper/zły klucz/wygasły klucz/brak podpisu).**
      `verify: ./mvnw -f backend test -Dtest=SignatureVerificationTest` → `Tests run: 10, Failures: 0` — PASS (2026-07-15). Rzeczywista kryptografia Ed25519 (JCA `KeyPairGenerator`/`Signature`, żaden mechanizm nie zmockowany): valid → `VERIFIED` + oba wiersze evidence; tampered payload → `FAILED{TAMPERED_OR_INVALID}`, brak wiersza `message_signatures`; tampered signature → to samo; wrong-but-active key → `FAILED{TAMPERED_OR_INVALID}` (klucz istnieje i jest aktywny, ale kryptograficznie nie pasuje); unknown/expired/future key → `FAILED{KEY_NOT_FOUND_OR_INACTIVE}`; unsupported algorithm → `FAILED{UNSUPPORTED_ALGORITHM}` (przed nawet lookupem klucza); missing signature + required → `FAILED{MISSING_REQUIRED_SIGNATURE}`; missing signature + not required → `NOT_APPLICABLE` (kanał JSON-like).
- [x] **Test kolejności: parsowanie XML nie wykonuje się przed weryfikacją na kanałach podpisywanych** (G1, jako test kolejności filtrów, nie tylko opis).
      `verify: ./mvnw -f backend test -Dtest=SignatureBeforeParseOrderingTest` → `Tests run: 4, Failures: 0` — PASS (2026-07-15, współdzielony z EPIC-19 Story 19.2 — ten sam test, patrz EPIC-19.md dla pełnego opisu). Niepróżność potwierdzona mutacją: tymczasowe wyłączenie guardu "FAILED zatrzymuje pipeline" w `SignedChannelIngestionPipeline` powodowało zawodzenie dokładnie dwóch testów negatywnych (`tamperedSignatureStopsBeforeParsingButStillArchivesAndVerifies`, `missingRequiredSignatureStopsBeforeParsingButStillArchives`) — mutacja odwrócona przed commitem (working tree nadal niecommitowany).

## Story 31.3 — SIG-S3: podpisywanie dla egress

status: not-started
depends_on: [Story 31.1, EPIC-43-egress-rail-outbound-dispatch]

Opis: stan `SIGNED` realny, podpis detached, signer-stub przez port, round-trip sign→verify. `[MVP]` Iteracja 5.

Taski:
- [ ] **`SignatureSigningPort` wywoływany z `egress`, guardowany flagą `signing_required`, podpis detached przechowany.**
      `verify: ./mvnw -f backend test -Dtest=*SigningRoundTripTest*`

## Story 31.4 — Rejestr kluczy (KeyRegistryPort)

status: done
depends_on: [Story 31.1]

Opis: rejestracja+lookup po `as_of`. `[MVP]` rejestracja/lookup, `[P1]` UI rotacji.

`[2026-07-15]` `KeyRegistryPort.register`/`lookup(participantId, purpose, asOf)` zaimplementowane w `JdbcKeyRegistryStore` (`com.sepanexus.signature.internal`), przez dedykowane połączenie `signature_role` (nie przez współdzielony pool `sepa_app`) — zgodnie z wymogiem "dedykowana runtime writer role". Lookup: `status='ACTIVE' AND valid_from <= as_of AND (valid_to IS NULL OR as_of < valid_to)`, `purpose = ? OR purpose = 'BOTH'`. Brak logowania `private_material_ref` (nie ma żadnych logów w tej klasie).

Taski:
- [x] **`KeyRegistryPort`: rejestracja i lookup klucza po `as_of`.**
      `verify: ./mvnw -f backend test -Dtest=KeyRegistryLookupTest` → `Tests run: 5, Failures: 0` — PASS (2026-07-15). Pokrycie: aktywny klucz (znaleziony), przyszły klucz (`valid_from` w przyszłości → puste), wygasły klucz (`valid_to` w przeszłości → puste), nieznany klucz (brak wiersza → puste), klucz `BOTH` zaspokaja zarówno `VERIFY` jak i `SIGN` lookup. Pełny regres backendu po tej story: `./mvnw -f backend test` → `73/73 PASS` (było 63/63 po OQ-14).

## Otwarte pytania

- `[OPEN-QUESTION]` Story Iteracji 1 dla signature jest jawnie warunkowa w źródle (§13 blueprintu signature): cienki wiersz werdyktu może wylądować w Iteracji 1 "jeśli chcemy wczesnego demo podpisanego kanału, inaczej czeka" — nierozstrzygnięte w dokumentacji, nie rozstrzygam.
- `[OPEN-QUESTION 2026-07-15]` Blueprint §12 mówi tylko "one synthetic algorithm suffices for MVP", bez podania dokładnej nazwy JCA. Story 31.2 wybrała pragmatycznie **Ed25519** (natywny w JDK 25, bez zewnętrznego providera, jeden dozwolony algorytm na allowliście) — nie rozstrzygam tego jako "the" algorytm źródła, tylko jako uzasadniony wybór inżynierski w braku decyzji dokumentacyjnej.
