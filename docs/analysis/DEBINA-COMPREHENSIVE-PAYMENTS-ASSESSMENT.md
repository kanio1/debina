# Kompleksowy audyt biznesowo-techniczny systemu płatniczego „Debina”

## 1. Strona tytułowa

**Produkt audytowany:** Debina, odtworzona z repozytorium SEPA Nexus / `Playwright-Learning-Development`  
**Rodzaj audytu:** evidence-first, biznesowo-domenowy (około 70%) i techniczny (około 30%)  
**Data odcięcia dowodów:** 17 lipca 2026, Europe/Warsaw  
**Zakres repo:** cały dostępny working tree, 76 EPIC-ów, 279 stories, dokumentacja, kod, migracje, testy i infrastruktura  
**Autor:** jeden skoordynowany zespół BA/SEPA/ISO 20022/product/architecture/security/data/Java/Kafka/QA  
**Klasyfikacja:** wewnętrzna analiza projektowa; nie jest opinią prawną, certyfikacją EPC/CSM ani deklaracją production readiness

Dokument główny ma kompletne podsumowanie; szczegółowe katalogi znajdują się w:

- [Aneks A — artefakty i 76 EPIC-ów](annexes/DEBINA-ARTIFACT-EPIC-CATALOG.md),
- [Aneks B — 30 flows, ISO 20022 i traceability](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md),
- [Aneks C — 45 luk, ryzyka i backlog](annexes/DEBINA-GAP-RISK-BACKLOG.md).

## 2. Executive summary

Debina ma **spójną i wartościową wizję syntetycznego laboratorium Credit Transfer/ISO 20022 dla Senior QA/SDET**, ale **nie jest obecnie pełnym payment hubem ani payment concentratorem zdolnym wykonywać realne płatności SEPA**. To rozróżnienie nie jest semantyczne: `AGENTS.md` jawnie wyklucza realny bank/CSM i compliance claims, a dokument `sepa-nexus-full-blueprint-review-and-task-plan.md` świadomie deferuje Direct Debit i mandate lifecycle.

Najmocniejsza część produktu to zamrożony model odpowiedzialności: jeden Payment Lifecycle spine, settlement profile po `(settlement_basis, liquidity_mode)`, one-writer-per-schema, append-only ledger za `LedgerPort`, pięć niezależnych osi statusu, egress bez prawa do ustawiania finality, reconciliation bez prawa do naprawy i case bez prawa do wykonywania księgowań. Warto zachować te reguły.

Implementacja osiągnęła walking skeleton: JSON i pojedynczy pain.001.001.09 intake, raw archive, idempotency, payment record, outbox/inbox, cienki FSM, ISO identifiers/lineage, częściowa korelacja pacs.002.001.10, signature evidence, podstawowe UI/BFF oraz DDL egress i ledger. Nie istnieje jednak wykonywalny łańcuch route→settle→ledger→render/sign→CSM→receipt→finality→reconciliation. Incoming pacs.008, rzeczywiste adaptery CSM, pełne R-transactions, claims, operations i SDD są nieobecne lub tylko zaprojektowane.

Audyt wykrył **45 luk**, w tym **6 BLOCKER i 13 CRITICAL**. W stanie audytowym najpoważniejsze wykonawcze defekty obejmowały: ledger dopuszczający bilansowanie EUR przeciw USD i nieistniejące konta; `DISPATCHED` błędnie oznaczany jako final; event `payment.submitted.v1` publikowany na `payment.validated` i konsumowany jako własny trigger walidacji; oraz role dispatcherów poprawnie utworzone w SQL, lecz niewykorzystane przez Spring. Późniejsze, odrębne programy naprawcze usunęły fałszywe `DISPATCHED⇒final` (V30) oraz część wskazanych luk ledger/outbox; nie zamyka to pełnej finality authority ani LedgerPort. Wynik **356 testów PASS** dotyczył momentu pierwotnego audytu i wykazywał wtedy lukę w oracle/wiring tests.

Ocena końcowa: **4 — częściowa implementacja** jako learning lab, **2 — częściowy projekt** jako pełny SEPA hub. Debina nie jest gotowa do integracyjnego pilotażu z bankiem/CSM, certyfikacji ani produkcji.

### Tabela A — Coverage produktów

| Obszar | Zaprojektowany | Zaimplementowany | Przetestowany | Zgodny ze standardem | Główne luki |
|---|---|---|---|---|---|
| SCT | częściowo, outbound-first | intake only | dobre slice tests | nieudowodnione | pacs.008, routing, settlement, egress, incoming, R/claims |
| SCT Inst | generic GrossInstant | resolver only | DDL/strategy fragment | nie | 10 s/24×7/VoP boundary, CSM, incoming |
| SDD Core | jawnie deferred | nie | nie | nie dotyczy current scope | mandate, collection, refund/claims/R |
| SDD B2B | jawnie deferred | nie | nie | nie dotyczy current scope | debtor mandate check, finality, R |
| R-transactions | dobry model return/recall/reject | pacs.002 extraction fragment | correlation unit/DB | nie | end-to-end processing/timers/reasons |
| ISO 20022 | szeroki katalog projektowy | pain.001 + pacs.002 fragment | parser/lineage tests | nie | XSD/TVS/EPC/CSM profiles, pacs/camt render |
| Data integrity | silny frozen design | 7 schemas/28 migrations | wiele Testcontainers | częściowo | ledger currency/FK/reversal, history races |
| Security | target model 12 ról | local realm 4 role/BFF | część auth tests | częściowo | audience, SoD, M2M, RLS coverage |
| Operations/recon | szczegółowo zaprojektowane | thin read UI only | nie | nie | stuck/DLQ/manual/recon/reporting |

## 3. Najważniejszy werdykt zespołu

**Jednolity werdykt:** nie należy „uzupełniać” Debiny od razu o wszystkie funkcje bankowe. Najpierw trzeba formalnie zdecydować, czy projekt pozostaje syntetycznym CT labem, czy zmienia cel na pełny hub. Niezależnie od tej decyzji trzeba natychmiast naprawić ledger, finality, kontrakt Kafka i runtime role wiring, ponieważ podważają wiarygodność nawet laboratorium. Dopiero potem należy zbudować jeden kompletny outgoing SCT vertical slice. SDD, realny CSM i certification backlog są legalne dopiero po source-backed scope decision; w przeciwnym razie naruszałyby zakaz wymyślania architektury.

## 4. Zakres i ograniczenia analizy

