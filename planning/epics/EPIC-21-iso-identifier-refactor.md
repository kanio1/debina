---
status: in-progress
depends_on: [EPIC-19-ingress-staging-pipeline]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CORE-2, line 1249)"
---

# EPIC-21 — Refaktor identyfikatorów ISO (EPIC-CORE-2)

Przeniesienie identyfikatorów z `payments` do `iso.payment_iso_identifiers` (G4 — ISO identity nie może być spłaszczone w stan biznesowy).

## Story 21.1 — Schemat + migracja `iso.payment_iso_identifiers`

status: done
depends_on: []

`[PLANNING-DEFECT 2026-07-14]`: ta tabela **już powstała w EPIC-19** (Story 19.1, migracja `V11__iso_json_direct_lineage.sql`) — dokładnie z kluczem złożonym `(payment_id, source_message_type, iso_message_id)` wymaganym tutaj. Nie tworzono drugiej migracji; ten task jest już spełniony przez istniejący stan bazy, potwierdzone ponownie w tej sesji.

Taski:
- [x] **Migracja tworząca `iso.payment_iso_identifiers` z kluczem złożonym `(payment_id, source_message_type, iso_message_id)`.**
      `verify: podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus -c "\d iso.payment_iso_identifiers"` → `PRIMARY KEY, btree (payment_id, source_message_type, iso_message_id)` — PASS (2026-07-14, przeciw realnej, długo działającej bazie po zastosowaniu `V10`/`V11` przez `flyway:migrate`).

## Story 21.2 — Przekierowanie zapytań korelacyjnych

status: blocked
depends_on: [Story 21.1]

`[PLANNING-DEFECT 2026-07-14, świadomie odłożone]`: task wymaga usunięcia pól identyfikatorów z `payment.payments` — dziś to wyłącznie `end_to_end_id`. Ta kolumna jest dziś aktywnie używana w trzech miejscach jednocześnie: (1) unikalny indeks `payments_tenant_e2e_idx` (bezpieczeństwo współbieżności przy duplikacie — atomowy na poziomie bazy); (2) `PaymentService.submitPayment`'s `existsByTenantIdAndEndToEndId` (ta sama gwarancja, w kodzie); (3) `PaymentSummaryResponse`/lista płatności we frontendzie (`endToEndId` wyświetlane bezpośrednio z encji). Bezpieczne usunięcie wymagałoby: nowego unikalnego indeksu współbieżnościowo-bezpiecznego na `iso.payment_iso_identifiers` (która dziś nie ma nawet kolumny `tenant_id` — potrzebna do zakresowania unikalności per tenant), przepięcia odczytu listy płatności na JOIN, i przepięcia duplicate-check bez utraty atomowości. To realny, wieloczęściowy redesign, nie rozszerzenie o kilka linii — ryzykowne do zrobienia pospiesznie pod koniec budżetu tej sesji bez pełnej weryfikacji regresji read-modelu/współbieżności. **Nie zaimplementowano.** **Status `blocked`** — odblokuj dedykowaną sesją/story projektującą: (a) `tenant_id` na `iso.payment_iso_identifiers` lub równoważne zakresowanie, (b) nowy unikalny indeks tam, (c) przepięcie read-modelu na JOIN, (d) migrację usuwającą `payments.end_to_end_id` dopiero po (a)-(c) zweryfikowanych.

Taski:
- [ ] **Wszystkie zapytania korelacyjne przepięte na nową tabelę, usunięte pola identyfikatorów z `payment.payments`.**
      `verify: ./mvnw -f backend test -Dtest=*IdentifierQueryRepointedTest*` — `NOT RUN`, `blocked` (patrz wyżej).

## Story 21.3 — Test lineage per `source_message_type`

status: done
depends_on: [Story 21.1]

Taski:
- [x] **Test: lineage pacs.002/R-message poprawny per `source_message_type`.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=LineageBySourceMessageTypeTest` → `Tests run: 1, Failures: 0` — PASS (2026-07-14). Nowy `backend/src/test/java/com/sepanexus/modules/paymentlifecycle/isoadapter/LineageBySourceMessageTypeTest.java`: prawdziwe zgłoszenie JSON_DIRECT (przez `PaymentService`), następnie symulowany drugi wiersz `iso.iso_messages`/`iso.payment_iso_identifiers` dla `source_message_type='camt.056'` na TĘ SAMĄ płatność (odzwierciedlający przyszły R-message/recall, którego prawdziwy producent — `EPIC-26`/`iso-adapter` — jeszcze nie istnieje) — dowodzi, że klucz złożony `(payment_id, source_message_type, iso_message_id)` genuinie pozwala dwóm niezależnym wierszom identyfikatorów współistnieć dla jednej płatności bez kolizji, dokładnie zgodnie z modelem "message lineage, not flattened state" z §4.3. Prawdziwe pacs.002/R-message correlation (9-step policy, `iso.iso_message_correlation`) czeka na `EPIC-26`/`EPIC-ISO-2` — ten test dowodzi poprawności modelu danych, nie silnika korelacji.
