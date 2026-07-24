# HANDOFF

## Zadanie

Debina realizuje Phase E1 jako kontrolowane domknięcie integralności kohorty
signed `pain.001`, bez implementacji produkcyjnej i bez migracji kanonicznych
stories. Bieżący rezultat to mechanicznie zwalidowany pakiet decyzyjny dla
rzeczywistych ludzkich review.

## Zrobione

- Skorygowano referencje Phase E do katalogów kanonicznych: approval wskazuje
  `UC-SCT-APPROVAL-001`, return/recall wskazują `UC-RTRANS-001` i
  `UC-RTRANS-002`, a wiadomości investigation bez przyjętego actor goal
  pozostają `HUMAN_REVIEW_REQUIRED`. Pseudo-slice `UC-SCT-002/S1` zastąpiono
  kanonicznym `UCS-SCT-002-A`.
- Ustalono kontrakt slices `UC-SCT-002`: `UCS-SCT-002-A` to zweryfikowane
  wejście przez walidację, mapowanie i accepted-payment lineage;
  `UCS-SCT-002-B` to nieudany podpis z trwałym bezpiecznym verdict/evidence,
  bez parsowania i mapowania płatności.
- Zastąpiono niekanoniczny `PROPOSED-E1-06` typowanym
  `PHASE-E1-STORY-PROPOSAL-001` i rekomendacją
  `ADD_STORY_TO_EXISTING_EPIC` jako przyszłe `EPIC-24/24.10`. Istniejących
  plików epików/stories nie zmieniono.
- Utworzono pełny pakiet
  `planning/reviews/phase-e/e1/` z rejestrem decyzji, bramką approvals i
  pięcioma dokumentami review. Wszystkie stany pozostają `NOT_REVIEWED`;
  migracja kanonicznych stories jest wyłączona.
- Dodano 28-polowy artefakt
  `docs/data/mappings/E1-PAIN001-SINGLE-INSTRUCTION-MAPPING.yaml`. Wykrywa on
  m.in. semantyczne pomieszanie source `GrpHdr/CreDtTm` z receive time oraz
  brak mapowania `NbOfTxs`, `CtrlSum`, `PmtMtd`, requested execution date,
  parties/agents, remittance, purpose i addresses.
- Rozdzielono `evidence_status` jako kompletność metadata od
  `semantic_review_state` jako ludzkiego domain/ISO/legal assurance.
  `SOURCE_CONFIRMED + READY` wymaga teraz `VERIFIED + HUMAN_APPROVED` dla
  każdego materialnego evidence.
- Dodano `tools/planning/validate-phase-e-artifacts.py`, 13 negatywnych
  fixtures i jeden poprawny przypadek. Walidator sprawdza referencje,
  ownership slices, źródła/evidence/rules, moduły, quality scenarios,
  story/proposal typing, scalar classifications, tags, outcomes, review
  queues, readiness, approval gate i zgodność E1 między artefaktami. Jest
  uruchamiany przez `validate-enterprise-governance.sh`.
- Focused source refresh potwierdził bez zmiany wersji EPC132-08 2025 v1.0,
  publiczny charakter EPC TVS/XSD ZIP, namespace
  `urn:iso:std:iso:20022:tech:xsd:pain.001.001.09` oraz EPC153-22 v2.1.
  Checksumy, lawful repository distribution i semantyczne approval pozostają
  otwarte.
- Wszystkie wymagane walidatory oraz 10 testów jednostkowych przeszły.
  Końcowy audyt `/tmp/phase-e-reference-audit-final.tsv` ma 442 rozwiązane
  referencje i zero nierozwiązanych. Baseline pozostał 79 epików, 304
  stories, 296 legacy use-case warnings i 69 planning-semantic warnings.
- Lokalne commity przed handoffem: `a403659`, `323e4da`, `5b5b9cf`,
  `ef218b8`, `c0bf6a6`. Nie wykonano push i nie zmieniono kodu produkcyjnego.

## Utknęliśmy na

Nic nie blokuje kompletności pakietu review. Implementacja i migracja stories
są celowo zablokowane brakiem datowanych `HUMAN_APPROVED` dla
`PAYMENTS_DOMAIN_REVIEW`, `ISO_MESSAGE_REVIEW`, `SECURITY_REVIEW`,
`DATABASE_MAPPING_REVIEW`, `ARCHITECTURE_REVIEW`, `PRODUCT_REVIEW` i
`QA_REVIEW`. Otwarte decyzje obejmują kanał REST+BFF, profil detached
Ed25519, trust/rotation/revocation, lawful XSD/TVS acquisition i checksumy,
mierzone limity, raw evidence visibility, RLS, retencję i archiwizację.

## Plan na następny krok

Otwórz `planning/reviews/phase-e/e1/E1-APPROVALS.yaml` i rozpocznij prawdziwy
`PAYMENTS_DOMAIN_REVIEW` od zatwierdzenia albo odrzucenia kontraktu slices i
claimów w `PAYMENTS-DOMAIN-REVIEW.md`; zapisz tożsamość reviewera, datę i
dowód decyzji, nie zmieniając jeszcze kanonicznych stories.

## Pułapki, których nie wolno powtórzyć

- `UCS-SCT-002-B` nie jest operational-read slice; oznacza failure podpisu
  przed parse/mapping. Payment detail i ISO lineage są kontynuacją A.
- Nie używać authority tags jako `source_classification` ani nie nazywać
  metadata `VERIFIED` ludzkim semantic approval.
- Nie przedstawiać detached headers lub Ed25519 jako wymogu EPC i nie uznawać
  ISO XSD za EPC TVS.
- Nie pobierać/commitować XSD lub TVS bez lawful acquisition, policy i
  checksum review.
- Nie tworzyć `EPIC-24/24.10` ani nie migrować pięciu legacy stories bez
  kompletu datowanych approvals.
- Nie rozszerzać E1 na batch, clearing, settlement, R-transactions, SDD,
  broad Playwright lub nowy moduł/agregat.
- Nie wykonywać push ani nie modyfikować
  `build/generated-spring-modulith/javadoc.json`.