Audyt objął dokumentację biznesową i techniczną, ADR-y, 76 plików EPIC, 279 stories, capability graph, backend, frontend, konfigurację Keycloak, AsyncAPI, Flyway, PostgreSQL, Kafka, testy, CI i infrastrukturę. Nie znaleziono wykonawczych OpenAPI, GraphQL schema, protobuf, EPC XSD/TVS ani Playwright tests.

Ograniczenia:

- working tree zawierał wcześniejsze, niezatwierdzone zmiany; zostały zachowane i ocenione jako bieżący stan, nie release;
- szczegółowe RT1/STEP2/STET participant/certification documents nie są publiczne; ich zgodność to `BRAK DANYCH`;
- ocena regulacyjna opisuje zastosowanie systemowe, nie zastępuje porady prawnej;
- 261/279 stories ma w capability graph `NOT_DEEP_DIVED`; raport uczciwie nie udaje pełnej ręcznej matrycy każdego acceptance criterion;
- publiczne źródła zostały zweryfikowane na 2026-07-17; przyszłe zmiany są oddzielone od obowiązującego baseline.

## 5. Zastosowana metoda badawcza

Zastosowano hybrydę **capability analysis + evidence-led architecture archaeology + standards-to-requirements traceability + end-to-end journey/value-stream analysis + lifecycle/state-machine modelling + ISO message choreography + domain-event storming + negative-path/FMEA + risk-based testability review**.

Kolejność była celowa:

1. Inwentaryzacja artefaktów i wersji oraz ustalenie normatywnego precedence: ADR/[FREEZE]→blueprint→EPIC/story→code/test.
2. Rekonstrukcja celu, aktorów, capabilities i granic odpowiedzialności.
3. Value-stream mapping od kanału do biznesowego końca, osobno inbound/outbound oraz happy/negative/manual.
4. Analiza lifecycle na pięciu osiach i identyfikatorów ISO/lineage.
5. Mapowanie aktualnych EPC/regulation/CSM requirements do capabilities, epików, implementacji i testów.
6. FMEA dla duplicate, timeout, partial commit, wrong order, wrong correlation, operator race i recovery.
7. Gap severity według wpływu na pieniądze, finality, settlement, regulacje, operacje i certyfikację.

Kompletność nie była oceniana liczbą endpointów. Flow jest „complete” dopiero, gdy ma trigger, walidację, decyzję, state transition, side effects, failure/timeout/duplicate behavior, finality, reconciliation, operator evidence i test. Ukryte luki między EPIC-ami wykrywano przez śledzenie required capability/event/message/table/role/test do następnego właściciela. Jeśli łańcuch kończył się na event name albo DDL sketch, flow pozostawał niekompletny.

## 6. Źródła i wersje dokumentów

### Wewnętrzne

Normatywne źródła repo i ich relacje opisuje [Aneks A §A.2](annexes/DEBINA-ARTIFACT-EPIC-CATALOG.md). Frozen ADR-N1–N8 są binding; raport nie proponuje ich cichej zmiany.

### Zewnętrzne — registry

