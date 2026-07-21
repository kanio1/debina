# HANDOFF

## Zadanie

SEPA Nexus jest syntetyczną platformą płatności ISO 20022 do nauki testów enterprise. Wave 6 audytował pięć kontraktów settlement/routing, aby odblokować wyłącznie źródłowo potwierdzone capabilities.

## Zrobione

- Wave 3--5 pozostają historycznym dowodem; Wave 5 (`4c47158`) dostarczył deferred cycle FSM, G6 locking, netting, item-based `ON_CYCLE_SETTLED`, V51/V52 i settlement-owned `CutoffStateReader`. Nie odtwarzano ani nie liczono ich ponownie.
- Wave 6 utworzył `planning/programs/DEBINA-DECISION-AND-CAPABILITY-UNLOCK-WAVE-6.md` z baseline `63fbf7f`, mapą źródeł/hashy, pełną macierzą pięciu blockerów i audytem rezerwowym.
- `planning/decisions/DEBINA-WAVE-6-CONSOLIDATED-DECISION-PACKET.md` konsoliduje D6-01..D6-05: cutoff/cycle precedence, liquidity precheck, internal book, file batch i deferred queue/next-cycle.
- Skorygowano stale statusy/planning evidence dla EPIC-37, 38, 40, 50, 55 i 74; `story-inventory.json` został wygenerowany ponownie. Governance (5 validatorów), full skills validator i `git diff --check` są PASS. Nie zmieniono kodu, migracji ani runtime config, dlatego pełne regresje backendu nie były wymagane.

## Utknęliśmy na

Nie ma READY story w Wave 6. EPIC-55/55.2 i 55.3, EPIC-38/38.1--38.2 oraz EPIC-40/40.2 wymagają decyzji Class C; 55.4 i 40.3 są capability-blocked. EPIC-50/50.1 nie ma retry/DLQ/delivery-attempt facts. EPIC-74/74.1 ma źródłowo potwierdzony 12-role model, ale brak jest powtarzalnej boot-import/export reprezentacji Organization membership/claim; 74.5 zależy od niej i od osobnej bazy Keycloak.

## Plan na następny krok

Otwórz `planning/decisions/DEBINA-WAVE-6-CONSOLIDATED-DECISION-PACKET.md`, uzyskaj odpowiedź D6-01 (cutoff/cycle primary outcome i consequence), a następnie ponownie oceń EPIC-55/55.2 i wykonaj pierwszy RED test tylko dla zatwierdzonego zachowania.

## Pułapki, których nie wolno powtórzyć

- Nie wybieraj primary routing outcome, account mapping, queue/cycle rollover ani file-batch contract z enumów lub nazw strategii.
- Nie rozszerzaj ADR-N11 poza gross-instant i nie dawaj routingowi dostępu do `ledger.*`.
- Maven nadpisuje `build/generated-spring-modulith/javadoc.json`; przy przyszłym Maven run przywróć plik, jeżeli zmiana nie jest zamierzona. Nie traktuj explicite uruchomionego skilla jako dowodu implicit routing.
