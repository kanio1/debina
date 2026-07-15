---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-26-iso-message-lineage-core, EPIC-21-iso-identifier-refactor]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-2, line 1258); sepa-nexus-blueprint-ownership-integration.md §9 (line 346, 'ISO lineage split')"
---

# EPIC-10 — Ownership: ISO lineage split (EPIC-OWN-2)

Wymuszenie, że `iso-adapter` jest jedynym właścicielem tabel lineage/identyfikatorów, i że nie podejmuje decyzji biznesowych.

`[REEWALUACJA 2026-07-15]`: `EPIC-21` (jeden z trzech `depends_on`) jest teraz `done` — `payment.payments.end_to_end_id` usunięty, `iso.payment_iso_identifiers` jest jedynym miejscem przechowywania identyfikatorów ISO. To realnie przybliża **Story 10.2** (korelacja wyłącznie przez `iso.payment_iso_identifiers`, nigdy przez pole na `payment.payments`) — ta struktrualna gwarancja jest teraz bezwarunkowo prawdziwa (nie ma już alternatywnego pola do sprawdzenia). Mimo to Story 10.2 pozostaje `not-started`: jej `verify:` wymaga integracyjnego testu **korelacji pacs.002**, a silnik korelacji (`EPIC-27`, `not-started`) wciąż nie istnieje — nie ma czego testować end-to-end, tylko strukturalną przesłankę. Story 10.1 (wyłączność zapisu roli `iso-adapter` do schematu `iso`) nadal `not-started` — `iso-adapter` nadal nie jest osobnym modułem/rolą DB (`sepa_app` nadal pisze do obu `payment` i `iso`). Story 10.3 niezmieniona (zależy od `EPIC-09` Story 9.4, niezwiązana z EPIC-21). Nie rozpoczynano implementacji EPIC-10 w tej sesji.

`[REEWALUACJA 2026-07-15 — druga sesja, EPIC-11/EPIC-24 timeline]`: przy okazji budowy `EPIC-11` Story 11.1 (`PaymentHistoryRecorder`) i wcześniej `EPIC-19` Story 19.4 (`Pain001CanonicalMapper`) potwierdzony realny, uruchomiony dziś wzorzec: `PaymentService.submitPayment` jest jedną `@Transactional` metodą, która wewnątrz jednej transakcji JDBC pisze do `payment.payments`, `payment.outbox_events`, `payment.payment_status_history`, `payment.payment_events` (schemat `payment`) **oraz** do `iso.iso_messages`/`iso.payment_iso_identifiers`/`iso.message_lineage` (schemat `iso`, przez `JsonDirectLineageRecorder`/`Pain001CanonicalMapper`) — dziś to działa bezproblemowo, bo obie grupy tabel są dziś pisane przez tę samą rolę DB (`sepa_app`) w tym samym połączeniu, w ramach jednego deployowalnego Spring Modulith. `[OPEN-QUESTION] EPIC-10 — koordynacja transakcji jeden-writer-per-schema`: żaden przeczytany dokument źródłowy (`sepa-nexus-blueprint-ownership-integration.md` §3.6/§9, `sepa-nexus-message-flow-and-data-blueprint.md` §8) nie rozstrzyga, co ma się stać z tym dokładnym wzorcem **po** Story 10.1 (moment, gdy `iso-adapter` dostaje własną, odrębną rolę DB piszącą wyłącznie do `iso.*`, a `payment-lifecycle` traci prawo zapisu do `iso.*`): czy (a) `payment-lifecycle` przestaje wywoływać `iso-adapter`'owe zapisy bezpośrednio i przechodzi na outbox/inbox (asynchronicznie, tak jak już robi to dla Kafka) — kosztem utraty dzisiejszej atomowości "jeden HTTP request = payment + lineage zapisane razem"; czy (b) `iso-adapter` pozostaje wywoływany synchronicznie w tej samej transakcji przez jakiś inny mechanizm (np. `SET LOCAL ROLE` wewnątrz jednej transakcji, albo osobny serwis wołany przez port/interfejs, ale wciąż ta sama fizyczna transakcja Postgres) — żaden z dokumentów architektonicznych nie precyzuje tego wyboru dla modułów **współdzielących jeden deployowalny proces** (w odróżnieniu od `routing`, gdzie ADR-N2 explicite mówi "in-process Modulith module, no gRPC call" — czyli nie ma tu tego problemu, ale nie mówi nic analogicznego dla `iso-adapter`/`payment-lifecycle`). Nie rozstrzygnięto tutaj samodzielnie (zasada: nie podejmować decyzji architektonicznych przy konflikcie/luce źródeł) — zapisane też w `planning/README.md` jako otwarte pytanie. Nie rozpoczęto implementacji EPIC-10 w tej sesji.

## Story 10.1 — `iso.payment_iso_identifiers` + `message_lineage` jako własność `iso-adapter`

status: not-started
depends_on: []

Opis: potwierdzenie ownership schematu `iso` po migracji z EPIC-21.

Kryterium ukończenia: tylko rola `iso-adapter` pisze do `iso.*`.

Taski:
- [ ] **Grant-test potwierdzający wyłączność zapisu `iso-adapter` do schematu `iso`.**
      `verify: ./mvnw -f backend test -Dtest=*IsoSchemaOwnershipTest*`

## Story 10.2 — Korelacja pacs.002 przez lineage

status: not-started
depends_on: [Story 10.1]

Opis: zapytania korelacyjne przechodzą wyłącznie przez `iso.payment_iso_identifiers`.

Kryterium ukończenia: brak alternatywnej ścieżki korelacji poza lineage.

Taski:
- [ ] **Test integracyjny: korelacja pacs.002 wyłącznie przez `iso.payment_iso_identifiers`, nie przez pole na `payment.payments`.**
      `verify: ./mvnw -f backend test -Dtest=*IsoCorrelationOwnershipTest*`

## Story 10.3 — `iso-adapter` nie podejmuje decyzji biznesowej (arch test)

status: not-started
depends_on: [EPIC-09-ownership-schema-grants/Story 9.4]

Opis: `iso-adapter` nigdy nie zmienia `payment.status`, nie routuje, nie rozlicza.

Kryterium ukończenia: reguła ArchUnit wymuszona.

Taski:
- [ ] **Reguła ArchUnit: pakiet `iso-adapter` nie wywołuje niczego, co zmienia `payment.status`/routing/settlement.**
      `verify: ./mvnw -f backend test -Dtest=*IsoAdapterNoBusinessDecisionTest*`
