# HANDOFF

## Zadanie

Debina prowadzi Phase E jako source-backed program kontrolowanej migracji
backlogu i badań end-to-end SEPA. Sesja zakończyła E0 research/reconciliation
oraz zapisała kanoniczny program; nie implementowała funkcji produkcyjnych.

## Zrobione

- Utworzono
  `planning/programs/DEBINA-PHASE-E-CONTROLLED-BACKLOG-MIGRATION.md`: baseline,
  authority, regulacje, scheme/ISO/CSM research, lifecycle, kohorty E0–E8,
  sześć kontraktów E1, architecture realization, full-stack backlog, test
  allocation, warning burn-down, review queues, DoR/DoD i commit plan.
- Wybrano E1 dla `UC-SCT-002`:
  `EPIC-31/31.2`, `EPIC-19/19.2`, `EPIC-19/19.4`,
  `EPIC-26/26.3`, `EPIC-26/26.4` i proposed `E1-06`.
  `19.4` sklasyfikowano jako `PARTIALLY_IMPLEMENTED`, ponieważ działający
  endpoint nie dowodzi jeszcze ISO XSD/EPC TVS, pełnego profilu kanału ani
  zatwierdzonych limitów. Nie zmieniono kanonicznych statusów stories.
- Dodano proposal-first reconciliation:
  `planning/backlog-change-proposals/phase-e/phase-e-reconciliation.yaml`
  oraz frontend matrix
  `planning/programs/DEBINA-FRONTEND-CAPABILITY-RECONCILIATION.md`.
- Dodano source artifacts:
  `SEPA-SCHEME-COVERAGE-MATRIX.yaml`,
  `ISO-20022-MESSAGE-COVERAGE-MATRIX.yaml`,
  `CSM-CAPABILITY-MATRIX.yaml`, `SOURCE-GAP-REGISTER.yaml` i
  `SOURCE-CHANGE-REGISTER.yaml`; rozszerzono source registry/evidence.
  Manifest ma 19 wpisów z wersją, datami, sekcjami, addenda, dostępem i
  review state. Participant-only STEP2/RT1/TIPS/STET pozostają metadata-only.
- Dodano metodę i szablon mapowania:
  `docs/data/ISO-XML-DOMAIN-DATABASE-MAPPING-METHOD.md` oraz
  `ISO-XML-DOMAIN-DATABASE-MAPPING-TEMPLATE.yaml`. Dodano
  `docs/testing/SEPA-PAYMENT-TEST-ALLOCATION-MATRIX.md`; Playwright E1 to
  dokładnie jeden login/upload/result-or-detail smoke.
- Zachowano baseline 79 epików, 304 stories, 296 legacy use-case warnings i
  69 planning-semantic warnings. Phase D pozostała zamknięta.
- Wszystkie wymagane walidatory przeszły z exit 0: classification, use-case
  traceability, source traceability (28 źródeł), methodology assurance,
  planning semantics, module catalog (15), ADR lifecycle, skill evals,
  wszystkie skills, story inventory i enterprise governance. Wszystkie
  zmienione YAML parsują się. `MODEL-BEHAVIOR` pozostaje uczciwie
  `NOT_EXECUTED`.
- Chroniony `build/generated-spring-modulith/javadoc.json` zachował SHA-256
  `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`.
- Lokalne commity Phase E przed tym handoffem:
  `a88bd71`, `0aa882f`, `f9850a6`, `e4aed46`. Nie wykonano push.

## Utknęliśmy na

Planowanie sesji nie ma blockera. Implementacja E1 pozostaje celowo
`DECISION_BLOCKED`/review-gated na zatwierdzonym profilu kanału i detached
signature, legalnym/pinned zestawie EPC TVS, limitach payload/depth/text/count
oraz decyzjach retencji i ekspozycji evidence. Szczegółowe STEP2/RT1/TIPS
MyStandards/STET participant rules wymagają lawful participant documentation.
`dagger core version` nie mógł połączyć się z
`/run/dagger/engine.sock`; to diagnostyka środowiska, nie defekt ani ponowne
otwarcie Phase D.

## Plan na następny krok

Otwórz sekcję E1 w
`planning/programs/DEBINA-PHASE-E-CONTROLLED-BACKLOG-MIGRATION.md` i
przeprowadź wspólny `PAYMENTS_DOMAIN_REVIEW`, `ISO_MESSAGE_REVIEW` oraz
`SECURITY_REVIEW` profilu kanału/signature/TVS/limitów; zapisz decyzje i
dopiero potem materialnie zaktualizuj sześć wybranych stories do kontraktu
`ENFORCED`.

## Pułapki, których nie wolno powtórzyć

- Nie nazywać projektu Ed25519 lub detached-header konwencji wymogiem EPC.
- Nie uznawać XSD za EPC TVS ani ISO “latest” za wersję wskazaną przez scheme
  lub rail profile.
- Nie implementować partial-file processing, Spring Batch ani File/Group
  aggregate z obecnych nazw stories bez źródła, product decision i
  architecture admission.
- Nie kopiować TIPS, RT1, STEP2, STET ani local-community rules do common core
  i nie wyprowadzać finality z dispatch/delivery/status bez authority evidence.
- Nie budować GraphQL mutation lub frontend-owned modelu; REST/gRPC zachowują
  commands, GraphQL pozostaje query-only i source-owned.
- Nie wykonywać hurtowej migracji 304 stories, broad Playwright, zmian
  produkcyjnych, push ani modyfikacji chronionego Modulith javadoc w E1 review.
