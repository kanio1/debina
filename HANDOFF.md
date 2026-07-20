# HANDOFF

## Zadanie

SEPA Nexus to syntetyczna platforma płatności ISO 20022 służąca do nauki testowania enterprise. Wave 3 odzyskał historię po `ac9aa47`, utrzymał aktywne guidance ISO/Kafka i dostarczył źródłowo potwierdzony, ośmiostory’owy slice routingu bez rozszerzania semantyki biznesowej.

## Zrobione

- Historyczne siedem zweryfikowanych backend stories z `ac9aa47` pozostało nienaruszone. Jednorazowo potwierdzono brak nowych źródeł dla EPIC-33 Story 33.3 (`SOURCE-BLOCKED`), EPIC-42 Story 42.1 (`CAPABILITY-BLOCKED`) oraz EPIC-29 Story 29.1/EPIC-44 (`SOURCE-BLOCKED`). Szczegóły i hashe są w `planning/programs/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3.md`.
- `4722014` dostarczył EPIC-51 Stories 51.1–51.2: katalog V45 oraz read-only `RouteCandidateResolver`; `c3f542f` dostarczył EPIC-52 Stories 52.1–52.3: V46 katalogów eligibility i V47 reachability plus routing outbox/inbox.
- Końcowy slice EPIC-53 Stories 53.1–53.3: V48 tworzy `routing.route_decisions`, `route_candidate_results` i `route_decision_explanations` zgodnie z blueprint §4.10. Decyzje i zależne dowody są tenant-RLS scoped przez `app.tenant_id`, a granty `routing_role` są tylko `SELECT, INSERT`, więc nie ma UPDATE/DELETE po zapisie.
- `RouteDecisionPersistenceMigrationTest` i `RouteDecisionPersistenceUpgradePathTest` pokrywają RED (brak relacji), fresh V48, V47→V48, puste/same/cross-tenant GUC, FK-derived child RLS, immutability i role grants. Mutacja polityki candidate `USING` do `true` ujawniła w teście cross-tenant jeden rekord i została natychmiast cofnięta.
- `OutboxDispatcherNoDomainWriteSweepTest` rozpoznaje także source-defined routing outbox z kolumnami `topic,type`; zachowuje dynamiczny sweep wszystkich outboxów i uprawnienia dispatcher-only do `published_at`.
- Targeted routing 51–53 suite: 12/0/0; focused dispatcher+route security sweep: 30/0/0. Dwie czyste pełne regresje backendu: 457/0/0 i 457/0/0 (`/tmp/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3/backend-regression-4-retry.log`, `backend-regression-5.log`). Inventory, capability graph i `bash tools/skills/validate-all-skills.sh` są PASS; Maven-generated `build/generated-spring-modulith/javadoc.json` przywrócono.

## Utknęliśmy na

Nic nie blokuje zakończonego slice’a routingu. Nie wolno samodzielnie rozstrzygać Class C luk: kontraktu `payment.sla.breached`, return/requested-payment intake i outbound artifact/render-profile snapshot. EPIC-51 Story 51.3 nie ma reprezentacji allowed-message-set; EPIC-57 nie ma source DDL profili reconciliation; EPIC-65 nie ma source DDL schematu case; EPIC-73 Story 73.1 nie ma intake metadata/business-date/file-signature policy.

## Plan na następny krok

Otwórz `planning/programs/DEBINA-AUTONOMOUS-CAPABILITY-WAVE-3.md`, wybierz najwyżej wartościowy niezablokowany kandydat po ponownym sprawdzeniu źródła, zależności i `verify:`; nie wracaj do trzech znanych blockerów bez nowego źródła.

## Pułapki, których nie wolno powtórzyć

- Maven nadpisuje `build/generated-spring-modulith/javadoc.json`; przywróć go przez `apply_patch`, chyba że zmiana jest celowa i zrecenzowana.
- Dynamiczny dispatcher sweep odkrywa nowe outboxy automatycznie, ale fixture SQL musi znać ich faktyczny source-defined kształt. Routing używa `topic,type`, podobnie jak egress, a nie `event_type,correlation_id`.
- Nie licz przerwanej ani uruchomionej przed poprawką regresji jako final gate. Finalne dowody tej fali to dokładnie dwa zielone runy po poprawce po 457 testów.
- Nie myl aktywacji skills z implicit routing proof i nie wykorzystuj skills do wymyślania brakującej semantyki SLA, return lub egress profile.
