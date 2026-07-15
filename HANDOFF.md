# HANDOFF

## Zadanie

Dedykowana sesja dla łańcucha zdolności `EPIC-31 Story 31.2` (weryfikacja podpisu) → `EPIC-19 Story 19.2` (signature-before-parse w ingress). Kontynuacja poprzedniej sesji (OQ-12/13/14, EPIC-31 Story 31.1+31.4) — working tree z tamtej sesji był niecommitowany i celowy, nie cofnięty. Local HEAD == `origin/main` (`c03e059`) przez całą sesję, bez divergencji.

## Zrobione

**Rozwiązano cykl zależności Story 31.2 ↔ Story 19.2** (już naprawiony na poziomie planningu w poprzedniej sesji — potwierdzony ponownie, bez zmian): `31.1` (schemat/porty) → `31.2` (zdolność weryfikacji) → `19.2` (integracja z `ingress`), nigdy odwrotnie.

**EPIC-31 Story 31.2 [done] — rzeczywista weryfikacja podpisu Ed25519.** `SignaturePort.verify` rozszerzony minimalnie o `SignatureVerificationRequest` (rawMessageId, rawBytes, signatureBytes, declaredSignerId, algo, channel, signatureRequired, asOf) — Story 31.1 zostawiła to jako czysty stub bez pól potrzebnych do faktycznej weryfikacji. `[OPEN-QUESTION]` blueprint nie przypina dokładnego algorytmu JCA ("one synthetic algorithm suffices for MVP") — wybrano **Ed25519** (natywny JDK, allowlist jednoelementowa, nie dowolna wartość od callera). Nowy `Ed25519SignatureVerifier` (`com.sepanexus.signature.internal`): allowlist algorytmu → obecność podpisu vs `signatureRequired` (polityka kanału dostarczana przez callera, **nigdy** `if (messageType.endsWith("xml"))`) → `KeyRegistryPort.lookup` → weryfikacja kryptograficzna → werdykt. Trzy werdykty z blueprintu (`VERIFIED`/`FAILED`/`NOT_APPLICABLE`, bez rozdęcia taksonomii), reasonCode niesie szczegół (`TAMPERED_OR_INVALID`, `KEY_NOT_FOUND_OR_INACTIVE` — blueprintowy wildcard "KEY_*" pokrywający unknown/expired/future/revoked, `MISSING_REQUIRED_SIGNATURE`, `UNSUPPORTED_ALGORITHM`). Idempotencja rozstrzygnięta wprost przez źródło (§5: "every verify attempt" → zawsze nowy wiersz, bez deduplikacji). Migracja `V14__signature_evidence_append_only.sql` (nie edytuje zaaplikowanej V13): `REVOKE UPDATE, DELETE` na `message_signatures`/`signature_verification_events` (nie na `signature_keys`, która legalnie potrzebuje `UPDATE` do rotacji). `SignatureVerificationTest`: 10 scenariuszy (valid, tampered payload, tampered signature, wrong-but-active key, unknown key, expired key, future key, unsupported algorithm, missing+required, missing+optional) — realna kryptografia JCA, żaden mechanizm nie zmockowany.

**EPIC-19 Story 19.2 [done] — signature-before-parse w ingress.** Nowy `SignedChannelIngestionPipeline` (`com.sepanexus.modules.paymentlifecycle.ingress`): `archive → verify → parse`, `FAILED` zatrzymuje przed `HardenedXmlFactory.parse`, raw bytes archiwizowane niezależnie od werdyktu. Reużywa istniejący `RawMessageArchive` (19.1) i `HardenedXmlFactory` (19.3) — nie tworzy drugiego archiwum, nie przenosi parsera do modułu `signature`. To NIE jest endpoint REST pain.001 (to Story 19.4, nadal blocked na `CanonicalMapper`) — tylko komponent kolejności. Nowa zależność Modulith: `com.sepanexus.modules`' `package-info.java` → `allowedDependencies = {"shared", "signature"}` (zweryfikowane przez `ModularityTest`). `SignatureBeforeParseOrderingTest`: 4 scenariusze z `@MockitoSpyBean` na trzech prawdziwych collaboratorach (`InOrder` + `never()`) — poprawny podpis → `ARCHIVE→VERIFY→PARSE` (parser raz); tampered/missing-required → `ARCHIVE→VERIFY`, parser nigdy; kanał opcjonalny bez podpisu (JSON_DIRECT-jak) → `NOT_APPLICABLE`, parser nadal wywołany.

**Niepróżność potwierdzona mutacją (obie strony, odwrócone przed zakończeniem sesji):** (1) tymczasowe wyłączenie guardu "FAILED zatrzymuje pipeline" w `SignedChannelIngestionPipeline` → dokładnie 2 testy negatywne w `SignatureBeforeParseOrderingTest` zawiodły; (2) tymczasowe usunięcie `"signature"` z `allowedDependencies` modułu `modules` → `ModularityTest` zawiódł z jawną listą 5 naruszeń (`SignedChannelIngestionPipeline → SignaturePort/Verdict/...`). `git diff` czysty po obu odwróceniach.

**Regres backendu: 90/90 PASS** (było 73/73 na wejściu tej sesji). JSON_DIRECT regression (`JsonDirectIngestionTest`/`PaymentControllerTest`/`WalkingSkeletonIntegrationTest`/`InboxConsumerIdempotencyTest`) — 10/10 PASS, niezmienione. Migracja V14 zweryfikowana na świeżej bazie Testcontainers ORAZ na realnej, długo działającej bazie (`flyway:migrate` 13→14, granty potwierdzone `\dp signature.*`: `signature_role=ar` na oba logi evidence, `signature_role=arw` na `signature_keys`).