| ID | Dokument / organizacja | Wersja, publikacja, obowiązywanie | Status / zastosowanie |
|---|---|---|---|
| EXT-EPC-SCT | [2025 SCT Rulebook](https://www.europeanpaymentscouncil.eu/what-we-do/epc-payment-schemes/sepa-credit-transfer/sepa-credit-transfer-rulebook-and), EPC | v1.1; effective 2025-10-05; do 2027-11-21 | CURRENT; SCT baseline |
| EXT-EPC-SI | [2025 SCT Inst Rulebook](https://www.europeanpaymentscouncil.eu/document-library/rulebooks/2025-sepa-instant-credit-transfer-rulebook-version-11), EPC | v1.1; effective 2025-10-05 | CURRENT; SCT Inst baseline |
| EXT-EPC-CORE | [2025 SDD Core Rulebook](https://www.europeanpaymentscouncil.eu/document-library/rulebooks/2025-sepa-direct-debit-core-rulebook-version-11), EPC | v1.1; effective 2025-10-05 | CURRENT; SDD Core baseline |
| EXT-EPC-B2B | [2025 SDD B2B Rulebook](https://www.europeanpaymentscouncil.eu/document-library/rulebooks/2025-sepa-direct-debit-business-business-rulebook-version-11), EPC | v1.1; effective 2025-10-05 | CURRENT; SDD B2B baseline |
| EXT-IG-SCT | [SCT Inter-PSP IG EPC115-06](https://www.europeanpaymentscouncil.eu/sites/default/files/kb/file/2025-10/EPC115-06%20SCT%20Inter-PSP%20IG%202025%20V1.0.pdf), EPC | 2025 v1.0; issued 2024-11-28; effective 2025-10-05 | APPROVED/PUBLIC; ISO 2019 versions |
| EXT-IG-SI | [SCT Inst Inter-PSP IG EPC122-16](https://www.europeanpaymentscouncil.eu/sites/default/files/kb/file/2025-10/EPC122-16%20SCT%20Inst%20Inter-PSP%20IG%202025%20V1.0.pdf), EPC | 2025 v1.0; effective 2025-10-05 | CURRENT |
| EXT-IG-SDD | [SDD Core Inter-PSP IG EPC114-06](https://www.europeanpaymentscouncil.eu/sites/default/files/kb/file/2025-10/EPC114-06%20SDD%20Core%20Inter-PSP%20IG%202025%20V1.0.pdf), EPC | 2025 v1.0 | CURRENT; także non-XML claim nuance |
| EXT-IPR | [Regulation (EU) 2024/886](https://eur-lex.europa.eu/eli/reg/2024/886/oj/eng) | adopted 2024-03-13; applies staged from 2024 | OBOWIĄZUJE; instant/VoP/sanctions amendments |
| EXT-PSD2 | [Directive (EU) 2015/2366](https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=CELEX%3A32015L2366) | PSD2 | OBOWIĄZUJE |
| EXT-SCA | [Delegated Regulation 2018/389](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32018R0389) | consolidated baseline 2023-09-12 | OBOWIĄZUJE; SCA/CSC |
| EXT-PSD3 | [EP legislative tracker](https://www.europarl.europa.eu/legislative-train/carriage/revision-of-eu-rules-on-payment-services/report?sid=10301) + [Commission May 2026 minutes](https://finance.ec.europa.eu/document/download/a1f65f26-0f3b-4e91-9bea-5490f169f817_hr?filename=260519-fin-net-plenary-minutes_en.pdf) | political compromise 2025/2026; formal adoption pending | FUTURE/NOT LAW as of cutoff |
| EXT-TIPS | [TIPS professional documents](https://www.ecb.europa.eu/paym/target/target-professional-use-documents-links/tips/html/index.en.html), ECB | R2026.JUN current; R2026.NOV future | CURRENT/FUTURE separated |
| EXT-RT1 | [RT1](https://www.ebaclearing.eu/services-instant-payments/rt1/), EBA CLEARING | public current overview | public facts only; service docs restricted |
| EXT-STEP2 | [STEP2 SCT](https://www.ebaclearing.eu/services-sepa-payments/step2-sct/key-features/), EBA CLEARING | public current overview | public facts only; certification detail restricted |
| EXT-STET | [General Description SEPA(EU) 2025](https://www.stet.eu/assets/files/site/documentation/General%20description%20SEPA%28EU%29_Internet_VF2025.pdf), STET | 2025 | current public baseline; integration details incomplete |
| EXT-PG18 | [PostgreSQL 18 release](https://www.postgresql.org/about/news/postgresql-18-released-3142/) / [notes](https://www.postgresql.org/docs/18/release-18.html) | GA 2025-09-25; tested repo runtime 18.4 | CURRENT |
| EXT-PG19 | [PostgreSQL 19 Beta 2](https://www.postgresql.org/about/news/postgresql-19-beta-2-released-3350/) / [draft notes](https://www.postgresql.org/docs/19/release-19.html) | beta 2026-07-16 | PRE-RELEASE; non-production |
| EXT-KC | [Keycloak 26.6.4](https://www.keycloak.org/2026/06/keycloak-2664-released) | 2026-06-26 | repo exact/current 26.6 patch; 26.7 minor exists |
| EXT-JDK | [JDK 25](https://openjdk.org/projects/jdk/25/) | GA 2025-09-16 | CURRENT/LTS line |
| EXT-SPRING | [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Modulith reference](https://docs.spring.io/spring-modulith/reference/) | repo Boot 4.1.0, Framework 7.0.8, Modulith 2.1.0 | compatible with Java 25 |
| EXT-KAFKA | [Kafka producer config](https://kafka.apache.org/42/configuration/producer-configs/) | client 4.2.1 resolved | producer idempotence ≠ DB+Kafka atomicity |

EPC 2026 change requests i VoP v1.1 effective 2026-09-20 są przyszłe; VoP v2.0 jest konsultacją. PostgreSQL 19 jest beta. PSD3/PSR nie są jeszcze obowiązującym prawem. Te trzy grupy nie są używane jako obecny compliance baseline.

## 7. Inwentaryzacja artefaktów Debiny

Mechanicznie potwierdzono: 76 EPIC-ów, 279 stories, 110 production Java files, 67 backend test classes, 28 Flyway migrations, 27 tabel, 7 aktywnych schematów domenowych, 47 frontend source files i 28 kanałów w AsyncAPI. `story-inventory` i capability graph validators przechodzą; graf ma 144 nodes, 256 edges, zero cycles, ale nie pełne story coverage.

Stan EPIC: 15 done, 14 in-progress, 4 blocked, 43 not-started. Stan stories: 85 done, 30 blocked, 164 not-started. Pełny katalog i wiarygodność artefaktów: [Aneks A](annexes/DEBINA-ARTIFACT-EPIC-CATALOG.md).

Kluczowe rozbieżności: EPIC-14/44/45 index vs frontmatter; stale status w capability graph; 16/16 historycznych flows nie oznacza 16 implementations; target IAM vs realm; AsyncAPI vs runtime event semantics; frozen finality vs actual FSM.

## 8. Zrekonstruowany cel i granice produktu

Debina jest połączeniem **payment lifecycle platform + routing/settlement/egress laboratory + bank-facing BFF/API + ISO evidence platform**, z ambicją syntetycznego concentratora. Nie jest dziś clearingiem, CSM-em, realnym core banking ani pełnym payment hubem.

Odpowiedzialność direct: intake, raw evidence, canonicalisation, lifecycle truth, route/profile decision, settlement orchestration, ledger evidence, egress transport state, read-only reconciliation, case decision/coordination, audit/test observability. Delegowane lub nierozstrzygnięte: customer SCA/authorisation and VoP placement, account/core posting, AML/fraud decisions, sanctions workflow, treasury, actual CSM settlement/certification and external customer complaint liability.

Model jest multi-tenant/multi-participant w intencji (tenant/branch/RLS), ale nie dowodzi bezpiecznej izolacji wszystkich tabel. Real-time: JSON/XML command i plan GrossInstant. Async: Kafka/outbox, CSM responses. Batch: EPIC-73/deferred rail. Manual: case/recon/operator queues — tylko projekt.

## 9. Mapa aktorów i systemów

Główni aktorzy: customer/channel, payment submitter, payment approver, viewer, auditor, reference-data admin, settlement/egress/recon/case/security operators, internal initiating system, Debina services, Keycloak, PostgreSQL, Kafka, core/ledger/treasury/fraud/AML systems oraz CSM/other PSP. Tylko cztery lokalne role istnieją w realm. Context diagram znajduje się w [Aneksie B §B.5](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

## 10. Capability map

Capabilities projektowe są logiczne: ingress, ISO, payment lifecycle, signature, routing, settlement, ledger, egress, reconciliation, case, reference data, risk, simulation, reporting, IAM, evidence/audit. Implementacja koncentruje się na foundation, ingress, ISO/identity i thin lifecycle. Routing ma 0/24 stories done, recon 0/27, case 0/26; settlement/ledger 4/36, egress 3/28. Diagram: [Aneks B §B.6](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

## 11. Stan EPIC-ów i stories

### Tabela D — EPIC assessment (grupy; pełne 76 w Aneksie A)

| EPIC | Cel | Pokrycie procesu | Spójność biznesowa | Spójność techniczna | Brakujące stories | Werdykt |
|---|---|---|---|---|---|---|
| 00–09 | foundation/walking skeleton | foundation complete | dobra | częściowo; CI/runtime gaps | hermetic runtime proof | POTWIERDZONE/CZĘŚCIOWE |
| 10–18 | ownership boundaries | partial | zgodne z frozen | grants/wiring/topic conflicts | decision packets/per-schema rollout | CZĘŚCIOWE/blocked |
| 19–22 | ingress/FSM/IDs/time | intake only | FSM finality sprzeczny | working thin slice | full lifecycle/negative/concurrency | CZĘŚCIOWE/SPRZECZNE |
| 23–25 | frontend/observability | payments read thin | operations incomplete | build passes, no tests/tracing | operator UI/alerts/E2E | CZĘŚCIOWE |
| 26–30 | ISO lineage/correlation/R | pain.001+pacs.002 fragment | correct intent | no validation/channel/R handlers | full message profiles and flows | CZĘŚCIOWE/DESIGN |
| 31 | signature | scoped complete | strong | strong tests/evidence | production trust integration | POTWIERDZONE |
| 32–42 | ledger/settlement | schema/resolver only | strong frozen intent | blocker ledger defects | posting/finality/liquidity/cycles | CZĘŚCIOWE/DESIGN |
| 43–50 | egress | claim skeleton | role correct in design | runtime unusable | render/sign/send/retry/receipt | CZĘŚCIOWE/DESIGN |
| 51–56 | routing | none | coherent design | no code | all 24 stories | TYLKO ZAPROJEKTOWANE |
| 57–64 | reconciliation | none | correct read-only model | no code | all 27 stories | TYLKO ZAPROJEKTOWANE |
| 65–72 | case/R/claims | none | generally coherent but incomplete claims | no code | all 26 stories | TYLKO ZAPROJEKTOWANE |
| 73–75 | file/security/docs | docs only | scope coherent | file/security absent | file rail, target IAM | MIXED |

Stories są technicznie weryfikowalne (276/279 `verify:`), lecz słabsze biznesowo: 0 structured owner, 98 descriptions, 50 explicit completion criteria, zero external versioned source. Następny krok planningowy to nie mechaniczne dodanie endpointów, lecz uzupełnienie actor/start/end/negative/timing/manual/standard traceability.

## 12. Katalog przepływów end-to-end

Zidentyfikowano **30 rozłącznych flows**. Dwa intake są częściowo wykonawcze, kilka mechanizmów duplicate/correlation ma działające fragmenty, 16 historycznych value streams jest zaprojektowanych, a incoming CT, SDD i customer claims są nieobecne. Pełna Tabela B z triggerem, kierunkiem, negative/R/recon/ops i statusem: [Aneks B §B.1](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

## 13. SCT

Outgoing SCT zaczyna się dziś od JSON lub single pain.001 i kończy na `VALIDATED`; outbound pacs.008 jest P1. Nie ma route selection, scheme-grade validation, settlement, ledger posting, transport receipt ani finality. Incoming SCT pacs.008 jest absent. Reject/return/recall/claims są projektowe. Dojrzałość **3 — spójny częściowy projekt**, implementacyjnie **4 dla intake, 0–2 dla downstream**.

## 14. SCT Inst

Istnieje generic `(settlement_basis, liquidity_mode)` resolver i poprawna architektonicznie idea GrossInstant, lecz brak scheme mapping, pacs.008, 10-second orchestration, immediate restore, late response, 24×7 operations, beneficiary response i realnego TIPS/RT1/STET adaptera. VoP responsibility nie jest przypisana. Dojrzałość **2 — częściowy projekt**.

## 15. SDD Core

SDD Core jest świadomie poza frozen scope. Brak mandate, Creditor Identifier, collection sequence, pain.008/pacs.003, pre-notification boundary, calendars, pacs.002/004/007, refund 8 weeks/unauthorised 13 months i non-XML claim flows. Nie jest to defect current CT lab, ale jest BLOCKER full-hub ambition. Dojrzałość **0 — brak**.

## 16. SDD B2B

Brak całego produktu. Szczególnie nieobecne są obowiązki debtor PSP związane z walidacją i przechowaniem mandate, różnica refund rights oraz B2B finality/return windows. Nie wolno klonować Core stories: potrzebna source-backed decyzja i osobny lifecycle. Dojrzałość **0 — brak**.

## 17. R-transactions

Model projektowy prawidłowo nie redukuje wszystkiego do `FAILED`, a return-after-finality ma być nową opposite-direction payment. Wykonawczo jest tylko część pacs.002 extraction/correlation. Reject, technical/business/settlement rejection, return, recall/RFRO, response, late/duplicate/orphan mają katalog lub prose, ale brak handlers/timers/ledger/recon/operator completion. Refund/refusal/revocation i SDD R są absent. Dojrzałość **2 — częściowy projekt**.

## 18. Recalls, returns, refunds i reversals

- **Recall/RFRO:** camt.056→case→camt.029 negative lub pacs.004 positive; design only, brak 15-business-day timer.
- **Return:** poprawnie modelowana jako nowa related payment po finality; brak wykonania.
- **Refund:** brak, bo SDD outside scope.
- **Reversal:** ledger schema przewiduje `reversal_of_entry_id`, lecz nie chroni relacji typem/unikalnością; SDD pacs.007 absent.

Diagram procesu R/recall: [Aneks B §B.7](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

## 19. Claims i investigations

Aktualny case concept to thin investigation/decision coordination, nie pełny claims system. EPC SCT formalnie wyróżnia pacs.028, camt.027 Claim Non-Receipt i camt.087 Claim for Value Date Correction. Są tylko coarse P1/P2 prose. Customer complaint, fraud, unauthorised/incorrect/delayed execution, liability, SLA/timers i evidence ownership są nierozstrzygnięte. W SDD część claims nie ma dedykowanego XML; trzeba zaprojektować jawny manual/alternative channel, nie wymyślać komunikatu. Dojrzałość **1 — pomysł/fragment projektu**.

## 20. ISO 20022 message catalogue

Obowiązkowa Tabela C i wersje znajdują się w [Aneksie B §B.3](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md). Current EPC pins m.in. pain.001.001.09, pain.002.001.10, pacs.008.001.08, pacs.002.001.10, pacs.004.001.09, camt.056.001.08, camt.029.001.09, pacs.028.001.03, camt.027.001.07, camt.087.001.06; SDD adds pain.008.001.08, pacs.003.001.08 i pacs.007.001.09.

Debina ma hardened XML parse, nie conformance validation. Nie ma XSD/TVS/profile registry; source timestamp jest błędnie zastąpiony processing time; original lineage nie ma unique invariant; correlation pacs.002 nie ma realnego inbound channel. Dojrzałość ISO **4 — częściowa implementacja bez scheme validation**.

## 21. Integracje z bankami, PSP i CSM

Potwierdzone kanały: REST JSON, REST XML pain.001, OIDC BFF, local Kafka, PostgreSQL. Planowane: file/SFTP/MFT, CSM adapter, ledger/core, GraphQL read-only. Brak gRPC/MQ/protobuf dowodów. Tabela H z protocol/format/direction/SLA/retry/idempotency/monitoring: [Aneks B §B.4](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

System projektowo rozróżnia acceptance, validation, processing, dispatch, CSM acknowledgement, settlement, posting i finality. Current thin FSM nadal nie implementuje wszystkich niezależnych osi, ale nie scala już terminalności biznesowej z finality: recorder zapisuje każdą bieżącą transition jako non-final, a egress nie ma prawa zapisywać payment history. SLA, auth/mTLS/certs, signatures, replay, DLQ and operator runbooks dla zewnętrznego partnera są absent.

## 22. TIPS, STET, RT1, STEP2 i inne wykryte kanały

TIPS publicznie potwierdza 24/7/365, central-bank-money RTGS i current R2026.JUN documents; RT1 potwierdza SCT Inst, TIPS interoperability i public SLA characteristics; STEP2 zapewnia SCT/SDD/file/cycle/R functionality; STET public 2025 baseline opisuje obecny SEPA(EU) instant service. Repo używa tylko „-like” synthetic profiles. Bez participant UDFS/UHB/service documentation, schemas, network/certificate onboarding i certification scenarios nie można ocenić realnej zgodności. Status: **BRAK DANYCH / brak adapterów**, nie „niezgodne”.

## 23. Modele stanów i lifecycle

Frozen target rozdziela pięć osi: business, ISO, finality, transport i receipt. To poprawna podstawa. Actual `PaymentStatus` zawiera `RECEIVED`, `VALIDATED`, `REJECTED`, `DISPATCHED`. `PaymentTransitionTable` może wskazać brak legalnego dalszego przejścia, ale wyłącznie jako topologię FSM; `PaymentHistoryRecorder` zapisuje obecnie `is_final=false` dla każdej przejściowej historii. Forward-only V30 usuwa odziedziczone false-positive `is_final=true` dla `REJECTED` i `DISPATCHED`. To jest naprawa bezpieczeństwa, nie implementacja niezależnego modelu finality. Kod realizuje dziś tylko `RECEIVED→VALIDATED` przez InboxConsumer.

Brakuje: transition source/evidence, expected version, trwałej settlement-owned authority dla finality, suspended/manual states, timeout timers, inbound/outbound variants, explicit late/duplicate policy i stuck detector. `max(seq)+1` w historii jest race-prone. Diagram actual vs required model: [Aneks B §B.8](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

## 24. Settlement i księgowanie

Settlement jest głównie projektem: istnieje resolver profile po frozen tuple oraz DDL ledger. Brak reserve/post/release, actual settlement strategies, liquidity decisions, cycles, finality signal and LedgerPort. Najpoważniejszy defect: deferred trigger bilansuje tylko `sum(amount_minor)` na entry, więc +100 EUR i −100 USD przechodzi; `account_id` nie ma FK. Reversal constraints są niepełne. Są wartościowe append-only/grant/unbalanced tests, ale nie testują tych failure modes.

Przed money movement należy: naprawić DB invariants, zatwierdzić reservation consumption model, wdrożyć triple-enforced port, powiązać idempotency/entry uniqueness z business action i udowodnić concurrency. Return po finality pozostaje nową payment, nie reversal starego ledger entry.

## 25. Reconcyliacja

Projekt jest biznesowo dobry: recon zbiera immutable evidence, wykrywa i eskaluje; nigdy nie naprawia źródłowych danych. EPIC-57–64 mają 0/27 stories done. Brakuje snapshotów, result/mismatch tables, settlement-vs-ledger, balance drift, egress/ISO/case reconciliation, manual matching command i EOD close/reporting. Dojrzałość **2 — spójny projekt bez implementacji**. Diagram: [Aneks B §B.7](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

## 26. Operacje manualne

Frontend daje submit/list/detail/timeline. Nie ma manual retry/release/cancel, orphan/unknown response association, DLQ replay, stuck queue, manual reconciliation, generated response, maker-checker ani operator audit. Każda przyszła akcja musi mieć: role/action permission, precondition i expected version, idempotency key, four-eyes tam gdzie zmienia financial outcome, immutable before/after evidence i recon confirmation. Direct SQL status edits są niedopuszczalne. Operator diagram: [Aneks B §B.10](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

## 27. PSD2, PSD3, PSR i inne regulacje

### Tabela F — Regulatory coverage

| Wymaganie | Źródło | Status prawny | Zastosowanie do Debiny | Pokrycie | Luka |
|---|---|---|---|---|---|
| PSD2 execution/security/unauthorised transactions | Directive 2015/2366 | obowiązuje | wspierać evidence/status/timing; liability zwykle PSP/channel | partial evidence only | no complaint/liability flow/boundary |
| SCA/secure communication | DR 2018/389 | obowiązuje | głównie channel/ASPSP; Debina BFF/IAM only if in flow | OIDC PKCE, CSRF partial | no explicit applicability/transaction authorisation |
| Instant receive/send/10 s/24×7 | Regulation 2024/886 | staged duties already applicable for euro-area PSP | Debina must support if processing instant | generic design | no timer/restore/adapter/SLO |
| Instant fee parity and payer limits | Regulation 2024/886 | applies | product/channel/core responsibility must be assigned | no evidence | open responsibility |
| VoP before authorisation, free | Regulation 2024/886 + EPC VoP v1.0 | current baseline | likely channel/VoP service, Debina may orchestrate evidence | no owner/integration | scope gap, not automatic new module |
| targeted sanctions screening cadence | Regulation 2024/886 Art. 5d | current | PSP duty; technical boundary with sanctions system | no integration | responsibility/data/evidence gap |
| PSD3/PSR fraud/liability/transparency changes | political compromise/tracker | **not law** at cutoff | future impact assessment only | watchlist absent | do not claim compliance |
| AML/fraud reporting | applicable sectoral law/process | bank/PSP responsibility | only if source assigns Debina | BRAK DANYCH | open boundary; legal input needed |

Debina nie powinna implementować SCA, sanctions or fraud decisions tylko dlatego, że payment platform je „kojarzy”. Najpierw responsibility mapping: channel/core/AML system/PSP/Debina. Regulatory compliance cannot be inferred from technical auth or EPC XML validation.

## 28. PostgreSQL 18

Rzeczywista konfiguracja używa image `postgres:18`; full test potwierdził **PostgreSQL 18.4**, 28 migrations i READ COMMITTED. Mocne strony: transactional outbox/inbox, unique idempotency, RLS na selected tables, append-only triggers/grants, deferred ledger balance check, separate schemas and Testcontainers.

### Tabela G — PostgreSQL assessment

| Obszar | Stan PostgreSQL 18 | Ryzyko | Rekomendacja 18 | Możliwość PostgreSQL 19 | Decyzja |
|---|---|---|---|---|---|
| Integrity | partial constraints/history/outbox | invalid ledger/status/amount | DB constraints + concurrency + immutable histories | richer temporal/upsert | use 18 now |
| RLS/ownership | payment+egress only, shared app role elsewhere | tenant/write bypass | applicability matrix, per-schema roles, FORCE where required | no dependency | harden 18 |
| IDs | random UUID | index locality | evaluate `uuidv7()` for technical IDs, not idempotency | not needed | optional 18 |
| Concurrency | READ COMMITTED, `max(seq)+1` | double/race/failure | expected version/locks/unique business effects | no magic fix | design on 18 |
| Outbox/inbox | pattern exists | grants/wiring/semantic failures | dedicated roles, health, crash-window tests | new upsert syntax only convenience | fix 18 |
| Partition/retention | absent | volume/maintenance | data-volume-driven partition/archival | concurrent operations promising | plan 18; observe 19 |
| Backup/DR | no repo evidence | unrecoverable loss | PITR/restore/RPO-RTO/logical replication drills | sequence replication improves | mandatory on 18 |

Additional issues: amount has no positive/scale check and currency defaults EUR; raw archive grants allow UPDATE; history/event tables omit FK and tenant/RLS context; JSON idempotency conflict rolls back raw evidence; idempotency scope is not operation/channel-qualified. Schemat danych przechowuje history, lecz nie wystarczająco chroni its business semantics.

## 29. Gotowość na PostgreSQL 19

PostgreSQL 19 Beta 2 was released 2026-07-16 and remains unsupported pre-release; GA is expected later in 2026, but details may change. Valuable candidates: SQL/PGQ, temporal `FOR PORTION`, `ON CONFLICT DO SELECT RETURNING`, concurrent REPACK/partition operations, parallel autovacuum, online checksum control, logical replication sequence values and new lock/recovery/vacuum stats.

Decision:

- **WYKORZYSTAĆ W POSTGRESQL 18:** current constraints, RLS, uuidv7 where measured, AIO benchmark, logical replication/PITR.
- **PRZYGOTOWAĆ SIĘ NA 19:** portable integration/performance tests and no reliance on undocumented internals.
- **OBSERWOWAĆ:** temporal DML, online maintenance, new statistics and replication improvements.
- **NIE UZALEŻNIAĆ SYSTEMU:** every PG19-only feature until GA and patch maturity.

Debina is „ready” for PG19 only in the modest sense that it can keep SQL portable and run a future lab matrix. No PG19 capability should drive current payment correctness.

## 30. Keycloak i authorization model

Actual version is **Keycloak 26.6.4**, exactly pinned, not 26.6.1. Realm `sepa-nexus` has four realm roles (`operator`, `payment_submitter`, `payment_approver`, `reference_data_admin`), two clients and four seeded users. Backend/frontend expect at least payment_viewer/auditor and broader 11/12-role model; those principals are absent. No groups, composites, client roles, service accounts/M2M or maker-checker workflow.

BFF strengths: Authorization Code+PKCE/state/nonce, HttpOnly opaque session cookie, server-held tokens, CSP/security headers and CSRF for POST. Gaps: no access-token audience mapper/validator for `sepa-api`, issuer-only validation; in-memory single-instance session/pending store; no refresh path; state-changing logout via GET; password/direct grants enabled for walking-skeleton. Backend service authorization is stronger than UI-only auth, but end-to-end Keycloak→claim→service→RLS→manual-action matrix is incomplete. Dojrzałość **4 — częściowa implementacja**.

## 31. Spring, JDK 25 i granice modułów

Verified runtime/build: JDK 25.0.3, Maven 3.9.11, Spring Boot 4.1.0, Framework 7.0.8, Security 7.1.0, Modulith 2.1.0, Hibernate 7.4.1, Flyway 12.11.0, Testcontainers 2.0.5. Stack is coherent and compatible. No JDK preview feature underpins business contracts.

Business boundaries broadly match capabilities and frozen architecture. Controllers are thin and service owns authorization/rule in implemented payment slice. Weaknesses: many logical modules sit under one Modulith root `com.sepanexus.modules`, so some intended boundaries are not independently verified; most planned modules do not exist; per-schema role wiring is incomplete; GraphQL owner is blocked. Spring/Modulith support clarity when used as guardrails, but annotations/tests cannot substitute for the missing processes.

## 32. Kafka i spójność event-driven

Resolved Kafka client is 4.2.1 / Spring Kafka 4.1.0; local runtime image is dangerously `apache/kafka:latest`. AsyncAPI lists 28 channels with skeletal `{}` payloads. Actual code has a thin payment outbox/inbox and ISO outbox fragments.

Critical semantic defect: outbox event type `payment.submitted.v1` is sent to `payment.validated`, then consumed by payment-lifecycle to cause validation, while AsyncAPI assigns `payment.received` ingress→payment-lifecycle and `payment.validated` payment-lifecycle→routing. This is not merely naming; it can assert validation without a validation fact.

No controlled schema evolution, retry topics, DLQ, replay policy, aggregate partition key or out-of-order contract exists. DB+Kafka atomicity relies on outbox, but runtime role failure blocks dispatch. Kafka producer idempotence prevents some broker retry duplicates; it does not prevent DB/Kafka split or duplicate business side effects. Diagrams: [Aneks B §B.9](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md).

## 33. Audit, monitoring i observability

Implemented: `X-Correlation-Id` echo, Actuator health/metrics, one consumer-lag gauge, database evidence/history. Missing: MDC/trace propagation, OpenTelemetry, Prometheus stack, dashboards, alerts, outbox age, retry/DLQ/stuck metrics, CSM SLA, end-to-end correlation and operator action audit. HTTP correlation ID is replaced by random event/outbox IDs, breaking trace continuity.

Outbox failure is only logged and row remains unpublished. The full Maven run logged repeated dispatcher permissions failures but stayed green. This is direct evidence that health/alert/test oracles do not represent operational truth.

## 34. Testowalność

Mocne strony: PostgreSQL 18 Testcontainers, grants/RLS/append-only tests, signature-before-parse and XXE negatives, architecture tests, deterministic `ClockPort`, idempotency and lineage slices. Audyt uruchomił:

- planning validators: PASS;
- backend full `./mvnw test`: **356 tests, 0 failures/errors, BUILD SUCCESS**, PostgreSQL 18.4, ale z repeated scheduled `permission denied` i Kafka localhost reconnect noise;
- frontend `lint`: 0 errors/1 TanStack compiler warning;
- frontend `typecheck`: PASS;
- frontend production build: PASS po dostępie do network dla Google fonts; build nie jest offline-hermetic.

Nie ma żadnych frontend test/spec files ani Playwright CI. Walking skeleton depends on Keycloak localhost not supplied by backend workflow. Missing risk tests: cross-currency/unknown-account ledger, concurrent same-key idempotency, actual datasource/GUC wiring, event-topic semantics, scheme validation, full lifecycle, timeout/late response, R/recon/ops and authorisation matrix. Minimal test layers per flow są w [Aneksie B §B.12](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md). Dojrzałość **4 — dobra partial test foundation, no E2E assurance**.

## 35. Traceability matrix

Wymagany chain to:

`external source/version/effective status → scheme/regulatory requirement ID → product/direction/rail → flow → capability → EPIC/story → module/API/message → event → table/migration → UI/manual action/role → test → evidence status`

Current internal chain starts at ADR/blueprint→EPIC source→story→verify. External source/product-flow layers are missing, and graph covers only 68 story nodes. SCT, SCT Inst, SDD, integration/security/recon/operations matrices są w [Aneksie B §B.11](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md); full 76-EPIC linkage w [Aneksie A §A.3](annexes/DEBINA-ARTIFACT-EPIC-CATALOG.md). Confidence is high where code/migration/test and official source agree; medium for design-only; `BRAK DANYCH` for restricted CSM detail.

## 36. Rejestr sprzeczności

Najważniejsze siedem: planning status parity; graph status drift; actual DISPATCHED/finality vs ADR; Kafka topic semantics vs AsyncAPI; DB roles vs Spring datasource wiring; target IAM vs realm; historyczne 16 flows misread as implementations. Pełna tabela: [Aneks A §A.5](annexes/DEBINA-ARTIFACT-EPIC-CATALOG.md).

## 37. Rejestr założeń

| ID | Założenie | Status/ryzyko |
|---|---|---|
| ASM-001 | „Debina” oznacza produkt z bieżącego repo SEPA Nexus | WNIOSKOWANE z prompt+cwd; high confidence |
| ASM-002 | Working-tree V28/egress/ledger changes są częścią audytowanego state | POTWIERDZONE jako files, nie release |
| ASM-003 | Current product remains synthetic CT lab until approved scope change | POTWIERDZONE przez AGENTS/blueprint |
| ASM-004 | A real CSM would require restricted participant documentation | POTWIERDZONE przez official portals |
| ASM-005 | Bank/channel owns SCA/VoP unless an artifact assigns orchestration to Debina | OPEN; do not implement from assumption |
| ASM-006 | Core posting/AML/fraud/treasury are external/delegated | WNIOSKOWANE, needs responsibility matrix |
| ASM-007 | Actual production volumes/RPO/RTO/retention are unknown | BRAK DANYCH; no sizing claims |
| ASM-008 | EPC 2025 v1.1/IG v1.0 remain current at cutoff | POTWIERDZONE official sources |

## 38. Pełna lista luk

Pełny rejestr zawiera **45 gaps: 6 BLOCKER, 13 CRITICAL, 16 HIGH, 8 MEDIUM, 2 LOW**, z dowodem, wpływem, rekomendacją, ownerem i proposed backlog: [Aneks C §C.1](annexes/DEBINA-GAP-RISK-BACKLOG.md). Najważniejsze IDs: `BUS-GAP-001`, `SCHEME-GAP-001/002`, `FLOW-GAP-001–005`, `MSG-GAP-001/002`, `DATA-GAP-001–004`, `KAFKA-GAP-001`, `SEC-GAP-001`, `INTEG-GAP-001`, `RECON-GAP-001`, `OPS-GAP-001`.

## 39. Ryzyka

Top failure modes: accepted-but-never-dispatched, cross-currency journal accepted, false finality, wrong correlation, duplicate money effect, instant timeout without restore, tenant/token exposure, false certification claim, concurrent unsafe operator action and unrecoverable disaster. Full FMEA: [Aneks C §C.2](annexes/DEBINA-GAP-RISK-BACKLOG.md).

## 40. Rekomendowany backlog

### Tabela I — skrót finalnego backlogu

| Priorytet | EPIC/story | Uzasadnienie biznesowe | Ryzyko | Zależności | Rozmiar |
|---|---|---|---|---|---|
| A0 | Product/standards scope gate | ustala prawdziwy produkt | błędny backlog/compliance | team decision/ADR if needed | S |
| A1–A4 | ledger, finality, dispatcher/Kafka, LedgerPort | poprawność pieniędzy i spine | corruption/stuck/false state | current decision packets | M–L |
| A5 | first outgoing CT E2E | dowód wartości i procesowej kompletności | brak produktu | A1–A4 | XL |
| B1–B4 | standards registry, ISO/CSM profiles, security, one adapter | external integration | failed onboarding | chosen CSM/docs | L–XL |
| D/F | instant/R/claims/recon/ops | scheme and operational completion | unsafe exceptions | stable CT slice | L–XL |
| E | SDD discovery then product | full-hub expansion | invented architecture | explicit scope approval | M→XL |
| C/G/H | scale, PG18 hardening, PG19 lab | resilience/future | volume/recovery | working E2E | S–L |

Pełna Tabela I i acceptance criteria: [Aneks C §C.3–C.4](annexes/DEBINA-GAP-RISK-BACKLOG.md). Relative sizes are not time estimates.

## 41. Kolejność dalszej implementacji

1. Stop-the-line: product scope, ledger constraints, five-axis/finality, dispatcher datasource/GUC and Kafka semantic contract.
2. Complete existing decision packets: ISO ownership, ledger reservation, settlement profile, egress render/profile, correlation fallback, GraphQL owner, reference-data CRUD, simulation owner.
3. Build one outgoing SCT slice end-to-end including negative/timeout/duplicate/recon/operator evidence.
4. Add real pacs.002 ingress and one selected CSM simulator/adapter with versioned profiles.
5. Harden IAM, audit, observability and recovery; then incoming CT.
6. Complete return/recall/claims and recon/case/operator flows.
7. Scale/DR/partition/replay after working functional truth.
8. Consider SDD only after formal scope/architecture source. Observe PG19 until GA/maturity.

## 42. Obszary niewymagające zmian

Nie zmieniać bez superseding ADR:

- CPC-SP single deployable and one Payment Lifecycle spine;
- settlement strategy keyed by business tuple, never CSM/profile name;
- one-writer-per-schema triple enforcement goal;
- explicit finality separate from accepted/posted/delivered;
- return-after-finality as new opposite payment;
- five status axes;
- egress transport-only responsibility;
- read-only reconciliation and decision-only case;
- GraphQL read-only / REST-gRPC commands;
- services owning business rule+authorization and thin controllers/repos;
- RLS-only tenancy, no Hibernate tenant filter;
- signature verification-before-parse, immutable evidence and deterministic ClockPort.

## 43. Decyzje, których nie należy jeszcze podejmować

- Nie wybierać SDD module/data architecture przed scope decision and source-backed discovery.
- Nie wybierać realnego CSM/transport/certification approach without participant docs and target institution.
- Nie „upgrade’ować” ISO message versions do najnowszego ISO repository zamiast EPC/CSM pinned version.
- Nie przypisywać Debinie SCA/VoP/AML/fraud/core posting bez responsibility matrix.
- Nie projektować na PostgreSQL 19 beta features.
- Nie ustalać partitioning, retention, RPO/RTO i capacity bez volume/legal/ops inputs.
- Nie zmieniać frozen finality, writer ownership, ledger or module map by implementation shortcut.

## 44. Otwarte pytania

1. Czy target pozostaje QA/SDET CT labem, czy ma zostać realnym multi-scheme hubem?
2. Jaki jeden produkt/kierunek/CSM jest celem pierwszego integracyjnego proof?
3. Kto owns VoP, SCA, limits, account posting, AML/fraud/sanctions and customer liability?
4. Jaki jest approved ledger reservation/release and reversal model?
5. Jaki jest authoritative scheme-profile/egress artifact/render library decision?
6. Jakie są actual participant specs, certificates, SLAs and certification packs?
7. Jakie są volumes, retention, PII boundary, RPO/RTO and EOD requirements?
8. Które manual actions require maker-checker and which roles may execute them?
9. Czy incoming interbank CT i customer claims are approved scope additions?
10. Czy full 279-story deep dive is required before next implementation wave?

## 45. Wnioski końcowe

### Jednoznaczne odpowiedzi

1. **Spójna wizja produktu?** Tak dla synthetic CT/ISO learning lab; nie dla pełnego huba z briefu.
2. **Czy EPIC-i tworzą pełny concentrator?** Nie. Tworzą szeroki projekt CT platform, ale brakuje incoming, SDD, CSM and executable closure.
3. **Spójny E2E lifecycle?** Spójny frozen design, niespójna/cienka implementacja.
4. **Incoming i outgoing kompletne?** Nie; outgoing intake partial, incoming interbank absent.
5. **XML sufficiently covered?** Nie; no scheme/CSM validation and most pacs/camt missing.
6. **R-transactions first-class?** W design częściowo tak, w runtime nie.
7. **Claims/investigations adequate?** Nie.
8. **Bank/CSM integrations feasible?** Architectural boundaries suggest feasibility, but no adapter/spec/cert evidence.
9. **PostgreSQL 18 correctly used?** Valuable partial use, but critical ledger/data gaps mean no for money-bearing flow.
10. **Ready for PostgreSQL 19?** Only for future compatibility experiments; no production dependency.
11. **Keycloak matches roles?** No; 4 implemented vs broader target, no audience/SoD.
12. **Spring/Kafka support process?** Spring/Modulith support clarity; Kafka current semantics/wiring complicate and break flow.
13. **Ready for integration pilot?** **No.**
14. **Ready for certification?** **No.**
15. **Production ready?** **No.**
16. **First five problems:** ledger invariants; finality/FSM; dispatcher+Kafka contract; first outgoing CT E2E; standards/CSM validation traceability.
17. **Five strengths:** frozen responsibility model; five-axis/finality semantics; one-writer intent; raw/idempotency/lineage/outbox/inbox evidence; strong DB/signature/architecture test foundation.
18. **Next logical stage:** scope gate + stop-the-line fixes, then one source-backed outgoing SCT vertical slice.

### Maturity scale

| Obszar | 0–8 | Uzasadnienie |
|---|---:|---|
| SCT | 3 | coherent partial design, intake implementation, no E2E/incoming |
| SCT Inst | 2 | generic strategy design only |
| SDD Core | 0 | explicitly absent |
| SDD B2B | 0 | explicitly absent |
| R-transactions | 2 | design + pacs.002 fragment |
| Claims/investigations | 1 | coarse idea/prose |
| ISO 20022 | 4 | partial pain.001/pacs.002 implementation, no conformance validation |
| Integration | 2 | internal REST/Kafka only; no external CSM |
| Data integrity | 4 | strong foundation but blocker ledger/history defects |
| Security | 4 | working local BFF/realm, incomplete roles/audience/RLS |
| Operations | 2 | planned, thin read UI |
| Reconciliation | 2 | coherent design, no code |
| Testability | 4 | good backend/DB slices, no full business/Playwright assurance |

Overall: **4 as a learning lab; 2 as a real payment hub**. Levels 6–8 are not justified.

## 46. Aneksy

- [Aneks A — Katalog artefaktów i klasyfikacja 76 EPIC-ów](annexes/DEBINA-ARTIFACT-EPIC-CATALOG.md)
- [Aneks B — Katalog 30 flows, ISO 20022, integrations, diagrams and traceability](annexes/DEBINA-FLOWS-MESSAGES-TRACEABILITY.md)
- [Aneks C — 45 gaps, FMEA, final backlog and PostgreSQL decisions](annexes/DEBINA-GAP-RISK-BACKLOG.md)

## 47. Słownik terminów i skrótów

| Termin | Znaczenie |
|---|---|
| CSM | Clearing and Settlement Mechanism |
| SCT / SCT Inst | SEPA Credit Transfer / SEPA Instant Credit Transfer |
| SDD Core / B2B | SEPA Direct Debit Core / Business-to-Business |
| R-transaction | scheme-defined reject/refusal/return/refund/reversal/revocation/recall-related process, not generic failure |
| RFRO | Request for Recall by the Originator |
| VoP | Verification of Payee |
| EPC | European Payments Council |
| IPR | Instant Payments Regulation, Regulation (EU) 2024/886 |
| C2PSP / Inter-PSP | Customer-to-PSP / PSP-to-PSP message profile |
| TVS | Technical Validation Subset published for a scheme profile |
| Finality | explicit point after which settlement/payment outcome is final according to configured profile; not delivery |
| Outbox/Inbox | transactional publication record / consumer deduplication record |
| RLS / GUC | PostgreSQL Row-Level Security / runtime configuration variable used by policy |
| SoD | Segregation of Duties; maker-checker/four-eyes |
| PITR / RPO / RTO | Point-in-Time Recovery / Recovery Point Objective / Recovery Time Objective |
| POTWIERDZONE | direct code/config/migration and proportionate test evidence |
| CZĘŚCIOWO POTWIERDZONE | a working fragment without complete process evidence |
| TYLKO ZAPROJEKTOWANE | blueprint/EPIC/story only |
| SPRZECZNE | authoritative artifacts or code semantics disagree |
| BRAK DANYCH | evidence unavailable; no inference made |

---

**Final statement:** Debina is worth continuing because its frozen conceptual model is stronger than its implementation coverage. The safe path is not architectural reinvention; it is closing the detected contradictions, proving one complete CT flow, and expanding only through versioned standards and explicit product decisions.