**OQ-13 doprecyzowane:** sygnatura-bloker Story 19.4 rozwiązany (31.1+31.2 done) — jedyny pozostały bloker to `CanonicalMapper` (wciąż bez właściciela w katalogu, nie rozstrzygnięto samodzielnie).

**Reewaluacja starszych epików (bez zmian, jak przewidziano):** EPIC-10 nadal `blocked` (iso-adapter nadal nie jest osobnym modułem, EPIC-21 Story 21.2 nadal nie zbudowana, EPIC-26 korelacja nadal nie zbudowana — sygnatura tego nie dotyka). EPIC-20 Story 20.3 nadal `blocked` na `EPIC-26` (weryfikacja podpisu nie dostarcza silnika korelacji ani kanału status-inbound). EPIC-28 Story 28.1 pozostaje `not-started` — jego formalna zależność (`Story 19.3`) była już spełniona przed tą sesją; nowy pipeline nie zmienia tego (nie persystuje wyniku hartowania XML do `iso.iso_message_parse_errors` — to wciąż nie istnieje). Świadomie nie rozpoczęte (czwarty klaster, poza zakresem tej sesji).

## Utknęliśmy na

- **EPIC-19 Story 19.4** — `blocked` wyłącznie na `CanonicalMapper` (pain.001 XML→canonical mapping). Nie zbudowane w tej sesji — osobna, poważna zdolność, bez własnego epika w katalogu.
- **EPIC-31 Story 31.3** (podpisywanie dla egress) — `not-started`, nie rozpoczęte (zależy od `EPIC-43`, który ma zero kodu). Zgodnie z instrukcją tej sesji.
- **`act -W backend.yml -j test`** — nadal `INFRASTRUCTURE BLOCKED`, ten sam pre-existing brak docker-in-docker w środowisku `act` (teraz 36 wystąpień "Previous attempts to find a Docker environment failed" na więcej testów Testcontainers niż poprzednio — proporcjonalne do liczby nowych testów, nie nowa regresja). Natywny `./mvnw -f backend test` → 90/90 PASS.
- **`act -W frontend.yml -j build`** — PASS (bez zmian frontendowych).
- **EPIC-21 Story 21.2**, **EPIC-24 Story 24.7** — świadomie nie rozpoczęte, zgodnie z instrukcją tej sesji (zatrzymać się po dwóch klastrach: 31.2 i 19.2).

## Plan na następny krok

1. Dedykowana sesja na `CanonicalMapper` (pain.001 XML→canonical) — jedyny pozostały bloker dla `EPIC-19` Story 19.4. Wymaga decyzji planningowej, który epik/story dostaje ten zakres (nie rozstrzygnięte — brak własnego epika w katalogu, patrz OQ-13).
2. Alternatywnie: dedykowana sesja na `EPIC-21` Story 21.2 (redesign identyfikatorów ISO) — niezależny klaster, opisany w poprzednim `HANDOFF.md` (patrz `git log -p HANDOFF.md`).
3. `EPIC-28` Story 28.1 (`iso.iso_message_parse_errors`) jest formalnie odblokowana (zależy tylko od już-`done` Story 19.3) — dobry, tani kandydat na kolejną sesję, jeśli `CanonicalMapper`/`EPIC-21` okażą się za duże na dostępny budżet.
4. Naprawić `act`+Testcontainers (docker-in-docker) w tym środowisku, jeśli `act` CI ma być realnym gate'em.

## Pułapki, których nie wolno powtórzyć

- **Blueprint nie zawsze przypina dokładny algorytm/parametr — gdy tak, wybierz pragmatycznie i zapisz `[OPEN-QUESTION]`, nie blokuj się czekaniem na doprecyzowanie źródła.** Ed25519 wybrany dla Story 31.2 z tego właśnie powodu.
- **Mutacja jako dowód niepróżności testu działa najlepiej dosłownie**: podmień jeden warunek na stałą (`if (false)` zamiast realnego guardu), uruchom test, potwierdź failure z konkretnym asercyjnym komunikatem, dopiero potem odwróć. Rób to PRZED uznaniem story za `done`, nie po.
- **`@MockitoSpyBean` na interfejsie portu (np. `SignaturePort`) nadal wykonuje prawdziwą logikę pod spy** — dokładnie to, czego potrzeba do testu kolejności bez mockowania kryptografii. Spy + `InOrder`/`never()` na rzeczywistych collaboratorach jest lepsze niż mock, gdy trzeba udowodnić zarówno kolejność, jak i poprawność wyniku.
- **Rozszerzanie interfejsu portu z poprzedniej story jest tańsze niż tworzenie drugiego, konkurencyjnego portu** — `SignaturePort.verify` zmienił sygnaturę (dodał `SignatureVerificationRequest`), bo Story 31.1 zostawiła go jako pusty stub bez pól potrzebnych do weryfikacji. Bezpieczne, bo nic jeszcze go nie wołało.
- Pułapki z poprzednich sesji (Podman nie Docker, `DOCKER_HOST`, `pnpm`/Node 24.18.0, RLS GUC empty-zero-rows, FK między schematami łamie cudze `TRUNCATE` bez `CASCADE`, nowy `@Bean DataSource` wyłącza autokonfigurację domyślnego datasource, `act`+Testcontainers = broken w tym środowisku, statyczny CSP bez nonce blokuje RSC bootstrap, plaintext hasło z realm-exportu blokowane przez harness) wciąż obowiązują — pełna historia w `git log -p HANDOFF.md`.
